package com.example.test.data.model

import com.google.android.gms.maps.model.LatLng
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

enum class MarkerType { NEARBY, MEDIUM, FAR }

data class LoadMarker(
    val id: Long,
    val position: LatLng,
    val title: String,
    val fromCity: String,
    val fromDistrict: String?,
    val toCity: String,
    val toDistrict: String?,
    val tonnage: Int?,
    val tonnageLabel: String?,
    val vehicleType: String?,
    val price: Int?,
    val phone: String,
    val notes: String?,
    val distanceToUser: Int,
    val markerType: MarkerType,
    val matchScore: Int? = null,
    val createdAt: Long
)

data class HeatmapPoint(
    val position: LatLng,
    val intensity: Double,
    val city: String,
    val loadCount: Int,
    val totalTonnage: Int
)

data class CityDensity(
    val city: String,
    val loadCount: Int,
    val totalTonnage: Int,
    val avgPrice: Int,
    val position: LatLng
)

data class UserLocation(
    val latLng: LatLng,
    val city: String? = null,
    val district: String? = null,
    val lastUpdate: Long = System.currentTimeMillis()
)

object CityCoordinates {

    val cities = mapOf(
        "ADANA" to LatLng(37.0000, 35.3213),
        "ADIYAMAN" to LatLng(37.7648, 38.2786),
        "AFYONKARAHISAR" to LatLng(38.7569, 30.5387),
        "AGRI" to LatLng(39.7191, 43.0503),
        "AKSARAY" to LatLng(38.3687, 34.0370),
        "AMASYA" to LatLng(40.6533, 35.8331),
        "ANKARA" to LatLng(39.9334, 32.8597),
        "ANTALYA" to LatLng(36.8969, 30.7133),
        "ARDAHAN" to LatLng(41.1105, 42.7022),
        "ARTVIN" to LatLng(41.1828, 41.8183),
        "AYDIN" to LatLng(37.8560, 27.8416),
        "BALIKESIR" to LatLng(39.6484, 27.8826),
        "BARTIN" to LatLng(41.6344, 32.3375),
        "BATMAN" to LatLng(37.8812, 41.1351),
        "BAYBURT" to LatLng(40.2552, 40.2249),
        "BILECIK" to LatLng(40.1500, 29.9833),
        "BINGOL" to LatLng(38.8847, 40.4939),
        "BITLIS" to LatLng(38.4006, 42.1095),
        "BOLU" to LatLng(40.7395, 31.6116),
        "BURDUR" to LatLng(37.7203, 30.2908),
        "BURSA" to LatLng(40.1885, 29.0610),
        "CANAKKALE" to LatLng(40.1553, 26.4142),
        "CANKIRI" to LatLng(40.6013, 33.6134),
        "CORUM" to LatLng(40.5499, 34.9537),
        "DENIZLI" to LatLng(37.7830, 29.0963),
        "DIYARBAKIR" to LatLng(37.9144, 40.2306),
        "DUZCE" to LatLng(40.8438, 31.1565),
        "EDIRNE" to LatLng(41.6771, 26.5557),
        "ELAZIG" to LatLng(38.6810, 39.2264),
        "ERZINCAN" to LatLng(39.7500, 39.5000),
        "ERZURUM" to LatLng(39.9000, 41.2700),
        "ESKISEHIR" to LatLng(39.7767, 30.5206),
        "GAZIANTEP" to LatLng(37.0662, 37.3833),
        "GIRESUN" to LatLng(40.9128, 38.3895),
        "GUMUSHANE" to LatLng(40.4603, 39.4814),
        "HAKKARI" to LatLng(37.5744, 43.7408),
        "HATAY" to LatLng(36.4018, 36.3498),
        "IGDIR" to LatLng(39.9237, 44.0450),
        "ISPARTA" to LatLng(37.7648, 30.5566),
        "ISTANBUL" to LatLng(41.0082, 28.9784),
        "IZMIR" to LatLng(38.4237, 27.1428),
        "KAHRAMANMARAS" to LatLng(37.5753, 36.9228),
        "KARABUK" to LatLng(41.2061, 32.6204),
        "KARAMAN" to LatLng(37.1811, 33.2150),
        "KARS" to LatLng(40.6013, 43.0975),
        "KASTAMONU" to LatLng(41.3887, 33.7827),
        "KAYSERI" to LatLng(38.7205, 35.4826),
        "KILIS" to LatLng(36.7184, 37.1212),
        "KIRIKKALE" to LatLng(39.8468, 33.5153),
        "KIRKLARELI" to LatLng(41.7333, 27.2167),
        "KIRSEHIR" to LatLng(39.1458, 34.1606),
        "KOCAELI" to LatLng(40.8533, 29.8815),
        "KONYA" to LatLng(37.8746, 32.4932),
        "KUTAHYA" to LatLng(39.4167, 29.9833),
        "MALATYA" to LatLng(38.3552, 38.3095),
        "MANISA" to LatLng(38.6140, 27.4296),
        "MARDIN" to LatLng(37.3122, 40.7350),
        "MERSIN" to LatLng(36.8121, 34.6415),
        "MUGLA" to LatLng(37.2153, 28.3636),
        "MUS" to LatLng(38.9462, 41.7539),
        "NEVSEHIR" to LatLng(38.6244, 34.7144),
        "NIGDE" to LatLng(37.9667, 34.6833),
        "ORDU" to LatLng(40.9862, 37.8797),
        "OSMANIYE" to LatLng(37.0742, 36.2478),
        "RIZE" to LatLng(41.0201, 40.5234),
        "SAKARYA" to LatLng(40.7569, 30.3781),
        "SAMSUN" to LatLng(41.2867, 36.3300),
        "SANLIURFA" to LatLng(37.1591, 38.7969),
        "SIIRT" to LatLng(37.9333, 41.9500),
        "SINOP" to LatLng(42.0278, 35.1511),
        "SIRNAK" to LatLng(37.5164, 42.4611),
        "SIVAS" to LatLng(39.7477, 37.0179),
        "TEKIRDAG" to LatLng(40.9781, 27.5110),
        "TOKAT" to LatLng(40.3167, 36.5500),
        "TRABZON" to LatLng(41.0027, 39.7168),
        "TUNCELI" to LatLng(39.1083, 39.5483),
        "USAK" to LatLng(38.6823, 29.4082),
        "VAN" to LatLng(38.4891, 43.4089),
        "YALOVA" to LatLng(40.6500, 29.2667),
        "YOZGAT" to LatLng(39.8181, 34.8147),
        "ZONGULDAK" to LatLng(41.4564, 31.7987)
    )

    private val aliases = mapOf(
        "AFYON" to "AFYONKARAHISAR",
        "ICEL" to "MERSIN",
        "MARAS" to "KAHRAMANMARAS",
        "URFA" to "SANLIURFA",
        "GEBZE" to "KOCAELI",
        "SILOPI" to "SIRNAK"
    )

    fun getCoordinate(city: String): LatLng {
        return getCoordinateOrNull(city) ?: LatLng(39.0, 35.0)
    }

    fun getCoordinateOrNull(city: String): LatLng? {
        val normalizedCity = normalizeCity(city)
        val firstToken = normalizedCity.split(" ").firstOrNull().orEmpty()
        return cities[normalizedCity]
            ?: aliases[normalizedCity]?.let { cities[it] }
            ?: cities[firstToken]
            ?: aliases[firstToken]?.let { cities[it] }
    }

    private fun normalizeCity(city: String): String = city.uppercase().trim()
        .replace("İ", "I")
        .replace("Ş", "S")
        .replace("Ğ", "G")
        .replace("Ü", "U")
        .replace("Ö", "O")
        .replace("Ç", "C")

    fun findNearestCity(latLng: LatLng): String {
        var nearestCity = "ANKARA"
        var minDistance = Double.MAX_VALUE
        for ((city, coord) in cities) {
            val distance = calculateDistance(latLng, coord)
            if (distance < minDistance) {
                minDistance = distance
                nearestCity = city
            }
        }
        return nearestCity
    }

    fun calculateDistance(p1: LatLng, p2: LatLng): Double {
        val earthRadius = 6371.0
        val dLat = Math.toRadians(p2.latitude - p1.latitude)
        val dLon = Math.toRadians(p2.longitude - p1.longitude)
        val a = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(p1.latitude)) *
                cos(Math.toRadians(p2.latitude)) *
                sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadius * c
    }

    fun calculateDistanceInt(p1: LatLng, p2: LatLng): Int = calculateDistance(p1, p2).toInt()
}
