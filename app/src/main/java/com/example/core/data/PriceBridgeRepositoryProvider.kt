package com.example.core.data

import android.content.Context
import com.example.core.data.local.PriceBridgeDatabase

/**
 * Global repository provider that supplies the live network repository to all ViewModels.
 */
object PriceBridgeRepositoryProvider {
    @Volatile
    private var instance: PriceBridgeRepository? = null

    fun init(context: Context) {
        if (instance == null) {
            synchronized(this) {
                if (instance == null) {
                    val db = PriceBridgeDatabase.getInstance(context.applicationContext)
                    instance = NetworkPriceBridgeRepository(database = db, context = context.applicationContext)
                }
            }
        }
    }

    val repository: PriceBridgeRepository
        get() = instance ?: NetworkPriceBridgeRepository()
}
