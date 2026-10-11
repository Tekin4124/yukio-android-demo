package com.example.test.ui.screens

import com.example.test.data.local.LoadOfferEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class RawMessageHighlightTest {

    @Test
    fun `blank lines disappear and selected route continuation is fully highlighted`() {
        val lines = highlightedRawMessageLines(
            load(
                rawMessage = """
                    AYDEM LOJİSTİK

                    İSTANBUL → ANKARA
                    TIR   TENTE
                    24 ton

                    BURSA → İZMİR
                    KAMYON
                """.trimIndent()
            )
        )

        assertEquals(
            listOf(
                RawMessageDisplayLine("AYDEM LOJİSTİK", false),
                RawMessageDisplayLine("İSTANBUL → ANKARA", true),
                RawMessageDisplayLine("TIR   TENTE", true),
                RawMessageDisplayLine("24 ton", true),
                RawMessageDisplayLine("BURSA → İZMİR", false),
                RawMessageDisplayLine("KAMYON", false)
            ),
            lines
        )
    }

    @Test
    fun `single line source is highlighted as a whole`() {
        val lines = highlightedRawMessageLines(
            load(rawMessage = "İSTANBUL   ANKARA TIR TENTE 24 ton")
        )

        assertEquals(
            listOf(RawMessageDisplayLine("İSTANBUL   ANKARA TIR TENTE 24 ton", true)),
            lines
        )
    }

    @Test
    fun `masked phone and orphan contact label are removed`() {
        val lines = highlightedRawMessageLines(
            load(
                rawMessage = """
                    İSTANBUL → ANKARA
                    TIR   TENTE
                    İletişim:
                    [TELEFON_1]
                    📞 [TELEFON_2]
                """.trimIndent()
            )
        )

        assertEquals(
            listOf(
                RawMessageDisplayLine("İSTANBUL → ANKARA", true),
                RawMessageDisplayLine("TIR   TENTE", true)
            ),
            lines
        )
    }

    @Test
    fun `single paragraph multi load message highlights only the selected block`() {
        // Bildirilen hata: boş satır olmayan çok ilanlı mesajda her şey maviye
        // boyanıyordu. Diğer ilanın rota ve araç satırları boyanmamalı.
        val message = """
            AYDEM LOJİSTİK
            İSTANBUL → ANKARA
            TIR   TENTE
            24 ton
            BURSA → İZMİR
            KAMYON
            8000 TL
        """.trimIndent()
        val sibling = load(
            rawMessage = message,
            fromCity = "Bursa",
            toCity = "İzmir",
            vehicleType = "KAMYON",
            priceTry = 8000,
            id = 2L
        )

        val lines = highlightedRawMessageLines(
            load(rawMessage = message),
            siblingLoads = listOf(sibling)
        )

        assertEquals(
            listOf(
                RawMessageDisplayLine("AYDEM LOJİSTİK", false),
                RawMessageDisplayLine("İSTANBUL → ANKARA", true),
                RawMessageDisplayLine("TIR   TENTE", true),
                RawMessageDisplayLine("24 ton", true),
                RawMessageDisplayLine("BURSA → İZMİR", false),
                RawMessageDisplayLine("KAMYON", false),
                RawMessageDisplayLine("8000 TL", false)
            ),
            lines
        )
    }

    @Test
    fun `sibling route line stops extension even without siblings passed`() {
        // Kardeş bilgisi yoksa da "X → Y" biçimindeki yabancı rota satırında durulur.
        val lines = highlightedRawMessageLines(
            load(
                rawMessage = """
                    İSTANBUL → ANKARA
                    TIR   TENTE
                    BURSA → İZMİR
                    KAMYON
                """.trimIndent()
            )
        )

        assertEquals(
            listOf(
                RawMessageDisplayLine("İSTANBUL → ANKARA", true),
                RawMessageDisplayLine("TIR   TENTE", true),
                RawMessageDisplayLine("BURSA → İZMİR", false),
                RawMessageDisplayLine("KAMYON", false)
            ),
            lines
        )
    }

    @Test
    fun `teker lines and separate price lines follow their own route`() {
        val message = """
            ANKARA - SAMSUN
            8 TEKER 15 TON
            GEBZE - KONYA
            10 TEKER 12 TON 14000 TL
        """.trimIndent()
        val ankara = load(
            rawMessage = message,
            fromCity = "Ankara",
            toCity = "Samsun",
            vehicleType = "8 TEKER",
            tonnageTon = 15
        )
        val gebze = load(
            rawMessage = message,
            fromCity = "Gebze",
            toCity = "Konya",
            vehicleType = "10 TEKER",
            tonnageTon = 12,
            priceTry = 14000,
            id = 2L
        )

        val ankaraLines = highlightedRawMessageLines(ankara, siblingLoads = listOf(gebze))
        assertEquals(
            listOf(true, true, false, false),
            ankaraLines.map { it.highlighted }
        )

        val gebzeLines = highlightedRawMessageLines(gebze, siblingLoads = listOf(ankara))
        assertEquals(
            listOf(false, false, true, true),
            gebzeLines.map { it.highlighted }
        )
    }

    @Test
    fun `shared vehicle line may highlight for both loads sharing it`() {
        // İki ilan aynı araç satırını paylaşıyorsa satır her ikisi için de
        // kaynak sayılır; yeter ki başka ilanın rotası boyanmasın.
        val message = """
            TIR TENTE ARAÇLARIMIZ MEVCUT
            İSTANBUL → ANKARA 24 ton
            İSTANBUL → KONYA 20 ton
        """.trimIndent()
        val ankara = load(rawMessage = message)
        val konya = load(
            rawMessage = message,
            toCity = "Konya",
            tonnageTon = 20,
            id = 2L
        )

        val ankaraLines = highlightedRawMessageLines(ankara, siblingLoads = listOf(konya))
        assertEquals(true, ankaraLines[1].highlighted)
        assertEquals(false, ankaraLines[2].highlighted)

        val konyaLines = highlightedRawMessageLines(konya, siblingLoads = listOf(ankara))
        assertEquals(false, konyaLines[1].highlighted)
        assertEquals(true, konyaLines[2].highlighted)
    }

    @Test
    fun `no confident source produces no fake highlight`() {
        // Mesajda ilanın rotasına dair iz yoksa hiçbir satır boyanmaz;
        // UI bu durumda "kaynak işareti bulunamadı" notu gösterir.
        val lines = highlightedRawMessageLines(
            load(
                rawMessage = """
                    GENEL DUYURU
                    YARIN MESAİ SAAT 8DE BAŞLAR
                    HERKESE HAYIRLI İŞLER
                """.trimIndent()
            )
        )

        assertEquals(listOf(false, false, false), lines.map { it.highlighted })
    }

    private fun load(
        rawMessage: String,
        fromCity: String = "İstanbul",
        toCity: String = "Ankara",
        vehicleType: String? = "TIR TENTE",
        tonnageTon: Int? = 24,
        priceTry: Int? = null,
        id: Long = 1L
    ) = LoadOfferEntity(
        id = id,
        fromCity = fromCity,
        fromDistrict = null,
        toCity = toCity,
        toDistrict = null,
        tonnageTon = tonnageTon,
        vehicleType = vehicleType,
        priceTry = priceTry,
        phone = "",
        notes = null,
        sourceGroup = "test",
        sourceSender = "test",
        rawMessage = rawMessage,
        createdAt = 1L
    )
}
