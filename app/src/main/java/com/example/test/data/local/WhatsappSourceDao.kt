package com.example.test.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface WhatsappSourceDao {

    @Query(
        """
        SELECT * FROM whatsapp_sources
        ORDER BY
            CASE mode
                WHEN 'ALLOW' THEN 0
                WHEN 'AUTO' THEN 1
                WHEN 'BLOCK' THEN 2
                ELSE 3
            END,
            lastSeenAt DESC
        """
    )
    fun getAllFlow(): Flow<List<WhatsappSourceEntity>>

    @Query("SELECT * FROM whatsapp_sources WHERE normalizedName = :normalizedName LIMIT 1")
    suspend fun getByNormalizedName(normalizedName: String): WhatsappSourceEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(source: WhatsappSourceEntity): Long

    @Query(
        """
        UPDATE whatsapp_sources
        SET groupName = :groupName,
            lastSeenAt = :lastSeenAt,
            seenCount = seenCount + 1,
            lastDecision = :lastDecision
        WHERE normalizedName = :normalizedName
        """
    )
    suspend fun updateSeen(
        groupName: String,
        normalizedName: String,
        lastSeenAt: Long,
        lastDecision: String
    ): Int

    @Query(
        """
        UPDATE whatsapp_sources
        SET groupName = :groupName,
            mode = :mode,
            lastSeenAt = :updatedAt
        WHERE normalizedName = :normalizedName
        """
    )
    suspend fun updateMode(
        groupName: String,
        normalizedName: String,
        mode: String,
        updatedAt: Long
    ): Int

    @Transaction
    suspend fun recordSeen(
        groupName: String,
        normalizedName: String,
        decision: String,
        now: Long = System.currentTimeMillis()
    ): WhatsappSourceEntity? {
        if (normalizedName.isBlank()) return null
        val insertedId = insert(
            WhatsappSourceEntity(
                groupName = groupName,
                normalizedName = normalizedName,
                mode = WhatsappSourceMode.AUTO,
                lastSeenAt = now,
                seenCount = 1,
                lastDecision = decision
            )
        )
        if (insertedId == -1L) {
            updateSeen(
                groupName = groupName,
                normalizedName = normalizedName,
                lastSeenAt = now,
                lastDecision = decision
            )
        }
        return getByNormalizedName(normalizedName)
    }

    @Transaction
    suspend fun setMode(
        groupName: String,
        normalizedName: String,
        mode: String,
        now: Long = System.currentTimeMillis()
    ) {
        if (normalizedName.isBlank()) return
        val updated = updateMode(
            groupName = groupName,
            normalizedName = normalizedName,
            mode = mode,
            updatedAt = now
        )
        if (updated == 0) {
            insert(
                WhatsappSourceEntity(
                    groupName = groupName,
                    normalizedName = normalizedName,
                    mode = mode,
                    lastSeenAt = now,
                    seenCount = 0,
                    lastDecision = WhatsappSourceDecision.UNKNOWN
                )
            )
        }
    }

    @Query("DELETE FROM whatsapp_sources WHERE normalizedName = :normalizedName")
    suspend fun deleteByNormalizedName(normalizedName: String)

    @Query("DELETE FROM whatsapp_sources")
    suspend fun deleteAll()
}
