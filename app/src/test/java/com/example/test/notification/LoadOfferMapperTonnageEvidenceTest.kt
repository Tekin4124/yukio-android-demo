package com.example.test.notification

import com.example.test.data.remote.Ilan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LoadOfferMapperTonnageEvidenceTest {
    @Test
    fun rejectsParserTonnageWithoutSourceEvidence() {
        val load = mapTonnage("35 ton", "Ankara -> Istanbul 13.60 acik dorse")
        assertNull(load.tonnageTon)
        assertNull(load.tonnageText)
    }

    @Test
    fun keepsPerOfferTonnageThatExistsAmongMultipleSourceWeights() {
        val load = mapTonnage(
            "20 ton",
            "Ankara -> Istanbul 10 ton\nBursa -> Izmir 20 ton"
        )
        assertEquals(20, load.tonnageTon)
        assertEquals("20 ton", load.tonnageText)
    }

    @Test
    fun doesNotGuessWhenParserFieldIsBlankAndMessageHasMultipleWeights() {
        val load = mapTonnage(
            null,
            "Ankara -> Istanbul 10 ton\nBursa -> Izmir 20 ton"
        )
        assertNull(load.tonnageTon)
        assertNull(load.tonnageText)
    }

    @Test
    fun keepsSingleSourceKilogramEvidenceWhenParserFieldIsBlank() {
        val load = mapTonnage(null, "Gebze -> Tuzla 250 kg")
        assertEquals(1, load.tonnageTon)
        assertEquals("250 kg", load.tonnageText)
    }

    private fun mapTonnage(parsedTonnage: String?, rawMessage: String) =
        LoadOfferMapper.fromIlan(
            ilan = Ilan(
                gondericiIl = "ANKARA",
                gondericiIlce = null,
                aliciIl = "ISTANBUL",
                aliciIlce = null,
                aracTipi = "TIR",
                dorseTipi = null,
                tonaj = parsedTonnage,
                miktar = null,
                miktarBirimi = null,
                agirlik = null,
                yukCinsi = null,
                yukDetay = null,
                fiyat = null,
                fiyatNotu = null,
                yuklemeTarihi = null,
                firma = null,
                yetkili = null,
                telefon = null
            ),
            rawMessage = rawMessage,
            sourceGroup = "WhatsApp",
            fallbackSender = "Gizli"
        )
}
