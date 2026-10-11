package com.example.test.notification

import com.example.test.data.remote.Ilan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LoadOfferMapperPriceTest {
    @Test
    fun keepsExplicitCurrencyPrice() {
        assertEquals(4_500, mapPrice("4500", "Ankara -> Istanbul 4.500 TL"))
    }

    @Test
    fun keepsBinAndShortKdvPrices() {
        assertEquals(80_000, mapPrice("80000", "Fiyat: 80 Bin"))
        assertEquals(12_500, mapPrice("12500", "Ankara Istanbul 12,5 + KDV"))
    }

    @Test
    fun rejectsTonnageAndTrailerDimensionsAsPrice() {
        assertNull(mapPrice("35", "Ankara -> Istanbul 35 ton"))
        assertNull(mapPrice("1360", "Ankara -> Istanbul 13.60 acik dorse"))
    }

    @Test
    fun rejectsParserPriceThatDoesNotMatchSourceEvidence() {
        assertNull(mapPrice("4500", "Ankara -> Istanbul 3000 TL"))
    }

    @Test
    fun rejectsBareNumberWithoutPriceEvidence() {
        assertNull(mapPrice("5000", "Ankara -> Istanbul 5000"))
    }

    private fun mapPrice(parsedPrice: String?, rawMessage: String): Int? {
        return LoadOfferMapper.fromIlan(
            ilan = Ilan(
                gondericiIl = "ANKARA",
                gondericiIlce = null,
                aliciIl = "ISTANBUL",
                aliciIlce = null,
                aracTipi = "TIR",
                dorseTipi = null,
                tonaj = null,
                miktar = null,
                miktarBirimi = null,
                agirlik = null,
                yukCinsi = null,
                yukDetay = null,
                fiyat = parsedPrice,
                fiyatNotu = null,
                yuklemeTarihi = null,
                firma = null,
                yetkili = null,
                telefon = null
            ),
            rawMessage = rawMessage,
            sourceGroup = "WhatsApp",
            fallbackSender = "Gizli"
        ).priceTry
    }
}
