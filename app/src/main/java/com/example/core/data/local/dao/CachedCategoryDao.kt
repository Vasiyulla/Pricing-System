package com.example.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.core.data.local.entity.CachedCategoryEntity
import kotlinx.coroutines.flow.Flow

/**
 * Room Data Access Object for cached categories.
 */
@Dao
interface CachedCategoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<CachedCategoryEntity>)

    @Query("SELECT * FROM cached_categories ORDER BY name ASC")
    suspend fun getAllCategories(): List<CachedCategoryEntity>

    @Query("SELECT * FROM cached_categories ORDER BY name ASC")
    fun observeAllCategories(): Flow<List<CachedCategoryEntity>>

    @Query("DELETE FROM cached_categories")
    suspend fun clearAll()
}
