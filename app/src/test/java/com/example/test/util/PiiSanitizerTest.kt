package com.example.test.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PiiSanitizerTest {

    @Test
    fun `mobil numara cesitli formatlarda yakalanir ve normalize edilir`() {
        val cases = listOf(
            "0532 123 45 67",
            "+90 532 123 45 67",
            "+905321234567",
            "5321234567",
            "0532-123-45-67",
            "0(532) 123 45 67",
            "0.532.123.45.67"
        )
        for (case in cases) {
            val s = PiiSanitizer.sanitize("Tel: $case yuk var")
            assertEquals("case=$case", listOf("+905321234567"), s.phones)
            assertTrue("masked: ${s.cleanText}", s.cleanText.contains("[TELEFON_1]"))
        }
    }

    @Test
    fun `sabit hat numarasi yakalanir`() {
        val s = PiiSanitizer.sanitize("Ofis: 0212 555 11 22 arayin")
        assertEquals(listOf("+902125551122"), s.phones)
    }

    @Test
    fun `birden fazla numara hepsi yakalanir`() {
        val s = PiiSanitizer.sanitize("Mehmet 0532 111 22 33 ya da Ali 0533 444 55 66")
        assertEquals(setOf("+905321112233", "+905334445566"), s.phones.toSet())
        assertFalse(s.cleanText.contains("0532"))
        assertFalse(s.cleanText.contains("0533"))
        assertFalse(s.cleanText.contains("Mehmet"))
        assertFalse(s.cleanText.contains("Ali"))
    }

    @Test
    fun `tonaj veya fiyat gibi rakamlar yanlis yakalanmaz`() {
        val s = PiiSanitizer.sanitize("25 ton yuk, 12500 TL fiyat")
        assertEquals(emptyList<String>(), s.phones)
        assertEquals("25 ton yuk, 12500 TL fiyat", s.cleanText)
    }

    @Test
    fun `mukerrer numara tek kez listelenir`() {
        val s = PiiSanitizer.sanitize("ara 0532 111 22 33 yine 0532 111 22 33")
        assertEquals(listOf("+905321112233"), s.phones)
    }

    @Test
    fun `mesajda telefon yoksa sender telefon fallback olarak maskelenir`() {
        val s = PiiSanitizer.sanitize(
            text = "Luleburgaz'dan Ergene'ye 5 ton hamderi",
            fallbackPhoneCandidate = "+90 532 111 22 33"
        )

        assertEquals(listOf("+905321112233"), s.phones)
        assertTrue(s.cleanText.contains("Irtibat: [TELEFON_1]"))
        assertFalse(s.cleanText.contains("0532"))
    }

    @Test
    fun `mesajdaki telefon varsa sender fallback eklenmez`() {
        val s = PiiSanitizer.sanitize(
            text = "Tuzla Bursa 0533 444 55 66",
            fallbackPhoneCandidate = "+90 532 111 22 33"
        )

        assertEquals(listOf("+905334445566"), s.phones)
        assertFalse(s.cleanText.contains("Irtibat:"))
    }

    @Test
    fun `bos metin guvenli`() {
        val s = PiiSanitizer.sanitize("")
        assertTrue(s.phones.isEmpty())
        assertEquals("", s.cleanText)
    }

    @Test
    fun `telefon yanindaki isim maskelenir ve geri yuklenir`() {
        val s = PiiSanitizer.sanitize("Gebze Ankara 22 ton Ahmet 0532 111 22 33")

        assertTrue(s.cleanText.contains("[ISIM_1] [TELEFON_1]"))
        assertEquals(mapOf("[ISIM_1]" to "Ahmet"), s.names)
        assertEquals("Ahmet", PiiSanitizer.restoreNames("[ISIM_1]", s.names))
        assertEquals("+905321112233", PiiSanitizer.restorePhone("[TELEFON_1]", s.phoneMap, null))
    }

    @Test
    fun `irtibat etiketinden sonraki isim maskelenir`() {
        val s = PiiSanitizer.sanitize("Tuzla Izmir frigo 18 ton Irtibat: Mehmet - 0544 222 33 44")

        assertTrue(s.cleanText.contains("Irtibat: [ISIM_1] - [TELEFON_1]"))
        assertEquals(mapOf("[ISIM_1]" to "Mehmet"), s.names)
    }

    @Test
    fun `isim ve telefon haritasi encode decode edilir`() {
        val names = mapOf("[ISIM_1]" to "Ahmet", "[ISIM_2]" to "Mehmet Kaya")
        val phones = mapOf("[TELEFON_1]" to "+905321112233", "[TELEFON_2]" to "+905334445566")
        val encoded = PiiSanitizer.encodeNames(names)
        val encodedPhones = PiiSanitizer.encodePhones(phones)

        assertEquals(names, PiiSanitizer.decodeNames(encoded))
        assertEquals(phones, PiiSanitizer.decodePhones(encodedPhones))
        assertEquals("+905334445566", PiiSanitizer.restorePhone("[TELEFON_2]", phones, null))
    }

    @Test
    fun `buyuk harfli isim telefon oncesinde maskelenir`() {
        val s = PiiSanitizer.sanitize("YALOVA MERSIN\nDILOVASI ADANA\n\nAHMET YILMAZ\n0532 999 88 77")

        assertFalse(s.cleanText.contains("AHMET"))
        assertTrue(s.cleanText.contains("[ISIM_1]"))
        assertTrue(s.cleanText.contains("[TELEFON_1]"))
        assertEquals(mapOf("[ISIM_1]" to "AHMET YILMAZ"), s.names)
    }

    @Test
    fun `kg ve tonaj ifadeleri isim diye maskelenmez`() {
        val s = PiiSanitizer.sanitize("Bozuyuk Mersin 3 palet 3500 Kg\n0532 111 22 33")

        assertTrue(s.cleanText.contains("3500 Kg"))
        assertFalse(s.cleanText.contains("[ISIM_1]"))
        assertEquals(emptyMap<String, String>(), s.names)
    }

    @Test
    fun `hafif tonaj isim diye maskelenmez`() {
        val s = PiiSanitizer.sanitize("DUDULLU HATAY BELEN\nHAFIF TONAJ\n0 532 444 55 66")

        assertTrue(s.cleanText.contains("HAFIF TONAJ"))
        assertFalse(s.cleanText.contains("[ISIM_1]"))
        assertEquals(emptyMap<String, String>(), s.names)
    }
}
