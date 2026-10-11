package com.example.test.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface FailedMessageDao {

    @Query("SELECT * FROM failed_messages ORDER BY lastRetryTime ASC LIMIT :limit")
    suspend fun getAll(limit: Int): List<FailedMessage>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: FailedMessage)

    @Delete
    suspend fun delete(message: FailedMessage)

    @Query("DELETE FROM failed_messages WHERE contentHash = :contentHash")
    suspend fun deleteByContentHash(contentHash: String)

    @Query("DELETE FROM failed_messages")
    suspend fun deleteAll()
}
