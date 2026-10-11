package com.example.test.data.remote

import com.google.gson.Gson
import com.example.test.notification.LoadOfferMapper
import org.junit.Assert.*
import org.junit.Test

class DemoParserContractTest {
    @Test
    fun `local demo response maps into two usable Android offers`() {
        val json = requireNotNull(javaClass.getResourceAsStream("/demo-response.json"))
            .bufferedReader().use { it.readText() }
        val response = Gson().fromJson(json, ParseResponse::class.java)
        val text = "İSTANBUL -> ANKARA | TIR | 20 ton | 18000 TL\nBURSA -> İZMİR | KAMYON | 8 ton | 9000 TL"
        val offers = response.ilanlar.orEmpty().mapNotNull { ilan ->
            LoadOfferMapper.fromIlanOrNull(ilan, text, "Yerel Demo", "Demo")
        }
        assertEquals(2, offers.size)
        assertEquals("ISTANBUL", offers[0].fromCity)
        assertEquals("ANKARA", offers[0].toCity)
        assertEquals(18000, offers[0].priceTry)
        assertEquals(20, offers[0].tonnageTon)
        assertEquals("", offers[0].phone)
        assertEquals("IZMIR", offers[1].toCity)
        assertEquals(9000, offers[1].priceTry)
    }
}
