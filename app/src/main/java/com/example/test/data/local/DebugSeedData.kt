package com.example.test.data.local

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object DebugSeedData {
    suspend fun seedIfNeeded(context: Context) {
        withContext(Dispatchers.IO) {
            val database = AppDatabase.getInstance(context)
            val loadDao = database.loadOfferDao()
            val profileDao = database.userProfileDao()

            if (loadDao.getCount() > 0) return@withContext

            if (profileDao.getProfileOnce() == null) {
                profileDao.insertOrUpdate(sampleProfile())
            }

            sampleLoads().forEach { load ->
                loadDao.insertDistinct(load)
            }
        }
    }

    private fun sampleProfile(): UserProfileEntity {
        return UserProfileEntity(
            vehicleType = "TIR",
            trailerType = "TENTE",
            maxTonnage = 24,
            height = 2.7f,
            width = 2.45f,
            length = 13.6f,
            fuelConsumption = 32f,
            costPerKm = 19f,
            priceMin = 15000,
            priceMax = 90000,
            preferredLoadTypes = listOf("Palet", "Koli", "Gida"),
            routes = listOf(
                RouteItem("KOCAELI", "ANKARA"),
                RouteItem("ISTANBUL", "IZMIR"),
                RouteItem("BURSA", "ANTALYA")
            ),
            isVehicleEmpty = true
        )
    }

    private fun sampleLoads(): List<LoadOfferEntity> {
        val routes = listOf(
            SampleLoad("KOCAELI", "GEBZE", "ANKARA", "SINCAN", 22, "TIR TENTE", 38000, "paletli ambalaj"),
            SampleLoad("ISTANBUL", "ESENYURT", "IZMIR", "KEMALPASA", 18, "FRIGO TIR", 52000, "gida"),
            SampleLoad("BURSA", "NILUFER", "ANTALYA", "KEPEZ", 10, "KAMYON ACIK", 29500, "makine parcasi"),
            SampleLoad("DENIZLI", null, "ISTANBUL", "AVCILAR", 6, "KAMYONET KAPALI", 16000, "tekstil kolileri"),
            SampleLoad("ADANA", "SEYHAN", "NIGDE", "BOR", 24, "KAPALI TIR", 43000, "sogan cuvali"),
            SampleLoad("KONYA", "SELJUKLU", "ISTANBUL", "HADIMKOY", 4, "KAMYON KAPALI", 14500, "paletli malzeme"),
            SampleLoad("SAKARYA", "HENDEK", "ANKARA", "MAMAK", 12, "ACIK TIR", 25000, "su paleti"),
            SampleLoad("BOLU", null, "KOCAELI", "GEBZE", 20, "TIR KAPALI", 32000, "koli yuk"),
            SampleLoad("USAK", null, "TRABZON", null, 22, "KAPALI TIR", 58000, "seramik"),
            SampleLoad("ELAZIG", "OSB", "GAZIANTEP", null, 16, "TENTELI TIR", 36000, "karma yuk"),
            SampleLoad("MANISA", "AKHISAR", "SAMSUN", null, 21, "TIR TENTE", 50000, "prefabrik malzeme"),
            SampleLoad("DUDULLU", null, "CORLU", null, 24, "ACIK TIR", 27000, "sanayi malzemesi"),
            SampleLoad("GEBZE", null, "MANISA", "ALASEHIR", 13, "ACIK TIR", 34000, "paletli urun"),
            SampleLoad("ANKARA", "GOLBASI", "EDIRNE", null, 24, "TIR", 47000, "insaat malzemesi"),
            SampleLoad("SILIVRI", null, "KARTAL", null, 3, "TENTELI TIR", 9000, "tonajsiz palet"),
            SampleLoad("HADIMKOY", null, "ADANA", null, 18, "TENTELI TIR", 44000, "paketli urun"),
            SampleLoad("TUZLA", null, "KAYSERI", null, 8, "KAPALI KAMYON", 21000, "elektronik koli"),
            SampleLoad("BOYABAT", null, "SILIVRI", null, 24, "ACIK TIR", 40000, "paletli tugla"),
            SampleLoad("YALOVA", null, "NEVSEHIR", null, 20, "FRIGO TIR", 45500, "isi kontrollu yuk"),
            SampleLoad("CAYCUMA", null, "DILOVASI", null, 22, "TENTE TIR", 30000, "rulo malzeme")
        )
        val todayMorning = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 9)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        val baseTime = maxOf(System.currentTimeMillis(), todayMorning)

        return List(5) { index ->
            val route = routes[index % routes.size]
            val round = index / routes.size
            val sequence = index + 1
            val phone = ""
            val price = route.priceTry + round * 1250 + (index % 4) * 500
            val tonnage = (route.tonnageTon + round).coerceAtMost(26)
            val fromText = route.fromDistrict?.let { "${route.fromCity}/$it" } ?: route.fromCity
            val toText = route.toDistrict?.let { "${route.toCity}/$it" } ?: route.toCity

            LoadOfferEntity(
                fromCity = route.fromCity,
                fromDistrict = route.fromDistrict,
                toCity = route.toCity,
                toDistrict = route.toDistrict,
                tonnageTon = tonnage,
                vehicleType = route.vehicleType,
                priceTry = price,
                phone = phone,
                notes = "Ornek ilan #$sequence: ${route.loadType}. Bugun yukleme, rampada iletisim.",
                sourceGroup = "Ornek Yukler",
                sourceSender = "Yuk Bulma",
                rawMessage = "$fromText -> $toText $tonnage ton ${route.vehicleType} ${route.loadType} $price TL tel $phone #$sequence",
                createdAt = baseTime - index * 60_000L
            )
        }
    }

    private data class SampleLoad(
        val fromCity: String,
        val fromDistrict: String?,
        val toCity: String,
        val toDistrict: String?,
        val tonnageTon: Int,
        val vehicleType: String,
        val priceTry: Int,
        val loadType: String
    )
}
