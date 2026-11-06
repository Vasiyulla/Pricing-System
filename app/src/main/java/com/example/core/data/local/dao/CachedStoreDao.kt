package com.example.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.core.data.local.entity.CachedStoreEntity
import kotlinx.coroutines.flow.Flow

/**
 * Room Data Access Object for cached stores.
 */
@Dao
interface CachedStoreDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStores(stores: List<CachedStoreEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStore(store: CachedStoreEntity)

    @Query("SELECT * FROM cached_stores WHERE id = :storeId LIMIT 1")
    suspend fun getStoreById(storeId: String): CachedStoreEntity?

    @Query("SELECT * FROM cached_stores ORDER BY name ASC")
    suspend fun getAllStores(): List<CachedStoreEntity>

    @Query("SELECT * FROM cached_stores ORDER BY name ASC")
    fun observeAllStores(): Flow<List<CachedStoreEntity>>

    @Query("DELETE FROM cached_stores WHERE id = :storeId")
    suspend fun deleteStore(storeId: String)

    @Query("DELETE FROM cached_stores")
    suspend fun clearAll()
}
