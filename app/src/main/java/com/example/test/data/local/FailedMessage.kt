package com.example.test.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "failed_messages",
    indices = [
        Index(value = ["contentHash"], unique = true)
    ]
)
data class FailedMessage(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val groupName: String,
    val sender: String,
    val messageText: String,
    val timestamp: Long,
    val contentHash: String,
    val devicePhone: String?,
    val devicePhones: String?,
    val deviceNames: String?,
    val retryCount: Int,
    val lastRetryTime: Long
)
