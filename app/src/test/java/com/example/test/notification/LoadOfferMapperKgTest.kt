package com.example.test.notification

import com.example.test.ai.MatchScoreEngine
import com.example.test.data.local.RouteItem
import com.example.test.data.local.UserProfileEntity
import com.example.test.data.local.displayTonnage
import com.example.test.data.local.effectiveWeightKg
import com.example.test.data.remote.Ilan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LoadOfferMapperKgTest {
    @Test
    fun mapsKilogramTonnageWithoutTreatingPalletCountAsTonnage() {
        val load = LoadOfferMapper.fromIlan(
            ilan = Ilan(
                gondericiIl = "KOCAELI",
                gondericiIlce = "GEBZE",
                aliciIl = "ISTANBUL",
                aliciIlce = "TUZLA",
                aracTipi = null,
                dorseTipi = null,
                tonaj = "250 kg",
                miktar = null,
                miktarBirimi = null,
                agirlik = null,
                yukCinsi = "Palet",
                yukDetay = "2 palet 250 kg",
                fiyat = null,
                fiyatNotu = null,
                yuklemeTarihi = null,
                firma = null,
                yetkili = null,
                telefon = "0532 111 22 33"
            ),
            rawMessage = "Gebze Tuzla 2 palet 250 kg 0532 111 22 33",
            sourceGroup = "test",
            fallbackSender = "sender"
        )

        assertEquals("250 kg", load.displayTonnage())
        assertEquals(250, load.effectiveWeightKg())
        assertEquals(1, load.tonnageTon)
    }

    @Test
    fun lightKilogramLoadScoresWellForKamyonetAndKamyon() {
        val load = LoadOfferMapper.fromIlan(
            ilan = Ilan(
                gondericiIl = "KOCAELI",
                gondericiIlce = "GEBZE",
                aliciIl = "ISTANBUL",
                aliciIlce = "TUZLA",
                aracTipi = null,
                dorseTipi = null,
                tonaj = "250 kg",
                miktar = null,
                miktarBirimi = null,
                agirlik = null,
                yukCinsi = "Palet",
                yukDetay = "1 palet 250 kg",
                fiyat = null,
                fiyatNotu = null,
                yuklemeTarihi = null,
                firma = null,
                yetkili = null,
                telefon = "0532 111 22 33"
            ),
            rawMessage = "Gebze Tuzla 1 palet 250 kg",
            sourceGroup = "test",
            fallbackSender = "sender"
        )

        val kamyonet = profile("Kamyonet", maxTonnage = 1)
        val kamyon = profile("Kamyon", maxTonnage = 10)

        val engine = MatchScoreEngine()
        assertTrue((engine.calculateMatchScore(load, kamyonet).factors["vehicle"]?.rawScore ?: 0) >= 90)
        assertTrue((engine.calculateMatchScore(load, kamyon).factors["vehicle"]?.rawScore ?: 0) >= 90)
    }

    private fun profile(vehicleType: String, maxTonnage: Int): UserProfileEntity {
        return UserProfileEntity(
            vehicleType = vehicleType,
            trailerType = null,
            maxTonnage = maxTonnage,
            height = null,
            width = null,
            length = null,
            fuelConsumption = null,
            costPerKm = null,
            priceMin = null,
            priceMax = null,
            preferredLoadTypes = emptyList(),
            routes = listOf(RouteItem("KOCAELI", "ISTANBUL")),
            isVehicleEmpty = true
        )
    }
}
