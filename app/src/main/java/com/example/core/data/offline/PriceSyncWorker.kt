package com.example.core.data.offline

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.core.data.PriceBridgeRepositoryProvider
import com.example.core.data.local.PriceBridgeDatabase
import com.example.core.data.local.entity.QueuedSubmissionEntity
import com.example.core.network.ApiClient
import com.example.core.network.NetworkPriceSubmission
import com.example.core.session.AppSessionManager
import retrofit2.HttpException
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Background WorkManager worker responsible for synchronizing offline queued price submissions.
 * Configured with CONNECTED network constraints and exponential backoff.
 * Leverages idempotency keys across client and server to prevent duplicate records.
 */
class PriceSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        // Hydrate SharedPreferences session token cleanly if WorkManager woke in a fresh process
        AppSessionManager.init(applicationContext)
        PriceBridgeRepositoryProvider.init(applicationContext)

        val database = PriceBridgeDatabase.getInstance(applicationContext)
        val dao = database.queuedSubmissionDao()
        val pendingSubmissions = dao.getPendingSubmissions()

        if (pendingSubmissions.isEmpty()) {
            return Result.success()
        }

        var hasRetryableError = false

        for (item in pendingSubmissions) {
            dao.updateStatus(
                id = item.id,
                status = QueuedSubmissionEntity.STATUS_SYNCING,
                retryCount = item.retryCount,
                lastAttemptAt = System.currentTimeMillis(),
                error = null
            )

            try {
                val evidenceId = item.evidencePhotoUri
                    ?.takeIf { it.isNotBlank() && !it.startsWith("sample_") && !it.startsWith("photo_captured_") }
                    ?.let { uri ->
                        PriceBridgeRepositoryProvider.repository.uploadEvidence(
                            productId = item.productId,
                            storeId = item.storeId,
                            sourceType = item.sourceType,
                            imagePath = uri
                        )
                    }

                val pence = (item.price * 100).toInt()
                val payload = NetworkPriceSubmission(
                    productId = item.productId,
                    storeId = item.storeId,
                    regularPricePence = pence,
                    sourceType = item.sourceType,
                    idempotencyKey = item.idempotencyKey,
                    evidenceId = evidenceId
                )

                ApiClient.service.submitPrice(
                    submission = payload,
                    idempotencyKey = item.idempotencyKey
                )

                // Successfully synced - mark and delete from local queue
                dao.updateStatus(
                    id = item.id,
                    status = QueuedSubmissionEntity.STATUS_SYNCED,
                    retryCount = item.retryCount,
                    lastAttemptAt = System.currentTimeMillis(),
                    error = null
                )
                dao.deleteSubmission(item.id)
            } catch (ioe: IOException) {
                // Network unreachable or timeout -> retry later with backoff
                hasRetryableError = true
                dao.updateStatus(
                    id = item.id,
                    status = QueuedSubmissionEntity.STATUS_PENDING,
                    retryCount = item.retryCount + 1,
                    lastAttemptAt = System.currentTimeMillis(),
                    error = ioe.localizedMessage ?: "Network connection unavailable"
                )
            } catch (httpEx: HttpException) {
                if (httpEx.code() in 500..599) {
                    // Server error -> retryable
                    hasRetryableError = true
                    dao.updateStatus(
                        id = item.id,
                        status = QueuedSubmissionEntity.STATUS_PENDING,
                        retryCount = item.retryCount + 1,
                        lastAttemptAt = System.currentTimeMillis(),
                        error = "Server error HTTP ${httpEx.code()}"
                    )
                } else if (httpEx.code() == 409) {
                    // Idempotent duplicate: already recorded on backend, treat as synced
                    dao.updateStatus(
                        id = item.id,
                        status = QueuedSubmissionEntity.STATUS_SYNCED,
                        retryCount = item.retryCount,
                        lastAttemptAt = System.currentTimeMillis(),
                        error = null
                    )
                    dao.deleteSubmission(item.id)
                } else {
                    // Client error (4xx except 409) -> non-retryable
                    dao.updateStatus(
                        id = item.id,
                        status = QueuedSubmissionEntity.STATUS_FAILED,
                        retryCount = item.retryCount + 1,
                        lastAttemptAt = System.currentTimeMillis(),
                        error = "HTTP ${httpEx.code()}: ${httpEx.message()}"
                    )
                }
            } catch (ex: Exception) {
                // Unexpected runtime exception
                dao.updateStatus(
                    id = item.id,
                    status = QueuedSubmissionEntity.STATUS_FAILED,
                    retryCount = item.retryCount + 1,
                    lastAttemptAt = System.currentTimeMillis(),
                    error = ex.localizedMessage ?: "Submission error"
                )
            }
        }

        return if (hasRetryableError) {
            Result.retry()
        } else {
            Result.success()
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "price_sync_worker"

        /**
         * Builds a OneTimeWorkRequest with CONNECTED network constraints
         * and exponential backoff starting at 15 seconds.
         */
        fun buildWorkRequest(): OneTimeWorkRequest {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            return OneTimeWorkRequestBuilder<PriceSyncWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    15,
                    TimeUnit.SECONDS
                )
                .build()
        }

        /**
         * Enqueues or appends unique sync work to WorkManager.
         */
        fun enqueue(context: Context) {
            val workManager = WorkManager.getInstance(context.applicationContext)
            workManager.enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                buildWorkRequest()
            )
        }
    }
}
