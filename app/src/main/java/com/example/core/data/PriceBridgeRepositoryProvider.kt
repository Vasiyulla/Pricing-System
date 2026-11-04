package com.example.core.data

/**
 * Global repository provider that supplies the live network repository to all ViewModels.
 */
object PriceBridgeRepositoryProvider {
    val repository: PriceBridgeRepository by lazy {
        NetworkPriceBridgeRepository()
    }
}

