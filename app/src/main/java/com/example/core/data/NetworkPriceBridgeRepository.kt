package com.example.core.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import com.example.core.data.local.PriceBridgeDatabase
import com.example.core.data.local.entity.BasketItemEntity
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Locale
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

    // Live user coordinates (dynamically supplied by DeviceLocationManager GPS)
    private var currentLat: Double = 0.0
    private var currentLon: Double = 0.0
    private var currentRouteMode: String = "walking"

    fun setRouteMode(mode: String) {
        currentRouteMode = if (mode == "driving") "driving" else "walking"
    }

    fun getRouteMode(): String = currentRouteMode

    fun setCoordinates(lat: Double, lon: Double, neighborhood: String? = null) {
        val movedSignificantly = kotlin.math.abs(lat - currentLat) > 0.02 ||
            kotlin.math.abs(lon - currentLon) > 0.02
        currentLat = lat
        currentLon = lon
        currentLocation.update {
            it.copy(
                latitude = lat,
                longitude = lon,
                neighborhood = neighborhood ?: it.neighborhood,
                addressLine = if (lat != 0.0 || lon != 0.0) "Live GPS Active" else it.addressLine
            )
        }
        if (movedSignificantly) {
            scope.launch {
                try {
                    database?.cachedDealDao()?.clearDeals()
                    database?.cachedStoreDao()?.clearAll()
                } catch (_: Exception) {}
            }
        }
    }

    private val currentLocation = MutableStateFlow(
        LocationInfo(
            title = "Current Location",
            neighborhood = "Detecting Live Location...",
            addressLine = "Real-time GPS",
            latitude = 0.0,
            longitude = 0.0,
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
    private val myAddedProductsState = MutableStateFlow<List<Product>>(emptyList())
    private val isLoggedInState = MutableStateFlow(false)

    private val dismissedPromptId = MutableStateFlow<String?>(null)

    init {
        scope.launch {
            try {
                val savedItems = database?.basketItemDao()?.getAllItems()?.map { it.toShoppingListItem() } ?: emptyList()
                if (savedItems.isNotEmpty()) {
                    shoppingBasketState.update { basket ->
                        recalculateBasketState(basket.copy(items = savedItems))
                    }
                }
            } catch (_: Exception) {}
        }
    }

    // ── Location & Feed ──

    override fun getLocation(): Flow<LocationInfo> = currentLocation.asStateFlow()

    override fun getSmartBasketSummary(): Flow<SmartBasketSummary> = basketSummary.asStateFlow()

    override fun getCategories(): Flow<List<Category>> = flow {
        // Step 1: Immediately emit cached categories from Room if available
        val cached = try {
            database?.cachedCategoryDao()?.getAllCategories()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }
        if (!cached.isNullOrEmpty()) {
            emit(cached.map { it.toCategory() })
        }

        // Step 2: Fetch fresh categories from backend and update cache
        val netResult: List<Category>? = try {
            val netCats = api.service.getCategories()
            if (netCats.isNotEmpty()) {
                val categories = netCats.map { Category(id = it.id, name = it.name) }
                try {
                    database?.cachedCategoryDao()?.insertCategories(
                        netCats.map { CachedCategoryEntity(id = it.id, name = it.name) }
                    )
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                }
                categories
            } else if (cached.isNullOrEmpty()) {
                listOf(Category(id = "all", name = "All"))
            } else {
                null
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (cached.isNullOrEmpty()) listOf(Category(id = "all", name = "All")) else null
        }
        if (netResult != null) {
            emit(netResult)
        }
    }.flowOn(Dispatchers.IO)

    override fun getKarmaPrompt(): Flow<KarmaVerificationPrompt?> = flow {
        val result: KarmaVerificationPrompt? = try {
            val prompt = api.service.getActiveBountyPrompt()
            if (prompt != null && prompt.bountyId.isNotEmpty() && prompt.bountyId != dismissedPromptId.value) {
                KarmaVerificationPrompt(
                    id = prompt.bountyId,
                    storeName = prompt.storeName,
                    description = "Is ${prompt.productName} still ${prompt.lastObservedPrice} at ${prompt.storeName}? Verify shelf tag to earn Karma points!",
                    karmaRewardPoints = prompt.rewardPoints
                )
            } else {
                null
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }
        emit(result)
    }.flowOn(Dispatchers.IO)

    override fun getBestDeals(categoryId: String?): Flow<List<Product>> = flow {
        if (currentLat == 0.0 && currentLon == 0.0) {
            emit(emptyList())
            return@flow
        }

        // Step 1: Immediately emit from Room SQLite local cache (if any exists from previous sync)
        val cached = try {
            if (categoryId == null || categoryId == "all") {
                database?.cachedDealDao()?.getAllDealsSync()?.map { it.toProduct() } ?: emptyList()
            } else {
                database?.cachedDealDao()?.getDealsByCategorySync(categoryId)?.map { it.toProduct() } ?: emptyList()
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            emptyList()
        }
        if (cached.isNotEmpty()) {
            emit(cached)
        }

        // Step 2: Fetch live single-query batch deals from backend
        val liveProducts: List<Product>? = try {
            val netDeals = api.service.getBestDealsBatch(
                lat = currentLat,
                lon = currentLon,
                category = if (categoryId == "all") null else categoryId,
                mode = currentRouteMode
            )
            if (netDeals.isNotEmpty()) {
                val mapped = netDeals.map { deal ->
                    val alternatives = deal.alternatives.map { it.toStorePrice() }
                    val bestStorePrice = StorePrice(
                        storeId = deal.bestStoreId,
                        storeName = deal.bestStoreName,
                        distanceFormatted = formatRouteDistance(
                            km = deal.bestStoreDistanceKm,
                            durationMinutes = deal.bestStoreDurationMinutes,
                            routeMode = deal.bestStoreRouteMode ?: currentRouteMode
                        ),
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
                // Persist live products to Room database cache (replacing stale deals from other locations)
                try {
                    database?.cachedDealDao()?.clearDeals()
                    database?.cachedDealDao()?.insertDeals(mapped.map { CachedDealEntity.fromProduct(it) })
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                }
                mapped
            } else {
                // Backend succeeded with 0 deals for this radius/category: clear stale deals from prior location
                try {
                    database?.cachedDealDao()?.clearDeals()
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                }
                emptyList()
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null // Keep displaying cached deals gracefully if offline
        }
        if (liveProducts != null) {
            emit(liveProducts)
        }
    }.flowOn(Dispatchers.IO)

    // ── Search & Barcode ──

    override fun searchProducts(query: String, categoryId: String?): Flow<List<Product>> = flow {
        val actualCategory = if (categoryId == "all" || categoryId.isNullOrEmpty()) null else categoryId
        val result: List<Product> = try {
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
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    netProd.toProduct()
                }
            }

            // Persist to Room cache
            try {
                database?.cachedProductDao()?.insertProducts(
                    products.map { CachedProductEntity.fromProduct(it) }
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }

            products
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            // Offline fallback: Query Room cache
            val cached = try {
                if (actualCategory != null) {
                    database?.cachedProductDao()?.searchProductsByCategory(query, actualCategory)
                } else if (query.isNotBlank()) {
                    database?.cachedProductDao()?.searchProducts(query)
                } else {
                    database?.cachedProductDao()?.getAllProducts()
                }
            } catch (e2: Exception) {
                if (e2 is CancellationException) throw e2
                null
            } ?: emptyList()

            if (cached.isNotEmpty()) {
                cached.map { it.toProduct() }
            } else {
                val cachedDeals = try {
                    if (actualCategory != null) {
                        database?.cachedDealDao()?.getDealsByCategorySync(actualCategory)
                    } else {
                        database?.cachedDealDao()?.getAllDealsSync()
                    }
                } catch (e2: Exception) {
                    if (e2 is CancellationException) throw e2
                    null
                } ?: emptyList()

                val filteredDeals = if (query.isNotBlank()) {
                    cachedDeals.filter { it.name.contains(query, ignoreCase = true) }
                } else {
                    cachedDeals
                }
                filteredDeals.map { it.toProduct() }
            }
        }
        emit(result)
    }.flowOn(Dispatchers.IO)

    override fun getProductByBarcode(barcode: String): Flow<Product?> = flow {
        val result: Product? = try {
            val netProd = api.service.getProductByBarcode(barcode)
            val comps = api.service.compareProductPrices(netProd.id, currentLat, currentLon)
            val storePrices = comps.map { it.toStorePrice() }
            val best = storePrices.minByOrNull { it.price }
            val product = netProd.toProduct(bestPrice = best, comparedCount = comps.size, alternatives = storePrices)
            try {
                database?.cachedProductDao()?.insertProduct(CachedProductEntity.fromProduct(product))
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
            product
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            val cached = try {
                database?.cachedProductDao()?.getProductByBarcode(barcode)
            } catch (e2: Exception) {
                if (e2 is CancellationException) throw e2
                null
            }
            cached?.toProduct()
        }
        emit(result)
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
        val result: Product? = try {
            val netProd = api.service.getProductById(productId)
            val comps = api.service.compareProductPrices(productId, currentLat, currentLon, currentRouteMode)
            val storePrices = comps.map { it.toStorePrice() }
            val best = storePrices.minByOrNull { it.price }
            val product = netProd.toProduct(bestPrice = best, comparedCount = comps.size, alternatives = storePrices)
            try {
                database?.cachedProductDao()?.insertProduct(CachedProductEntity.fromProduct(product))
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
            product
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            val cached = try {
                database?.cachedProductDao()?.getProductById(productId)
            } catch (e2: Exception) {
                if (e2 is CancellationException) throw e2
                null
            }
            if (cached != null) {
                cached.toProduct()
            } else {
                val deal = try {
                    database?.cachedDealDao()?.getAllDealsSync()?.find { it.id == productId }
                } catch (e2: Exception) {
                    if (e2 is CancellationException) throw e2
                    null
                }
                deal?.toProduct()
            }
        }
        emit(result)
    }.flowOn(Dispatchers.IO)

    override fun getStorePricesForProduct(productId: String): Flow<List<StorePrice>> = flow {
        val result: List<StorePrice> = try {
            val comps = api.service.compareProductPrices(productId, currentLat, currentLon, currentRouteMode)
            comps.map { it.toStorePrice() }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            emptyList()
        }
        emit(result)
    }.flowOn(Dispatchers.IO)

    override suspend fun addProduct(product: Product): String {
        val idempotencyKey = "prod_${product.id}"
        val payload = mapOf(
            "id" to product.id,
            "name" to product.name,
            "brand" to product.name.split(" ").firstOrNull().orEmpty(),
            "barcode" to (product.barcode ?: ""),
            "category" to product.categoryId,
            "package_size" to product.quantityDescription,
            "unit_metric" to "unit",
            "target_mrp_gbp" to (product.bestStorePrice?.mrp ?: product.bestStorePrice?.price ?: 0.0),
            "idempotency_key" to idempotencyKey
        )
        val created = withContext(Dispatchers.IO) {
            api.service.createProduct(payload, idempotencyKey)
        }
        val persistedProduct = created.toProduct(
            bestPrice = StorePrice(
                storeId = "user_added",
                storeName = "Added by you",
                distanceFormatted = "--",
                price = created.targetMrpGbp,
                mrp = created.targetMrpGbp,
                lastObservedRelativeTime = "Just added",
                sourceDescription = "Added by you",
                statusBadge = PriceStatusBadgeType.CommunityReported,
                isLowestPrice = true
            ),
            comparedCount = 0
        )
        try {
            database?.cachedProductDao()?.insertProduct(CachedProductEntity.fromProduct(persistedProduct))
        } catch (_: Exception) {}
        myAddedProductsState.update { current ->
            listOf(persistedProduct) + current.filter { it.id != persistedProduct.id }
        }
        val currentProfile = AppSessionManager.userProfile.value
        AppSessionManager.updateStats(
            karmaPoints = currentProfile.karmaPoints,
            pricesReported = currentProfile.pricesReported,
            pricesVerified = currentProfile.pricesVerified,
            productsAdded = maxOf(currentProfile.productsAdded, myAddedProductsState.value.size)
        )
        return created.id
    }

    override suspend fun confirmPriceFreshness(productId: String, storeId: String) {
        withContext(Dispatchers.IO) {
            api.service.confirmPriceFreshness(
                mapOf("product_id" to productId, "store_id" to storeId)
            )
        }
    }

    override suspend fun reportDiscrepancy(productId: String, storeId: String, reason: String, notes: String?) {
        withContext(Dispatchers.IO) {
            api.service.reportStoreDiscrepancy(
                storeId = storeId,
                payload = mapOf(
                    "reason" to reason,
                    "details" to (notes ?: "Reported for product $productId"),
                    "user_latitude" to currentLat,
                    "user_longitude" to currentLon
                )
            )
        }
    }

    // ── Stores ──

    override fun getNearbyStores(): Flow<List<Store>> = flow {
        if (currentLat == 0.0 && currentLon == 0.0) {
            emit(emptyList())
            return@flow
        }
        val result: List<Store> = try {
            val netStores = api.service.getNearbyStores(lat = currentLat, lon = currentLon, radiusKm = 10.0, mode = currentRouteMode)
            val stores = netStores.map { it.toStore() }
            try {
                database?.cachedStoreDao()?.clearAll()
                if (stores.isNotEmpty()) {
                    database?.cachedStoreDao()?.insertStores(
                        stores.map { CachedStoreEntity.fromStore(it) }
                    )
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
            stores
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            val cached = try {
                database?.cachedStoreDao()?.getAllStores()
            } catch (e2: Exception) {
                if (e2 is CancellationException) throw e2
                null
            } ?: emptyList()
            cached.map { it.toStore() }
        }
        emit(result)
    }.flowOn(Dispatchers.IO)

    override fun getAllProducts(): Flow<List<Product>> = flow {
        val result: List<Product> = try {
            val netProducts = api.service.searchProducts("")
            val products = netProducts.map { it.toProduct() }
            try {
                database?.cachedProductDao()?.insertProducts(
                    products.map { CachedProductEntity.fromProduct(it) }
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
            products
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            val cached = try {
                database?.cachedProductDao()?.getAllProducts()
            } catch (e2: Exception) {
                if (e2 is CancellationException) throw e2
                null
            } ?: emptyList()

            if (cached.isNotEmpty()) {
                cached.map { it.toProduct() }
            } else {
                val deals = try {
                    database?.cachedDealDao()?.getAllDealsSync()
                } catch (e2: Exception) {
                    if (e2 is CancellationException) throw e2
                    null
                } ?: emptyList()
                deals.map { it.toProduct() }
            }
        }
        emit(result)
    }.flowOn(Dispatchers.IO)

    override fun getStoreById(storeId: String): Flow<Store?> = flow {
        val result: Store? = try {
            val netStore = api.service.getStoreById(storeId)
            val store = netStore.toStore()
            try {
                database?.cachedStoreDao()?.insertStore(CachedStoreEntity.fromStore(store))
            } catch (e: Exception) {
                if (e is CancellationException) throw e
            }
            store
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            val cached = try {
                database?.cachedStoreDao()?.getStoreById(storeId)
            } catch (e2: Exception) {
                if (e2 is CancellationException) throw e2
                null
            }
            cached?.toStore()
        }
        emit(result)
    }.flowOn(Dispatchers.IO)

    override fun getStorePrices(storeId: String): Flow<List<StorePrice>> = flow {
        val result: List<StorePrice> = try {
            val netProducts = api.service.searchProducts("")
            val results = mutableListOf<StorePrice>()
            for (p in netProducts) {
                try {
                    val comps = api.service.compareProductPrices(p.id, currentLat, currentLon)
                    val match = comps.find { it.store.id == storeId }
                    if (match != null) {
                        results.add(match.toStorePrice())
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                }
            }
            results
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            emptyList()
        }
        emit(result)
    }.flowOn(Dispatchers.IO)

    // ── Price Submission ──

    override suspend fun submitPrice(
        productId: String,
        storeId: String,
        price: Double,
        mrp: Double,
        sourceType: String,
        evidenceId: String?
    ) {
        val pence = (price * 100).toInt()
        val idempotencyKey = "sub_${UUID.randomUUID()}"
        val payload = NetworkPriceSubmission(
            productId = productId,
            storeId = storeId,
            regularPricePence = pence,
            sourceType = sourceType,
            idempotencyKey = idempotencyKey,
            evidenceId = evidenceId
        )
        api.service.submitPrice(payload, idempotencyKey)

        val resolvedProductName = try {
            database?.cachedProductDao()?.getProductById(productId)?.name
        } catch (_: Exception) { null } ?: productId
        val resolvedStoreName = try {
            database?.cachedStoreDao()?.getStoreById(storeId)?.name
        } catch (_: Exception) { null } ?: storeId

        submissionHistory.update { history ->
            listOf(
                PriceSubmission(
                    id = UUID.randomUUID().toString(),
                    productName = resolvedProductName,
                    storeName = resolvedStoreName,
                    price = price,
                    mrp = mrp,
                    currencySymbol = "£",
                    sourceType = sourceType,
                    submittedRelativeTime = "Just now",
                    statusBadge = PriceStatusBadgeType.RecentlyObserved
                )
            ) + history
        }

        if (AppSessionManager.isLoggedIn.value && AppSessionManager.getAuthToken().isNotBlank()) {
            try {
                val remote = withContext(Dispatchers.IO) { api.service.getMyProducts() }
                val mapped = remote.map { it.toUserAddedProduct() }
                myAddedProductsState.value = mapped
            } catch (_: Exception) {}
        }
    }

    override suspend fun uploadEvidence(
        productId: String?,
        storeId: String?,
        sourceType: String,
        imagePath: String,
        ocrRawText: String
    ): String? = withContext(Dispatchers.IO) {
        try {
            val bitmap: Bitmap? = when {
                imagePath.startsWith("content://") -> {
                    val ctx = context ?: PriceBridgeDatabase.appContext
                    ctx?.contentResolver?.openInputStream(Uri.parse(imagePath))?.use { stream ->
                        BitmapFactory.decodeStream(stream)
                    }
                }
                else -> {
                    val f = File(imagePath)
                    if (f.exists()) BitmapFactory.decodeFile(f.absolutePath) else null
                }
            }
            if (bitmap == null) return@withContext null

            val maxDim = 1280
            val scale = minOf(1f, maxDim.toFloat() / maxOf(bitmap.width, bitmap.height).coerceAtLeast(1))
            val scaled = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * scale).toInt().coerceAtLeast(1),
                    (bitmap.height * scale).toInt().coerceAtLeast(1),
                    true
                )
            } else {
                bitmap
            }

            val baos = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 80, baos)
            val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)

            val resp = api.service.uploadEvidence(
                buildMap {
                    put("source_type", sourceType)
                    put("image_base64", b64)
                    put("ocr_raw_text", ocrRawText)
                    if (!productId.isNullOrBlank()) put("product_id", productId)
                    if (!storeId.isNullOrBlank()) put("store_id", storeId)
                }
            )
            resp.evidenceId
        } catch (_: Exception) {
            null
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
        val stores = basket.items
            .filter { it.bestStorePrice > 0.0 && it.bestStoreName.isNotBlank() && it.bestStoreName != "No local store price yet" }
            .groupBy { it.bestStoreName }
            .map { (store, items) ->
                StoreSplitSummary(
                    storeName = store,
                    itemCount = items.sumOf { it.quantity },
                    subtotal = items.sumOf { it.bestStorePrice * it.quantity }
                )
            }
        val updated = basket.copy(
            singleStoreEstimatedTotal = total,
            optimizedEstimatedTotal = total,
            estimatedSavingsAmount = 0.0,
            storeSplits = stores
        )
        basketSummary.value = SmartBasketSummary(
            estimatedSavingsAmount = 0.0,
            storesComparedCount = stores.size,
            storeNamesSample = stores.map { it.storeName },
            basketItemCount = updated.items.sumOf { it.quantity },
            basketName = updated.title
        )
        return updated
    }

    override fun getShoppingBasket(): Flow<ShoppingBasket> = shoppingBasketState.asStateFlow()

    override suspend fun toggleItemChecked(itemId: String) {
        var updatedItem: ShoppingListItem? = null
        shoppingBasketState.update { basket ->
            recalculateBasketState(basket.copy(
                items = basket.items.map {
                    if (it.id == itemId) {
                        val toggled = it.copy(isChecked = !it.isChecked)
                        updatedItem = toggled
                        toggled
                    } else it
                }
            ))
        }
        updatedItem?.let { item ->
            try { database?.basketItemDao()?.upsertItem(BasketItemEntity.fromShoppingListItem(item)) } catch (_: Exception) {}
        }
    }

    override suspend fun updateItemQuantity(itemId: String, newQuantity: Int) {
        var updatedItem: ShoppingListItem? = null
        shoppingBasketState.update { basket ->
            recalculateBasketState(basket.copy(
                items = basket.items.map {
                    if (it.id == itemId) {
                        val mod = it.copy(quantity = newQuantity)
                        updatedItem = mod
                        mod
                    } else it
                }
            ))
        }
        updatedItem?.let { item ->
            try { database?.basketItemDao()?.upsertItem(BasketItemEntity.fromShoppingListItem(item)) } catch (_: Exception) {}
        }
    }

    override suspend fun addShoppingItem(name: String, quantity: Int, packageSize: String) {
        var bestPrice = 0.0
        var bestStore = "No local store price yet"
        var timeAgo = "Local item"
        var badge = PriceStatusBadgeType.CommunityReported

        if (currentLat != 0.0 || currentLon != 0.0) {
            try {
                val results = withContext(Dispatchers.IO) { api.service.searchProducts(name) }
                val matched = results.firstOrNull()
                if (matched != null) {
                    val comps = withContext(Dispatchers.IO) {
                        api.service.compareProductPrices(matched.id, currentLat, currentLon, currentRouteMode)
                    }
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
        }

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
        try { database?.basketItemDao()?.upsertItem(BasketItemEntity.fromShoppingListItem(newItem)) } catch (_: Exception) {}
    }

    override suspend fun deleteShoppingItem(itemId: String) {
        shoppingBasketState.update { basket ->
            recalculateBasketState(basket.copy(items = basket.items.filter { it.id != itemId }))
        }
        try { database?.basketItemDao()?.deleteItem(itemId) } catch (_: Exception) {}
    }

    override suspend fun clearCheckedItems() {
        shoppingBasketState.update { basket ->
            recalculateBasketState(basket.copy(items = basket.items.filter { !it.isChecked }))
        }
        try { database?.basketItemDao()?.deleteCheckedItems() } catch (_: Exception) {}
    }

    override fun getSavedShoppingLists(): Flow<List<SavedShoppingList>> = flow {
        val current = shoppingBasketState.value
        if (current.items.isNotEmpty()) {
            emit(
                listOf(
                    SavedShoppingList(
                        id = "active_basket",
                        title = current.title,
                        itemCount = current.items.sumOf { it.quantity },
                        estimatedTotal = current.singleStoreEstimatedTotal,
                        isRecurring = false,
                        lastRunDate = "Active"
                    )
                )
            )
        } else {
            emit(emptyList())
        }
    }

    override fun getPriceWatchAlerts(): Flow<List<PriceWatchAlert>> = flow {
        emit(emptyList())
    }

    override suspend fun loadSavedListIntoActiveBasket(listId: String) {}

    // ── Profile & Auth ──

    override fun getUserProfile(): Flow<UserProfile> = flow {
        val base = AppSessionManager.userProfile.value
        emit(base)
        if (AppSessionManager.isLoggedIn.value && AppSessionManager.getAuthToken().isNotBlank()) {
            try {
                val stats = withContext(Dispatchers.IO) { api.service.getMyProfile() }
                AppSessionManager.updateStats(
                    karmaPoints = stats.karmaPoints,
                    pricesReported = stats.pricesReported,
                    pricesVerified = stats.pricesVerified,
                    productsAdded = stats.productsAdded
                )
            } catch (_: Exception) {}
        }
        AppSessionManager.userProfile.collect { emit(it) }
    }.flowOn(Dispatchers.IO)

    override fun getSubmissionHistory(): Flow<List<PriceSubmission>> = flow {
        emit(submissionHistory.value)
        if (AppSessionManager.isLoggedIn.value && AppSessionManager.getAuthToken().isNotBlank()) {
            try {
                val records = withContext(Dispatchers.IO) { api.service.getMySubmissions() }
                val mapped = records.map { rec ->
                    PriceSubmission(
                        id = rec.id,
                        productName = rec.productName.ifBlank { rec.productId },
                        storeName = rec.storeName.ifBlank { rec.storeId },
                        price = rec.regularPricePence / 100.0,
                        mrp = rec.regularPricePence / 100.0,
                        currencySymbol = "£",
                        sourceType = rec.sourceType,
                        submittedRelativeTime = "Recorded",
                        statusBadge = when (rec.freshnessStatus) {
                            "STORE_CONFIRMED" -> PriceStatusBadgeType.StoreConfirmed
                            "COMMUNITY_CORROBORATED" -> PriceStatusBadgeType.CommunityCorroborated
                            "PRICE_MAY_BE_OUTDATED" -> PriceStatusBadgeType.PriceMayBeOutdated
                            else -> PriceStatusBadgeType.RecentlyObserved
                        }
                    )
                }
                submissionHistory.value = mapped
            } catch (_: Exception) {}
        }
        submissionHistory.collect { emit(it) }
    }.flowOn(Dispatchers.IO)

    override fun getMyAddedProducts(): Flow<List<Product>> = flow {
        emit(myAddedProductsState.value)
        if (AppSessionManager.isLoggedIn.value && AppSessionManager.getAuthToken().isNotBlank()) {
            try {
                val remote = withContext(Dispatchers.IO) { api.service.getMyProducts() }
                val remoteMapped = remote.map { it.toUserAddedProduct() }
                val remoteIds = remoteMapped.map { it.id }.toSet()
                val merged = remoteMapped + myAddedProductsState.value.filter { it.id !in remoteIds }
                myAddedProductsState.value = merged
                try {
                    database?.cachedProductDao()?.insertProducts(
                        remoteMapped.map { CachedProductEntity.fromProduct(it) }
                    )
                } catch (_: Exception) {}
            } catch (_: Exception) {}
        }
        myAddedProductsState.collect { emit(it) }
    }.flowOn(Dispatchers.IO)

    override fun isUserLoggedIn(): Flow<Boolean> = AppSessionManager.isLoggedIn

    override suspend fun loginWithGoogle(
        idToken: String?,
        email: String?,
        displayName: String?,
        photoUrl: String?
    ): Boolean {
        val token = idToken ?: throw IllegalArgumentException("Missing Google ID token")
        val session = api.service.loginWithGoogle(mapOf("id_token" to token))
        AppSessionManager.setLoggedIn(
            loggedIn = true,
            mode = "GOOGLE",
            email = email ?: session.email,
            name = displayName ?: session.displayName,
            avatarUrl = photoUrl ?: session.avatarUrl,
            token = session.token
        )
        return true
    }

    override suspend fun signInWithEmail(email: String, password: String): Boolean {
        val session = api.service.signInWithEmail(
            mapOf("email" to email.trim(), "password" to password)
        )
        AppSessionManager.setLoggedIn(
            loggedIn = true,
            mode = "NEON_EMAIL",
            email = session.email,
            name = session.displayName,
            avatarUrl = session.avatarUrl,
            token = session.token
        )
        return true
    }

    override suspend fun signUpWithEmail(name: String, email: String, password: String): Boolean {
        val session = api.service.signUpWithEmail(
            mapOf("name" to name.trim(), "email" to email.trim(), "password" to password)
        )
        AppSessionManager.setLoggedIn(
            loggedIn = true,
            mode = "NEON_EMAIL",
            email = session.email,
            name = session.displayName,
            avatarUrl = session.avatarUrl,
            token = session.token
        )
        return true
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
            if (AppSessionManager.getAuthToken().isNotBlank()) {
                withContext(Dispatchers.IO) { api.service.logout() }
            }
        } catch (_: Exception) {}
        try {
            com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
        } catch (_: Exception) {}
        AppSessionManager.logout()
        myAddedProductsState.value = emptyList()
        submissionHistory.value = emptyList()
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

    override suspend fun updateLocation(location: LocationInfo) {
        val movedSignificantly = kotlin.math.abs(location.latitude - currentLat) > 0.02 ||
            kotlin.math.abs(location.longitude - currentLon) > 0.02
        currentLocation.value = location
        currentLat = location.latitude
        currentLon = location.longitude
        if (movedSignificantly) {
            withContext(Dispatchers.IO) {
                try {
                    database?.cachedDealDao()?.clearDeals()
                    database?.cachedStoreDao()?.clearAll()
                } catch (_: Exception) {}
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
                myAddedProductsState.value = emptyList()
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
        val result: List<com.example.feature.yellowsticker.YellowStickerItem> = try {
            val netDeals = api.service.getActiveYellowStickers()
            netDeals.map { deal ->
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
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            emptyList()
        }
        emit(result)
    }.flowOn(Dispatchers.IO)

    override fun getNearbyBounties(): Flow<List<com.example.feature.bounty.BountyItem>> = flow {
        val result: List<com.example.feature.bounty.BountyItem> = try {
            val netBounties = api.service.getNearbyBounties(currentLat, currentLon)
            netBounties.map { b ->
                val distStr = if (b.distanceKm < 1.0) "${(b.distanceKm * 1000).toInt()}m away" else "%.1f km away".format(b.distanceKm)
                com.example.feature.bounty.BountyItem(
                    id = b.id,
                    storeName = b.storeName,
                    productName = b.productName,
                    rewardPoints = b.rewardPoints,
                    distance = distStr
                )
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            emptyList()
        }
        emit(result)
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

    private fun formatRouteDistance(
        km: Double?,
        durationMinutes: Int?,
        routeMode: String?
    ): String {
        if (km == null) return "Nearby"
        val isDrive = routeMode == "driving"
        val mins = durationMinutes?.takeIf { it > 0 }
            ?: if (isDrive) (km * 2.5).toInt().coerceAtLeast(1) else (km * 12).toInt().coerceAtLeast(1)
        val unitLabel = if (isDrive) "drive" else "walk"
        return if (km < 1.0) {
            "${(km * 1000).toInt()}m · ${mins}m $unitLabel"
        } else {
            String.format(Locale.UK, "%.1f km · %dm %s", km, mins, unitLabel)
        }
    }

    private fun NetworkStorePriceComparison.toStorePrice(): StorePrice {
        val dist = formatRouteDistance(
            km = this.store.distanceKm,
            durationMinutes = this.store.durationMinutes,
            routeMode = this.store.routeMode
        )
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
        val dist = formatRouteDistance(
            km = this.distanceKm,
            durationMinutes = this.durationMinutes,
            routeMode = this.routeMode
        )
        return Store(
            id = this.id,
            name = this.name,
            address = "${this.address}, ${this.postcode}",
            distanceFormatted = dist,
            latitude = this.latitude,
            longitude = this.longitude,
            distanceKm = this.distanceKm ?: 1.0,
            durationMinutes = this.durationMinutes,
            routeMode = this.routeMode ?: "walking",
            isExactRoute = this.isExactRoute,
            isOpen = true,
            operatingHours = "7:00 AM - 11:00 PM",
            phone = null,
            reliabilityScore = 96,
            isStoreConfirmedPartner = this.chain in listOf("Tesco", "Sainsbury's", "Lidl", "Aldi", "Waitrose"),
            categoryTags = listOf(this.chain, this.format, "Supermarket")
        )
    }

    private fun NetworkProduct.toUserAddedProduct(): Product = this.toProduct(
        bestPrice = StorePrice(
            storeId = "user_added",
            storeName = "Added by you",
            distanceFormatted = "--",
            price = this.targetMrpGbp,
            mrp = this.targetMrpGbp,
            lastObservedRelativeTime = "Added by you",
            sourceDescription = "Catalog contribution",
            statusBadge = PriceStatusBadgeType.CommunityReported,
            isLowestPrice = true
        ),
        comparedCount = 0
    )

    private fun NetworkProduct.toProduct(
        bestPrice: StorePrice? = null,
        comparedCount: Int = 1,
        alternatives: List<StorePrice> = emptyList()
    ): Product {
        val defaultPrice = bestPrice ?: StorePrice(
            storeId = "catalog",
            storeName = "Catalog entry",
            distanceFormatted = "--",
            price = this.targetMrpGbp,
            mrp = this.targetMrpGbp,
            currencySymbol = "£",
            lastObservedRelativeTime = "Catalog reference",
            sourceDescription = "Catalog reference",
            statusBadge = PriceStatusBadgeType.CommunityReported
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
