package com.example.core.model

/**
 * Represents a single store's reported price observation for an item.
 */
data class StorePrice(
    val storeId: String,
    val storeName: String,
    val distanceFormatted: String, // e.g. "0.6 km"
    val price: Double,
    val mrp: Double,
    val currencySymbol: String = "₹",
    val lastObservedRelativeTime: String, // e.g. "2h ago"
    val sourceDescription: String, // e.g. "Community reported", "Verified by 4 contributors"
    val statusBadge: PriceStatusBadgeType,
    val hasPhotoVerification: Boolean = false,
    val isLowestPrice: Boolean = false
) {
    val savingsAmount: Double = if (mrp > price) mrp - price else 0.0
    val discountPercent: Int = if (mrp > 0 && mrp > price) (((mrp - price) / mrp) * 100).toInt() else 0
}
