package com.example.test.utils

import android.content.Context
import android.location.Location
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Beta9 deliberately has no location permission or live-location feature.
 *
 * The type is kept as a no-op compatibility boundary for the dormant map code
 * so an upgrade cannot accidentally revive location collection by scheduling
 * an old worker or navigating to an internal screen.
 */
class LocationTracker(@Suppress("UNUSED_PARAMETER") context: Context) {
    fun hasLocationPermission(): Boolean = false

    fun getLocationUpdates(@Suppress("UNUSED_PARAMETER") intervalMs: Long = 10_000): Flow<Location> =
        emptyFlow()

    suspend fun getLastLocation(): Location? = null
}

object GeoUtils {
    fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earth = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
            kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
            kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
        val c = 2 * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
        return earth * c
    }

    fun calculateDistance(from: LatLng, to: LatLng): Double =
        calculateDistance(from.latitude, from.longitude, to.latitude, to.longitude)
}
