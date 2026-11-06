package com.example.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.core.model.PriceStatusBadgeType
import com.example.core.model.Product
import com.example.core.model.StorePrice
import com.example.core.network.NetworkProduct

/**
 * Room entity representing a cached product in SQLite for offline access.
 */
@Entity(tableName = "cached_products")
data class CachedProductEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val brand: String = "",
    val barcode: String? = null,
    @ColumnInfo(name = "category_id")
    val categoryId: String,
    @ColumnInfo(name = "package_size")
    val packageSize: String = "",
    @ColumnInfo(name = "unit_metric")
    val unitMetric: String = "",
    @ColumnInfo(name = "target_mrp_gbp")
    val targetMrpGbp: Double = 0.0,
    @ColumnInfo(name = "image_url")
    val imageUrl: String = "",
    @ColumnInfo(name = "stores_compared_count")
    val storesComparedCount: Int = 1,
    @ColumnInfo(name = "best_store_id")
    val bestStoreId: String = "",
    @ColumnInfo(name = "best_store_name")
    val bestStoreName: String = "",
    @ColumnInfo(name = "best_store_distance")
    val bestStoreDistance: String = "Nearby",
    @ColumnInfo(name = "best_price")
    val bestPrice: Double = 0.0,
    val mrp: Double = 0.0,
    @ColumnInfo(name = "currency_symbol")
    val currencySymbol: String = "£",
    @ColumnInfo(name = "last_observed_time")
    val lastObservedTime: String = "Recently observed",
    @ColumnInfo(name = "status_badge")
    val statusBadge: String = "RecentlyObserved",
    @ColumnInfo(name = "is_lowest_price")
    val isLowestPrice: Boolean = true,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toProduct(): Product {
        val effectivePrice = if (bestPrice > 0.0) bestPrice else targetMrpGbp
        val effectiveMrp = if (mrp > 0.0) mrp else (if (targetMrpGbp > 0.0) targetMrpGbp else effectivePrice)
        val storePrice = StorePrice(
            storeId = bestStoreId.ifBlank { "unknown" },
            storeName = bestStoreName.ifBlank { "Nearby Supermarket" },
            distanceFormatted = bestStoreDistance.ifBlank { "Nearby" },
            price = effectivePrice,
            mrp = effectiveMrp,
            currencySymbol = currencySymbol.ifBlank { "£" },
            lastObservedRelativeTime = lastObservedTime.ifBlank { "Recently observed" },
            sourceDescription = "Cached offline catalog",
            statusBadge = when (statusBadge) {
                "StoreConfirmed" -> PriceStatusBadgeType.StoreConfirmed
                "PriceMayBeOutdated" -> PriceStatusBadgeType.PriceMayBeOutdated
                "CommunityCorroborated" -> PriceStatusBadgeType.CommunityCorroborated
                else -> PriceStatusBadgeType.RecentlyObserved
            },
            hasPhotoVerification = true,
            isLowestPrice = isLowestPrice,
            reliabilityScore = 95
        )

        val quantDesc = if (brand.isNotBlank() && packageSize.isNotBlank()) {
            "$brand · $packageSize"
        } else if (packageSize.isNotBlank()) {
            packageSize
        } else {
            name
        }

        return Product(
            id = id,
            name = name,
            categoryId = categoryId,
            quantityDescription = quantDesc,
            packageSizeBadge = packageSize.ifBlank { "Std" },
            imageUrl = imageUrl,
            bestStorePrice = storePrice,
            storesComparedCount = storesComparedCount,
            alternativePrices = emptyList(),
            barcode = barcode
        )
    }

    companion object {
        fun fromProduct(product: Product): CachedProductEntity {
            val bp = product.bestStorePrice
            return CachedProductEntity(
                id = product.id,
                name = product.name,
                brand = "",
                barcode = product.barcode,
                categoryId = product.categoryId,
                packageSize = product.packageSizeBadge,
                unitMetric = "",
                targetMrpGbp = bp.mrp,
                imageUrl = product.imageUrl,
                storesComparedCount = product.storesComparedCount,
                bestStoreId = bp.storeId,
                bestStoreName = bp.storeName,
                bestStoreDistance = bp.distanceFormatted,
                bestPrice = bp.price,
                mrp = bp.mrp,
                currencySymbol = bp.currencySymbol,
                lastObservedTime = bp.lastObservedRelativeTime,
                statusBadge = bp.statusBadge.name,
                isLowestPrice = bp.isLowestPrice,
                updatedAt = System.currentTimeMillis()
            )
        }

        fun fromNetwork(
            net: NetworkProduct,
            bestPrice: StorePrice? = null,
            comparedCount: Int = 1
        ): CachedProductEntity {
            val bp = bestPrice
            return CachedProductEntity(
                id = net.id,
                name = net.name,
                brand = net.brand,
                barcode = net.barcode,
                categoryId = net.category,
                packageSize = net.packageSize,
                unitMetric = net.unitMetric,
                targetMrpGbp = net.targetMrpGbp,
                imageUrl = "",
                storesComparedCount = comparedCount,
                bestStoreId = bp?.storeId ?: "",
                bestStoreName = bp?.storeName ?: "Nearby Supermarket",
                bestStoreDistance = bp?.distanceFormatted ?: "Nearby",
                bestPrice = bp?.price ?: net.targetMrpGbp,
                mrp = bp?.mrp ?: net.targetMrpGbp,
                currencySymbol = bp?.currencySymbol ?: "£",
                lastObservedTime = bp?.lastObservedRelativeTime ?: "Recently observed",
                statusBadge = bp?.statusBadge?.name ?: "RecentlyObserved",
                isLowestPrice = bp?.isLowestPrice ?: true,
                updatedAt = System.currentTimeMillis()
            )
        }
    }
}
