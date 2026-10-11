package com.example.test.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.test.data.local.AppDatabase
import com.example.test.data.local.RouteItem
import com.example.test.data.local.UserProfileEntity
import com.example.test.data.local.WhatsappSourceDao
import com.example.test.data.local.WhatsappSourceEntity
import com.example.test.data.local.WhatsappSourceMode
import com.example.test.data.repository.UserProfileRepository
import com.example.test.util.LogisticSourceFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UserProfileUiState(
    val vehicleType: String = "",
    val trailerType: String? = null,
    val maxTonnage: Int = 20,
    val height: String = "",
    val width: String = "",
    val length: String = "",
    val fuelConsumption: String = "",
    val costPerKm: String = "",
    val priceMin: String = "",
    val priceMax: String = "",
    val preferredLoadTypes: List<String> = emptyList(),
    val routes: List<RouteItem> = emptyList(),
    val whatsappSources: List<WhatsappSourceEntity> = emptyList(),
    val isVehicleEmpty: Boolean = true,
    val currentCities: List<String> = emptyList(),
    val gpsCity: String? = null,
    val gpsCityAt: Long? = null,
    val gpsAutoEnabled: Boolean = true,
    val notifyOnVehicleMatch: Boolean = true,
    val isLoading: Boolean = false,
    val isSaved: Boolean = false,
    val errorMessage: String? = null
)

class UserProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: UserProfileRepository
    private val whatsappSourceDao: WhatsappSourceDao
    private val _uiState = MutableStateFlow(UserProfileUiState())
    val uiState: StateFlow<UserProfileUiState> = _uiState.asStateFlow()

    init {
        val database = AppDatabase.getInstance(application)
        repository = UserProfileRepository(database.userProfileDao())
        whatsappSourceDao = database.whatsappSourceDao()
        loadProfile()
        observeWhatsappSources()
    }

    private fun loadProfile() {
        viewModelScope.launch {
            repository.profile.collect { profile ->
                profile?.let {
                    _uiState.update { current ->
                        current.copy(
                        vehicleType = it.vehicleType,
                        trailerType = it.trailerType,
                        maxTonnage = it.maxTonnage,
                        height = it.height?.toString() ?: "",
                        width = it.width?.toString() ?: "",
                        length = it.length?.toString() ?: "",
                        fuelConsumption = it.fuelConsumption?.toString() ?: "",
                        costPerKm = it.costPerKm?.toString() ?: "",
                        priceMin = it.priceMin?.toString() ?: "",
                        priceMax = it.priceMax?.toString() ?: "",
                        preferredLoadTypes = it.preferredLoadTypes,
                        routes = it.routes,
                        isVehicleEmpty = it.isVehicleEmpty,
                        currentCities = it.currentCities,
                        gpsCity = it.gpsCity,
                        gpsCityAt = it.gpsCityAt,
                        gpsAutoEnabled = it.gpsAutoEnabled,
                        notifyOnVehicleMatch = it.notifyOnVehicleMatch
                    )
                    }
                }
            }
        }
    }

    private fun observeWhatsappSources() {
        viewModelScope.launch {
            whatsappSourceDao.getAllFlow().collect { sources ->
                _uiState.update { it.copy(whatsappSources = sources) }
            }
        }
    }

    fun updateVehicleType(type: String) {
        _uiState.update { it.copy(vehicleType = type, isSaved = false) }
        persistProfile()
    }

    fun updateTrailerType(type: String?) {
        _uiState.update { it.copy(trailerType = type, isSaved = false) }
        persistProfile()
    }

    fun updateMaxTonnage(tonnage: Int) {
        _uiState.update { it.copy(maxTonnage = tonnage, isSaved = false) }
        persistProfile()
    }

    fun updateHeight(value: String) {
        _uiState.update { it.copy(height = value, isSaved = false) }
    }

    fun updateWidth(value: String) {
        _uiState.update { it.copy(width = value, isSaved = false) }
    }

    fun updateLength(value: String) {
        _uiState.update { it.copy(length = value, isSaved = false) }
    }

    fun updateFuelConsumption(value: String) {
        _uiState.update { it.copy(fuelConsumption = value, isSaved = false) }
    }

    fun updateCostPerKm(value: String) {
        _uiState.update { it.copy(costPerKm = value, isSaved = false) }
    }

    fun updatePriceMin(value: String) {
        _uiState.update { it.copy(priceMin = value, isSaved = false) }
    }

    fun updatePriceMax(value: String) {
        _uiState.update { it.copy(priceMax = value, isSaved = false) }
    }

    fun updateVehicleStatus(isEmpty: Boolean) {
        _uiState.update { it.copy(isVehicleEmpty = isEmpty, isSaved = false) }
    }

    fun setNotifyOnVehicleMatch(enabled: Boolean) {
        _uiState.update { it.copy(notifyOnVehicleMatch = enabled) }
        viewModelScope.launch {
            repository.updateNotifyOnVehicleMatch(enabled)
        }
    }

    fun setGpsAutoEnabled(enabled: Boolean) {
        _uiState.update { state ->
            if (!enabled) {
                state.copy(gpsAutoEnabled = false, gpsCity = null, gpsCityAt = null)
            } else {
                state.copy(gpsAutoEnabled = true)
            }
        }
        // Anında persist — switch tıklaması her zaman kalıcı olsun
        viewModelScope.launch {
            repository.updateGpsAutoEnabled(enabled)
        }
    }

    fun addCurrentCity(city: String) {
        val trimmed = city.trim()
        if (trimmed.isBlank()) return
        val updated = run {
            val current = _uiState.value.currentCities
            if (current.any { it.equals(trimmed, ignoreCase = true) }) current
            else current + trimmed
        }
        _uiState.update { it.copy(currentCities = updated) }
        viewModelScope.launch {
            repository.updateCurrentCities(updated)
        }
    }

    fun removeCurrentCity(city: String) {
        val updated = _uiState.value.currentCities
            .filterNot { it.equals(city, ignoreCase = true) }
        _uiState.update { it.copy(currentCities = updated) }
        viewModelScope.launch {
            repository.updateCurrentCities(updated)
        }
    }

    fun toggleLoadType(loadType: String) {
        _uiState.update { state ->
            val current = state.preferredLoadTypes.toMutableList()
            if (current.contains(loadType)) current.remove(loadType) else current.add(loadType)
            state.copy(preferredLoadTypes = current, isSaved = false)
        }
    }

    fun addRoute(from: String, to: String) {
        if (from.isBlank() || to.isBlank()) return
        _uiState.update { state ->
            val current = state.routes.toMutableList()
            val newRoute = RouteItem(from.trim(), to.trim())
            if (!current.contains(newRoute)) current.add(newRoute)
            state.copy(routes = current, isSaved = false)
        }
        persistProfile()
    }

    fun removeRoute(route: RouteItem) {
        _uiState.update { state ->
            state.copy(routes = state.routes - route, isSaved = false)
        }
        persistProfile()
    }

    fun allowWhatsappSource(groupName: String) {
        setWhatsappSourceMode(groupName, WhatsappSourceMode.ALLOW)
    }

    fun blockWhatsappSource(source: WhatsappSourceEntity) {
        setWhatsappSourceMode(source.groupName, WhatsappSourceMode.BLOCK)
    }

    fun allowWhatsappSource(source: WhatsappSourceEntity) {
        setWhatsappSourceMode(source.groupName, WhatsappSourceMode.ALLOW)
    }

    fun setWhatsappSourceAuto(source: WhatsappSourceEntity) {
        setWhatsappSourceMode(source.groupName, WhatsappSourceMode.AUTO)
    }

    /**
     * Grubu izleme listesinden tamamen siler. Kalıcı yasak değil — o gruptan
     * tekrar mesaj gelirse yeniden (AUTO olarak) listeye düşer. Kullanıcı artık
     * üye olmadığı eski grupları temizleyebilsin diye.
     */
    fun deleteWhatsappSource(source: WhatsappSourceEntity) {
        viewModelScope.launch {
            runCatching {
                whatsappSourceDao.deleteByNormalizedName(source.normalizedName)
            }
        }
    }

    private fun setWhatsappSourceMode(groupName: String, mode: String) {
        val cleanName = groupName
            .replace(Regex("\\s+"), " ")
            .trim(' ', ':', '-', '~')
        val normalizedName = LogisticSourceFilter.normalize(cleanName)
        if (cleanName.isBlank() || normalizedName.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Grup adı boş olamaz") }
            return
        }

        viewModelScope.launch {
            whatsappSourceDao.setMode(
                groupName = cleanName,
                normalizedName = normalizedName,
                mode = mode
            )
        }
    }

    /**
     * Otomatik kayıt: bir alan değişince profili sessizce DB'ye yazar (tek satır
     * upsert, id=1). "Profili Kaydet" butonu kaldırıldı — kullanıcı araç/dorse/
     * tonaj/güzergah seçer seçmez kalıcı olur. Navigasyon/toast/spinner yok.
     * Yazma hatası (disk dolu vb.) uygulamayı çökertmesin diye runCatching.
     */
    private fun persistProfile() {
        val state = _uiState.value
        viewModelScope.launch {
            runCatching {
                repository.saveProfile(
                    UserProfileEntity(
                        vehicleType = state.vehicleType,
                        trailerType = state.trailerType,
                        maxTonnage = state.maxTonnage,
                        height = state.height.toFloatOrNull(),
                        width = state.width.toFloatOrNull(),
                        length = state.length.toFloatOrNull(),
                        fuelConsumption = state.fuelConsumption.toFloatOrNull(),
                        costPerKm = state.costPerKm.toFloatOrNull(),
                        priceMin = state.priceMin.toIntOrNull(),
                        priceMax = state.priceMax.toIntOrNull(),
                        preferredLoadTypes = state.preferredLoadTypes,
                        routes = state.routes,
                        isVehicleEmpty = state.isVehicleEmpty,
                        currentCities = state.currentCities,
                        gpsCity = state.gpsCity,
                        gpsCityAt = state.gpsCityAt,
                        gpsAutoEnabled = state.gpsAutoEnabled,
                        notifyOnVehicleMatch = state.notifyOnVehicleMatch
                    )
                )
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
