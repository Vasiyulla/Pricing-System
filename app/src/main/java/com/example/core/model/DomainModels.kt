package com.example.core.model

data class Category(
    val id: String,
    val name: String
)

data class SmartBasketSummary(
    val estimatedSavingsAmount: Double,
    val storesComparedCount: Int,
    val storeNamesSample: List<String>,
    val basketItemCount: Int,
    val basketName: String
)

data class KarmaVerificationPrompt(
    val id: String,
    val storeName: String,
    val description: String,
    val karmaRewardPoints: Int = 20
)

data class LocationInfo(
    val title: String = "Current Location",
    val neighborhood: String,
    val addressLine: String,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val isLiveTrackingActive: Boolean = true
)

/**
 * Blinkit/Zomato saved delivery address with label (Home, Work, Other).
 */
data class SavedAddress(
    val id: String = java.util.UUID.randomUUID().toString(),
    val label: String, // "Home", "Work", "Other"
    val addressLine: String, // "Flat 402, Green Glen Layout"
    val locality: String, // "Bellandur, Bengaluru"
    val latitude: Double,
    val longitude: Double
)
