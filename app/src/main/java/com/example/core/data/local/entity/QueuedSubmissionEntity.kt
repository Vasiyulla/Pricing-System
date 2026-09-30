package com.example.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing an offline-queued price submission.
 * Designed to satisfy Plan Section 6 & Sprint 6 resilience:
 * - Append-only local storage before network confirmation.
 * - Client-generated idempotency key reused across retries to prevent double-recording.
 */
@Entity(tableName = "queued_submissions")
data class QueuedSubmissionEntity(
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "idempotency_key")
    val idempotencyKey: String,

    @ColumnInfo(name = "product_id")
    val productId: String,

    @ColumnInfo(name = "product_name")
    val productName: String,

    @ColumnInfo(name = "store_id")
    val storeId: String,

    @ColumnInfo(name = "store_name")
    val storeName: String,

    val price: Double,
    val mrp: Double,

    @ColumnInfo(name = "source_type")
    val sourceType: String,

    @ColumnInfo(name = "evidence_photo_uri")
    val evidencePhotoUri: String? = null,

    val status: String = STATUS_PENDING,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "retry_count")
    val retryCount: Int = 0,

    @ColumnInfo(name = "last_attempt_at")
    val lastAttemptAt: Long? = null,

    @ColumnInfo(name = "error_message")
    val errorMessage: String? = null
) {
    companion object {
        const val STATUS_PENDING = "PENDING"
        const val STATUS_SYNCING = "SYNCING"
        const val STATUS_SYNCED = "SYNCED"
        const val STATUS_FAILED = "FAILED"
    }
}
