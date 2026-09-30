package com.example.core.model

enum class StoreClaimStatus {
    UNCLAIMED,
    CLAIMED_BY_MERCHANT,
    VERIFIED_PARTNER
}

/**
 * A retail store profile with community reliability, observation metrics, and discrepancy status.
 */
data class Store(
    val id: String,
    val name: String,
    val address: String,
    val distanceFormatted: String,
    val isOpen: Boolean = true,
    val operatingHours: String = "8:00 AM - 10:00 PM",
    val phone: String? = "+91 80 2520 1122",
    val reliabilityScore: Int = 96, // 0 - 100
    val isStoreConfirmedPartner: Boolean = false,
    val claimStatus: StoreClaimStatus = StoreClaimStatus.UNCLAIMED,
    val totalObservationsCount: Int = 184,
    val verifiedPricesCount: Int = 42,
    val recentDiscrepancyCount: Int = 0,
    val activeWarningNotice: String? = null,
    val categoryTags: List<String> = listOf("Kirana", "Groceries", "Dairy")
)
