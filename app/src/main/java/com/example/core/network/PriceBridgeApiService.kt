package com.example.core.network

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface PriceBridgeApiService {

    @GET("health")
    suspend fun checkHealth(): Map<String, Any>

    @GET("api/v1/stores/nearby")
    suspend fun getNearbyStores(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("radius_km") radiusKm: Double = 10.0,
        @Query("mode") mode: String = "walking"
    ): List<NetworkStore>

    @GET("api/v1/stores/{id}")
    suspend fun getStoreById(@Path("id") storeId: String): NetworkStore

    @GET("api/v1/products/search")
    suspend fun searchProducts(
        @Query("q") query: String,
        @Query("category") category: String? = null
    ): List<NetworkProduct>

    @GET("api/v1/products/barcode/{ean}")
    suspend fun getProductByBarcode(@Path("ean") barcode: String): NetworkProduct

    @GET("api/v1/products/{id}")
    suspend fun getProductById(@Path("id") productId: String): NetworkProduct

    @GET("api/v1/products/{id}/compare")
    suspend fun compareProductPrices(
        @Path("id") productId: String,
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("mode") mode: String = "walking"
    ): List<NetworkStorePriceComparison>

    @GET("api/v1/products/{id}/trolley-whisper")
    suspend fun getTrolleyWhisper(
        @Path("id") productId: String,
        @Query("store_id") storeId: String,
        @Query("lat") lat: Double,
        @Query("lon") lon: Double
    ): NetworkTrolleyWhisper

    @POST("api/v1/prices/submit")
    suspend fun submitPrice(
        @Body submission: NetworkPriceSubmission,
        @Header("Idempotency-Key") idempotencyKey: String? = null
    ): Map<String, Any>

    @POST("api/v1/prices/batch-sync")
    suspend fun batchSyncPrices(
        @Body submissions: List<NetworkPriceSubmission>,
        @Header("Idempotency-Key") idempotencyKey: String? = null
    ): Map<String, Any>

    @POST("api/v1/basket/optimize")
    suspend fun optimizeBasket(@Body request: NetworkBasketOptimizeRequest): NetworkTwoStopBasketResult

    @GET("api/v1/bounties/nearby")
    suspend fun getNearbyBounties(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double
    ): List<NetworkBounty>

    @POST("api/v1/bounties/{id}/claim")
    suspend fun claimBounty(@Path("id") bountyId: String): Map<String, Any>

    @GET("api/v1/yellow-stickers/active")
    suspend fun getActiveYellowStickers(
        @Query("store_id") storeId: String? = null
    ): List<NetworkYellowStickerDeal>

    @GET("api/v1/categories")
    suspend fun getCategories(): List<NetworkCategory>

    @GET("api/v1/bounties/prompt")
    suspend fun getActiveBountyPrompt(): NetworkBountyPrompt?

    @POST("api/v1/ai/receipt-scan")
    suspend fun scanReceiptAI(@Body payload: Map<String, String>): NetworkExtractedReceipt

    @POST("api/v1/ai/yellow-sticker-scan")
    suspend fun scanYellowStickerAI(@Body payload: Map<String, String>): NetworkYellowStickerDeal

    @POST("api/v1/ai/shelf-tag-scan")
    suspend fun scanShelfTagAI(@Body payload: Map<String, String>): NetworkExtractedShelfTag

    @GET("api/v1/products/deals")
    suspend fun getBestDealsBatch(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("category") category: String? = null,
        @Query("mode") mode: String = "walking"
    ): List<NetworkProductDealSummary>

    @POST("api/v1/auth/google")
    suspend fun loginWithGoogle(
        @Body payload: Map<String, String>
    ): NetworkUserSession

    @POST("api/v1/auth/sign-in")
    suspend fun signInWithEmail(
        @Body payload: Map<String, String>
    ): NetworkUserSession

    @POST("api/v1/auth/sign-up")
    suspend fun signUpWithEmail(
        @Body payload: Map<String, String>
    ): NetworkUserSession

    @POST("api/v1/auth/logout")
    suspend fun logout(): Map<String, String>

    @POST("api/v1/admin/db/reset")
    suspend fun resetDatabase(
        @Body payload: Map<String, @JvmSuppressWildcards Any> = emptyMap()
    ): Map<String, Any>

    @POST("api/v1/products")
    suspend fun createProduct(
        @Body payload: Map<String, @JvmSuppressWildcards Any>
    ): NetworkProduct

    @POST("api/v1/prices/confirm-freshness")
    suspend fun confirmPriceFreshness(
        @Body payload: Map<String, String>
    ): Map<String, Any>

    @POST("api/v1/stores/{id}/report-discrepancy")
    suspend fun reportStoreDiscrepancy(
        @Path("id") storeId: String,
        @Body payload: Map<String, @JvmSuppressWildcards Any>
    ): Map<String, Any>

    @POST("api/v1/evidence/upload")
    suspend fun uploadEvidence(
        @Body payload: Map<String, String>
    ): NetworkEvidenceUploadResponse

    @GET("api/v1/users/me/profile")
    suspend fun getMyProfile(): NetworkUserProfileStats

    @GET("api/v1/users/me/submissions")
    suspend fun getMySubmissions(): List<NetworkPriceRecordItem>
}

