package com.example.test.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction

/**
 * Kimliksiz parser örneği etkileşim kuyruğu (outbox). Yalnız opak sampleId,
 * ilan sırası ve olay kategorisi tutulur; mesaj içeriği, rota, telefon veya
 * kimlik burada yoktur. Gönderim best-effort'tur: ağ yoksa satır PENDING
 * kalır, sınırlı sayıda tekrar denenir, kullanıcı tercihi kapatırsa PENDING
 * satırlar silinir.
 *
 * offerIndex -1 = mesaj/sample seviyesi olay (OPENED, FEEDBACK_MISSING).
 * (sampleId, offerIndex, kind) benzersizdir; feedback'te son tıklama kazanır.
 */
@Entity(
    tableName = "parser_sample_events",
    indices = [
        Index(value = ["sampleId", "offerIndex", "kind"], unique = true),
        Index(value = ["state"])
    ]
)
data class ParserSampleEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sampleId: String,
    val offerIndex: Int = MESSAGE_LEVEL_OFFER_INDEX,
    val kind: String,
    val eventType: String,
    val state: String = STATE_PENDING,
    val attempts: Int = 0,
    val createdAt: Long,
    val updatedAt: Long
) {
    companion object {
        const val MESSAGE_LEVEL_OFFER_INDEX = -1
        const val KIND_OPENED = "OPENED"
        const val KIND_FEEDBACK = "FEEDBACK"
        const val STATE_PENDING = "PENDING"
        const val STATE_SENT = "SENT"
        const val MAX_ATTEMPTS = 5
        const val MAX_ROWS = 500
    }
}

@Dao
interface ParserSampleEventDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(event: ParserSampleEventEntity): Long

    @Query(
        """
        SELECT * FROM parser_sample_events
        WHERE sampleId = :sampleId AND offerIndex = :offerIndex AND kind = :kind
        LIMIT 1
        """
    )
    suspend fun findSlot(sampleId: String, offerIndex: Int, kind: String): ParserSampleEventEntity?

    @Query(
        """
        UPDATE parser_sample_events
        SET eventType = :eventType, state = :state, attempts = 0, updatedAt = :now
        WHERE id = :id
        """
    )
    suspend fun updateSlot(id: Long, eventType: String, state: String, now: Long)

    /**
     * OPENED bir kez kuyruğa girer; feedback'te aynı slot son değerle ezilir
     * ve yeniden PENDING olur (sunucu tarafı zaten latest-wins upsert yapar).
     */
    @Transaction
    suspend fun enqueue(
        sampleId: String,
        offerIndex: Int,
        kind: String,
        eventType: String,
        now: Long
    ): Boolean {
        val existing = findSlot(sampleId, offerIndex, kind)
        if (existing == null) {
            insertIgnore(
                ParserSampleEventEntity(
                    sampleId = sampleId,
                    offerIndex = offerIndex,
                    kind = kind,
                    eventType = eventType,
                    state = ParserSampleEventEntity.STATE_PENDING,
                    attempts = 0,
                    createdAt = now,
                    updatedAt = now
                )
            )
            pruneIfOversized()
            return true
        }
        if (kind == ParserSampleEventEntity.KIND_OPENED) return false
        if (existing.eventType == eventType &&
            existing.state == ParserSampleEventEntity.STATE_SENT
        ) {
            return false
        }
        updateSlot(existing.id, eventType, ParserSampleEventEntity.STATE_PENDING, now)
        return true
    }

    @Query(
        """
        SELECT * FROM parser_sample_events
        WHERE state = :state AND attempts < :maxAttempts
        ORDER BY updatedAt ASC
        LIMIT :limit
        """
    )
    suspend fun pending(
        limit: Int,
        maxAttempts: Int = ParserSampleEventEntity.MAX_ATTEMPTS,
        state: String = ParserSampleEventEntity.STATE_PENDING
    ): List<ParserSampleEventEntity>

    @Query("UPDATE parser_sample_events SET state = 'SENT', updatedAt = :now WHERE id = :id")
    suspend fun markSent(id: Long, now: Long)

    @Query(
        """
        UPDATE parser_sample_events
        SET attempts = attempts + 1, updatedAt = :now
        WHERE id = :id
        """
    )
    suspend fun recordFailedAttempt(id: Long, now: Long)

    @Query("DELETE FROM parser_sample_events WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Tercih kapatıldığında bekleyen hiçbir olay ağa çıkmamalıdır. */
    @Query("DELETE FROM parser_sample_events WHERE state = 'PENDING'")
    suspend fun deleteAllPending(): Int

    @Query("DELETE FROM parser_sample_events")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM parser_sample_events")
    suspend fun countAll(): Int

    @Query(
        """
        DELETE FROM parser_sample_events
        WHERE id IN (
            SELECT id FROM parser_sample_events
            ORDER BY (state = 'SENT') DESC, updatedAt ASC
            LIMIT :count
        )
        """
    )
    suspend fun deleteOldest(count: Int)

    @Transaction
    suspend fun pruneIfOversized() {
        val overflow = countAll() - ParserSampleEventEntity.MAX_ROWS
        if (overflow > 0) deleteOldest(overflow)
    }
}
