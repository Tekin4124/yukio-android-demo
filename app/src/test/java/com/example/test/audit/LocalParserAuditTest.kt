package com.example.test.audit

import com.example.test.data.local.LoadOfferEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalParserAuditTest {
    @Test
    fun reportsAggregateAnomaliesWithoutContent() {
        val clean = load(id = 1, raw = "a", from = "ANKARA", to = "ISTANBUL")
        val same = load(id = 2, raw = "b", from = "IZMIR", to = "IZMIR")
        val invalidFields = load(
            id = 3,
            raw = "c",
            from = "",
            to = "BURSA",
            tonnage = 120,
            price = 10
        )
        val duplicate = clean.copy(id = 4)

        val report = LocalParserAudit.analyze(listOf(clean, same, invalidFields, duplicate))

        assertEquals(4, report.totalOffers)
        assertEquals(3, report.distinctMessages)
        assertEquals(2, report.requiresReviewOffers)
        assertEquals(1, report.missingCoreRoute)
        assertEquals(1, report.sameExactRoute)
        assertEquals(1, report.invalidTonnage)
        assertEquals(1, report.invalidPrice)
        assertEquals(1, report.duplicateOffers)
    }

    @Test
    fun treatsDifferentDistrictsInSameCityAsValidRoute() {
        val report = LocalParserAudit.analyze(
            listOf(
                load(
                    id = 1,
                    raw = "a",
                    from = "ISTANBUL",
                    to = "ISTANBUL",
                    fromDistrict = "TUZLA",
                    toDistrict = "ESENYURT"
                )
            )
        )

        assertEquals(0, report.sameExactRoute)
        assertEquals(0, report.requiresReviewOffers)
    }

    private fun load(
        id: Long,
        raw: String,
        from: String,
        to: String,
        fromDistrict: String? = null,
        toDistrict: String? = null,
        tonnage: Int? = 20,
        price: Int? = 5_000
    ) = LoadOfferEntity(
        id = id,
        fromCity = from,
        fromDistrict = fromDistrict,
        toCity = to,
        toDistrict = toDistrict,
        tonnageTon = tonnage,
        tonnageText = tonnage?.let { "$it ton" },
        vehicleType = "TIR",
        vehicleCanonical = "tir",
        priceTry = price,
        phone = "",
        notes = null,
        sourceGroup = "WhatsApp",
        sourceSender = "Gizli",
        rawMessage = raw,
        createdAt = id
    )
}
