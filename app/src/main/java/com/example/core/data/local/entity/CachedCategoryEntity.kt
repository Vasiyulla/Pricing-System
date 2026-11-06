package com.example.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.core.model.Category

/**
 * Room entity representing a cached product category in SQLite.
 */
@Entity(tableName = "cached_categories")
data class CachedCategoryEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toCategory() = Category(id = id, name = name)
}
