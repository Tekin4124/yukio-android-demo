package com.example.test.ai

import com.example.test.data.local.LoadOfferEntity
import com.example.test.data.local.UserProfileEntity
import com.example.test.data.local.deliveryStops
import com.example.test.data.local.pickupStops

/**
 * "Bana göre" filtresi ile yeni ilan bildirimlerinin ortak uygunluk kuralı.
 *
 * Kasa ve tonaj eleme ölçütü değildir; yalnız eşleşme puanını etkiler. Bir ilan:
 * - profilin araç sınıfıyla uyumluysa,
 * - tanımlı rota varsa gidiş veya dönüş yönünde bu rotaya uyuyorsa,
 * - mevcut şehir biliniyorsa o şehirden çıkıyorsa
 * "Bana göre" kapsamındadır.
 */
object ProfileLoadEligibility {
    private val canonicalVehicleClassTokens = setOf(
        "KAMYONET", "KAMYON", "CEKICI", "LOWBED", "TIR", "PANELVAN", "PICKUP", "ROMORK"
    )
    // Raw parser alanında önceki "Bana göre" davranışını koruyan ana sınıflar.
    // PANELVAN/PICKUP/ROMORK kanonik alanda güvenilir biçimde geldiğinde yukarıdaki
    // tam küme üzerinden değerlendirilir.
    private val rawVehicleClassTokens = setOf("KAMYONET", "KAMYON", "CEKICI", "LOWBED", "TIR")

    private val routeCityAliases = mapOf(
        "GEBZE" to "KOCAELI",
        "IZMIT" to "KOCAELI",
        "SEKERPINAR" to "KOCAELI",
        "DARICA" to "KOCAELI",
        "CAYIROVA" to "KOCAELI",
        "DILOVASI" to "KOCAELI",
        "DERINCE" to "KOCAELI",
        "KORFEZ" to "KOCAELI",
        "KARTEPE" to "KOCAELI",
        "BASISKELE" to "KOCAELI",
        "GOLCUK" to "KOCAELI",
        "KANDIRA" to "KOCAELI",
        "KARAMURSEL" to "KOCAELI",
        "TUZLA" to "ISTANBUL",
        "PENDIK" to "ISTANBUL",
        "KARTAL" to "ISTANBUL",
        "MALTEPE" to "ISTANBUL",
        "ATASEHIR" to "ISTANBUL",
        "FERHATPASA" to "ISTANBUL",
        "UMRANIYE" to "ISTANBUL",
        "USKUDAR" to "ISTANBUL",
        "SULTANBEYLI" to "ISTANBUL",
        "SANCAKTEPE" to "ISTANBUL",
        "CEKMEKOY" to "ISTANBUL",
        "BAYRAMPASA" to "ISTANBUL",
        "ESENYURT" to "ISTANBUL",
        "ESENLER" to "ISTANBUL",
        "BAGCILAR" to "ISTANBUL",
        "BASAKSEHIR" to "ISTANBUL",
        "AVCILAR" to "ISTANBUL",
        "BEYLIKDUZU" to "ISTANBUL",
        "SILIVRI" to "ISTANBUL",
        "CATALCA" to "ISTANBUL"
    )

    fun matchesBanaGore(load: LoadOfferEntity, profile: UserProfileEntity): Boolean {
        return matchesVehicleAndRoute(load, profile) && matchesCurrentCity(load, profile)
    }

    fun matchesVehicleAndRoute(load: LoadOfferEntity, profile: UserProfileEntity): Boolean {
        return matchesVehicle(load, profile) && matchesRoute(load, profile)
    }

    fun matchesCurrentCity(load: LoadOfferEntity, profile: UserProfileEntity): Boolean {
        val currentCities = profile.currentCities
            .map { normalizeRouteCity(it, null) }
            .filter { it.isNotBlank() }
            .toSet()
        if (currentCities.isEmpty()) return true
        return load.pickupStops().any { stop ->
            val city = stop.city ?: return@any false
            normalizeRouteCity(city, stop.district) in currentCities
        }
    }

    private fun matchesVehicle(load: LoadOfferEntity, profile: UserProfileEntity): Boolean {
        val canonicalVehicleTags = load.vehicleCanonical
            ?.takeIf { it.isNotBlank() }
            ?.split(",")
            ?.map { normalize(it.trim()) }
            ?.filter { it.isNotBlank() && it in canonicalVehicleClassTokens }
            .orEmpty()

        if (canonicalVehicleTags.isNotEmpty()) {
            return canonicalVehicleTags.any { tag ->
                VehicleCompatibility.getCompatibility(profile.vehicleType, tag) >= 0.5f
            }
        }

        return load.vehicleType?.takeIf { it.isNotBlank() }?.let { loadVehicle ->
            val normalizedLoadVehicle = normalize(loadVehicle)
            when {
                normalizedLoadVehicle.contains("SERBEST") -> true
                rawVehicleClassTokens.none { normalizedLoadVehicle.contains(it) } -> true
                else -> VehicleCompatibility.getCompatibility(profile.vehicleType, loadVehicle) >= 0.5f
            }
        } ?: true
    }

    private fun matchesRoute(load: LoadOfferEntity, profile: UserProfileEntity): Boolean {
        if (profile.routes.isEmpty()) return true
        val pickups = load.pickupStops().mapNotNull { stop ->
            stop.city?.let { city -> normalizeRouteCity(city, stop.district) }
        }
        val deliveries = load.deliveryStops().mapNotNull { stop ->
            stop.city?.let { city -> normalizeRouteCity(city, stop.district) }
        }
        return profile.routes.any { route ->
            val routeFrom = normalizeRouteCity(route.fromCity, null)
            val routeTo = normalizeRouteCity(route.toCity, null)
            pickups.any { loadFrom ->
                deliveries.any { loadTo ->
                    (loadFrom == routeFrom && loadTo == routeTo) ||
                        (loadFrom == routeTo && loadTo == routeFrom)
                }
            }
        }
    }

    private fun normalizeRouteCity(city: String, district: String?): String {
        val normalizedCity = normalize(city)
        val firstCityToken = normalizedCity.split(" ").firstOrNull().orEmpty()
        val normalizedDistrict = district?.let(::normalize).orEmpty()
        val firstDistrictToken = normalizedDistrict.split(" ").firstOrNull().orEmpty()
        return routeCityAliases[normalizedCity]
            ?: routeCityAliases[firstCityToken]
            ?: routeCityAliases[normalizedDistrict]
            ?: routeCityAliases[firstDistrictToken]
            ?: normalizedCity
    }

    private fun normalize(value: String): String {
        return value.uppercase()
            .replace("İ", "I")
            .replace("Ş", "S")
            .replace("Ğ", "G")
            .replace("Ü", "U")
            .replace("Ö", "O")
            .replace("Ç", "C")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
