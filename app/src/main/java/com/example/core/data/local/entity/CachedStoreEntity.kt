package com.example.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.core.model.Store
import com.example.core.model.StoreClaimStatus
import com.example.core.network.NetworkStore

/**
 * Room entity representing a cached store in SQLite for offline access.
 */
@Entity(tableName = "cached_stores")
data class CachedStoreEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val address: String,
    val chain: String = "",
    val format: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    @ColumnInfo(name = "distance_formatted")
    val distanceFormatted: String = "Nearby",
    @ColumnInfo(name = "is_open")
    val isOpen: Boolean = true,
    @ColumnInfo(name = "operating_hours")
    val operatingHours: String = "7:00 AM - 11:00 PM",
    val phone: String? = null,
    @ColumnInfo(name = "reliability_score")
    val reliabilityScore: Int = 96,
    @ColumnInfo(name = "is_store_confirmed_partner")
    val isStoreConfirmedPartner: Boolean = false,
    @ColumnInfo(name = "claim_status")
    val claimStatus: String = "UNCLAIMED",
    @ColumnInfo(name = "total_observations_count")
    val totalObservationsCount: Int = 184,
    @ColumnInfo(name = "verified_prices_count")
    val verifiedPricesCount: Int = 42,
    @ColumnInfo(name = "recent_discrepancy_count")
    val recentDiscrepancyCount: Int = 0,
    @ColumnInfo(name = "active_warning_notice")
    val activeWarningNotice: String? = null,
    @ColumnInfo(name = "category_tags")
    val categoryTags: String = "Supermarket",
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toStore(): Store {
        val tags = if (categoryTags.isNotBlank()) {
            categoryTags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        } else {
            listOf("Supermarket")
        }

        return Store(
            id = id,
            name = name,
            address = address,
            distanceFormatted = distanceFormatted,
            latitude = latitude,
            longitude = longitude,
            isOpen = isOpen,
            operatingHours = operatingHours,
            phone = phone,
            reliabilityScore = reliabilityScore,
            isStoreConfirmedPartner = isStoreConfirmedPartner,
            claimStatus = when (claimStatus) {
                "CLAIMED_BY_MERCHANT" -> StoreClaimStatus.CLAIMED_BY_MERCHANT
                "VERIFIED_PARTNER" -> StoreClaimStatus.VERIFIED_PARTNER
                else -> StoreClaimStatus.UNCLAIMED
            },
            totalObservationsCount = totalObservationsCount,
            verifiedPricesCount = verifiedPricesCount,
            recentDiscrepancyCount = recentDiscrepancyCount,
            activeWarningNotice = activeWarningNotice,
            categoryTags = tags
        )
    }

    companion object {
        fun fromStore(store: Store): CachedStoreEntity {
            return CachedStoreEntity(
                id = store.id,
                name = store.name,
                address = store.address,
                chain = "",
                format = "",
                latitude = store.latitude,
                longitude = store.longitude,
                distanceFormatted = store.distanceFormatted,
                isOpen = store.isOpen,
                operatingHours = store.operatingHours,
                phone = store.phone,
                reliabilityScore = store.reliabilityScore,
                isStoreConfirmedPartner = store.isStoreConfirmedPartner,
                claimStatus = store.claimStatus.name,
                totalObservationsCount = store.totalObservationsCount,
                verifiedPricesCount = store.verifiedPricesCount,
                recentDiscrepancyCount = store.recentDiscrepancyCount,
                activeWarningNotice = store.activeWarningNotice,
                categoryTags = store.categoryTags.joinToString(","),
                updatedAt = System.currentTimeMillis()
            )
        }

        fun fromNetwork(net: NetworkStore): CachedStoreEntity {
            val dist = if (net.distanceKm != null) {
                val km = net.distanceKm
                val mins = (km * 12).toInt().coerceAtLeast(1)
                if (km < 1.0) "${(km * 1000).toInt()}m · ${mins}m walk" else "%.1f km · ${mins}m walk".format(km)
            } else {
                "Nearby"
            }
            val isPartner = net.chain in listOf("Tesco", "Sainsbury's", "Lidl", "Aldi", "Waitrose")
            return CachedStoreEntity(
                id = net.id,
                name = net.name,
                address = "${net.address}, ${net.postcode}",
                chain = net.chain,
                format = net.format,
                latitude = net.latitude,
                longitude = net.longitude,
                distanceFormatted = dist,
                isOpen = true,
                operatingHours = "7:00 AM - 11:00 PM",
                phone = null,
                reliabilityScore = 96,
                isStoreConfirmedPartner = isPartner,
                claimStatus = "UNCLAIMED",
                categoryTags = "${net.chain},${net.format},Supermarket",
                updatedAt = System.currentTimeMillis()
            )
        }
    }
}
