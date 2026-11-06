package com.example.core.data.offline

import android.content.Context
import com.example.core.data.PriceBridgeRepository
import com.example.core.data.local.PriceBridgeDatabase
import com.example.core.data.local.dao.QueuedSubmissionDao
import com.example.core.data.local.entity.QueuedSubmissionEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * Service managing offline price submissions, idempotent queuing, and synchronization.
 * Implements Sprint 6 resilience: offline queue with Room, preserving idempotency keys across retries,
 * and background synchronization via WorkManager.
 */
class OfflineSubmissionManager(
    private val dao: QueuedSubmissionDao,
    private val repository: PriceBridgeRepository,
    private val context: Context? = null
) {
    /**
     * Observes count of pending offline submissions.
     */
    fun observePendingCount(): Flow<Int> = dao.observePendingCount()

    /**
     * Observes all submissions in the offline queue.
     */
    fun observeAllSubmissions(): Flow<List<QueuedSubmissionEntity>> = dao.getAllSubmissions()

    /**
     * Enqueue a submission for offline storage.
     * Generates a unique client-side idempotency key that persists across retries.
     * Schedules PriceSyncWorker to synchronize once network connectivity is available.
     */
    suspend fun enqueue(
        productId: String,
        productName: String,
        storeId: String,
        storeName: String,
        price: Double,
        mrp: Double,
        sourceType: String,
        evidencePhotoUri: String? = null
    ): QueuedSubmissionEntity {
        val submission = QueuedSubmissionEntity(
            id = UUID.randomUUID().toString(),
            idempotencyKey = "sub_${UUID.randomUUID()}",
            productId = productId,
            productName = productName,
            storeId = storeId,
            storeName = storeName,
            price = price,
            mrp = mrp,
            sourceType = sourceType,
            evidencePhotoUri = evidencePhotoUri,
            status = QueuedSubmissionEntity.STATUS_PENDING,
            createdAt = System.currentTimeMillis()
        )
        dao.insert(submission)

        // Enqueue WorkManager background job
        val ctx = context ?: PriceBridgeDatabase.appContext
        if (ctx != null) {
            PriceSyncWorker.enqueue(ctx)
        }

        return submission
    }

    /**
     * Attempts to synchronize all pending submissions to the backend repository.
     * Returns count of successfully synced submissions.
     */
    suspend fun syncPending(): Int {
        val ctx = context ?: PriceBridgeDatabase.appContext
        if (ctx != null) {
            PriceSyncWorker.enqueue(ctx)
        }

        val pending = dao.getPendingSubmissions()
        var successCount = 0

        for (item in pending) {
            dao.updateStatus(
                id = item.id,
                status = QueuedSubmissionEntity.STATUS_SYNCING,
                retryCount = item.retryCount,
                lastAttemptAt = System.currentTimeMillis(),
                error = null
            )

            try {
                // Submit to repository
                repository.submitPrice(
                    productId = item.productId,
                    storeId = item.storeId,
                    price = item.price,
                    mrp = item.mrp,
                    sourceType = item.sourceType
                )

                // Mark synced and remove from queue
                dao.updateStatus(
                    id = item.id,
                    status = QueuedSubmissionEntity.STATUS_SYNCED,
                    retryCount = item.retryCount,
                    lastAttemptAt = System.currentTimeMillis(),
                    error = null
                )
                dao.deleteSubmission(item.id)
                successCount++
            } catch (ex: Exception) {
                // Mark failed or retry
                dao.updateStatus(
                    id = item.id,
                    status = QueuedSubmissionEntity.STATUS_PENDING,
                    retryCount = item.retryCount + 1,
                    lastAttemptAt = System.currentTimeMillis(),
                    error = ex.localizedMessage ?: "Sync error"
                )
            }
        }

        return successCount
    }

    /**
     * Delete an individual submission from the queue.
     */
    suspend fun delete(id: String) {
        dao.deleteSubmission(id)
    }

    /**
     * Clear all synced submissions from the queue.
     */
    suspend fun clearSynced() {
        dao.clearSynced()
    }
}
