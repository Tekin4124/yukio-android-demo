package com.example.test.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

object WhatsappSourceMode {
    const val AUTO = "AUTO"
    const val ALLOW = "ALLOW"
    const val BLOCK = "BLOCK"
}

object WhatsappSourceDecision {
    const val UNKNOWN = "UNKNOWN"
    const val ALLOWED = "ALLOWED"
    const val BLOCKED = "BLOCKED"
}

@Entity(
    tableName = "whatsapp_sources",
    indices = [
        Index(value = ["normalizedName"], unique = true),
        Index(value = ["lastSeenAt"])
    ]
)
data class WhatsappSourceEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val groupName: String,
    val normalizedName: String,
    val mode: String = WhatsappSourceMode.AUTO,
    val lastSeenAt: Long = System.currentTimeMillis(),
    val seenCount: Int = 0,
    val lastDecision: String = WhatsappSourceDecision.UNKNOWN
)
