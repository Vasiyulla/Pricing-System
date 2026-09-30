package com.example.core.data

import com.example.core.model.Category
import com.example.core.model.KarmaVerificationPrompt
import com.example.core.model.LocationInfo
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
import com.example.core.model.StoreRecommendation
import com.example.core.model.StoreSplitSummary
import com.example.core.model.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import java.util.UUID

class FakePriceBridgeRepository : PriceBridgeRepository {

    private val currentLocation = MutableStateFlow(
        LocationInfo(
            neighborhood = "Indiranagar, 100ft Rd",
            addressLine = "Bangalore, Karnataka",
            radiusDescription = "1.5 km radius",
            isLiveTrackingActive = true
        )
    )

    private val basketSummary = MutableStateFlow(
        SmartBasketSummary(
            estimatedSavingsAmount = 148.0,
            storesComparedCount = 6,
            storeNamesSample = listOf("Nilgiris", "Reliance", "Kiranas"),
            basketItemCount = 4,
            basketName = "Weekend Basket"
        )
    )

    private val categories = MutableStateFlow(
        listOf(
            Category(id = "all", name = "All"),
            Category(id = "groceries", name = "Groceries & Staples"),
            Category(id = "dairy", name = "Dairy & Eggs"),
            Category(id = "personal_care", name = "Personal Care"),
            Category(id = "pharmacy", name = "Pharmacy"),
            Category(id = "snacks", name = "Snacks")
        )
    )

    private val karmaPromptState = MutableStateFlow<KarmaVerificationPrompt?>(
        KarmaVerificationPrompt(
            id = "prompt_reliance_1",
            storeName = "Reliance Smart",
            description = "Reliance Smart just updated fresh prices. Verify a shelf tag photo and help your neighborhood save!",
            karmaRewardPoints = 20
        )
    )

    // ── Nearby Stores ──
    private val nearbyStores = MutableStateFlow(
        listOf(
            Store("nilgiris_indiranagar", "Nilgiris Supermarket", "100ft Rd, Indiranagar", "0.6 km"),
            Store("apna_bazar", "Apna Bazar Kirana", "CMH Rd, Indiranagar", "0.3 km"),
            Store("reliance_indiranagar", "Reliance Smart", "12th Main, Indiranagar", "1.1 km"),
            Store("dmart_koramangala", "DMart", "80ft Rd, Koramangala", "2.3 km"),
            Store("more_supermarket", "More Supermarket", "100ft Rd, Indiranagar", "0.8 km"),
            Store("bigbasket_hub", "BigBasket Collection Point", "Domlur, Indiranagar", "1.4 km")
        )
    )

    // ── All Products (expanded with alternative prices) ──
    private val allProducts = MutableStateFlow(
        listOf(
            Product(
                id = "fortune_sunflower_1l",
                name = "Fortune Sunlite Sunflower Oil",
                categoryId = "groceries",
                quantityDescription = "1L Pouch",
                packageSizeBadge = "1L",
                imageUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuCwbB_NV4B9e1DbuCKB4eNIA43x0tfRnHMPm9Glu2VeHRKMEEXiwmPTAVB5yPHirBtfSrjNor3P_MVSo8c6RKXrvFi6eAPM8f3FH7eJdPcUUVyUDJLymttkUDgaLQNGpbtpp6LJgNRrOtYU-DxB7TsaEweAKdzHtohzXcu-wtMvc6t8xcwHB7fXoKNH1lsdu4BsbxhO2TNxZBQrMuOYXr7ANwD2vAL7px5-ilg938k4uDDbqLPI60ScMg",
                bestStorePrice = StorePrice(
                    storeId = "nilgiris_indiranagar",
                    storeName = "Nilgiris Supermarket",
                    distanceFormatted = "0.6 km",
                    price = 142.0,
                    mrp = 165.0,
                    lastObservedRelativeTime = "2h ago",
                    sourceDescription = "Community corroborated",
                    statusBadge = PriceStatusBadgeType.CommunityCorroborated,
                    hasPhotoVerification = true,
                    isLowestPrice = true
                ),
                storesComparedCount = 4,
                alternativePrices = listOf(
                    StorePrice("apna_bazar", "Apna Bazar Kirana", "0.3 km", 148.0, 165.0, lastObservedRelativeTime = "3h ago", sourceDescription = "Recently observed", statusBadge = PriceStatusBadgeType.RecentlyObserved, hasPhotoVerification = false, reliabilityScore = 92, supersededPrice = 155.0, correctionHistoryCount = 1),
                    StorePrice("reliance_indiranagar", "Reliance Smart", "1.1 km", 150.0, 165.0, lastObservedRelativeTime = "4h ago", sourceDescription = "Community reported", statusBadge = PriceStatusBadgeType.CommunityReported, reliabilityScore = 68, discrepancyWarning = "Counter overcharging reported by 2 users"),
                    StorePrice("dmart_koramangala", "DMart", "2.3 km", 139.0, 165.0, lastObservedRelativeTime = "Yesterday", sourceDescription = "Store confirmed", statusBadge = PriceStatusBadgeType.StoreConfirmed, isLowestPrice = true, reliabilityScore = 99, isStoreConfirmedPartner = true, supersededPrice = 145.0, correctionHistoryCount = 2)
                ),
                barcode = "8901030383709"
            ),
            Product(
                id = "amul_taaza_1l",
                name = "Amul Taaza Homogenised Toned Milk",
                categoryId = "dairy",
                quantityDescription = "1L Tetra Pack",
                packageSizeBadge = "1L Tetra",
                imageUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuBwZDw7ZEe78Qj0Yt1jAgES78Ajb36EWRltjH3XQanBn-EquXx36vKoCqdb_hV_SRTgiY8hS8e4sHNRQ9-3N13rrwL-U70kArC1vEgTK5iy7JND6Pg1GSsfHi5GeG9_GzdOn9KvdNK7yCxaS-eoeFDh3ZBC5eFet9gP_t7qU2EFC5kLWxWj4Aaw_QMKP2iF19VK5hiuEWyEAjkfGBpA9UB4Brgtb4kCfM35BbxctGx03tm6Lt65z2fO9A",
                bestStorePrice = StorePrice(
                    storeId = "apna_bazar",
                    storeName = "Apna Bazar Kirana",
                    distanceFormatted = "0.3 km",
                    price = 72.0,
                    mrp = 75.0,
                    lastObservedRelativeTime = "3h ago",
                    sourceDescription = "Community corroborated",
                    statusBadge = PriceStatusBadgeType.CommunityCorroborated,
                    hasPhotoVerification = false,
                    isLowestPrice = true
                ),
                storesComparedCount = 3,
                alternativePrices = listOf(
                    StorePrice("nilgiris_indiranagar", "Nilgiris Supermarket", "0.6 km", 74.0, 75.0, lastObservedRelativeTime = "2h ago", sourceDescription = "Recently observed", statusBadge = PriceStatusBadgeType.RecentlyObserved),
                    StorePrice("reliance_indiranagar", "Reliance Smart", "1.1 km", 75.0, 75.0, lastObservedRelativeTime = "5h ago", sourceDescription = "Community reported", statusBadge = PriceStatusBadgeType.CommunityReported)
                ),
                barcode = "8901262010058"
            ),
            Product(
                id = "dhara_sunflower_1l",
                name = "Dhara Refined Sunflower Oil",
                categoryId = "groceries",
                quantityDescription = "1L Pouch",
                packageSizeBadge = "1L",
                imageUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuCdENTDeSNwM9dxgnF9P5tCPAHl5gufWwwJGujv4paEGaxZY_buQ_47CyBwAA-M33Mz024hjlBkaYoq2EIWOrxZWP54x-JkVy1TmEPb0WJE-3B1X6oy8dvZKw9gfkBvI0aBZ3RY3TxJ3JvA5OBTRCmQMkCkOIY3gKDzXb3jpU9BD6OpkVshDmwDoXA6NSwii07OlPgThfMoXvJa4H1UH-L6dfupzERnXwa5IEo5fG0SN6p3wyV0-8PVww",
                bestStorePrice = StorePrice(
                    storeId = "apna_bazar",
                    storeName = "Apna Bazar Kirana",
                    distanceFormatted = "0.3 km",
                    price = 138.0,
                    mrp = 160.0,
                    lastObservedRelativeTime = "4h ago",
                    sourceDescription = "Recently observed",
                    statusBadge = PriceStatusBadgeType.RecentlyObserved,
                    hasPhotoVerification = true,
                    isLowestPrice = true
                ),
                storesComparedCount = 3,
                alternativePrices = listOf(
                    StorePrice("nilgiris_indiranagar", "Nilgiris Supermarket", "0.6 km", 142.0, 160.0, lastObservedRelativeTime = "3h ago", sourceDescription = "Community corroborated", statusBadge = PriceStatusBadgeType.CommunityCorroborated),
                    StorePrice("more_supermarket", "More Supermarket", "0.8 km", 145.0, 160.0, lastObservedRelativeTime = "Yesterday", sourceDescription = "Community reported", statusBadge = PriceStatusBadgeType.CommunityReported)
                ),
                barcode = "8906007280014"
            ),
            Product(
                id = "aashirvaad_atta_5kg",
                name = "Aashirvaad Superior MP Sharbati Atta",
                categoryId = "groceries",
                quantityDescription = "5kg Bag",
                packageSizeBadge = "5kg",
                imageUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuCPNmE2n4ebhgWz0KUXj5_g_0JCcgVP1WhwiQdh2p0nl0nVqJ4K8zJzYYw4IpZrnNqCLS2of7njuXaQXEj6ZCDKV8wljxHBkPlOtuOc5qm8Tfz9Znh6QAlVDN694g4EghCxrvVL5oconUwrD5oiOEqaqcx_PR0CJFTCj53H9BJNWTg4AvYTiempK-3pycF3Rb43bq082ubUTr2XZAxuV2lW-5yWe1QAL10rIE59M3Bqx-oqjAmqhtre1w",
                bestStorePrice = StorePrice(
                    storeId = "reliance_indiranagar",
                    storeName = "Reliance Smart",
                    distanceFormatted = "1.1 km",
                    price = 245.0,
                    mrp = 285.0,
                    lastObservedRelativeTime = "4h ago",
                    sourceDescription = "Community reported",
                    statusBadge = PriceStatusBadgeType.CommunityReported,
                    hasPhotoVerification = false,
                    isLowestPrice = true
                ),
                storesComparedCount = 4,
                alternativePrices = listOf(
                    StorePrice("nilgiris_indiranagar", "Nilgiris Supermarket", "0.6 km", 255.0, 285.0, lastObservedRelativeTime = "3h ago", sourceDescription = "Community corroborated", statusBadge = PriceStatusBadgeType.CommunityCorroborated),
                    StorePrice("dmart_koramangala", "DMart", "2.3 km", 242.0, 285.0, lastObservedRelativeTime = "Yesterday", sourceDescription = "Store confirmed", statusBadge = PriceStatusBadgeType.StoreConfirmed, isLowestPrice = true),
                    StorePrice("more_supermarket", "More Supermarket", "0.8 km", 260.0, 285.0, lastObservedRelativeTime = "2 days ago", sourceDescription = "Price may be outdated", statusBadge = PriceStatusBadgeType.PriceMayBeOutdated)
                ),
                barcode = "8901725181222"
            ),
            Product(
                id = "tata_salt_1kg",
                name = "Tata Salt Vacuum Evaporated Iodised Salt",
                categoryId = "groceries",
                quantityDescription = "1kg Pouch",
                packageSizeBadge = "1kg",
                imageUrl = "",
                bestStorePrice = StorePrice(
                    storeId = "apna_bazar",
                    storeName = "Apna Bazar Kirana",
                    distanceFormatted = "0.3 km",
                    price = 24.0,
                    mrp = 28.0,
                    lastObservedRelativeTime = "1h ago",
                    sourceDescription = "Store confirmed",
                    statusBadge = PriceStatusBadgeType.StoreConfirmed,
                    hasPhotoVerification = false,
                    isLowestPrice = true
                ),
                storesComparedCount = 3,
                alternativePrices = listOf(
                    StorePrice("nilgiris_indiranagar", "Nilgiris Supermarket", "0.6 km", 26.0, 28.0, lastObservedRelativeTime = "2h ago", sourceDescription = "Community corroborated", statusBadge = PriceStatusBadgeType.CommunityCorroborated),
                    StorePrice("reliance_indiranagar", "Reliance Smart", "1.1 km", 27.0, 28.0, lastObservedRelativeTime = "4h ago", sourceDescription = "Community reported", statusBadge = PriceStatusBadgeType.CommunityReported)
                ),
                barcode = "8901058850023"
            ),
            Product(
                id = "dettol_soap_125g",
                name = "Dettol Original Germ Protection Bathing Soap",
                categoryId = "personal_care",
                quantityDescription = "125g Bar",
                packageSizeBadge = "125g",
                imageUrl = "",
                bestStorePrice = StorePrice(
                    storeId = "more_supermarket",
                    storeName = "More Supermarket",
                    distanceFormatted = "0.8 km",
                    price = 42.0,
                    mrp = 49.0,
                    lastObservedRelativeTime = "5h ago",
                    sourceDescription = "Recently observed",
                    statusBadge = PriceStatusBadgeType.RecentlyObserved,
                    hasPhotoVerification = false,
                    isLowestPrice = true
                ),
                storesComparedCount = 2,
                alternativePrices = listOf(
                    StorePrice("nilgiris_indiranagar", "Nilgiris Supermarket", "0.6 km", 45.0, 49.0, lastObservedRelativeTime = "3h ago", sourceDescription = "Community reported", statusBadge = PriceStatusBadgeType.CommunityReported)
                ),
                barcode = "8901030825315"
            ),
            Product(
                id = "maggi_noodles_4pack",
                name = "Maggi 2-Minute Masala Noodles",
                categoryId = "snacks",
                quantityDescription = "4-Pack (280g)",
                packageSizeBadge = "4-Pack",
                imageUrl = "",
                bestStorePrice = StorePrice(
                    storeId = "apna_bazar",
                    storeName = "Apna Bazar Kirana",
                    distanceFormatted = "0.3 km",
                    price = 52.0,
                    mrp = 56.0,
                    lastObservedRelativeTime = "1h ago",
                    sourceDescription = "Community corroborated",
                    statusBadge = PriceStatusBadgeType.CommunityCorroborated,
                    hasPhotoVerification = true,
                    isLowestPrice = true
                ),
                storesComparedCount = 3,
                alternativePrices = listOf(
                    StorePrice("reliance_indiranagar", "Reliance Smart", "1.1 km", 54.0, 56.0, lastObservedRelativeTime = "3h ago", sourceDescription = "Community reported", statusBadge = PriceStatusBadgeType.CommunityReported),
                    StorePrice("dmart_koramangala", "DMart", "2.3 km", 50.0, 56.0, lastObservedRelativeTime = "Yesterday", sourceDescription = "Store confirmed", statusBadge = PriceStatusBadgeType.StoreConfirmed, isLowestPrice = true)
                ),
                barcode = "8901058852881"
            )
        )
    )

    // ── Recent Searches ──
    private val recentSearches = MutableStateFlow(
        listOf("Fortune Oil", "Amul Milk", "Atta 5kg", "Tata Salt", "Dettol")
    )

    // ── Auth & Profiles ──
    private val isLoggedInState = MutableStateFlow(true)

    private val availableProfiles = MutableStateFlow(
        listOf(
            UserProfile(
                id = "user_001",
                displayName = "Price Explorer",
                email = "explorer@pricebridge.in",
                phoneNumber = "+91 98765 43210",
                karmaPoints = 240,
                pricesReported = 12,
                pricesVerified = 8,
                accuracyPercent = 92,
                memberSince = "Sep 2026",
                reputationLevel = "Trusted Contributor",
                role = com.example.core.model.UserRole.ActiveContributor
            ),
            UserProfile(
                id = "user_002",
                displayName = "Priya Sharma",
                email = "priya.sharma@gmail.com",
                phoneNumber = "+91 98450 11223",
                karmaPoints = 580,
                pricesReported = 34,
                pricesVerified = 26,
                accuracyPercent = 97,
                memberSince = "Jul 2026",
                reputationLevel = "Community Steward",
                role = com.example.core.model.UserRole.AreaModerator
            ),
            UserProfile(
                id = "user_003",
                displayName = "Apna Bazar Kirana (Store Partner)",
                email = "apna.bazar@kirana.in",
                phoneNumber = "+91 99160 33445",
                karmaPoints = 1200,
                pricesReported = 145,
                pricesVerified = 89,
                accuracyPercent = 99,
                memberSince = "May 2026",
                reputationLevel = "Store Partner",
                role = com.example.core.model.UserRole.StorePartner
            )
        )
    )

    // ── User Profile ──
    private val userProfile = MutableStateFlow(
        availableProfiles.value.first()
    )

    // ── Submission History ──
    private val submissionHistory = MutableStateFlow(
        listOf(
            PriceSubmission("sub_1", "Fortune Sunlite Sunflower Oil", "Nilgiris Supermarket", 142.0, 165.0, sourceType = "Shelf label", submittedRelativeTime = "2h ago", statusBadge = PriceStatusBadgeType.CommunityCorroborated),
            PriceSubmission("sub_2", "Amul Taaza Toned Milk", "Apna Bazar Kirana", 72.0, 75.0, sourceType = "Personal observation", submittedRelativeTime = "3h ago", statusBadge = PriceStatusBadgeType.CommunityCorroborated),
            PriceSubmission("sub_3", "Aashirvaad Sharbati Atta", "Reliance Smart", 245.0, 285.0, sourceType = "Shelf label", submittedRelativeTime = "4h ago", statusBadge = PriceStatusBadgeType.CommunityReported),
            PriceSubmission("sub_4", "Tata Salt 1kg", "Nilgiris Supermarket", 26.0, 28.0, sourceType = "Personal observation", submittedRelativeTime = "Yesterday", statusBadge = PriceStatusBadgeType.StoreConfirmed),
            PriceSubmission("sub_5", "Dettol Soap 125g", "More Supermarket", 42.0, 49.0, sourceType = "Shelf label", submittedRelativeTime = "2 days ago", statusBadge = PriceStatusBadgeType.RecentlyObserved)
        )
    )

    // Shopping List / Basket State
    private val shoppingBasketState = MutableStateFlow(
        ShoppingBasket(
            id = "weekend_basket_1",
            title = "Weekend Grocery List",
            items = listOf(
                ShoppingListItem(
                    id = "item_oil_1",
                    productName = "Fortune Sunlite Sunflower Oil",
                    quantity = 1,
                    packageSize = "1L Pouch",
                    isChecked = false,
                    bestStoreName = "Nilgiris Supermarket",
                    bestStorePrice = 142.0,
                    lastObservedRelativeTime = "2h ago",
                    statusBadge = PriceStatusBadgeType.CommunityCorroborated
                ),
                ShoppingListItem(
                    id = "item_milk_1",
                    productName = "Amul Taaza Homogenised Toned Milk",
                    quantity = 2,
                    packageSize = "1L Tetra Pack",
                    isChecked = false,
                    bestStoreName = "Apna Bazar Kirana",
                    bestStorePrice = 72.0,
                    lastObservedRelativeTime = "3h ago",
                    statusBadge = PriceStatusBadgeType.CommunityCorroborated
                ),
                ShoppingListItem(
                    id = "item_atta_1",
                    productName = "Aashirvaad Sharbati Atta",
                    quantity = 1,
                    packageSize = "5kg Bag",
                    isChecked = false,
                    bestStoreName = "Reliance Smart",
                    bestStorePrice = 245.0,
                    lastObservedRelativeTime = "4h ago",
                    statusBadge = PriceStatusBadgeType.CommunityReported
                ),
                ShoppingListItem(
                    id = "item_salt_1",
                    productName = "Tata Salt Vacuum Evaporated",
                    quantity = 1,
                    packageSize = "1kg Pouch",
                    isChecked = true,
                    bestStoreName = "Nilgiris Supermarket",
                    bestStorePrice = 26.0,
                    lastObservedRelativeTime = "Yesterday",
                    statusBadge = PriceStatusBadgeType.StoreConfirmed
                )
            ),
            singleStoreEstimatedTotal = 633.0,
            optimizedEstimatedTotal = 485.0,
            estimatedSavingsAmount = 148.0,
            storeSplits = listOf(
                StoreSplitSummary(storeName = "Nilgiris Supermarket", itemCount = 2, subtotal = 168.0),
                StoreSplitSummary(storeName = "Apna Bazar Kirana", itemCount = 1, subtotal = 72.0),
                StoreSplitSummary(storeName = "Reliance Smart", itemCount = 1, subtotal = 245.0)
            )
        )
    )

    // ── Home ──
    override fun getLocation(): Flow<LocationInfo> = currentLocation.asStateFlow()
    override fun getSmartBasketSummary(): Flow<SmartBasketSummary> = basketSummary.asStateFlow()
    override fun getCategories(): Flow<List<Category>> = categories.asStateFlow()

    override fun getBestDeals(categoryId: String?): Flow<List<Product>> {
        return allProducts.map { list ->
            if (categoryId == null || categoryId == "all") {
                list
            } else {
                list.filter { it.categoryId == categoryId }
            }
        }
    }

    override fun getKarmaPrompt(): Flow<KarmaVerificationPrompt?> = karmaPromptState.asStateFlow()

    // ── Search ──
    override fun searchProducts(query: String, categoryId: String?): Flow<List<Product>> {
        return allProducts.map { list ->
            val q = query.trim().lowercase()
            val filtered = if (q.isBlank()) {
                list
            } else {
                list.filter {
                    it.name.lowercase().contains(q) ||
                            it.quantityDescription.lowercase().contains(q) ||
                            it.categoryId.lowercase().contains(q)
                }
            }
            if (categoryId == null || categoryId == "all") {
                filtered
            } else {
                filtered.filter { it.categoryId == categoryId }
            }
        }
    }

    override fun getProductByBarcode(barcode: String): Flow<Product?> {
        return allProducts.map { list ->
            list.find { it.barcode.equals(barcode.trim(), ignoreCase = true) }
        }
    }

    override fun getRecentSearches(): Flow<List<String>> = recentSearches.asStateFlow()

    override suspend fun addRecentSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isNotBlank()) {
            val current = recentSearches.value.toMutableList()
            current.remove(trimmed)
            current.add(0, trimmed)
            recentSearches.value = current.take(10)
        }
    }

    // ── Product Detail & Catalog ──
    override fun getProductById(productId: String): Flow<Product?> {
        return allProducts.map { list -> list.find { it.id == productId } }
    }

    override fun getStorePricesForProduct(productId: String): Flow<List<StorePrice>> {
        return allProducts.map { list ->
            val product = list.find { it.id == productId }
            if (product != null) {
                (listOf(product.bestStorePrice) + product.alternativePrices)
                    .sortedBy { it.price }
            } else {
                emptyList()
            }
        }
    }

    override suspend fun addProduct(product: Product): String {
        allProducts.value = allProducts.value + product
        return product.id
    }

    override suspend fun confirmPriceFreshness(productId: String, storeId: String) {
        allProducts.value = allProducts.value.map { product ->
            if (product.id == productId) {
                val updatedBest = if (product.bestStorePrice.storeId == storeId) {
                    product.bestStorePrice.copy(
                        statusBadge = PriceStatusBadgeType.RecentlyObserved,
                        lastObservedRelativeTime = "Just now"
                    )
                } else product.bestStorePrice
                val updatedAlternatives = product.alternativePrices.map { alt ->
                    if (alt.storeId == storeId) {
                        alt.copy(
                            statusBadge = PriceStatusBadgeType.RecentlyObserved,
                            lastObservedRelativeTime = "Just now"
                        )
                    } else alt
                }
                product.copy(bestStorePrice = updatedBest, alternativePrices = updatedAlternatives)
            } else product
        }
        userProfile.value = userProfile.value.copy(
            karmaPoints = userProfile.value.karmaPoints + 5
        )
    }

    override suspend fun reportDiscrepancy(
        productId: String,
        storeId: String,
        reason: String,
        notes: String?
    ) {
        allProducts.value = allProducts.value.map { product ->
            if (product.id == productId) {
                val updatedBest = if (product.bestStorePrice.storeId == storeId) {
                    product.bestStorePrice.copy(
                        statusBadge = PriceStatusBadgeType.FlaggedForReview
                    )
                } else product.bestStorePrice
                val updatedAlternatives = product.alternativePrices.map { alt ->
                    if (alt.storeId == storeId) {
                        alt.copy(
                            statusBadge = PriceStatusBadgeType.FlaggedForReview
                        )
                    } else alt
                }
                product.copy(bestStorePrice = updatedBest, alternativePrices = updatedAlternatives)
            } else product
        }
        userProfile.value = userProfile.value.copy(
            karmaPoints = userProfile.value.karmaPoints + 10
        )
    }

    // ── Phase 3 Shopping Tasks: Saved Lists & Price Watch ──
    private val savedListsState = MutableStateFlow(
        listOf(
            SavedShoppingList(
                id = "list_weekly_staples",
                title = "Weekly Staples & Dairy",
                itemCount = 4,
                estimatedTotal = 385.0,
                recurringInterval = "Weekly",
                isRecurring = true,
                lastRunDate = "2 days ago"
            ),
            SavedShoppingList(
                id = "list_monthly_pantry",
                title = "Monthly Pantry Restock",
                itemCount = 8,
                estimatedTotal = 1240.0,
                recurringInterval = "Monthly",
                isRecurring = true,
                lastRunDate = "Last week"
            ),
            SavedShoppingList(
                id = "list_weekend_baking",
                title = "Baking & Dessert Kit",
                itemCount = 5,
                estimatedTotal = 560.0,
                recurringInterval = null,
                isRecurring = false,
                lastRunDate = "May 12"
            )
        )
    )

    private val priceWatchAlertsState = MutableStateFlow(
        listOf(
            PriceWatchAlert(
                id = "pw_sunflower_oil",
                productName = "Fortune Sunlite Sunflower Oil",
                packageSize = "1L Pouch",
                targetPrice = 135.0,
                currentBestPrice = 139.0,
                currentBestStore = "DMart Koramangala",
                isTargetMet = false,
                priceDropPercent = 4
            ),
            PriceWatchAlert(
                id = "pw_tata_salt",
                productName = "Tata Salt Iodised",
                packageSize = "1kg Pack",
                targetPrice = 25.0,
                currentBestPrice = 24.0,
                currentBestStore = "Apna Bazar Kirana",
                isTargetMet = true,
                priceDropPercent = 12
            ),
            PriceWatchAlert(
                id = "pw_atta_5kg",
                productName = "Aashirvaad Superior MP Sharbati Atta",
                packageSize = "5kg Bag",
                targetPrice = 310.0,
                currentBestPrice = 325.0,
                currentBestStore = "Nilgiris Supermarket",
                isTargetMet = false,
                priceDropPercent = 0
            )
        )
    )

    // ── Shopping Basket ──
    override fun getShoppingBasket(): Flow<ShoppingBasket> = shoppingBasketState.asStateFlow()

    override suspend fun toggleItemChecked(itemId: String) {
        val currentBasket = shoppingBasketState.value
        val updatedItems = currentBasket.items.map { item ->
            if (item.id == itemId) item.copy(isChecked = !item.isChecked) else item
        }
        recalculateBasket(updatedItems)
    }

    override suspend fun updateItemQuantity(itemId: String, newQuantity: Int) {
        val currentBasket = shoppingBasketState.value
        val updatedItems = if (newQuantity <= 0) {
            currentBasket.items.filter { it.id != itemId }
        } else {
            currentBasket.items.map { item ->
                if (item.id == itemId) item.copy(quantity = newQuantity) else item
            }
        }
        recalculateBasket(updatedItems)
    }

    override suspend fun addShoppingItem(name: String, quantity: Int, packageSize: String) {
        val currentBasket = shoppingBasketState.value
        val newItem = ShoppingListItem(
            id = "custom_${UUID.randomUUID()}",
            productName = name,
            quantity = quantity,
            packageSize = if (packageSize.isBlank()) "Standard Pack" else packageSize,
            isChecked = false,
            bestStoreName = "Nearby Kirana",
            bestStorePrice = 65.0,
            lastObservedRelativeTime = "Recently observed",
            statusBadge = PriceStatusBadgeType.RecentlyObserved,
            alternativeName = "Store Brand equivalent",
            alternativePrice = 58.0
        )
        val updatedItems = currentBasket.items + newItem
        recalculateBasket(updatedItems)
    }

    override suspend fun deleteShoppingItem(itemId: String) {
        val currentBasket = shoppingBasketState.value
        val updatedItems = currentBasket.items.filter { it.id != itemId }
        recalculateBasket(updatedItems)
    }

    override suspend fun clearCheckedItems() {
        val currentBasket = shoppingBasketState.value
        val updatedItems = currentBasket.items.filter { !it.isChecked }
        recalculateBasket(updatedItems)
    }

    override fun getSavedShoppingLists(): Flow<List<SavedShoppingList>> = savedListsState.asStateFlow()

    override fun getPriceWatchAlerts(): Flow<List<PriceWatchAlert>> = priceWatchAlertsState.asStateFlow()

    override suspend fun loadSavedListIntoActiveBasket(listId: String) {
        // Populate active basket based on selected template
        val templateItems = listOf(
            ShoppingListItem(
                id = "item_oil_${UUID.randomUUID()}",
                productName = "Fortune Sunlite Sunflower Oil",
                quantity = 2,
                packageSize = "1L Pouch",
                bestStoreName = "Nilgiris Supermarket",
                bestStorePrice = 142.0,
                lastObservedRelativeTime = "2h ago",
                statusBadge = PriceStatusBadgeType.CommunityCorroborated,
                alternativeName = "Saffola Gold (₹160)",
                alternativePrice = 160.0
            ),
            ShoppingListItem(
                id = "item_milk_${UUID.randomUUID()}",
                productName = "Amul Taaza Homogenised Milk",
                quantity = 3,
                packageSize = "1L Tetra",
                bestStoreName = "Apna Bazar Kirana",
                bestStorePrice = 72.0,
                lastObservedRelativeTime = "3h ago",
                statusBadge = PriceStatusBadgeType.CommunityCorroborated,
                alternativeName = "Nandini Goodlife (₹68)",
                alternativePrice = 68.0
            ),
            ShoppingListItem(
                id = "item_atta_${UUID.randomUUID()}",
                productName = "Aashirvaad Sharbati Atta",
                quantity = 1,
                packageSize = "5kg Bag",
                bestStoreName = "DMart",
                bestStorePrice = 325.0,
                lastObservedRelativeTime = "Yesterday",
                statusBadge = PriceStatusBadgeType.StoreConfirmed,
                alternativeName = "Pillsbury Chakki Fresh (₹315)",
                alternativePrice = 315.0
            )
        )
        recalculateBasket(templateItems)
    }

    private fun recalculateBasket(items: List<ShoppingListItem>) {
        val optimizedTotal = items.sumOf { it.estimatedItemTotal }
        val singleStoreTotal = (optimizedTotal * 1.25).coerceAtLeast(optimizedTotal)
        val savings = if (singleStoreTotal > optimizedTotal) singleStoreTotal - optimizedTotal else 0.0

        val splitMap = items.groupBy { it.bestStoreName }
        val splits = splitMap.map { (storeName, storeItems) ->
            StoreSplitSummary(
                storeName = storeName,
                itemCount = storeItems.size,
                subtotal = storeItems.sumOf { it.estimatedItemTotal }
            )
        }

        // 1-Store Recommendation (single trip convenience)
        val oneStoreRec = StoreRecommendation(
            title = "Fastest: 1-Store Run",
            isTwoStoreSplit = false,
            primaryStoreName = "Nilgiris Supermarket",
            primaryStoreDistance = "0.6 km",
            primaryItemCount = items.size,
            totalEstimatedCost = singleStoreTotal,
            savingsVsSingleStore = 0.0,
            missingItemsCount = 0,
            reliabilityScore = 96,
            badgeLabel = "Fastest • Single Stop",
            isRecommended = false
        )

        // 2-Store Recommendation (maximum savings optimization)
        val topStores = splits.sortedByDescending { it.itemCount }
        val store1 = topStores.getOrNull(0)
        val store2 = topStores.getOrNull(1)

        val twoStoreRec = StoreRecommendation(
            title = "Max Savings: 2-Store Split",
            isTwoStoreSplit = true,
            primaryStoreName = store1?.storeName ?: "DMart",
            primaryStoreDistance = "1.2 km",
            primaryItemCount = store1?.itemCount ?: ((items.size + 1) / 2),
            secondaryStoreName = store2?.storeName ?: "Nilgiris",
            secondaryStoreDistance = "0.6 km",
            secondaryItemCount = store2?.itemCount ?: (items.size / 2),
            totalEstimatedCost = optimizedTotal,
            savingsVsSingleStore = savings,
            missingItemsCount = 0,
            reliabilityScore = 98,
            badgeLabel = "Max Savings • Save ₹${savings.toInt()}",
            isRecommended = true
        )

        shoppingBasketState.value = shoppingBasketState.value.copy(
            items = items,
            optimizedEstimatedTotal = optimizedTotal,
            singleStoreEstimatedTotal = singleStoreTotal,
            estimatedSavingsAmount = savings,
            storeSplits = splits,
            oneStoreRecommendation = oneStoreRec,
            twoStoreRecommendation = twoStoreRec
        )

        basketSummary.value = basketSummary.value.copy(
            estimatedSavingsAmount = savings,
            basketItemCount = items.size
        )
    }

    // ── Price Submission ──
    override fun getNearbyStores(): Flow<List<Store>> = nearbyStores.asStateFlow()
    override fun getAllProducts(): Flow<List<Product>> = allProducts.asStateFlow()

    override suspend fun submitPrice(
        productId: String,
        storeId: String,
        price: Double,
        mrp: Double,
        sourceType: String
    ) {
        // Simulate network delay
        kotlinx.coroutines.delay(1200)

        val product = allProducts.value.find { it.id == productId }
        val store = nearbyStores.value.find { it.id == storeId }

        if (product != null && store != null) {
            val newSubmission = PriceSubmission(
                id = "sub_${UUID.randomUUID()}",
                productName = product.name,
                storeName = store.name,
                price = price,
                mrp = mrp,
                sourceType = sourceType,
                submittedRelativeTime = "Just now",
                statusBadge = PriceStatusBadgeType.CommunityReported
            )
            submissionHistory.value = listOf(newSubmission) + submissionHistory.value

            // Bump karma
            userProfile.value = userProfile.value.copy(
                karmaPoints = userProfile.value.karmaPoints + 20,
                pricesReported = userProfile.value.pricesReported + 1
            )
        }
    }

    // ── Offline Submission Queue ──
    private val pendingOfflineCount = MutableStateFlow(0)
    private val queuedOfflineItems = MutableStateFlow<List<QueuedOfflineItem>>(emptyList())

    data class QueuedOfflineItem(
        val id: String,
        val idempotencyKey: String,
        val productId: String,
        val storeId: String,
        val price: Double,
        val mrp: Double,
        val sourceType: String
    )

    override fun getPendingOfflineSubmissionCount(): Flow<Int> = pendingOfflineCount.asStateFlow()

    override suspend fun enqueueOfflinePrice(
        productId: String,
        storeId: String,
        price: Double,
        mrp: Double,
        sourceType: String
    ): String {
        val id = "offline_${UUID.randomUUID()}"
        val idempKey = "idemp_${UUID.randomUUID()}"
        val item = QueuedOfflineItem(
            id = id,
            idempotencyKey = idempKey,
            productId = productId,
            storeId = storeId,
            price = price,
            mrp = mrp,
            sourceType = sourceType
        )
        queuedOfflineItems.value = queuedOfflineItems.value + item
        pendingOfflineCount.value = queuedOfflineItems.value.size
        return idempKey
    }

    override suspend fun syncOfflineSubmissions(): Int {
        val items = queuedOfflineItems.value
        if (items.isEmpty()) return 0

        for (item in items) {
            submitPrice(
                productId = item.productId,
                storeId = item.storeId,
                price = item.price,
                mrp = item.mrp,
                sourceType = item.sourceType
            )
        }

        val count = items.size
        queuedOfflineItems.value = emptyList()
        pendingOfflineCount.value = 0
        return count
    }

    // ── Profile & Auth ──
    override fun getUserProfile(): Flow<UserProfile> = userProfile.asStateFlow()
    override fun getSubmissionHistory(): Flow<List<PriceSubmission>> = submissionHistory.asStateFlow()
    override fun isUserLoggedIn(): Flow<Boolean> = isLoggedInState.asStateFlow()

    override suspend fun loginWithPhone(phoneNumber: String, otp: String): Boolean {
        kotlinx.coroutines.delay(600)
        isLoggedInState.value = true
        userProfile.value = userProfile.value.copy(
            phoneNumber = phoneNumber,
            isGuest = false
        )
        return true
    }

    override suspend fun loginWithGoogle(): Boolean {
        kotlinx.coroutines.delay(600)
        isLoggedInState.value = true
        userProfile.value = userProfile.value.copy(
            displayName = "Priya Sharma",
            email = "priya.sharma@gmail.com",
            isGuest = false
        )
        return true
    }

    override suspend fun loginAsGuest() {
        isLoggedInState.value = true
        userProfile.value = UserProfile(
            id = "guest_user",
            displayName = "Guest Shopper",
            email = "",
            karmaPoints = 0,
            pricesReported = 0,
            pricesVerified = 0,
            accuracyPercent = 100,
            memberSince = "Today",
            reputationLevel = "Price Explorer",
            role = com.example.core.model.UserRole.CommunityShopper,
            isGuest = true
        )
    }

    override suspend fun logout() {
        isLoggedInState.value = false
        userProfile.value = UserProfile(
            id = "guest_user",
            displayName = "Guest Shopper",
            email = "",
            karmaPoints = 0,
            pricesReported = 0,
            pricesVerified = 0,
            accuracyPercent = 100,
            memberSince = "Today",
            reputationLevel = "Price Explorer",
            role = com.example.core.model.UserRole.CommunityShopper,
            isGuest = true
        )
    }

    override fun getAvailableProfiles(): Flow<List<UserProfile>> = availableProfiles.asStateFlow()

    override suspend fun switchProfile(profileId: String) {
        val selected = availableProfiles.value.find { it.id == profileId }
        if (selected != null) {
            userProfile.value = selected
            isLoggedInState.value = true
        }
    }

    // ── Settings ──
    override suspend fun updateLocation(neighborhood: String, radiusKm: Double) {
        currentLocation.value = LocationInfo(
            neighborhood = neighborhood,
            addressLine = "Bangalore, Karnataka",
            radiusDescription = "$radiusKm km radius",
            isLiveTrackingActive = true
        )
    }

    override suspend fun dismissKarmaPrompt(promptId: String) {
        if (karmaPromptState.value?.id == promptId) {
            karmaPromptState.value = null
        }
    }
}
