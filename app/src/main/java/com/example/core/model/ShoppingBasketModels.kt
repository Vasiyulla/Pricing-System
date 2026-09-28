package com.example.core.model

data class ShoppingListItem(
    val id: String,
    val productName: String,
    val quantity: Int = 1,
    val packageSize: String, // e.g. "1L", "5kg", "500g"
    val isChecked: Boolean = false,
    val bestStoreName: String,
    val bestStorePrice: Double,
    val lastObservedRelativeTime: String,
    val statusBadge: PriceStatusBadgeType
) {
    val estimatedItemTotal: Double = bestStorePrice * quantity
}

data class StoreSplitSummary(
    val storeName: String,
    val itemCount: Int,
    val subtotal: Double
)

data class ShoppingBasket(
    val id: String,
    val title: String,
    val items: List<ShoppingListItem>,
    val singleStoreEstimatedTotal: Double,
    val optimizedEstimatedTotal: Double,
    val estimatedSavingsAmount: Double,
    val storeSplits: List<StoreSplitSummary>
)
