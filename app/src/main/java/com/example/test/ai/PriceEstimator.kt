package com.example.test.ai

import com.example.test.data.local.LoadOfferEntity
import com.example.test.data.local.effectiveMaxTonnage
import com.example.test.data.model.CityCoordinates
import kotlin.math.max
import kotlin.math.min

object CityDistances {
    fun getDistance(from: String, to: String): Int {
        val a = CityCoordinates.getCoordinateOrNull(from) ?: return 500
        val b = CityCoordinates.getCoordinateOrNull(to) ?: return 500
        return CityCoordinates.calculateDistanceInt(a, b)
    }
}

class PriceEstimator {
    fun estimate(load: LoadOfferEntity, userMin: Int?, userMax: Int?): Pair<Int, String> {
        val loadPrice = load.priceTry ?: return 50 to "ℹ️ Fiyat belirtilmemiş"
        val distance = CityDistances.getDistance(load.fromCity, load.toCity)
        val pricePerKm = if (distance > 0) loadPrice.toFloat() / distance else 0f
        val marketAvgPerKm = estimateMarketPrice(load.effectiveMaxTonnage())

        val score = when {
            userMin != null && userMax != null -> {
                when {
                    loadPrice in userMin..userMax -> 100
                    loadPrice > userMax -> min(100, 95 + (loadPrice - userMax) / 500)
                    loadPrice >= userMin * 0.90 -> 70
                    loadPrice >= userMin * 0.75 -> 50
                    else -> 30
                }
            }
            pricePerKm > 0 && marketAvgPerKm > 0 -> {
                val ratio = pricePerKm / marketAvgPerKm
                when {
                    ratio >= 1.20f -> 100
                    ratio >= 1.00f -> 85
                    ratio >= 0.85f -> 65
                    ratio >= 0.70f -> 45
                    else -> 25
                }
            }
            else -> 50
        }

        val reason = when {
            score >= 95 -> "✅ Mükemmel fiyat: ${loadPrice}₺ (${pricePerKm.toInt()}₺/km)"
            score >= 80 -> "👍 İyi fiyat: ${loadPrice}₺"
            score >= 60 -> "ℹ️ Kabul edilebilir: ${loadPrice}₺"
            else -> "⚠️ Düşük fiyat: ${loadPrice}₺"
        }
        return max(0, min(100, score)) to reason
    }

    private fun estimateMarketPrice(tonnage: Int?): Float = when {
        tonnage == null -> 22f
        tonnage >= 28 -> 28f
        tonnage >= 24 -> 25f
        tonnage >= 18 -> 22f
        tonnage >= 10 -> 18f
        else -> 15f
    }
}
