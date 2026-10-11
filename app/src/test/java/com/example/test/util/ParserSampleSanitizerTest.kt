package com.example.test.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParserSampleSanitizerTest {

    private val syntheticRaw = listOf(
        "MARMARA NAKLİYAT",
        "Erdem : 0532 123 45 67",
        "Saat 14:32",
        "",
        "TUZLA - ADANA 1 PALET",
        "100x120     350 KG",
        "3500₺",
        "",
        "FETHİYE - MALTEPE 1 SCOOTER",
        "3000₺",
        "",
        "BALIKESİR/KARESİ - MALTEPE 1 SCOOTER",
        "2500₺",
        "",
        "SİVAS - MALTEPE 1 SCOOTER",
        "4000₺",
        "",
        "WhatsApp aramaları kapalıdır, mesaj yazınız"
    ).joinToString("\n")

    private val syntheticExpected = listOf(
        "TUZLA - ADANA 1 PALET",
        "100x120     350 KG",
        "3500₺",
        "",
        "FETHİYE - MALTEPE 1 SCOOTER",
        "3000₺",
        "",
        "BALIKESİR/KARESİ - MALTEPE 1 SCOOTER",
        "2500₺",
        "",
        "SİVAS - MALTEPE 1 SCOOTER",
        "4000₺"
    ).joinToString("\n")

    @Test
    fun `sentetik dort ilanli mesajda yuk bloklari ve satir duzeni birebir korunur`() {
        val result = ParserSampleSanitizer.sanitize(syntheticRaw)
        assertTrue(result.ok)
        assertEquals(syntheticExpected, result.text)
    }

    @Test
    fun `firma isim telefon saat ve footer satirlari cikartilir`() {
        val result = ParserSampleSanitizer.sanitize(syntheticRaw)
        val text = result.text.orEmpty()
        for (forbidden in listOf("MARMARA", "NAKLİYAT", "Erdem", "0532", "Saat", "WhatsApp")) {
            assertFalse("çıktı '$forbidden' içermemeli", text.contains(forbidden))
        }
    }

    @Test
    fun `telefon placeholder plaka eposta url iban ve iletisim satirlari dusurulur`() {
        val cases = listOf(
            "ANKARA - KONYA 20 TON\nErdem 0532 123 45 67",
            "ANKARA - KONYA 20 TON\n[TELEFON_1]",
            "ANKARA - KONYA 20 TON\nİrtibat: [ISIM_1]",
            "ANKARA - KONYA 20 TON\n34 ABC 123 plakalı araç",
            "ANKARA - KONYA 20 TON\ninfo@ornek.com",
            "ANKARA - KONYA 20 TON\nhttps://wa.me/905321234567",
            "ANKARA - KONYA 20 TON\nTR33 0006 1005 1978 6457 8413 26",
            "ANKARA - KONYA 20 TON\nİletişim: ofis",
            "ANKARA - KONYA 20 TON\nWhatsApp aramaları kapalıdır",
            "ÖZKAN LOJİSTİK LTD ŞTİ\nANKARA - KONYA 20 TON",
            "Günaydın hayırlı işler\nANKARA - KONYA 20 TON",
            "Erdem\nANKARA - KONYA 20 TON",
            "Ahmet Yılmaz\nANKARA - KONYA 20 TON",
            "MARMARA TUR\nANKARA - KONYA 20 TON",
            "ACME\nANKARA - KONYA 20 TON",
            "34 abc 123\nANKARA - KONYA 20 TON",
            "34-ABC-123\nANKARA - KONYA 20 TON"
        )
        for (case in cases) {
            val result = ParserSampleSanitizer.sanitize(case)
            assertTrue("kabul edilmeli: $case", result.ok)
            assertEquals("yalnız yük satırı kalmalı: $case", "ANKARA - KONYA 20 TON", result.text)
        }
    }

    @Test
    fun `dorse olcusu 13-60 ve tonaj satirlari saat veya plaka sanilmaz`() {
        val result = ParserSampleSanitizer.sanitize("İZMİR - MERSİN\n13.60\n34 TON 500 KG PARÇA")
        assertTrue(result.ok)
        assertEquals("İZMİR - MERSİN\n13.60\n34 TON 500 KG PARÇA", result.text)
    }

    @Test
    fun `acik adres il ilce seviyesine indirilir`() {
        val result = ParserSampleSanitizer.sanitize(
            "GEBZE Cumhuriyet Mah. İnönü Cad. No: 12 - ANKARA 5 TON"
        )
        assertTrue(result.ok)
        val text = result.text.orEmpty()
        assertFalse(text.contains("Mah."))
        assertFalse(text.contains("No:"))
        assertTrue(text.contains("GEBZE"))
        assertTrue(text.contains("ANKARA 5 TON"))
    }

    @Test
    fun `bilinen iki ilce arasindaki rota satiri korunur serbest isimler korunmaz`() {
        val accepted = ParserSampleSanitizer.sanitize("Bakırköy-Maslak\n2 palet")
        assertTrue(accepted.ok)
        assertEquals("Bakırköy-Maslak\n2 palet", accepted.text)

        val rejectedNames = ParserSampleSanitizer.sanitize("Ahmet-Mehmet\n2 palet")
        assertTrue(rejectedNames.ok)
        assertEquals("2 palet", rejectedNames.text)
    }

    @Test
    fun `temiz icerik kalmazsa fail-closed olur`() {
        assertFalse(ParserSampleSanitizer.sanitize("Erdem 0532 123 45 67").ok)
        assertFalse(ParserSampleSanitizer.sanitize("").ok)
        assertFalse(ParserSampleSanitizer.sanitize("   \n  ").ok)
        assertFalse(ParserSampleSanitizer.sanitize(null).ok)
        assertFalse(ParserSampleSanitizer.sanitize("A".repeat(9000)).ok)
    }

    @Test
    fun `suphe kontrolu temiz metinde null kirli metinde sebep doner`() {
        assertNull(ParserSampleSanitizer.findSuspicion("ANKARA - KONYA 20 TON"))
        assertEquals("phone", ParserSampleSanitizer.findSuspicion("ANKARA 0532 123 45 67"))
        assertEquals("placeholder", ParserSampleSanitizer.findSuspicion("ANKARA [TELEFON_1]"))
        assertEquals("company", ParserSampleSanitizer.findSuspicion("MARMARA NAKLİYAT\nANKARA - KONYA"))
        assertEquals("non_logistics_line", ParserSampleSanitizer.findSuspicion("Erdem"))
        assertEquals("empty", ParserSampleSanitizer.findSuspicion("  "))
    }

    @Test
    fun `surum sabittir ve backend ile ayni tutulmalidir`() {
        assertEquals(3, ParserSampleSanitizer.SANITIZER_VERSION)
    }
}
