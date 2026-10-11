package com.example.test.data.repository

import com.example.test.data.local.UserProfileDao
import com.example.test.data.local.UserProfileEntity
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow

class UserProfileRepository(private val dao: UserProfileDao) {

    private val gson = Gson()

    val profile: Flow<UserProfileEntity?> = dao.getProfile()

    suspend fun getProfileOnce(): UserProfileEntity? = dao.getProfileOnce()

    suspend fun saveProfile(profile: UserProfileEntity) {
        dao.insertOrUpdate(profile.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun updateVehicleStatus(isEmpty: Boolean) {
        dao.updateVehicleStatus(isEmpty)
    }

    /**
     * "Şu an buradayım" şehir listesini anında DB'ye yazar. Form değişikliklerini
     * "Kaydet" beklemeden persist eder — chip ekle/sil işlemleri atomik olsun diye.
     * Profil henüz yoksa sessizce no-op (saveProfile zamanı geldiğinde içerilecek).
     */
    suspend fun updateCurrentCities(cities: List<String>) {
        if (!dao.hasProfile()) return
        dao.updateCurrentCitiesJson(gson.toJson(cities))
    }

    suspend fun updateGpsAutoEnabled(enabled: Boolean) {
        if (!dao.hasProfile()) return
        dao.updateGpsAutoEnabled(enabled)
        // Pasifleştirme: son tespit edilen şehir DB'den de temizlensin
        if (!enabled) {
            dao.updateGpsCity(city = null, detectedAt = null)
        }
    }

    suspend fun updateNotifyOnVehicleMatch(enabled: Boolean) {
        if (!dao.hasProfile()) return
        dao.updateNotifyOnVehicleMatch(enabled)
    }

    suspend fun deleteProfile() {
        dao.deleteProfile()
    }

    suspend fun hasProfile(): Boolean = dao.hasProfile()
}
