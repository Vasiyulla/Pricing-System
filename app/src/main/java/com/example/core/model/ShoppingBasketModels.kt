package com.example.core.model

data class ShoppingListItem(
    val id: String,
    val productName: String,
    val quantity: Int = 1,
    val packageSize: String, // e.g. "1L", "5kg", "500g"
    val unit: String = "unit",
    val isChecked: Boolean = false,
    val bestStoreName: String,
    val bestStorePrice: Double,
    val lastObservedRelativeTime: String,
    val statusBadge: PriceStatusBadgeType,
    val alternativeName: String? = null,
    val alternativePrice: Double? = null,
    val isAvailableAtPrimary: Boolean = true
) {
    val estimatedItemTotal: Double = bestStorePrice * quantity
}

data class StoreSplitSummary(
    val storeName: String,
    val itemCount: Int,
    val subtotal: Double
)

data class StoreRecommendation(
    val title: String,
    val isTwoStoreSplit: Boolean,
    val primaryStoreName: String,
    val primaryStoreDistance: String,
    val primaryItemCount: Int,
    val secondaryStoreName: String? = null,
    val secondaryStoreDistance: String? = null,
    val secondaryItemCount: Int? = null,
    val totalEstimatedCost: Double,
    val savingsVsSingleStore: Double = 0.0,
    val missingItemsCount: Int = 0,
    val reliabilityScore: Int = 95,
    val badgeLabel: String,
    val isRecommended: Boolean = false
)

data class SavedShoppingList(
    val id: String,
    val title: String,
    val itemCount: Int,
    val estimatedTotal: Double,
    val recurringInterval: String? = null, // e.g. "Weekly", "Monthly"
    val isRecurring: Boolean = false,
    val lastRunDate: String = "3 days ago"
)

data class PriceWatchAlert(
    val id: String,
    val productName: String,
    val packageSize: String,
    val targetPrice: Double,
    val currentBestPrice: Double,
    val currentBestStore: String,
    val isTargetMet: Boolean,
    val priceDropPercent: Int = 0
)

enum class ShoppingTaskTab(val label: String) {
    ActiveBasket("Active Basket"),
    SavedLists("Saved & Recurring"),
    PriceWatch("Price Watch")
}

data class ShoppingBasket(
    val id: String,
    val title: String,
    val items: List<ShoppingListItem>,
    val singleStoreEstimatedTotal: Double,
    val optimizedEstimatedTotal: Double,
    val estimatedSavingsAmount: Double,
    val storeSplits: List<StoreSplitSummary>,
    val oneStoreRecommendation: StoreRecommendation? = null,
    val twoStoreRecommendation: StoreRecommendation? = null
)
