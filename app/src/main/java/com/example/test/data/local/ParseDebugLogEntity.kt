package com.example.test.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "parse_debug_logs",
    indices = [
        Index(value = ["createdAt"]),
        Index(value = ["contentHash"]),
        Index(value = ["reviewStatus"])
    ]
)
data class ParseDebugLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val contentHash: String,
    val sourceGroup: String,
    val rawMessage: String,
    val sanitizedMessage: String,
    val apiResponseJson: String?,
    val status: String,
    val savedOfferCount: Int,
    val skippedOfferCount: Int,
    val error: String?,
    val receivedAt: Long,
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "'UNREVIEWED'")
    val reviewStatus: String = REVIEW_UNREVIEWED,
    val reviewNote: String? = null,
    val reviewedAt: Long? = null
) {
    companion object {
        const val REVIEW_UNREVIEWED = "UNREVIEWED"
        const val REVIEW_CORRECT = "CORRECT"
        const val REVIEW_INCORRECT = "INCORRECT"
        const val REVIEW_UNCERTAIN = "UNCERTAIN"
    }
}
