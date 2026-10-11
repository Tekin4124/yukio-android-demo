package com.example.test.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface LoadOfferDao {

    @Query("SELECT * FROM load_offers ORDER BY createdAt DESC")
    fun getAllFlow(): Flow<List<LoadOfferEntity>>

    @Query("SELECT * FROM load_offers ORDER BY createdAt DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<LoadOfferEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(loadOffer: LoadOfferEntity): Long

    @Transaction
    suspend fun insertDistinct(loadOffer: LoadOfferEntity): Long {
        val duplicateId = findDuplicateId(
            rawMessage = loadOffer.rawMessage,
            fromCity = loadOffer.fromCity,
            fromDistrict = loadOffer.fromDistrict,
            toCity = loadOffer.toCity,
            toDistrict = loadOffer.toDistrict,
            routeStops = loadOffer.routeStops,
            vehicleType = loadOffer.vehicleType,
            tonnageText = loadOffer.tonnageText,
            phone = loadOffer.phone,
            notes = loadOffer.notes
        )
        // Duplicate bulunduysa -1L don ki cagiranlar "atlandi" olarak saysin.
        // (insert() OnConflictStrategy.IGNORE ile burada unique index olmadigindan
        //  asla -1 donmez; dedup sinyali bu kontrolle veriliyor.)
        if (duplicateId != null) return -1L
        return insert(loadOffer)
    }

    @Query("""
SELECT id FROM load_offers
WHERE rawMessage = :rawMessage
AND fromCity = :fromCity
AND IFNULL(fromDistrict, '') = IFNULL(:fromDistrict, '')
AND toCity = :toCity
AND IFNULL(toDistrict, '') = IFNULL(:toDistrict, '')
AND IFNULL(routeStops, '') = IFNULL(:routeStops, '')
AND IFNULL(vehicleType, '') = IFNULL(:vehicleType, '')
AND IFNULL(tonnageText, '') = IFNULL(:tonnageText, '')
AND phone = :phone
AND IFNULL(notes, '') = IFNULL(:notes, '')
LIMIT 1
""")
    suspend fun findDuplicateId(
        rawMessage: String,
        fromCity: String,
        fromDistrict: String?,
        toCity: String,
        toDistrict: String?,
        routeStops: List<com.example.test.data.model.RouteStop>?,
        vehicleType: String?,
        tonnageText: String?,
        phone: String,
        notes: String?
    ): Long?

    @Query("UPDATE load_offers SET phone = :phone WHERE rawMessage = :rawMessage AND phone = ''")
    suspend fun updateMissingPhoneForRawMessage(rawMessage: String, phone: String): Int

    @Delete
    suspend fun delete(loadOffer:  LoadOfferEntity)

    @Query("DELETE FROM load_offers WHERE createdAt < :before")
    suspend fun deleteOlderThan(before: Long): Int

    @Query("DELETE FROM load_offers")
    suspend fun deleteAll()

    @Query(
        """
        DELETE FROM load_offers
        WHERE sourceGroup = :sourceGroup
        AND sourceSender IN (:sourceSender, :legacySourceSender)
        AND phone = ''
        AND substr(rawMessage, 1, length(:rawPrefix)) = :rawPrefix
        AND substr(IFNULL(notes, ''), 1, length(:notesPrefix)) = :notesPrefix
        """
    )
    suspend fun deleteFirstRunTestOffers(
        sourceGroup: String,
        sourceSender: String,
        legacySourceSender: String,
        rawPrefix: String,
        notesPrefix: String
    ): Int

    @Query("SELECT COUNT(*) FROM load_offers")
    suspend fun getCount(): Int

    @Query("SELECT * FROM load_offers WHERE (:vehicleType IS NULL OR UPPER(vehicleType) LIKE '%' || :vehicleType || '%') ORDER BY createdAt DESC")
    fun getByVehicleType(vehicleType: String?): Flow<List<LoadOfferEntity>>

    @Query("SELECT * FROM load_offers WHERE (:city IS NULL OR UPPER(fromCity) LIKE '%' || :city || '%' OR UPPER(toCity) LIKE '%' || :city || '%' OR UPPER(routeStops) LIKE '%' || :city || '%') ORDER BY createdAt DESC")
    fun getByCity(city:  String?): Flow<List<LoadOfferEntity>>

    @Query("""
SELECT * FROM load_offers
WHERE (:vehicleType IS NULL OR UPPER(vehicleType) LIKE '%' || :vehicleType || '%')
AND (
    :city IS NULL
    OR UPPER(fromCity) LIKE '%' || :city || '%'
    OR UPPER(toCity) LIKE '%' || :city || '%'
    OR UPPER(IFNULL(fromDistrict, '')) LIKE '%' || :city || '%'
    OR UPPER(IFNULL(toDistrict, '')) LIKE '%' || :city || '%'
    OR UPPER(routeStops) LIKE '%' || :city || '%'
)
AND (
    :minTonnage IS NULL
    OR tonnageTon IS NULL
    OR tonnageTon >= :minTonnage
)
AND (
    :maxTonnage IS NULL
    OR tonnageTon IS NULL
    OR tonnageTon <= :maxTonnage
)
AND createdAt >= :createdAfterMillis
ORDER BY createdAt DESC
""")

    fun getFiltered(
        vehicleType: String?,
        city: String?,
        minTonnage: Int?,
        maxTonnage: Int?,
        createdAfterMillis: Long
    ): Flow<List<LoadOfferEntity>>
}
