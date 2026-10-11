package com.example.test.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "user_interactions",
    indices = [
        Index("loadId"),
        Index("timestamp")
    ]
)
data class UserInteractionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val loadId: Long,
    val fromCity: String,
    val toCity: String,
    val vehicleType: String?,
    val tonnage: Int?,
    val price: Int?,
    val action: String,
    val timestamp: Long = System.currentTimeMillis(),
    val sessionDuration: Long? = null
)
