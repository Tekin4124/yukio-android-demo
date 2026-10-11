package com.example.test.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.test.data.local.AppDatabase
import com.example.test.data.local.LoadOfferEntity
import com.example.test.data.local.displayTonnage
import com.example.test.data.local.effectiveMaxTonnage
import com.example.test.data.model.CityCoordinates
import com.example.test.data.model.CityDensity
import com.example.test.data.model.LoadMarker
import com.example.test.data.model.MarkerType
import com.example.test.data.model.UserLocation
import com.example.test.utils.LocationTracker
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MapUiState(
    val isLoading: Boolean = false,
    val loadMarkers: List<LoadMarker> = emptyList(),
    val nearbyLoads: List<LoadMarker> = emptyList(),
    val selectedMarker: LoadMarker? = null,
    val userLocation: UserLocation? = null,
    val filterRadius: Int = 200,
    val showHeatmap: Boolean = false,
    val cityDensities: List<CityDensity> = emptyList()
)

private data class MapData(
    val userLocation: UserLocation,
    val filterRadius: Int,
    val loadMarkers: List<LoadMarker>,
    val nearbyLoads: List<LoadMarker>,
    val cityDensities: List<CityDensity>
)

class MapViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val dao = database.loadOfferDao()
    private val locationTracker = LocationTracker(application)

    private val defaultUserLocation = UserLocation(
        latLng = LatLng(39.9334, 32.8597),
        city = "Ankara"
    )

    private val loadsFlow = dao.getAllFlow()
    private val userLocationFlow = MutableStateFlow(defaultUserLocation)
    private val radiusFlow = MutableStateFlow(200)
    private val refreshTickFlow = MutableStateFlow(0L)

    private val _uiState = MutableStateFlow(MapUiState(userLocation = defaultUserLocation))
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    init {
        observeMapData()
        observeLocation()
    }

    private fun observeMapData() {
        val densitiesFlow = loadsFlow
            .map { loads -> calculateCityDensities(loads) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

        val markersFlow = combine(loadsFlow, userLocationFlow) { loads, userLocation ->
            val userLatLng = userLocation.latLng
            loads
                .mapNotNull { load -> entityToMarker(load, userLatLng) }
                .sortedBy { it.distanceToUser }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            combine(
                markersFlow,
                densitiesFlow,
                userLocationFlow,
                radiusFlow,
                refreshTickFlow
            ) { markers, densities, userLocation, radius, _ ->
                MapData(
                    userLocation = userLocation,
                    filterRadius = radius,
                    loadMarkers = markers,
                    nearbyLoads = markers.filter { it.distanceToUser <= radius },
                    cityDensities = densities
                )
            }.collect { data ->
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        userLocation = data.userLocation,
                        filterRadius = data.filterRadius,
                        loadMarkers = data.loadMarkers,
                        nearbyLoads = data.nearbyLoads,
                        cityDensities = data.cityDensities
                    )
                }
            }
        }
    }

    private fun observeLocation() {
        viewModelScope.launch {
            locationTracker.getLastLocation()?.let { location ->
                setUserLocation(LatLng(location.latitude, location.longitude))
            }

            locationTracker.getLocationUpdates(60_000).collect { location ->
                setUserLocation(LatLng(location.latitude, location.longitude))
            }
        }
    }

    private fun entityToMarker(entity: LoadOfferEntity, userLoc: LatLng): LoadMarker? {
        val coords = CityCoordinates.getCoordinateOrNull(entity.fromCity) ?: return null
        val distance = CityCoordinates.calculateDistanceInt(userLoc, coords)

        return LoadMarker(
            id = entity.id,
            position = coords,
            title = "${entity.fromCity} -> ${entity.toCity}",
            fromCity = entity.fromCity,
            fromDistrict = entity.fromDistrict,
            toCity = entity.toCity,
            toDistrict = entity.toDistrict,
            tonnage = entity.effectiveMaxTonnage(),
            tonnageLabel = entity.displayTonnage(),
            vehicleType = entity.vehicleType,
            price = entity.priceTry,
            phone = entity.phone,
            notes = entity.notes,
            distanceToUser = distance,
            markerType = when {
                distance <= 100 -> MarkerType.NEARBY
                distance <= 200 -> MarkerType.MEDIUM
                else -> MarkerType.FAR
            },
            matchScore = null,
            createdAt = entity.createdAt
        )
    }

    private fun calculateCityDensities(loads: List<LoadOfferEntity>): List<CityDensity> {
        return loads
            .groupBy { it.fromCity.uppercase() }
            .mapNotNull { (city, cityLoads) ->
                val coords = CityCoordinates.getCoordinateOrNull(city) ?: return@mapNotNull null
                CityDensity(
                    city = city,
                    loadCount = cityLoads.size,
                    totalTonnage = cityLoads.sumOf { it.effectiveMaxTonnage() ?: 0 },
                    avgPrice = cityLoads.mapNotNull { it.priceTry }.average().toInt().takeIf { it > 0 } ?: 0,
                    position = coords
                )
            }
            .sortedByDescending { it.loadCount }
    }

    fun selectMarker(marker: LoadMarker?) {
        _uiState.update { it.copy(selectedMarker = marker) }
    }

    fun setFilterRadius(radius: Int) {
        radiusFlow.value = radius
    }

    fun toggleHeatmap() {
        _uiState.update { it.copy(showHeatmap = !it.showHeatmap) }
    }

    fun refreshData() {
        _uiState.update { it.copy(isLoading = true) }
        refreshTickFlow.value = System.currentTimeMillis()
    }

    fun updateUserLocation(latLng: LatLng, city: String? = null) {
        setUserLocation(latLng, city)
    }

    private fun setUserLocation(latLng: LatLng, city: String? = null) {
        val cityName = city ?: CityCoordinates.findNearestCity(latLng)
        userLocationFlow.value = UserLocation(latLng, cityName)
    }
}
