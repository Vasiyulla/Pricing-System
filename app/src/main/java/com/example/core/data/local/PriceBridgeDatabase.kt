package com.example.core.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.core.data.local.dao.QueuedSubmissionDao
import com.example.core.data.local.entity.QueuedSubmissionEntity

/**
 * Main Room database for Price Bridge local cache and offline submission queue.
 */
@Database(
    entities = [QueuedSubmissionEntity::class],
    version = 1,
    exportSchema = false
)
abstract class PriceBridgeDatabase : RoomDatabase() {

    abstract fun queuedSubmissionDao(): QueuedSubmissionDao

    companion object {
        @Volatile
        private var INSTANCE: PriceBridgeDatabase? = null

        fun getInstance(context: Context): PriceBridgeDatabase {
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
