package com.example.test.data.local

import android.content.Context
import androidx.room.withTransaction
import com.example.test.data.FirstRunTestOfferStore
import com.example.test.data.firstRunTestOfferOperationGate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Fresh-install examples. They deliberately have no real contact details. */
object FirstRunTestOfferData {
    suspend fun seedIfNeeded(context: Context) {
        withContext(Dispatchers.IO) {
            val appContext = context.applicationContext
            val store = FirstRunTestOfferStore(appContext)
            firstRunTestOfferOperationGate.withLock {
                store.ensureGenerationStartedAt()
                if (!store.shouldSeed()) return@withLock

                val database = AppDatabase.getInstance(appContext)
                val loadDao = database.loadOfferDao()
                val now = System.currentTimeMillis()
                if (loadDao.getCount() > 0) {
                    store.markShown()
                    return@withLock
                }

                // One Room transaction prevents a process interruption from
                // leaving one-to-four demo rows that would suppress later repair.
                database.withTransaction {
                    offers(now).forEach { loadDao.insertDistinct(it) }
                }
                store.markShown()
            }
        }
    }

    private fun offers(now: Long): List<LoadOfferEntity> = listOf(
        testOffer("KOCAELİ", "GEBZE", "ANKARA", "SİNCAN", 22, "TIR TENTE", 38_000, "Paletli ambalaj", 1, now),
        testOffer("İSTANBUL", "ESENYURT", "İZMİR", "KEMALPAŞA", 18, "FRİGO TIR", 52_000, "Gıda", 2, now),
        testOffer("BURSA", "NİLÜFER", "ANTALYA", "KEPEZ", 10, "AÇIK KAMYON", 29_500, "Makine parçası", 3, now),
        testOffer("DENİZLİ", null, "İSTANBUL", "AVCILAR", 6, "KAPALI KAMYONET", 16_000, "Tekstil kolileri", 4, now),
        testOffer("ADANA", "SEYHAN", "NİĞDE", "BOR", 24, "KAPALI TIR", 43_000, "Soğan çuvalı", 5, now)
    )

    private fun testOffer(
        fromCity: String,
        fromDistrict: String?,
        toCity: String,
        toDistrict: String?,
        tonnage: Int,
        vehicleType: String,
        price: Int,
        cargo: String,
        number: Int,
        now: Long
    ) = LoadOfferEntity(
        fromCity = fromCity,
        fromDistrict = fromDistrict,
        toCity = toCity,
        toDistrict = toDistrict,
        tonnageTon = tonnage,
        vehicleType = vehicleType,
        priceTry = price,
        phone = "",
        notes = "Test ilanı #$number · Gerçek iletişim bilgisi içermez. İlk gerçek ilan geldiğinde otomatik kaldırılır.",
        sourceGroup = FirstRunTestOfferStore.TEST_SOURCE_GROUP,
        sourceSender = FirstRunTestOfferStore.TEST_SOURCE_SENDER,
        rawMessage = "TEST İLANI #$number | $fromCity${fromDistrict?.let { "/$it" } ?: ""} → $toCity${toDistrict?.let { "/$it" } ?: ""} | $tonnage ton $vehicleType | $cargo | ${price} TL",
        createdAt = now - number * 60_000L
    )
}
