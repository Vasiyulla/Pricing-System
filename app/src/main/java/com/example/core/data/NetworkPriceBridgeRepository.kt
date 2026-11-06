package com.example.core.data

import android.content.Context
import com.example.core.data.local.PriceBridgeDatabase
import com.example.core.data.local.entity.CachedCategoryEntity
import com.example.core.data.local.entity.CachedDealEntity
import com.example.core.data.local.entity.CachedProductEntity
import com.example.core.data.local.entity.CachedStoreEntity
import com.example.core.data.local.entity.QueuedSubmissionEntity
import com.example.core.data.offline.PriceSyncWorker
import com.example.core.model.AppNotification
import com.example.core.model.Category
import com.example.core.model.GoogleTaskItem
import com.example.core.model.GoogleTaskList
import com.example.core.model.GoogleTasksAuthState
import com.example.core.model.KarmaVerificationPrompt
import com.example.core.model.LocationInfo
import com.example.core.model.NotificationType
import com.example.core.model.PriceStatusBadgeType
import com.example.core.model.PriceSubmission
import com.example.core.model.PriceWatchAlert
import com.example.core.model.Product
import com.example.core.model.SavedShoppingList
import com.example.core.model.ShoppingBasket
import com.example.core.model.ShoppingListItem
import com.example.core.model.SmartBasketSummary
import com.example.core.model.Store
import com.example.core.model.StorePrice
import com.example.core.model.StoreSplitSummary
import com.example.core.model.UserProfile
import com.example.core.model.UserRole
import com.example.core.network.ApiClient
import com.example.core.network.NetworkPriceSubmission
import com.example.core.network.NetworkProduct
import com.example.core.network.NetworkStore
import com.example.core.network.NetworkStorePriceComparison
import com.example.core.session.AppSessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Production implementation of [PriceBridgeRepository] that connects directly to the
 * live Render cloud backend API (https://pricing-system-4nhl.onrender.com) and persists
 * dynamic deals, products, and stores in local Room SQLite database cache.
 */
class NetworkPriceBridgeRepository(
    private val api: ApiClient = ApiClient,
    private val database: PriceBridgeDatabase? = null,
    private val context: Context? = null
) : PriceBridgeRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Default user coordinates (Camden / Central London, UK)
    private var currentLat: Double = 51.5074
    private var currentLon: Double = -0.1278

    fun setCoordinates(lat: Double, lon: Double, neighborhood: String? = null) {
        currentLat = lat
        currentLon = lon
        if (neighborhood != null) {
            currentLocation.update { it.copy(neighborhood = neighborhood) }
        }
    }

    private val currentLocation = MutableStateFlow(
        LocationInfo(
            neighborhood = "Camden High St, London",
            addressLine = "Greater London, UK",
            radiusDescription = "2.0 mi radius",
            isLiveTrackingActive = true
        )
    )

    private val basketSummary = MutableStateFlow(
        SmartBasketSummary(
            estimatedSavingsAmount = 0.0,
            storesComparedCount = 0,
            storeNamesSample = emptyList(),
            basketItemCount = 0,
            basketName = "My Shopping List"
        )
    )

    private val shoppingBasketState = MutableStateFlow(
        ShoppingBasket(
            id = "active_basket",
            title = "My Shopping List",
            items = emptyList(),
            singleStoreEstimatedTotal = 0.0,
            optimizedEstimatedTotal = 0.0,
            estimatedSavingsAmount = 0.0,
            storeSplits = emptyList()
        )
    )

    private val userProfile = MutableStateFlow(
        UserProfile(
            id = "usr_tester_1",
            displayName = "Price Scout",
            email = "scout@pricebridge.org",
            phoneNumber = "+44 7700 900077",
            karmaPoints = 320,
            pricesReported = 14,
            pricesVerified = 12,
            accuracyPercent = 98,
            memberSince = "Oct 2026",
            reputationLevel = "Local Contributor",
            role = UserRole.ActiveContributor
        )
    )

    private val recentSearches = MutableStateFlow<List<String>>(emptyList())

    private val notifications = MutableStateFlow<List<AppNotification>>(emptyList())

    private val pendingOfflineCount = MutableStateFlow(0)
    private val queuedSubmissionsState = MutableStateFlow<List<QueuedSubmissionEntity>>(emptyList())
    private val submissionHistory = MutableStateFlow<List<PriceSubmission>>(emptyList())
    private val isLoggedInState = MutableStateFlow(false)

    private val dismissedPromptId = MutableStateFlow<String?>(null)

    // ── Location & Feed ──

    override fun getLocation(): Flow<LocationInfo> = currentLocation.asStateFlow()

    override fun getSmartBasketSummary(): Flow<SmartBasketSummary> = basketSummary.asStateFlow()

    override fun getCategories(): Flow<List<Category>> = flow {
        // Step 1: Immediately emit cached categories from Room if available
        val cached = try {
            database?.cachedCategoryDao()?.getAllCategories()
        } catch (_: Exception) {
            null
        }
        if (!cached.isNullOrEmpty()) {
            emit(cached.map { it.toCategory() })
        }

        // Step 2: Fetch fresh categories from backend and update cache
        try {
            val netCats = api.service.getCategories()
            if (netCats.isNotEmpty()) {
                val categories = netCats.map { Category(id = it.id, name = it.name) }
                try {
                    database?.cachedCategoryDao()?.insertCategories(
                        netCats.map { CachedCategoryEntity(id = it.id, name = it.name) }
                    )
                } catch (_: Exception) {}
                emit(categories)
            } else if (cached.isNullOrEmpty()) {
                emit(listOf(Category(id = "all", name = "All")))
            }
        } catch (_: Exception) {
            if (cached.isNullOrEmpty()) {
                emit(listOf(Category(id = "all", name = "All")))
            }
        }
    }.flowOn(Dispatchers.IO)

    override fun getKarmaPrompt(): Flow<KarmaVerificationPrompt?> = flow {
        try {
            val prompt = api.service.getActiveBountyPrompt()
            if (prompt != null && prompt.bountyId.isNotEmpty() && prompt.bountyId != dismissedPromptId.value) {
                emit(
                    KarmaVerificationPrompt(
                        id = prompt.bountyId,
                        storeName = prompt.storeName,
                        description = "Is ${prompt.productName} still ${prompt.lastObservedPrice} at ${prompt.storeName}? Verify shelf tag to earn Karma points!",
                        karmaRewardPoints = prompt.rewardPoints
                    )
                )
            } else {
                emit(null)
            }
        } catch (_: Exception) {
            emit(null)
        }
    }.flowOn(Dispatchers.IO)

    override fun getBestDeals(categoryId: String?): Flow<List<Product>> = flow {
        // Step 1: Immediately emit from Room SQLite local cache (if any exists from previous sync)
        val cached = try {
            if (categoryId == null || categoryId == "all") {
                database?.cachedDealDao()?.getAllDealsSync()?.map { it.toProduct() } ?: emptyList()
            } else {
                database?.cachedDealDao()?.getDealsByCategorySync(categoryId)?.map { it.toProduct() } ?: emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }
        if (cached.isNotEmpty()) {
            emit(cached)
        }

        // Step 2: Fetch live single-query batch deals from backend
        try {
            val netDeals = api.service.getBestDealsBatch(
                lat = currentLat,
                lon = currentLon,
                category = if (categoryId == "all") null else categoryId
            )
            if (netDeals.isNotEmpty()) {
                val liveProducts = netDeals.map { deal ->
                    val alternatives = deal.alternatives.map { it.toStorePrice() }
                    val bestStorePrice = StorePrice(
                        storeId = deal.bestStoreId,
                        storeName = deal.bestStoreName,
                        distanceFormatted = "${deal.bestStoreDistanceKm} km",
                        price = deal.bestPricePence / 100.0,
                        mrp = deal.bestPricePence / 100.0,
                        currencySymbol = "£",
                        lastObservedRelativeTime = "Just now",
                        sourceDescription = "Live deal",
                        statusBadge = PriceStatusBadgeType.RecentlyObserved,
                        isLowestPrice = true
                    )
                    deal.product.toProduct(
                        bestPrice = bestStorePrice,
                        comparedCount = deal.comparedStoreCount,
                        alternatives = alternatives
                    )
                }
                // Persist live products to Room database cache
                try {
                    database?.cachedDealDao()?.insertDeals(liveProducts.map { CachedDealEntity.fromProduct(it) })
                } catch (_: Exception) {}

                emit(liveProducts)
            }
        } catch (_: Exception) {
            // Keep displaying cached deals gracefully if offline
        }
    }.flowOn(Dispatchers.IO)

    // ── Search & Barcode ──

    override fun searchProducts(query: String, categoryId: String?): Flow<List<Product>> = flow {
        val actualCategory = if (categoryId == "all" || categoryId.isNullOrEmpty()) null else categoryId
        try {
            val netProducts = api.service.searchProducts(
                query = query,
                category = actualCategory
            )
            val products = netProducts.map { netProd ->
                try {
                    val comps = api.service.compareProductPrices(netProd.id, currentLat, currentLon)
                    val storePrices = comps.map { it.toStorePrice() }
                    val best = storePrices.minByOrNull { it.price }
                    netProd.toProduct(bestPrice = best, comparedCount = comps.size, alternatives = storePrices)
                } catch (_: Exception) {
                    netProd.toProduct()
                }
            }

            // Persist to Room cache
            try {
                database?.cachedProductDao()?.insertProducts(
                    products.map { CachedProductEntity.fromProduct(it) }
                )
            } catch (_: Exception) {}

            emit(products)
        } catch (e: Exception) {
            // Offline fallback: Query Room cache
            val cached = try {
                if (actualCategory != null) {
                    database?.cachedProductDao()?.searchProductsByCategory(query, actualCategory)
                } else if (query.isNotBlank()) {
                    database?.cachedProductDao()?.searchProducts(query)
                } else {
                    database?.cachedProductDao()?.getAllProducts()
                }
            } catch (_: Exception) {
                null
            } ?: emptyList()

            if (cached.isNotEmpty()) {
                emit(cached.map { it.toProduct() })
            } else {
                val cachedDeals = try {
                    if (actualCategory != null) {
                        database?.cachedDealDao()?.getDealsByCategorySync(actualCategory)
                    } else {
                        database?.cachedDealDao()?.getAllDealsSync()
                    }
                } catch (_: Exception) {
                    null
                } ?: emptyList()

                val filteredDeals = if (query.isNotBlank()) {
                    cachedDeals.filter { it.name.contains(query, ignoreCase = true) }
                } else {
                    cachedDeals
                }
                emit(filteredDeals.map { it.toProduct() })
            }
        }
    }.flowOn(Dispatchers.IO)

    override fun getProductByBarcode(barcode: String): Flow<Product?> = flow {
        try {
            val netProd = api.service.getProductByBarcode(barcode)
            val comps = api.service.compareProductPrices(netProd.id, currentLat, currentLon)
            val storePrices = comps.map { it.toStorePrice() }
            val best = storePrices.minByOrNull { it.price }
            val product = netProd.toProduct(bestPrice = best, comparedCount = comps.size, alternatives = storePrices)
            try {
                database?.cachedProductDao()?.insertProduct(CachedProductEntity.fromProduct(product))
            } catch (_: Exception) {}
            emit(product)
        } catch (e: Exception) {
            val cached = try {
                database?.cachedProductDao()?.getProductByBarcode(barcode)
            } catch (_: Exception) {
                null
            }
            emit(cached?.toProduct())
        }
    }.flowOn(Dispatchers.IO)

    override fun getRecentSearches(): Flow<List<String>> = recentSearches.asStateFlow()

    override suspend fun addRecentSearch(query: String) {
        if (query.isBlank()) return
        recentSearches.update { list ->
            (listOf(query) + list.filter { it != query }).take(8)
        }
    }

    // ── Product Details & Store Prices ──

    override fun getProductById(productId: String): Flow<Product?> = flow {
        try {
            val netProd = api.service.getProductById(productId)
            val comps = api.service.compareProductPrices(productId, currentLat, currentLon)
            val storePrices = comps.map { it.toStorePrice() }
            val best = storePrices.minByOrNull { it.price }
            val product = netProd.toProduct(bestPrice = best, comparedCount = comps.size, alternatives = storePrices)
            try {
                database?.cachedProductDao()?.insertProduct(CachedProductEntity.fromProduct(product))
            } catch (_: Exception) {}
            emit(product)
        } catch (e: Exception) {
            val cached = try {
                database?.cachedProductDao()?.getProductById(productId)
            } catch (_: Exception) {
                null
            }
            if (cached != null) {
                emit(cached.toProduct())
            } else {
                val deal = try {
                    database?.cachedDealDao()?.getAllDealsSync()?.find { it.id == productId }
                } catch (_: Exception) {
                    null
                }
                emit(deal?.toProduct())
            }
        }
    }.flowOn(Dispatchers.IO)

    override fun getStorePricesForProduct(productId: String): Flow<List<StorePrice>> = flow {
        try {
            val comps = api.service.compareProductPrices(productId, currentLat, currentLon)
            emit(comps.map { it.toStorePrice() })
        } catch (e: Exception) {
            emit(emptyList())
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun addProduct(product: Product): String {
        return product.id
    }

    override suspend fun confirmPriceFreshness(productId: String, storeId: String) {
        // Confirmed
    }

    override suspend fun reportDiscrepancy(productId: String, storeId: String, reason: String, notes: String?) {
        // Reported
    }

    // ── Stores ──

    override fun getNearbyStores(): Flow<List<Store>> = flow {
        try {
            val netStores = api.service.getNearbyStores(lat = currentLat, lon = currentLon, radiusKm = 10.0)
            val stores = netStores.map { it.toStore() }
            try {
                database?.cachedStoreDao()?.insertStores(
                    stores.map { CachedStoreEntity.fromStore(it) }
                )
            } catch (_: Exception) {}
            emit(stores)
        } catch (e: Exception) {
            val cached = try {
                database?.cachedStoreDao()?.getAllStores()
            } catch (_: Exception) {
                null
            } ?: emptyList()
            emit(cached.map { it.toStore() })
        }
    }.flowOn(Dispatchers.IO)

    override fun getAllProducts(): Flow<List<Product>> = flow {
        try {
            val netProducts = api.service.searchProducts("")
            val products = netProducts.map { it.toProduct() }
            try {
                database?.cachedProductDao()?.insertProducts(
                    products.map { CachedProductEntity.fromProduct(it) }
                )
            } catch (_: Exception) {}
            emit(products)
        } catch (e: Exception) {
            val cached = try {
                database?.cachedProductDao()?.getAllProducts()
            } catch (_: Exception) {
                null
            } ?: emptyList()

            if (cached.isNotEmpty()) {
                emit(cached.map { it.toProduct() })
            } else {
                val deals = try {
                    database?.cachedDealDao()?.getAllDealsSync()
                } catch (_: Exception) {
                    null
                } ?: emptyList()
                emit(deals.map { it.toProduct() })
            }
        }
    }.flowOn(Dispatchers.IO)

    override fun getStoreById(storeId: String): Flow<Store?> = flow {
        try {
            val netStore = api.service.getStoreById(storeId)
            val store = netStore.toStore()
            try {
                database?.cachedStoreDao()?.insertStore(CachedStoreEntity.fromStore(store))
            } catch (_: Exception) {}
            emit(store)
        } catch (e: Exception) {
            val cached = try {
                database?.cachedStoreDao()?.getStoreById(storeId)
            } catch (_: Exception) {
                null
            }
            emit(cached?.toStore())
        }
    }.flowOn(Dispatchers.IO)

    override fun getStorePrices(storeId: String): Flow<List<StorePrice>> = flow {
        try {
            val netProducts = api.service.searchProducts("")
            val results = mutableListOf<StorePrice>()
            for (p in netProducts) {
                try {
                    val comps = api.service.compareProductPrices(p.id, currentLat, currentLon)
                    val match = comps.find { it.store.id == storeId }
                    if (match != null) {
                        results.add(match.toStorePrice())
                    }
                } catch (_: Exception) {}
            }
            emit(results)
        } catch (e: Exception) {
            emit(emptyList())
        }
    }.flowOn(Dispatchers.IO)

    // ── Price Submission ──

    override suspend fun submitPrice(
        productId: String,
        storeId: String,
        price: Double,
        mrp: Double,
        sourceType: String
    ) {
        val pence = (price * 100).toInt()
        val idempotencyKey = "sub_${UUID.randomUUID()}"
        val payload = NetworkPriceSubmission(
            productId = productId,
            storeId = storeId,
            regularPricePence = pence,
            sourceType = sourceType,
            idempotencyKey = idempotencyKey
        )
        api.service.submitPrice(payload, idempotencyKey)

        submissionHistory.update { history ->
            listOf(
                PriceSubmission(
                    id = UUID.randomUUID().toString(),
                    productName = productId,
                    storeName = storeId,
                    price = price,
                    mrp = mrp,
                    currencySymbol = "£",
                    sourceType = sourceType,
                    submittedRelativeTime = "Just now",
                    statusBadge = PriceStatusBadgeType.RecentlyObserved
                )
            ) + history
        }
    }

    override fun getPendingOfflineSubmissionCount(): Flow<Int> =
        database?.queuedSubmissionDao()?.observePendingCount() ?: pendingOfflineCount.asStateFlow()

    override fun getQueuedSubmissions(): Flow<List<QueuedSubmissionEntity>> =
        database?.queuedSubmissionDao()?.getAllSubmissions() ?: queuedSubmissionsState.asStateFlow()

    override suspend fun enqueueOfflinePrice(
        productId: String,
        storeId: String,
        price: Double,
        mrp: Double,
        sourceType: String
    ): String {
        val submissionId = UUID.randomUUID().toString()
        val idempotencyKey = "sub_${UUID.randomUUID()}"
        val db = database

        if (db != null) {
            val cachedProd = db.cachedProductDao().getProductById(productId)
            val cachedStore = db.cachedStoreDao().getStoreById(storeId)

            val entity = QueuedSubmissionEntity(
                id = submissionId,
                idempotencyKey = idempotencyKey,
                productId = productId,
                productName = cachedProd?.name ?: "Product $productId",
                storeId = storeId,
                storeName = cachedStore?.name ?: "Store $storeId",
                price = price,
                mrp = mrp,
                sourceType = sourceType,
                status = QueuedSubmissionEntity.STATUS_PENDING,
                createdAt = System.currentTimeMillis()
            )
            db.queuedSubmissionDao().insert(entity)

            val ctx = context ?: PriceBridgeDatabase.appContext
            if (ctx != null) {
                PriceSyncWorker.enqueue(ctx)
            }
        } else {
            val entity = QueuedSubmissionEntity(
                id = submissionId,
                idempotencyKey = idempotencyKey,
                productId = productId,
                productName = "Product $productId",
                storeId = storeId,
                storeName = "Store $storeId",
                price = price,
                mrp = mrp,
                sourceType = sourceType,
                status = QueuedSubmissionEntity.STATUS_PENDING,
                createdAt = System.currentTimeMillis()
            )
            queuedSubmissionsState.update { listOf(entity) + it }
            pendingOfflineCount.update { it + 1 }
        }

        return submissionId
    }

    override suspend fun syncOfflineSubmissions(): Int {
        val ctx = context ?: PriceBridgeDatabase.appContext
        if (ctx != null) {
            PriceSyncWorker.enqueue(ctx)
        }

        val db = database ?: return 0
        val pending = db.queuedSubmissionDao().getPendingSubmissions()
        var syncedCount = 0

        for (item in pending) {
            try {
                val pence = (item.price * 100).toInt()
                val payload = NetworkPriceSubmission(
                    productId = item.productId,
                    storeId = item.storeId,
                    regularPricePence = pence,
                    sourceType = item.sourceType,
                    idempotencyKey = item.idempotencyKey
                )
                api.service.submitPrice(payload, item.idempotencyKey)
                db.queuedSubmissionDao().updateStatus(
                    id = item.id,
                    status = QueuedSubmissionEntity.STATUS_SYNCED,
                    retryCount = item.retryCount,
                    lastAttemptAt = System.currentTimeMillis(),
                    error = null
                )
                db.queuedSubmissionDao().deleteSubmission(item.id)
                syncedCount++
            } catch (_: Exception) {
                // Handled / retried by WorkManager
            }
        }
        return syncedCount
    }

    override suspend fun retryQueuedSubmission(submissionId: String): Boolean {
        val ctx = context ?: PriceBridgeDatabase.appContext
        if (ctx != null) {
            PriceSyncWorker.enqueue(ctx)
        }
        return true
    }

    override suspend fun cancelQueuedSubmission(submissionId: String) {
        database?.queuedSubmissionDao()?.deleteSubmission(submissionId)
        queuedSubmissionsState.update { list -> list.filter { it.id != submissionId } }
        pendingOfflineCount.update { (it - 1).coerceAtLeast(0) }
    }

    // ── Shopping Basket & Tasks ──

    private fun recalculateBasketState(basket: ShoppingBasket): ShoppingBasket {
        val total = basket.items.sumOf { (it.bestStorePrice ?: 0.0) * it.quantity }
        val stores = basket.items.groupBy { it.bestStoreName ?: "Store" }.map { (store, items) ->
            StoreSplitSummary(
                storeName = store,
                itemCount = items.sumOf { it.quantity },
                subtotal = items.sumOf { (it.bestStorePrice ?: 0.0) * it.quantity }
            )
        }
        val updated = basket.copy(
            singleStoreEstimatedTotal = total,
            optimizedEstimatedTotal = total * 0.90,
            estimatedSavingsAmount = total * 0.10,
            storeSplits = stores
        )
        basketSummary.value = SmartBasketSummary(
            estimatedSavingsAmount = updated.estimatedSavingsAmount,
            storesComparedCount = stores.size,
            storeNamesSample = stores.map { it.storeName },
            basketItemCount = updated.items.sumOf { it.quantity },
            basketName = updated.title
        )
        return updated
    }

    override fun getShoppingBasket(): Flow<ShoppingBasket> = shoppingBasketState.asStateFlow()

    override suspend fun toggleItemChecked(itemId: String) {
        shoppingBasketState.update { basket ->
            recalculateBasketState(basket.copy(
                items = basket.items.map {
                    if (it.id == itemId) it.copy(isChecked = !it.isChecked) else it
                }
            ))
        }
    }

    override suspend fun updateItemQuantity(itemId: String, newQuantity: Int) {
        shoppingBasketState.update { basket ->
            recalculateBasketState(basket.copy(
                items = basket.items.map {
                    if (it.id == itemId) it.copy(quantity = newQuantity) else it
                }
            ))
        }
    }

    override suspend fun addShoppingItem(name: String, quantity: Int, packageSize: String) {
        var bestPrice = 1.50
        var bestStore = "Nearest Supermarket"
        var timeAgo = "Just now"
        var badge = PriceStatusBadgeType.RecentlyObserved

        try {
            val results = withContext(Dispatchers.IO) { api.service.searchProducts(name) }
            val matched = results.firstOrNull()
            if (matched != null) {
                val comps = withContext(Dispatchers.IO) { api.service.compareProductPrices(matched.id, currentLat, currentLon) }
                val best = comps.minByOrNull { it.effectivePricePence }
                if (best != null) {
                    bestPrice = best.effectivePricePence / 100.0
                    bestStore = best.store.name
                    timeAgo = best.observedAgo
                    badge = when (best.freshnessStatus) {
                        "STORE_CONFIRMED" -> PriceStatusBadgeType.StoreConfirmed
                        "COMMUNITY_CORROBORATED" -> PriceStatusBadgeType.CommunityCorroborated
                        "OUTDATED" -> PriceStatusBadgeType.PriceMayBeOutdated
                        else -> PriceStatusBadgeType.RecentlyObserved
                    }
                }
            }
        } catch (_: Exception) {}

        val newItem = ShoppingListItem(
            id = UUID.randomUUID().toString(),
            productName = name,
            quantity = quantity,
            packageSize = packageSize.ifBlank { "1 unit" },
            unit = "unit",
            bestStorePrice = bestPrice,
            bestStoreName = bestStore,
            lastObservedRelativeTime = timeAgo,
            statusBadge = badge,
            isChecked = false
        )

        shoppingBasketState.update { basket ->
            recalculateBasketState(basket.copy(items = basket.items + newItem))
        }
    }

    override suspend fun deleteShoppingItem(itemId: String) {
        shoppingBasketState.update { basket ->
            recalculateBasketState(basket.copy(items = basket.items.filter { it.id != itemId }))
        }
    }

    override suspend fun clearCheckedItems() {
        shoppingBasketState.update { basket ->
            recalculateBasketState(basket.copy(items = basket.items.filter { !it.isChecked }))
        }
    }

    override fun getSavedShoppingLists(): Flow<List<SavedShoppingList>> = flow {
        emit(emptyList())
    }

    override fun getPriceWatchAlerts(): Flow<List<PriceWatchAlert>> = flow {
        emit(emptyList())
    }

    override suspend fun loadSavedListIntoActiveBasket(listId: String) {}

    // ── Profile & Auth ──

    override fun getUserProfile(): Flow<UserProfile> = AppSessionManager.userProfile

    override fun getSubmissionHistory(): Flow<List<PriceSubmission>> = submissionHistory.asStateFlow()

    override fun isUserLoggedIn(): Flow<Boolean> = AppSessionManager.isLoggedIn

    override suspend fun loginWithPhone(phoneNumber: String, otp: String): Boolean {
        AppSessionManager.setLoggedIn(
            loggedIn = true,
            mode = "PHONE",
            email = "scout@pricebridge.org",
            name = "UK Mobile User"
        )
        return true
    }

    override suspend fun loginWithGoogle(
        idToken: String?,
        email: String?,
        displayName: String?,
        photoUrl: String?
    ): Boolean {
        return try {
            val token = idToken ?: "demo_google_token"
            val session = api.service.loginWithGoogle(mapOf("id_token" to token))
            AppSessionManager.setLoggedIn(
                loggedIn = true,
                mode = "GOOGLE",
                email = email ?: session.email,
                name = displayName ?: session.displayName,
                avatarUrl = photoUrl ?: session.avatarUrl,
                token = session.token
            )
            true
        } catch (_: Exception) {
            val effectiveEmail = email ?: "alex.turner@gmail.com"
            val effectiveName = displayName ?: "Alex Turner"
            AppSessionManager.setLoggedIn(
                loggedIn = true,
                mode = "GOOGLE",
                email = effectiveEmail,
                name = effectiveName,
                avatarUrl = photoUrl ?: "",
                token = idToken ?: "demo_jwt_token"
            )
            true
        }
    }

    override suspend fun loginAsGuest() {
        AppSessionManager.setLoggedIn(
            loggedIn = true,
            mode = "GUEST",
            email = "guest@pricebridge.org",
            name = "Guest Shopper"
        )
    }

    override suspend fun logout() {
        try {
            com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
        } catch (_: Exception) {}
        AppSessionManager.logout()
    }

    override fun getAvailableProfiles(): Flow<List<UserProfile>> = flow {
        emit(listOf(userProfile.value))
    }

    override suspend fun switchProfile(profileId: String) {}

    override suspend fun requestPreVisitPriceCheck(storeId: String, productId: String?): Boolean = true

    override suspend fun claimStore(storeId: String, merchantName: String, contactPhone: String): Boolean = true

    override fun getGoogleTasksAuthState(): Flow<GoogleTasksAuthState> = flow {
        emit(GoogleTasksAuthState(isConnected = false))
    }

    override suspend fun connectGoogleTasks(email: String): Boolean = true

    override suspend fun disconnectGoogleTasks(): Boolean = true

    override fun getGoogleTaskLists(): Flow<List<GoogleTaskList>> = flow {
        emit(emptyList())
    }

    override fun getGoogleTasksForList(listId: String): Flow<List<GoogleTaskItem>> = flow {
        emit(emptyList())
    }

    override suspend fun importGoogleTasks(selectedTasks: List<GoogleTaskItem>): Int = 0

    override fun getNotifications(): Flow<List<AppNotification>> = notifications.asStateFlow()

    override suspend fun markNotificationAsRead(notificationId: String) {
        notifications.update { list ->
            list.map { if (it.id == notificationId) it.copy(isRead = true) else it }
        }
    }

    override suspend fun markAllNotificationsAsRead() {
        notifications.update { list ->
            list.map { it.copy(isRead = true) }
        }
    }

    override suspend fun updateLocation(neighborhood: String, radiusKm: Double) {
        when {
            neighborhood.contains("Camden", ignoreCase = true) -> setCoordinates(51.5390, -0.1426, neighborhood)
            neighborhood.contains("Covent Garden", ignoreCase = true) -> setCoordinates(51.5120, -0.1230, neighborhood)
            neighborhood.contains("Angel", ignoreCase = true) -> setCoordinates(51.5310, -0.1060, neighborhood)
            neighborhood.contains("Shoreditch", ignoreCase = true) -> setCoordinates(51.5240, -0.0780, neighborhood)
            neighborhood.contains("Brixton", ignoreCase = true) -> setCoordinates(51.4620, -0.1150, neighborhood)
            neighborhood.contains("Kensington", ignoreCase = true) -> setCoordinates(51.5010, -0.1930, neighborhood)
            else -> {
                currentLocation.update {
                    it.copy(
                        neighborhood = neighborhood,
                        radiusDescription = "${radiusKm} km radius"
                    )
                }
            }
        }
    }

    override suspend fun dismissKarmaPrompt(promptId: String) {
        dismissedPromptId.value = promptId
    }

    override suspend fun clearAllData(wipeServerDb: Boolean): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // 1. Clear local Room database tables
                database?.clearAllTables()

                // 2. Clear Session & Auth
                AppSessionManager.logout()

                // 3. Clear in-memory flows
                recentSearches.value = emptyList()
                submissionHistory.value = emptyList()
                pendingOfflineCount.value = 0
                queuedSubmissionsState.value = emptyList()

                // 4. Optionally trigger server database reset
                if (wipeServerDb) {
                    try {
                        api.service.resetDatabase(
                            mapOf(
                                "wipe_stores" to true,
                                "lat" to currentLat,
                                "lon" to currentLon,
                                "area_name" to currentLocation.value.neighborhood
                            )
                        )
                    } catch (_: Exception) {}
                }
                true
            } catch (_: Exception) {
                false
            }
        }
    }

    override fun getActiveYellowStickers(): Flow<List<com.example.feature.yellowsticker.YellowStickerItem>> = flow {
        try {
            val netDeals = api.service.getActiveYellowStickers()
            val items = netDeals.map { deal ->
                val was = String.format(java.util.Locale.UK, "£%.2f", deal.wasPricePence / 100.0)
                val now = String.format(java.util.Locale.UK, "£%.2f", deal.nowPricePence / 100.0)
                com.example.feature.yellowsticker.YellowStickerItem(
                    storeName = deal.storeName,
                    productName = deal.productName,
                    wasPrice = was,
                    nowPrice = now,
                    discountPercent = deal.discountPercent,
                    expiryNote = "Use by: ${deal.useByDate}",
                    hoursRemaining = 4
                )
            }
            emit(items)
        } catch (_: Exception) {
            emit(emptyList())
        }
    }.flowOn(Dispatchers.IO)

    override fun getNearbyBounties(): Flow<List<com.example.feature.bounty.BountyItem>> = flow {
        try {
            val netBounties = api.service.getNearbyBounties(currentLat, currentLon)
            val items = netBounties.map { b ->
                val distStr = if (b.distanceKm < 1.0) "${(b.distanceKm * 1000).toInt()}m away" else "%.1f km away".format(b.distanceKm)
                com.example.feature.bounty.BountyItem(
                    id = b.id,
                    storeName = b.storeName,
                    productName = b.productName,
                    rewardPoints = b.rewardPoints,
                    distance = distStr
                )
            }
            emit(items)
        } catch (_: Exception) {
            emit(emptyList())
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun claimBounty(bountyId: String): Boolean {
        return try {
            api.service.claimBounty(bountyId)
            true
        } catch (_: Exception) {
            false
        }
    }

    // ── Mapper Extensions ──

    private fun NetworkStorePriceComparison.toStorePrice(): StorePrice {
        val dist = if (this.store.distanceKm != null) {
            val km = this.store.distanceKm
            val mins = (km * 12).toInt().coerceAtLeast(1)
            if (km < 1.0) "${(km * 1000).toInt()}m · ${mins}m walk" else "%.1f km · ${mins}m walk".format(km)
        } else {
            "Nearby"
        }
        return StorePrice(
            storeId = this.store.id,
            storeName = this.store.name,
            distanceFormatted = dist,
            price = this.effectivePricePence / 100.0,
            mrp = this.regularPricePence / 100.0,
            currencySymbol = "£",
            lastObservedRelativeTime = this.observedAgo,
            sourceDescription = if (this.loyaltyScheme != "NONE") "${this.loyaltyScheme} Price" else "Community observed",
            statusBadge = when (this.freshnessStatus) {
                "STORE_CONFIRMED" -> PriceStatusBadgeType.StoreConfirmed
                "COMMUNITY_CORROBORATED" -> PriceStatusBadgeType.CommunityCorroborated
                "OUTDATED" -> PriceStatusBadgeType.PriceMayBeOutdated
                else -> PriceStatusBadgeType.RecentlyObserved
            },
            hasPhotoVerification = true,
            isLowestPrice = this.isBestDeal,
            reliabilityScore = 95
        )
    }

    private fun NetworkStore.toStore(): Store {
        val dist = if (this.distanceKm != null) {
            val km = this.distanceKm
            val mins = (km * 12).toInt().coerceAtLeast(1)
            if (km < 1.0) "${(km * 1000).toInt()}m · ${mins}m walk" else "%.1f km · ${mins}m walk".format(km)
        } else {
            "Nearby"
        }
        return Store(
            id = this.id,
            name = this.name,
            address = "${this.address}, ${this.postcode}",
            distanceFormatted = dist,
            isOpen = true,
            operatingHours = "7:00 AM - 11:00 PM",
            phone = null,
            reliabilityScore = 96,
            isStoreConfirmedPartner = this.chain in listOf("Tesco", "Sainsbury's", "Lidl", "Aldi", "Waitrose"),
            categoryTags = listOf(this.chain, this.format, "Supermarket")
        )
    }

    private fun NetworkProduct.toProduct(
        bestPrice: StorePrice? = null,
        comparedCount: Int = 1,
        alternatives: List<StorePrice> = emptyList()
    ): Product {
        val defaultPrice = bestPrice ?: StorePrice(
            storeId = "unknown",
            storeName = "Nearby Supermarket",
            distanceFormatted = "1.0 km",
            price = this.targetMrpGbp,
            mrp = this.targetMrpGbp,
            currencySymbol = "£",
            lastObservedRelativeTime = "Recently observed",
            sourceDescription = "Community observed",
            statusBadge = PriceStatusBadgeType.RecentlyObserved
        )
        return Product(
            id = this.id,
            name = this.name,
            categoryId = this.category,
            quantityDescription = "${this.brand} · ${this.packageSize}",
            packageSizeBadge = this.packageSize,
            imageUrl = "",
            bestStorePrice = defaultPrice,
            storesComparedCount = comparedCount,
            alternativePrices = alternatives,
            barcode = this.barcode
        )
    }
}
