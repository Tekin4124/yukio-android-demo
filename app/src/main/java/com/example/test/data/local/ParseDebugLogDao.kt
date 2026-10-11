package com.example.test.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ParseDebugLogDao {

    @Insert
    suspend fun insert(log: ParseDebugLogEntity): Long

    @Query("SELECT * FROM parse_debug_logs ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<ParseDebugLogEntity>

    @Query("SELECT * FROM parse_debug_logs ORDER BY createdAt DESC LIMIT :limit")
    fun getRecentFlow(limit: Int): Flow<List<ParseDebugLogEntity>>

    /**
     * Lightweight list-screen projection. Large API responses, errors, review notes and other
     * debug-only fields must not be hydrated while the main offer list is opening.
     */
    @Query(
        """
        SELECT rawMessage, sanitizedMessage, status
        FROM parse_debug_logs
        ORDER BY createdAt DESC
        LIMIT :limit
        """
    )
    fun getRecentSourceProjectionFlow(limit: Int): Flow<List<ParseSourceLogProjection>>

    @Query(
        """
        UPDATE parse_debug_logs
        SET reviewStatus = :status,
            reviewNote = :note,
            reviewedAt = :reviewedAt
        WHERE id = :id
        """
    )
    suspend fun updateReview(
        id: Long,
        status: String,
        note: String?,
        reviewedAt: Long?
    ): Int

    @Query("SELECT COUNT(*) FROM parse_debug_logs")
    suspend fun getCount(): Int

    @Query("""
SELECT COUNT(*) FROM parse_debug_logs
WHERE contentHash = :contentHash
AND createdAt >= :createdAfter
AND status NOT LIKE 'DROPPED_%'
AND status NOT IN ('queued_bad_response', 'retry_bad_response', 'queued_retryable', 'retry_retryable')
""")
    suspend fun countRecentByContentHash(contentHash: String, createdAfter: Long): Int

    @Query("DELETE FROM parse_debug_logs WHERE createdAt < :before")
    suspend fun deleteOlderThan(before: Long): Int

    @Query("DELETE FROM parse_debug_logs")
    suspend fun deleteAll()
}
