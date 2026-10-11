package com.example.test.viewmodel

import com.example.test.ai.MatchResult
import com.example.test.data.local.LoadOfferEntity
import com.example.test.data.model.CityCoordinates

internal object LoadListSorter {
    fun sort(
        loads: List<LoadOfferEntity>,
        sortOrder: SortOrder,
        matchResults: Map<Long, MatchResult>
    ): List<LoadOfferEntity> {
        return when (sortOrder) {
            SortOrder.NEWEST -> loads.sortedByDescending { it.createdAt }
            SortOrder.MATCH_SCORE -> loads.sortedWith(
                compareByDescending<LoadOfferEntity> { matchResults[it.id]?.score ?: -1 }
                    .thenByDescending { it.createdAt }
            )
            SortOrder.PRICE_HIGH -> loads.sortedWith(
                compareByDescending<LoadOfferEntity> { it.priceTry ?: 0 }
                    .thenByDescending { it.createdAt }
            )
            SortOrder.DISTANCE_NEAR -> loads.sortedWith(
                compareBy<LoadOfferEntity> { routeDistance(it) }
                    .thenByDescending { it.createdAt }
            )
        }
    }

    private fun routeDistance(load: LoadOfferEntity): Int {
        val from = CityCoordinates.getCoordinateOrNull(load.fromCity) ?: return Int.MAX_VALUE
        val to = CityCoordinates.getCoordinateOrNull(load.toCity) ?: return Int.MAX_VALUE
        return CityCoordinates.calculateDistanceInt(from, to)
    }
}
