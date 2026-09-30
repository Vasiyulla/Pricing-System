package com.example.core.model

/**
 * A price submission record for the user's contribution history.
 */
data class PriceSubmission(
    val id: String,
    val productName: String,
    val storeName: String,
    val price: Double,
    val mrp: Double,
    val currencySymbol: String = "₹",
    val sourceType: String,
    val submittedRelativeTime: String,
    val statusBadge: PriceStatusBadgeType
)

/**
 * Source types with confidence ordering per plan Section 2:
 * store confirmation > checkout/receipt > shelf label > personal observation > verbal quote
 */
enum class SourceType(val label: String, val confidence: Int) {
    StoreConfirmation("Store confirmation", 5),
    CheckoutReceipt("Checkout / receipt", 4),
    ShelfLabel("Shelf label", 3),
    PersonalObservation("Personal observation", 2),
    VerbalQuote("Verbal quote", 1)
}
