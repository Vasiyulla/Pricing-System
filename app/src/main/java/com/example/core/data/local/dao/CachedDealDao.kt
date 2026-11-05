package com.example.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.core.data.local.entity.CachedDealEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CachedDealDao {

    @Query("SELECT * FROM cached_deals ORDER BY bestPrice ASC")
    fun getAllDeals(): Flow<List<CachedDealEntity>>

    @Query("SELECT * FROM cached_deals WHERE categoryId = :category ORDER BY bestPrice ASC")
    fun getDealsByCategory(category: String): Flow<List<CachedDealEntity>>

    @Query("SELECT * FROM cached_deals ORDER BY bestPrice ASC")
    suspend fun getAllDealsSync(): List<CachedDealEntity>

    @Query("SELECT * FROM cached_deals WHERE categoryId = :category ORDER BY bestPrice ASC")
    suspend fun getDealsByCategorySync(category: String): List<CachedDealEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeals(deals: List<CachedDealEntity>)

    @Query("DELETE FROM cached_deals")
    suspend fun clearDeals()
}

