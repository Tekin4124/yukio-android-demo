package com.example.test.ai

import com.example.test.data.local.RouteItem
import kotlin.math.min

class DistanceEstimator {
    fun estimateDistanceToLoad(routes: List<RouteItem>, city: String): Int {
        if (routes.isEmpty()) return 300
        val cityUpper = city.uppercase()
        routes.forEach {
            if (it.fromCity.uppercase() == cityUpper) return 0
            if (it.toCity.uppercase() == cityUpper) return 30
        }
        var minDistance = Int.MAX_VALUE
        for (route in routes) {
            val distFrom = CityDistances.getDistance(route.fromCity, city)
            val distTo = CityDistances.getDistance(route.toCity, city)
            minDistance = min(minDistance, min(distFrom, distTo))
        }
        return if (minDistance == Int.MAX_VALUE) 300 else minDistance
    }
}