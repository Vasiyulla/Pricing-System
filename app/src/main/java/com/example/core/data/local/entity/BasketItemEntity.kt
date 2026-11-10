package com.example.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.core.model.PriceStatusBadgeType
import com.example.core.model.ShoppingListItem

/**
 * Room entity persisting active Smart Shopping Basket items locally across app restarts.
 */
@Entity(tableName = "basket_items")
data class BasketItemEntity(
    @PrimaryKey val id: String,
    val productName: String,
    val quantity: Int,
    val packageSize: String,
    val unit: String,
    val isChecked: Boolean,
    val bestStoreName: String,
    val bestStorePrice: Double,
    val lastObservedRelativeTime: String,
    val statusBadgeName: String,
    val alternativeName: String? = null,
    val alternativePrice: Double? = null,
    val isAvailableAtPrimary: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toShoppingListItem(): ShoppingListItem {
        val badge = runCatching { PriceStatusBadgeType.valueOf(statusBadgeName) }
            .getOrDefault(PriceStatusBadgeType.RecentlyObserved)
        return ShoppingListItem(
            id = id,
            productName = productName,
            quantity = quantity,
            packageSize = packageSize,
            unit = unit,
            isChecked = isChecked,
            bestStoreName = bestStoreName,
            bestStorePrice = bestStorePrice,
            lastObservedRelativeTime = lastObservedRelativeTime,
            statusBadge = badge,
            alternativeName = alternativeName,
            alternativePrice = alternativePrice,
            isAvailableAtPrimary = isAvailableAtPrimary
        )
    }

    companion object {
        fun fromShoppingListItem(item: ShoppingListItem): BasketItemEntity {
            return BasketItemEntity(
                id = item.id,
                productName = item.productName,
                quantity = item.quantity,
                packageSize = item.packageSize,
                unit = item.unit,
                isChecked = item.isChecked,
                bestStoreName = item.bestStoreName,
                bestStorePrice = item.bestStorePrice,
                lastObservedRelativeTime = item.lastObservedRelativeTime,
                statusBadgeName = item.statusBadge.name,
                alternativeName = item.alternativeName,
                alternativePrice = item.alternativePrice,
                isAvailableAtPrimary = item.isAvailableAtPrimary
            )
        }
    }
}

