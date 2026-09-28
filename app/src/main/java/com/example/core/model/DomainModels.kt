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
    val neighborhood: String,
    val addressLine: String,
    val radiusDescription: String,
    val isLiveTrackingActive: Boolean = true
)
