package com.example.test.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UserProfileDao {

    @Query("SELECT * FROM user_profile WHERE id = 1")
    fun getProfile(): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profile WHERE id = 1")
    suspend fun getProfileOnce(): UserProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(profile: UserProfileEntity)

    @Query("UPDATE user_profile SET isVehicleEmpty = :isEmpty, updatedAt = :updatedAt WHERE id = 1")
    suspend fun updateVehicleStatus(isEmpty: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE user_profile SET gpsCity = :city, gpsCityAt = :detectedAt, updatedAt = :updatedAt WHERE id = 1")
    suspend fun updateGpsCity(city: String?, detectedAt: Long?, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE user_profile SET notifyOnVehicleMatch = :enabled, updatedAt = :updatedAt WHERE id = 1")
    suspend fun updateNotifyOnVehicleMatch(enabled: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE user_profile SET gpsAutoEnabled = :enabled, updatedAt = :updatedAt WHERE id = 1")
    suspend fun updateGpsAutoEnabled(enabled: Boolean, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE user_profile SET currentCities = :citiesJson, updatedAt = :updatedAt WHERE id = 1")
    suspend fun updateCurrentCitiesJson(citiesJson: String, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM user_profile WHERE id = 1")
    suspend fun deleteProfile()

    @Query("DELETE FROM user_profile")
    suspend fun deleteAll()

    @Query("SELECT EXISTS(SELECT 1 FROM user_profile WHERE id = 1)")
    suspend fun hasProfile(): Boolean
}
