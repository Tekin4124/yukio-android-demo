package com.example.test.viewmodel

import com.example.test.data.local.LoadOfferEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class LoadListSorterTest {
    @Test
    fun newestSortPutsLatestLoadFirst() {
        val older = load(id = 1, createdAt = 1_000)
        val latest = load(id = 2, createdAt = 3_000)
        val middle = load(id = 3, createdAt = 2_000)

        val sorted = LoadListSorter.sort(
            loads = listOf(older, latest, middle),
            sortOrder = SortOrder.NEWEST,
            matchResults = emptyMap()
        )

        assertEquals(listOf(2L, 3L, 1L), sorted.map { it.id })
    }

    private fun load(id: Long, createdAt: Long): LoadOfferEntity {
        return LoadOfferEntity(
            id = id,
            fromCity = "KOCAELİ",
            fromDistrict = null,
            toCity = "ANKARA",
            toDistrict = null,
            tonnageTon = 1,
            vehicleType = "Kamyonet",
            vehicleCanonical = "kamyonet",
            priceTry = null,
            phone = "",
            notes = null,
            sourceGroup = "test",
            sourceSender = "test",
            rawMessage = "test-$id",
            createdAt = createdAt
        )
    }
}
