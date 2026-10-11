package com.example.test.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UserInteractionDao {

    @Query("SELECT * FROM user_interactions ORDER BY timestamp DESC")
    fun getAllFlow(): Flow<List<UserInteractionEntity>>

    @Query("SELECT * FROM user_interactions ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentFlow(limit: Int): Flow<List<UserInteractionEntity>>

    @Query("SELECT * FROM user_interactions ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<UserInteractionEntity>

    @Query("SELECT * FROM user_interactions WHERE action = :action ORDER BY timestamp DESC")
    suspend fun getByAction(action: String): List<UserInteractionEntity>

    @Query("SELECT * FROM user_interactions WHERE action IN ('CALLED', 'ACCEPTED') ORDER BY timestamp DESC LIMIT 50")
    suspend fun getPositiveInteractions(): List<UserInteractionEntity>

    @Insert
    suspend fun insert(interaction: UserInteractionEntity)

    @Query("DELETE FROM user_interactions WHERE timestamp < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)

    @Query("DELETE FROM user_interactions")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM user_interactions")
    suspend fun getCount(): Int
}
