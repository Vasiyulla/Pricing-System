package com.example.core.data

import com.example.core.model.Category
import com.example.core.model.KarmaVerificationPrompt
import com.example.core.model.LocationInfo
import com.example.core.model.PriceStatusBadgeType
import com.example.core.model.Product
import com.example.core.model.ShoppingBasket
import com.example.core.model.ShoppingListItem
import com.example.core.model.SmartBasketSummary
import com.example.core.model.StorePrice
import com.example.core.model.StoreSplitSummary
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
                storesComparedCount = 4
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
                storesComparedCount = 3
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
                storesComparedCount = 3
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
                storesComparedCount = 4
            )
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

    override fun getShoppingBasket(): Flow<ShoppingBasket> = shoppingBasketState.asStateFlow()

    override suspend fun toggleItemChecked(itemId: String) {
        val currentBasket = shoppingBasketState.value
        val updatedItems = currentBasket.items.map { item ->
            if (item.id == itemId) item.copy(isChecked = !item.isChecked) else item
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
            statusBadge = PriceStatusBadgeType.RecentlyObserved
        )
        val updatedItems = currentBasket.items + newItem
        recalculateBasket(updatedItems)
    }

    override suspend fun deleteShoppingItem(itemId: String) {
        val currentBasket = shoppingBasketState.value
        val updatedItems = currentBasket.items.filter { it.id != itemId }
        recalculateBasket(updatedItems)
    }

    private fun recalculateBasket(items: List<ShoppingListItem>) {
        val optimizedTotal = items.sumOf { it.estimatedItemTotal }
        val singleStoreTotal = optimizedTotal * 1.30
        val savings = if (singleStoreTotal > optimizedTotal) singleStoreTotal - optimizedTotal else 0.0

        val splitMap = items.groupBy { it.bestStoreName }
        val splits = splitMap.map { (storeName, storeItems) ->
            StoreSplitSummary(
                storeName = storeName,
                itemCount = storeItems.size,
                subtotal = storeItems.sumOf { it.estimatedItemTotal }
            )
        }

        shoppingBasketState.value = shoppingBasketState.value.copy(
            items = items,
            optimizedEstimatedTotal = optimizedTotal,
            singleStoreEstimatedTotal = singleStoreTotal,
            estimatedSavingsAmount = savings,
            storeSplits = splits
        )

        basketSummary.value = basketSummary.value.copy(
            estimatedSavingsAmount = savings,
            basketItemCount = items.size
        )
    }

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
