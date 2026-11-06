package com.example.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.core.data.local.entity.CachedProductEntity
import kotlinx.coroutines.flow.Flow

/**
 * Room Data Access Object for cached products.
 */
@Dao
interface CachedProductDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProducts(products: List<CachedProductEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: CachedProductEntity)

    @Query("SELECT * FROM cached_products WHERE id = :productId LIMIT 1")
    suspend fun getProductById(productId: String): CachedProductEntity?

    @Query("SELECT * FROM cached_products WHERE barcode = :barcode LIMIT 1")
    suspend fun getProductByBarcode(barcode: String): CachedProductEntity?

    @Query("SELECT * FROM cached_products WHERE name LIKE '%' || :query || '%' OR brand LIKE '%' || :query || '%' ORDER BY name ASC")
    suspend fun searchProducts(query: String): List<CachedProductEntity>

    @Query("SELECT * FROM cached_products WHERE category_id = :categoryId AND (name LIKE '%' || :query || '%' OR brand LIKE '%' || :query || '%') ORDER BY name ASC")
    suspend fun searchProductsByCategory(query: String, categoryId: String): List<CachedProductEntity>

    @Query("SELECT * FROM cached_products WHERE category_id = :categoryId ORDER BY name ASC")
    suspend fun getProductsByCategory(categoryId: String): List<CachedProductEntity>

    @Query("SELECT * FROM cached_products ORDER BY name ASC")
    suspend fun getAllProducts(): List<CachedProductEntity>

    @Query("SELECT * FROM cached_products ORDER BY name ASC")
    fun observeAllProducts(): Flow<List<CachedProductEntity>>

    @Query("DELETE FROM cached_products WHERE id = :productId")
    suspend fun deleteProduct(productId: String)

    @Query("DELETE FROM cached_products")
    suspend fun clearAll()
}
