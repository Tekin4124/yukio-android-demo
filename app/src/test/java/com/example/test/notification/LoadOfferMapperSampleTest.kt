package com.example.test.notification

import com.example.test.data.remote.Ilan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LoadOfferMapperSampleTest {

    private fun ilan(from: String, to: String) = Ilan(
        gondericiIl = from,
        gondericiIlce = null,
        aliciIl = to,
        aliciIlce = null,
        aracTipi = null,
        dorseTipi = null,
        tonaj = null,
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
    )

    @Test
    fun `coklu ilanda tum ilanlar ayni sampleId ve kendi offerIndexini tasir`() {
        val ilanlar = listOf(
            ilan("İSTANBUL", "ADANA"),
            ilan("MUĞLA", "İSTANBUL"),
            ilan("SİVAS", "İSTANBUL")
        )
        val entities = ilanlar.mapIndexed { index, ilan ->
            LoadOfferMapper.fromIlanOrNull(
                ilan = ilan,
                rawMessage = "test mesaji",
                sourceGroup = "grup",
                fallbackSender = "Gizli",
                sampleId = "d8bdL40oLBrp7fV__MFfWg",
                offerIndex = index
            )
        }
        assertEquals(3, entities.filterNotNull().size)
        entities.filterNotNull().forEachIndexed { index, entity ->
            assertEquals("d8bdL40oLBrp7fV__MFfWg", entity.sampleId)
            assertEquals(index, entity.offerIndex)
        }
    }

    @Test
    fun `sampleId yoksa offerIndex de yazilmaz`() {
        val entity = LoadOfferMapper.fromIlanOrNull(
            ilan = ilan("ANKARA", "KONYA"),
            rawMessage = "test",
            sourceGroup = "grup",
            fallbackSender = "Gizli",
            sampleId = null,
            offerIndex = 2
        )
        assertNull(entity!!.sampleId)
        assertNull(entity.offerIndex)
    }

    @Test
    fun `bos sampleId null gibi davranir`() {
        val entity = LoadOfferMapper.fromIlanOrNull(
            ilan = ilan("ANKARA", "KONYA"),
            rawMessage = "test",
            sourceGroup = "grup",
            fallbackSender = "Gizli",
            sampleId = "  ",
            offerIndex = 0
        )
        assertNull(entity!!.sampleId)
        assertNull(entity.offerIndex)
    }
}
