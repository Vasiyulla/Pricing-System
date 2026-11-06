package com.example.core.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.core.data.local.dao.CachedCategoryDao
import com.example.core.data.local.dao.CachedDealDao
import com.example.core.data.local.dao.CachedProductDao
import com.example.core.data.local.dao.CachedStoreDao
import com.example.core.data.local.dao.QueuedSubmissionDao
import com.example.core.data.local.entity.CachedCategoryEntity
import com.example.core.data.local.entity.CachedDealEntity
import com.example.core.data.local.entity.CachedProductEntity
import com.example.core.data.local.entity.CachedStoreEntity
import com.example.core.data.local.entity.QueuedSubmissionEntity

/**
 * Main Room database for Price Bridge local cache and offline submission queue.
 */
@Database(
    entities = [
        QueuedSubmissionEntity::class,
        CachedDealEntity::class,
        CachedProductEntity::class,
        CachedStoreEntity::class,
        CachedCategoryEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class PriceBridgeDatabase : RoomDatabase() {

    abstract fun queuedSubmissionDao(): QueuedSubmissionDao
    abstract fun cachedDealDao(): CachedDealDao
    abstract fun cachedProductDao(): CachedProductDao
    abstract fun cachedStoreDao(): CachedStoreDao
    abstract fun cachedCategoryDao(): CachedCategoryDao

    companion object {
        @Volatile
        private var INSTANCE: PriceBridgeDatabase? = null

        @Volatile
        var appContext: Context? = null
            private set

        fun getInstance(context: Context): PriceBridgeDatabase {
            appContext = context.applicationContext
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PriceBridgeDatabase::class.java,
                    "price_bridge_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
