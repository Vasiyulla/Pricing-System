package com.example.core.model

/**
 * Product item entity for comparison and feed displays.
 */
data class Product(
    val id: String,
    val name: String,
    val categoryId: String,
    val quantityDescription: String, // e.g. "1L Pouch", "1L Tetra Pack"
    val packageSizeBadge: String, // e.g. "1L", "1L Tetra"
    val imageUrl: String,
    val bestStorePrice: StorePrice,
    val storesComparedCount: Int,
    val alternativePrices: List<StorePrice> = emptyList(),
    val barcode: String? = null
)
