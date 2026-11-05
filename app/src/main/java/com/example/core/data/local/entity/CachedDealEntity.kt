package com.example.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.core.model.PriceStatusBadgeType
import com.example.core.model.Product
import com.example.core.model.StorePrice

@Entity(tableName = "cached_deals")
data class CachedDealEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val categoryId: String,
    val quantityDescription: String,
    val packageSizeBadge: String,
    val imageUrl: String,
    val barcode: String? = null,
    val storesComparedCount: Int,
    val bestStoreId: String,
    val bestStoreName: String,
    val bestStoreDistanceFormatted: String,
    val bestPrice: Double,
    val mrp: Double,
    val currencySymbol: String,
    val lastObservedRelativeTime: String,
    val statusBadge: String,
    val isLowestPrice: Boolean,
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toProduct(): Product {
        return Product(
            id = id,
            name = name,
            categoryId = categoryId,
            quantityDescription = quantityDescription,
            packageSizeBadge = packageSizeBadge,
            imageUrl = imageUrl,
            bestStorePrice = StorePrice(
                storeId = bestStoreId,
                storeName = bestStoreName,
                distanceFormatted = bestStoreDistanceFormatted,
                price = bestPrice,
                mrp = mrp,
                currencySymbol = currencySymbol,
                lastObservedRelativeTime = lastObservedRelativeTime,
                sourceDescription = "Cached live deal",
                statusBadge = when (statusBadge) {
                    "StoreConfirmed" -> PriceStatusBadgeType.StoreConfirmed
                    "PriceMayBeOutdated" -> PriceStatusBadgeType.PriceMayBeOutdated
                    "CommunityCorroborated" -> PriceStatusBadgeType.CommunityCorroborated
                    else -> PriceStatusBadgeType.RecentlyObserved
                },
                isLowestPrice = isLowestPrice
            ),
            storesComparedCount = storesComparedCount,
            barcode = barcode
        )
    }

    companion object {
        fun fromProduct(product: Product): CachedDealEntity {
            val bp = product.bestStorePrice
            return CachedDealEntity(
                id = product.id,
                name = product.name,
                categoryId = product.categoryId,
                quantityDescription = product.quantityDescription,
                packageSizeBadge = product.packageSizeBadge,
                imageUrl = product.imageUrl,
                barcode = product.barcode,
                storesComparedCount = product.storesComparedCount,
                bestStoreId = bp?.storeId ?: "",
                bestStoreName = bp?.storeName ?: "Nearby Store",
                bestStoreDistanceFormatted = bp?.distanceFormatted ?: "Nearby",
                bestPrice = bp?.price ?: 0.0,
                mrp = bp?.mrp ?: bp?.price ?: 0.0,
                currencySymbol = bp?.currencySymbol ?: "£",
                lastObservedRelativeTime = bp?.lastObservedRelativeTime ?: "Recently",
                statusBadge = bp?.statusBadge?.name ?: "RecentlyObserved",
                isLowestPrice = bp?.isLowestPrice ?: true
            )
        }
    }
}

