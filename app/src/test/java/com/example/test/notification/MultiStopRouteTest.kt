package com.example.test.notification

import com.example.test.data.local.Converters
import com.example.test.data.local.deliveryStops
import com.example.test.data.local.pickupStops
import com.example.test.data.remote.ParseResponse
import com.example.test.ui.format.displayRoute
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Test

class MultiStopRouteTest {
    @Test
    fun mapsOnePickupAndTwoDeliveriesIntoOneStoredOffer() {
        val load = mapFirstOffer(
            """
            {
              "ilanlar": [{
                "gonderici_il": "Elazığ",
                "alici_il": "Rize",
                "rota_noktalari": [
                  {"rol":"ALIS","sira":0,"il":"Elazığ","ilce":null},
                  {"rol":"TESLIM","sira":1,"il":"Rize","ilce":null},
                  {"rol":"TESLIM","sira":2,"il":"Giresun","ilce":null}
                ]
              }]
            }
            """.trimIndent()
        )

        assertEquals("ELAZIG", load.fromCity)
        assertEquals("RIZE", load.toCity)
        assertEquals(listOf("ELAZIG"), load.pickupStops().map { it.city })
        assertEquals(listOf("RIZE", "GIRESUN"), load.deliveryStops().map { it.city })
        assertEquals("Elazığ → Rize + Giresun", load.displayRoute())

        val converter = Converters()
        assertEquals(load.routeStops, converter.toRouteStopList(converter.fromRouteStopList(load.routeStops)))
    }

    @Test
    fun routeStopsAliasCanSupplyMissingScalarCitiesAndDeduplicatesStops() {
        val load = mapFirstOffer(
            """
            {
              "ilanlar": [{
                "route_stops": [
                  {"role":"PICKUP","sequence":1,"city":"İstanbul"},
                  {"role":"PICKUP","sequence":0,"city":"Kocaeli"},
                  {"role":"PICKUP","sequence":2,"city":"İstanbul"},
                  {"role":"DELIVERY","sequence":3,"city":"Ankara"}
                ]
              }]
            }
            """.trimIndent()
        )

        assertEquals("KOCAELI", load.fromCity)
        assertEquals("ANKARA", load.toCity)
        assertEquals(listOf("KOCAELI", "ISTANBUL"), load.pickupStops().map { it.city })
        assertEquals("Kocaeli + İstanbul → Ankara", load.displayRoute())
    }

    private fun mapFirstOffer(json: String) = requireNotNull(
        Gson().fromJson(json, ParseResponse::class.java)
            .ilanlar
            .orEmpty()
            .single()
            .let { ilan ->
                LoadOfferMapper.fromIlanOrNull(
                    ilan = ilan,
                    rawMessage = "çoklu rota",
                    sourceGroup = "test",
                    fallbackSender = "test",
                    createdAt = 1L
                )
            }
    )
}
