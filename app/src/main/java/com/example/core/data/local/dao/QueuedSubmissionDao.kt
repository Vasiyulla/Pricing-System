package com.example.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.core.data.local.entity.QueuedSubmissionEntity
import kotlinx.coroutines.flow.Flow

/**
 * Room Data Access Object for offline price submissions.
 */
@Dao
interface QueuedSubmissionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(submission: QueuedSubmissionEntity)

    @Query("SELECT * FROM queued_submissions WHERE status = 'PENDING' ORDER BY created_at ASC")
    suspend fun getPendingSubmissions(): List<QueuedSubmissionEntity>

    @Query("SELECT COUNT(*) FROM queued_submissions WHERE status = 'PENDING'")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT * FROM queued_submissions ORDER BY created_at DESC")
    fun getAllSubmissions(): Flow<List<QueuedSubmissionEntity>>

    @Query("UPDATE queued_submissions SET status = :status, retry_count = :retryCount, last_attempt_at = :lastAttemptAt, error_message = :error WHERE id = :id")
    suspend fun updateStatus(
        id: String,
        status: String,
        retryCount: Int,
        lastAttemptAt: Long,
        error: String?
    )

    @Query("DELETE FROM queued_submissions WHERE id = :id")
    suspend fun deleteSubmission(id: String)

    @Query("DELETE FROM queued_submissions WHERE status = 'SYNCED'")
    suspend fun clearSynced()
}
