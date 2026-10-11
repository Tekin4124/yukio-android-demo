package com.example.test.ai

import com.example.test.data.local.LoadOfferEntity
import com.example.test.data.local.RouteItem
import com.example.test.data.local.UserProfileEntity
import com.example.test.data.model.RouteStop
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileLoadEligibilityTest {
    @Test
    fun tirOnSameRouteDoesNotMatchKamyonetProfile() {
        val profile = profile(vehicleType = "Kamyonet")
        val tirLoad = load(vehicleCanonical = "tır", vehicleType = "TIR")

        assertFalse(ProfileLoadEligibility.matchesBanaGore(tirLoad, profile))
    }

    @Test
    fun compatibleVehicleRouteAndCurrentCityMatchesBanaGore() {
        val profile = profile(vehicleType = "Kamyonet")
        val kamyonetLoad = load(vehicleCanonical = "kamyonet", vehicleType = "Kamyonet")

        assertTrue(ProfileLoadEligibility.matchesBanaGore(kamyonetLoad, profile))
    }

    @Test
    fun matchingVehicleAndRouteFromAnotherCurrentCityDoesNotMatch() {
        val profile = profile(vehicleType = "Kamyonet", currentCities = listOf("BURSA"))
        val kamyonetLoad = load(vehicleCanonical = "kamyonet", vehicleType = "Kamyonet")

        assertFalse(ProfileLoadEligibility.matchesBanaGore(kamyonetLoad, profile))
    }

    @Test
    fun routeAliasesAndReturnDirectionUseSameRuleAsBanaGore() {
        val profile = profile(
            vehicleType = "Kamyonet",
            routes = listOf(RouteItem("İSTANBUL", "KOCAELİ")),
            currentCities = listOf("GEBZE")
        )
        val returnLoad = load(
            fromCity = "KOCAELİ",
            fromDistrict = "Gebze",
            toCity = "İSTANBUL",
            toDistrict = "Tuzla",
            vehicleCanonical = "kamyonet",
            vehicleType = "Kamyonet"
        )

        assertTrue(ProfileLoadEligibility.matchesBanaGore(returnLoad, profile))
    }

    @Test
    fun anyPickupAndDeliveryPairCanMatchProfileAndCurrentCity() {
        val profile = profile(
            vehicleType = "Kamyonet",
            routes = listOf(RouteItem("İSTANBUL", "ANKARA")),
            currentCities = listOf("İSTANBUL")
        )
        val multiStopLoad = load(
            fromCity = "KOCAELİ",
            toCity = "ANKARA",
            vehicleCanonical = "kamyonet",
            vehicleType = "Kamyonet",
            routeStops = listOf(
                RouteStop("ALIS", 0, "KOCAELİ", null),
                RouteStop("ALIS", 1, "İSTANBUL", null),
                RouteStop("TESLIM", 2, "ANKARA", null)
            )
        )

        assertTrue(ProfileLoadEligibility.matchesBanaGore(multiStopLoad, profile))
    }

    private fun profile(
        vehicleType: String,
        routes: List<RouteItem> = listOf(RouteItem("KOCAELİ", "ANKARA")),
        currentCities: List<String> = listOf("KOCAELİ")
    ): UserProfileEntity {
        return UserProfileEntity(
            vehicleType = vehicleType,
            trailerType = null,
            maxTonnage = 5,
            height = null,
            width = null,
            length = null,
            fuelConsumption = null,
            costPerKm = null,
            priceMin = null,
            priceMax = null,
            preferredLoadTypes = emptyList(),
            routes = routes,
            isVehicleEmpty = true,
            currentCities = currentCities
        )
    }

    private fun load(
        fromCity: String = "KOCAELİ",
        fromDistrict: String? = "Gebze",
        toCity: String = "ANKARA",
        toDistrict: String? = null,
        vehicleCanonical: String,
        vehicleType: String?,
        routeStops: List<RouteStop> = emptyList()
    ): LoadOfferEntity {
        return LoadOfferEntity(
            id = 1,
            fromCity = fromCity,
            fromDistrict = fromDistrict,
            toCity = toCity,
            toDistrict = toDistrict,
            tonnageTon = 1,
            vehicleType = vehicleType,
            vehicleCanonical = vehicleCanonical,
            priceTry = null,
            phone = "",
            notes = null,
            sourceGroup = "test",
            sourceSender = "test",
            rawMessage = "test",
            createdAt = 1,
            routeStops = routeStops
        )
    }
}
