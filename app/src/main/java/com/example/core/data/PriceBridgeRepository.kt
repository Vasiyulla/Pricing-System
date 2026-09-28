package com.example.core.data

import com.example.core.model.Category
import com.example.core.model.KarmaVerificationPrompt
import com.example.core.model.LocationInfo
import com.example.core.model.Product
import com.example.core.model.ShoppingBasket
import com.example.core.model.SmartBasketSummary
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for Price Bridge.
 * Kept clean for future OpenAPI Retrofit client replacement.
 */
interface PriceBridgeRepository {
    fun getLocation(): Flow<LocationInfo>
    fun getSmartBasketSummary(): Flow<SmartBasketSummary>
    fun getCategories(): Flow<List<Category>>
    fun getBestDeals(categoryId: String?): Flow<List<Product>>
    fun getKarmaPrompt(): Flow<KarmaVerificationPrompt?>
    fun getShoppingBasket(): Flow<ShoppingBasket>
    suspend fun toggleItemChecked(itemId: String)
    suspend fun addShoppingItem(name: String, quantity: Int, packageSize: String)
    suspend fun deleteShoppingItem(itemId: String)
    suspend fun updateLocation(neighborhood: String, radiusKm: Double)
    suspend fun dismissKarmaPrompt(promptId: String)
}
