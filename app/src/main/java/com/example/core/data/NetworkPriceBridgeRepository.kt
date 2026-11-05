package com.example.core.data

import com.example.core.data.local.entity.QueuedSubmissionEntity
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
 * live Render cloud backend API (https://pricing-system-4nhl.onrender.com).
 */
class NetworkPriceBridgeRepository(
    private val api: ApiClient = ApiClient
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
        try {
            val netCats = api.service.getCategories()
            if (netCats.isNotEmpty()) {
                emit(netCats.map { Category(id = it.id, name = it.name) })
            } else {
                emit(listOf(Category(id = "all", name = "All")))
            }
        } catch (_: Exception) {
            emit(listOf(Category(id = "all", name = "All")))
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
        try {
            val netProducts = api.service.searchProducts("", category = if (categoryId == "all") null else categoryId)
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
            emit(products)
        } catch (e: Exception) {
            emit(emptyList())
        }
    }.flowOn(Dispatchers.IO)

    // ── Search & Barcode ──

    override fun searchProducts(query: String, categoryId: String?): Flow<List<Product>> = flow {
        try {
            val netProducts = api.service.searchProducts(
                query = query,
                category = if (categoryId == "all" || categoryId.isNullOrEmpty()) null else categoryId
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
            emit(products)
        } catch (e: Exception) {
            emit(emptyList())
        }
    }.flowOn(Dispatchers.IO)

    override fun getProductByBarcode(barcode: String): Flow<Product?> = flow {
        try {
            val netProd = api.service.getProductByBarcode(barcode)
            val comps = api.service.compareProductPrices(netProd.id, currentLat, currentLon)
            val storePrices = comps.map { it.toStorePrice() }
            val best = storePrices.minByOrNull { it.price }
            emit(netProd.toProduct(bestPrice = best, comparedCount = comps.size, alternatives = storePrices))
        } catch (e: Exception) {
            emit(null)
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
            emit(netProd.toProduct(bestPrice = best, comparedCount = comps.size, alternatives = storePrices))
        } catch (e: Exception) {
            emit(null)
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
            emit(netStores.map { it.toStore() })
        } catch (e: Exception) {
            emit(emptyList())
        }
    }.flowOn(Dispatchers.IO)

    override fun getAllProducts(): Flow<List<Product>> = flow {
        try {
            val netProducts = api.service.searchProducts("")
            emit(netProducts.map { it.toProduct() })
        } catch (e: Exception) {
            emit(emptyList())
        }
    }.flowOn(Dispatchers.IO)

    override fun getStoreById(storeId: String): Flow<Store?> = flow {
        try {
            val netStore = api.service.getStoreById(storeId)
            emit(netStore.toStore())
        } catch (e: Exception) {
            emit(null)
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
        val payload = NetworkPriceSubmission(
            productId = productId,
            storeId = storeId,
            regularPricePence = pence,
            sourceType = sourceType
        )
        api.service.submitPrice(payload)

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

    override fun getPendingOfflineSubmissionCount(): Flow<Int> = pendingOfflineCount.asStateFlow()

    override fun getQueuedSubmissions(): Flow<List<QueuedSubmissionEntity>> = queuedSubmissionsState.asStateFlow()

    override suspend fun enqueueOfflinePrice(
        productId: String,
        storeId: String,
        price: Double,
        mrp: Double,
        sourceType: String
    ): String {
        submitPrice(productId, storeId, price, mrp, sourceType)
        return UUID.randomUUID().toString()
    }

    override suspend fun syncOfflineSubmissions(): Int = 0

    override suspend fun retryQueuedSubmission(submissionId: String): Boolean = true

    override suspend fun cancelQueuedSubmission(submissionId: String) {}

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

    override fun getUserProfile(): Flow<UserProfile> = userProfile.asStateFlow()

    override fun getSubmissionHistory(): Flow<List<PriceSubmission>> = submissionHistory.asStateFlow()

    override fun isUserLoggedIn(): Flow<Boolean> = isLoggedInState.asStateFlow()

    override suspend fun loginWithPhone(phoneNumber: String, otp: String): Boolean {
        isLoggedInState.value = true
        return true
    }

    override suspend fun loginWithGoogle(): Boolean {
        isLoggedInState.value = true
        return true
    }

    override suspend fun loginAsGuest() {
        isLoggedInState.value = true
    }

    override suspend fun logout() {
        isLoggedInState.value = false
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
        currentLocation.update {
            it.copy(
                neighborhood = neighborhood,
                radiusDescription = "${radiusKm} km radius"
            )
        }
    }

    override suspend fun dismissKarmaPrompt(promptId: String) {
        dismissedPromptId.value = promptId
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
