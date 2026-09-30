package com.example.core.data

import com.example.core.model.Category
import com.example.core.model.KarmaVerificationPrompt
import com.example.core.model.LocationInfo
import com.example.core.model.PriceSubmission
import com.example.core.model.PriceWatchAlert
import com.example.core.model.Product
import com.example.core.model.SavedShoppingList
import com.example.core.model.ShoppingBasket
import com.example.core.model.SmartBasketSummary
import com.example.core.model.Store
import com.example.core.model.StorePrice
import com.example.core.model.UserProfile
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for Price Bridge.
 * Kept clean for future OpenAPI Retrofit client replacement.
 */
interface PriceBridgeRepository {
    // ── Home ──
    fun getLocation(): Flow<LocationInfo>
    fun getSmartBasketSummary(): Flow<SmartBasketSummary>
    fun getCategories(): Flow<List<Category>>
    fun getBestDeals(categoryId: String?): Flow<List<Product>>
    fun getKarmaPrompt(): Flow<KarmaVerificationPrompt?>

    // ── Search & Barcode ──
    fun searchProducts(query: String, categoryId: String?): Flow<List<Product>>
    fun getProductByBarcode(barcode: String): Flow<Product?>
    fun getRecentSearches(): Flow<List<String>>
    suspend fun addRecentSearch(query: String)

    // ── Product Detail & Catalog ──
    fun getProductById(productId: String): Flow<Product?>
    fun getStorePricesForProduct(productId: String): Flow<List<StorePrice>>
    suspend fun addProduct(product: Product): String
    suspend fun confirmPriceFreshness(productId: String, storeId: String)
    suspend fun reportDiscrepancy(productId: String, storeId: String, reason: String, notes: String? = null)

    // ── Shopping Basket & Phase 3 Tasks ──
    fun getShoppingBasket(): Flow<ShoppingBasket>
    suspend fun toggleItemChecked(itemId: String)
    suspend fun updateItemQuantity(itemId: String, newQuantity: Int)
    suspend fun addShoppingItem(name: String, quantity: Int, packageSize: String)
    suspend fun deleteShoppingItem(itemId: String)
    suspend fun clearCheckedItems()
    fun getSavedShoppingLists(): Flow<List<SavedShoppingList>>
    fun getPriceWatchAlerts(): Flow<List<PriceWatchAlert>>
    suspend fun loadSavedListIntoActiveBasket(listId: String)

    // ── Price Submission & Offline Queue ──
    fun getNearbyStores(): Flow<List<Store>>
    fun getAllProducts(): Flow<List<Product>>
    suspend fun submitPrice(
        productId: String,
        storeId: String,
        price: Double,
        mrp: Double,
        sourceType: String
    )
    fun getPendingOfflineSubmissionCount(): Flow<Int>
    suspend fun enqueueOfflinePrice(
        productId: String,
        storeId: String,
        price: Double,
        mrp: Double,
        sourceType: String
    ): String
    suspend fun syncOfflineSubmissions(): Int

    // ── Profile & Auth ──
    fun getUserProfile(): Flow<UserProfile>
    fun getSubmissionHistory(): Flow<List<PriceSubmission>>
    fun isUserLoggedIn(): Flow<Boolean>
    suspend fun loginWithPhone(phoneNumber: String, otp: String): Boolean
    suspend fun loginWithGoogle(): Boolean
    suspend fun loginAsGuest()
    suspend fun logout()
    fun getAvailableProfiles(): Flow<List<UserProfile>>
    suspend fun switchProfile(profileId: String)

    // ── Settings ──
    suspend fun updateLocation(neighborhood: String, radiusKm: Double)
    suspend fun dismissKarmaPrompt(promptId: String)
}
