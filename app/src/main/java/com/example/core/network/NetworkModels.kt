package com.example.core.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class NetworkStore(
    val id: String,
    val chain: String,
    val format: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val address: String,
    val postcode: String,
    @Json(name = "distance_km") val distanceKm: Double? = null
)

@JsonClass(generateAdapter = true)
data class NetworkProduct(
    val id: String,
    val name: String,
    val brand: String,
    val barcode: String,
    val category: String,
    @Json(name = "package_size") val packageSize: String,
    @Json(name = "unit_metric") val unitMetric: String,
    @Json(name = "target_mrp_gbp") val targetMrpGbp: Double
)

@JsonClass(generateAdapter = true)
data class NetworkStorePriceComparison(
    val store: NetworkStore,
    @Json(name = "regular_price_pence") val regularPricePence: Int,
    @Json(name = "regular_price_gbp") val regularPriceGbp: String,
    @Json(name = "loyalty_price_pence") val loyaltyPricePence: Int? = null,
    @Json(name = "loyalty_price_gbp") val loyaltyPriceGbp: String? = null,
    @Json(name = "loyalty_scheme") val loyaltyScheme: String = "NONE",
    @Json(name = "effective_price_pence") val effectivePricePence: Int,
    @Json(name = "unit_price_pence") val unitPricePence: Int,
    @Json(name = "unit_price_formatted") val unitPriceFormatted: String,
    @Json(name = "freshness_status") val freshnessStatus: String,
    @Json(name = "is_best_deal") val isBestDeal: Boolean,
    @Json(name = "observed_ago") val observedAgo: String
)

@JsonClass(generateAdapter = true)
data class NetworkTrolleyWhisper(
    @Json(name = "product_id") val productId: String,
    @Json(name = "product_name") val productName: String,
    @Json(name = "current_store_id") val currentStoreId: String,
    @Json(name = "current_price_pence") val currentPricePence: Int,
    @Json(name = "whisper_text") val whisperText: String,
    val recommendation: String,
    @Json(name = "alternative_store") val alternativeStore: NetworkStore? = null,
    @Json(name = "potential_savings_pence") val potentialSavingsPence: Int? = null
)

@JsonClass(generateAdapter = true)
data class NetworkPriceSubmission(
    @Json(name = "product_id") val productId: String,
    @Json(name = "store_id") val storeId: String,
    @Json(name = "regular_price_pence") val regularPricePence: Int,
    @Json(name = "loyalty_price_pence") val loyaltyPricePence: Int? = null,
    @Json(name = "loyalty_scheme") val loyaltyScheme: String? = null,
    @Json(name = "source_type") val sourceType: String = "mobile_app",
    @Json(name = "idempotency_key") val idempotencyKey: String? = null
)

@JsonClass(generateAdapter = true)
data class NetworkBasketItem(
    @Json(name = "product_id") val productId: String,
    @Json(name = "product_name") val productName: String,
    val quantity: Int
)

@JsonClass(generateAdapter = true)
data class NetworkBasketOptimizeRequest(
    val items: List<NetworkBasketItem>,
    @Json(name = "user_lat") val userLat: Double,
    @Json(name = "user_lon") val userLon: Double
)

@JsonClass(generateAdapter = true)
data class NetworkTwoStopBasketResult(
    @Json(name = "store_1") val store1: NetworkStore,
    @Json(name = "store_1_items") val store1Items: List<NetworkBasketItem>,
    @Json(name = "store_1_total_pence") val store1TotalPence: Int,
    @Json(name = "store_1_total_gbp") val store1TotalGbp: String,

    @Json(name = "store_2") val store2: NetworkStore,
    @Json(name = "store_2_items") val store2Items: List<NetworkBasketItem>,
    @Json(name = "store_2_total_pence") val store2TotalPence: Int,
    @Json(name = "store_2_total_gbp") val store2TotalGbp: String,

    @Json(name = "combined_total_pence") val combinedTotalPence: Int,
    @Json(name = "combined_total_gbp") val combinedTotalGbp: String,

    @Json(name = "single_store_best_store") val singleStoreBestStore: NetworkStore,
    @Json(name = "single_store_best_pence") val singleStoreBestPence: Int,
    @Json(name = "single_store_best_gbp") val singleStoreBestGbp: String,

    @Json(name = "net_savings_pence") val netSavingsPence: Int,
    @Json(name = "net_savings_gbp") val netSavingsGbp: String,
    @Json(name = "walking_distance_meters") val walkingDistanceMeters: Int,
    @Json(name = "estimated_extra_minutes") val estimatedExtraMinutes: Int
)

@JsonClass(generateAdapter = true)
data class NetworkBounty(
    val id: String,
    @Json(name = "store_id") val storeId: String,
    @Json(name = "store_name") val storeName: String,
    @Json(name = "product_id") val productId: String,
    @Json(name = "product_name") val productName: String,
    @Json(name = "reward_points") val rewardPoints: Int,
    @Json(name = "expires_at") val expiresAt: String,
    val status: String,
    @Json(name = "distance_km") val distanceKm: Double
)

@JsonClass(generateAdapter = true)
data class NetworkYellowStickerDeal(
    val id: String,
    @Json(name = "store_id") val storeId: String,
    @Json(name = "store_name") val storeName: String,
    @Json(name = "product_name") val productName: String,
    @Json(name = "was_price_pence") val wasPricePence: Int,
    @Json(name = "now_price_pence") val nowPricePence: Int,
    @Json(name = "discount_percent") val discountPercent: Int,
    @Json(name = "use_by_date") val useByDate: String
)

@JsonClass(generateAdapter = true)
data class NetworkReceiptLineItem(
    val name: String,
    val quantity: Int,
    @Json(name = "regular_price_pence") val regularPricePence: Int,
    @Json(name = "loyalty_price_pence") val loyaltyPricePence: Int? = null,
    @Json(name = "loyalty_scheme") val loyaltyScheme: String? = null,
    @Json(name = "vat_rate_percent") val vatRatePercent: Int = 0
)

@JsonClass(generateAdapter = true)
data class NetworkExtractedReceipt(
    @Json(name = "store_name") val storeName: String,
    @Json(name = "branch_number") val branchNumber: String? = null,
    @Json(name = "vat_number") val vatNumber: String? = null,
    val date: String,
    @Json(name = "total_pence") val totalPence: Int,
    @Json(name = "vat_amount_pence") val vatAmountPence: Int,
    val items: List<NetworkReceiptLineItem>,
    @Json(name = "is_math_valid") val isMathValid: Boolean
)

@JsonClass(generateAdapter = true)
data class NetworkCategory(
    val id: String,
    val name: String
)

@JsonClass(generateAdapter = true)
data class NetworkBountyPrompt(
    @Json(name = "bounty_id") val bountyId: String = "",
    @Json(name = "product_name") val productName: String = "",
    @Json(name = "store_name") val storeName: String = "",
    @Json(name = "last_observed_price") val lastObservedPrice: String = "",
    @Json(name = "reward_points") val rewardPoints: Int = 0
)

@JsonClass(generateAdapter = true)
data class NetworkProductDealSummary(
    val product: NetworkProduct,
    @Json(name = "best_price_pence") val bestPricePence: Int,
    @Json(name = "best_price_gbp") val bestPriceGbp: String,
    @Json(name = "best_store_name") val bestStoreName: String,
    @Json(name = "best_store_id") val bestStoreId: String = "",
    @Json(name = "best_store_distance_km") val bestStoreDistanceKm: Double = 0.5,
    @Json(name = "compared_store_count") val comparedStoreCount: Int = 1,
    @Json(name = "freshness_status") val freshnessStatus: String = "RECENTLY_OBSERVED",
    val alternatives: List<NetworkStorePriceComparison> = emptyList()
)

@JsonClass(generateAdapter = true)
data class NetworkUserSession(
    val id: String,
    val email: String,
    @Json(name = "display_name") val displayName: String,
    @Json(name = "avatar_url") val avatarUrl: String? = null,
    val role: String = "ActiveContributor",
    val token: String = "",
    @Json(name = "karma_points") val karmaPoints: Int = 350,
    @Json(name = "auth_mode") val authMode: String = "GOOGLE"
)

