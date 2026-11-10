package com.example.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.core.data.local.entity.BasketItemEntity

@Dao
interface BasketItemDao {
    @Query("SELECT * FROM basket_items ORDER BY createdAt ASC")
    suspend fun getAllItems(): List<BasketItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertItem(item: BasketItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replaceAll(items: List<BasketItemEntity>)

    @Query("DELETE FROM basket_items WHERE id = :id")
    suspend fun deleteItem(id: String)

    @Query("DELETE FROM basket_items WHERE isChecked = 1")
    suspend fun deleteCheckedItems()

    @Query("DELETE FROM basket_items")
    suspend fun clearAll()
}

