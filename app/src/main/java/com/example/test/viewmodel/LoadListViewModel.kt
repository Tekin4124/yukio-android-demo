package com.example.test.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.test.ai.InteractionType
import com.example.test.ai.MatchResult
import com.example.test.ai.MatchScoreEngine
import com.example.test.ai.UserInteraction
import com.example.test.ai.VehicleCompatibility
import com.example.test.data.local.AppDatabase
import com.example.test.data.local.LoadOfferEntity
import com.example.test.data.local.ParseSourceLogProjection
import com.example.test.data.local.UserInteractionEntity
import com.example.test.data.local.UserProfileEntity
import com.example.test.data.local.effectiveMaxTonnage
import com.example.test.data.local.effectiveWeightKg
import com.example.test.data.remote.ManualListingsRepository
import com.example.test.data.remote.toLoadOrNull
import com.example.test.data.remote.ParserSampleFeedback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

enum class SortOrder { NEWEST, MATCH_SCORE, PRICE_HIGH, DISTANCE_NEAR }

enum class LoadDateFilter { TODAY, ALL }

data class LoadListFilter(
    val vehicleType: String? = null,
    val city: String? = null,
    val sourceGroup: String? = null,
    val dateFilter: LoadDateFilter = LoadDateFilter.TODAY,
    val minTonnage: Int? = null,
    val maxTonnage: Int? = null,
    val onlyMatchingProfile: Boolean = false,
    // "Şehrimden çıkanlar" — yalnız bulunduğu il çıkışlı yükleri göster.
    // Şehir kaynağı yalnız kullanıcının elle seçtiği profile.currentCities.
    val onlyFromMyCity: Boolean = false,
    val sortBy: SortOrder = SortOrder.NEWEST
)

/**
 * The exact subset of filter state that changes the Room query. UI-only filters and sorting are
 * intentionally absent so toggling them reuses the latest Room emission.
 */
internal data class LoadQueryParameters(
    val city: String?,
    val minTonnage: Int?,
    val maxTonnage: Int?,
    val createdAfterMillis: Long
)

internal fun LoadListFilter.toLoadQueryParameters(todayStartMillis: Long): LoadQueryParameters =
    LoadQueryParameters(
        city = city
            ?.takeIf { it.isNotBlank() && it != "Hepsi" }
            ?.let(::normalizeLoadFilterText)
            ?.takeIf { it.isNotBlank() },
        minTonnage = minTonnage,
        maxTonnage = maxTonnage,
        createdAfterMillis = if (dateFilter == LoadDateFilter.TODAY) {
            todayStartMillis
        } else {
            Long.MIN_VALUE
        }
    )

internal fun Flow<LoadListFilter>.distinctLoadQueryParameters(
    todayStartMillis: Long
): Flow<LoadQueryParameters> =
    map { filter -> filter.toLoadQueryParameters(todayStartMillis) }.distinctUntilChanged()

internal fun Flow<LoadListFilter>.distinctLoadQueryParameters(
    todayStartMillis: Flow<Long>
): Flow<LoadQueryParameters> =
    combine(this, todayStartMillis) { filter, dayStart ->
        filter.toLoadQueryParameters(dayStart)
    }.distinctUntilChanged()

internal fun startOfLocalDayMillis(
    nowMillis: Long,
    zoneId: ZoneId = ZoneId.systemDefault()
): Long = Instant.ofEpochMilli(nowMillis)
    .atZone(zoneId)
    .toLocalDate()
    .atStartOfDay(zoneId)
    .toInstant()
    .toEpochMilli()

internal fun nextLocalDayStartMillis(
    nowMillis: Long,
    zoneId: ZoneId = ZoneId.systemDefault()
): Long = Instant.ofEpochMilli(nowMillis)
    .atZone(zoneId)
    .toLocalDate()
    .plusDays(1)
    .atStartOfDay(zoneId)
    .toInstant()
    .toEpochMilli()

private val repeatedFilterWhitespace = Regex("\\s+")

internal fun normalizeLoadFilterText(value: String): String =
    value.uppercase(Locale.ROOT)
        .replace('\u0130', 'I')
        .replace('\u015E', 'S')
        .replace('\u011E', 'G')
        .replace('\u00DC', 'U')
        .replace('\u00D6', 'O')
        .replace('\u00C7', 'C')
        .replace(repeatedFilterWhitespace, " ")
        .trim()

data class LoadListUiState(
    val loads: List<LoadOfferEntity> = emptyList(),
    val filteredLoads: List<LoadOfferEntity> = emptyList(),
    val matchResults: Map<Long, MatchResult> = emptyMap(),
    val filter: LoadListFilter = LoadListFilter(),
    val userProfile: UserProfileEntity? = null,
    val isProfileSet: Boolean = false,
    val isLoading: Boolean = true,
    val availableVehicleTypes: List<String> = listOf(
        "Hepsi",
        "Tır",
        "Kamyon",
        "Kamyonet",
        "Çekici",
        "Tente",
        "Frigo",
        "Açık kasa",
        "Kapalı kasa",
        "Lowbed"
    ),
    val availableCities: List<String> = emptyList(),
    val availableSourceGroups: List<String> = emptyList(),
    val parserSources: Map<Long, String> = emptyMap(),
    val parseFeedback: Map<Long, InteractionType> = emptyMap()
)

internal data class LoadListSnapshot(
    val loads: List<LoadOfferEntity>,
    val profile: UserProfileEntity?,
    val filter: LoadListFilter,
    val interactions: List<UserInteractionEntity>,
    val parseLogs: List<ParseSourceLogProjection>
)

@OptIn(ExperimentalCoroutinesApi::class)
class LoadListViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val loadDao = database.loadOfferDao()
    private val userProfileDao = database.userProfileDao()
    private val userInteractionDao = database.userInteractionDao()
    private val parseDebugLogDao = database.parseDebugLogDao()
    private val matchScoreEngine = MatchScoreEngine()
    private val scoreCache = mutableMapOf<Long, Pair<Long, MatchResult>>()

    private val _uiState = MutableStateFlow(LoadListUiState())
    val uiState: StateFlow<LoadListUiState> = _uiState.asStateFlow()

    private val _filter = MutableStateFlow(LoadListFilter())
    private val todayStartMillis = MutableStateFlow(
        startOfLocalDayMillis(System.currentTimeMillis())
    )
    private val parseFeedbackActions = setOf(
        InteractionType.PARSE_CORRECT,
        InteractionType.PARSE_WRONG,
        InteractionType.PARSE_MISSING
    )
    private val vehicleClassTokens = setOf("KAMYONET", "KAMYON", "CEKICI", "LOWBED", "TIR")
    private val trailerTokens = setOf(
        "TENTE", "TENTELI", "FRIGO", "ACIK KASA", "KAPALI KASA", "KAPALI",
        "ACIK", "LOWBED", "KONTEYNER", "PLATFORM", "13.60", "1360"
    )
    init {
        observeLocalDayStart()
        observeLoads()
    }

    private fun observeLocalDayStart() {
        viewModelScope.launch {
            while (isActive) {
                val currentTime = System.currentTimeMillis()
                val zoneId = ZoneId.systemDefault()
                todayStartMillis.value = startOfLocalDayMillis(currentTime, zoneId)
                val delayMillis = (nextLocalDayStartMillis(currentTime, zoneId) - currentTime)
                    .coerceIn(DAY_BOUNDARY_MIN_DELAY_MILLIS, DAY_BOUNDARY_MAX_DELAY_MILLIS)
                delay(delayMillis)
            }
        }
    }

    private fun observeLoads() {
        viewModelScope.launch {
            val localLoadsFlow = _filter.distinctLoadQueryParameters(todayStartMillis)
                .flatMapLatest { query ->
                    loadDao.getFiltered(
                        vehicleType = null,
                        city = query.city,
                        minTonnage = query.minTonnage,
                        maxTonnage = query.maxTonnage,
                        createdAfterMillis = query.createdAfterMillis
                    )
                }

            val loadsFlow = combine(localLoadsFlow, ManualListingsRepository.listings, _filter, todayStartMillis) { local, shared, filter, dayStart ->
                val query = filter.toLoadQueryParameters(dayStart)
                (local + shared.mapNotNull { it.toLoadOrNull() }.filter { it.matchesSharedQuery(query) })
                    .sortedWith(compareByDescending<LoadOfferEntity> { it.createdAt }.thenBy { it.id })
            }
            combine(
                loadsFlow,
                userProfileDao.getProfile(),
                _filter,
                userInteractionDao.getRecentFlow(LIST_INTERACTION_LIMIT),
                parseDebugLogDao.getRecentSourceProjectionFlow(LIST_PARSE_LOG_LIMIT)
            ) { loads, profile, filter, interactions, parseLogs ->
                LoadListSnapshot(loads, profile, filter, interactions, parseLogs)
            }.collectLatest { snapshot ->
                // Projection engine owns the worker dispatcher. collectLatest cancels stale work
                // when Room or an in-memory UI filter emits a newer snapshot.
                val projection = LoadListProjectionEngine.project(
                    snapshot = snapshot,
                    vehicleMatcher = ::matchesVehicleFilter,
                    matchCalculator = ::calculateMatchResults
                )
                _uiState.update { state ->
                    state.copy(
                        loads = projection.loads,
                        filteredLoads = projection.filteredLoads,
                        matchResults = projection.matchResults,
                        filter = snapshot.filter,
                        userProfile = snapshot.profile,
                        isProfileSet = snapshot.profile != null,
                        availableCities = listOf("Hepsi") + projection.availableCities,
                        availableSourceGroups = listOf("Hepsi") +
                            projection.availableSourceGroups,
                        parserSources = projection.parserSources,
                        parseFeedback = projection.parseFeedback,
                        isLoading = false
                    )
                }
            }
        }
    }

    private suspend fun calculateMatchResults(
        loads: List<LoadOfferEntity>,
        profile: UserProfileEntity?,
        interactionEntities: List<UserInteractionEntity>
    ): Map<Long, MatchResult> {
        if (profile == null) return emptyMap()

        val interactions = interactionEntities
            .take(SCORE_INTERACTION_LIMIT)
            .mapNotNull { it.toUserInteractionOrNull() }
        val interactionVersion = interactionEntities
            .take(SCORE_INTERACTION_LIMIT)
            .fold(interactionEntities.size.toLong()) { acc, interaction ->
                acc xor interaction.timestamp xor interaction.action.hashCode().toLong()
            }
        val baseVersion = profile.updatedAt xor interactionVersion
        val activeLoadIds = loads.mapTo(HashSet(loads.size * 2)) { it.id }
        scoreCache.keys.removeAll { it !in activeLoadIds }
        val results = HashMap<Long, MatchResult>(loads.size * 2)
        val coroutineContext = currentCoroutineContext()

        loads.forEachIndexed { index, load ->
            if (index % CANCELLATION_CHECK_INTERVAL == 0) coroutineContext.ensureActive()
            val cacheVersion = baseVersion xor load.createdAt
            val cached = scoreCache[load.id]
            val result = if (cached != null && cached.first == cacheVersion) {
                cached.second
            } else {
                matchScoreEngine.calculateMatchScore(load, profile, interactions).also {
                    scoreCache[load.id] = cacheVersion to it
                }
            }
            results[load.id] = result
        }
        return results
    }

    private fun UserInteractionEntity.toUserInteractionOrNull(): UserInteraction? {
        val interactionType = runCatching { InteractionType.valueOf(action) }.getOrNull() ?: return null
        return UserInteraction(
            loadId = loadId,
            fromCity = fromCity,
            toCity = toCity,
            vehicleType = vehicleType,
            price = price,
            action = interactionType,
            timestamp = timestamp
        )
    }

    private fun matchesVehicleFilter(load: LoadOfferEntity, selectedVehicle: String): Boolean {
        val selected = normalizeForMatch(selectedVehicle)
        // Önce backend'in kanonik etiketlerine bak. "10 teker", "kırkayak",
        // "40 ayak" gibi ifadeler burada "kamyon" olarak gelir; ham vehicleType
        // contains-match'te yakalanamayan durumları kanonik kurtarır.
        load.vehicleCanonical?.takeIf { it.isNotBlank() }?.let { canonical ->
            val tokens = canonical.split(",")
                .map { normalizeForMatch(it) }
                .filter { it.isNotBlank() }
            if (tokens.any { token ->
                    token == selected ||
                        (trailerTokens.contains(token) && (selected.contains(token) || token.contains(selected)))
                }
            ) return true

            val vehicleTokens = tokens.filter { isCanonicalVehicleClass(it) }
            if (vehicleTokens.isNotEmpty() && vehicleClassTokens.contains(selected)) {
                // "KAMYONET" contains "KAMYON" diye kamyon filtresine düşmesin.
                return false
            }
        }
        val loadVehicle = load.vehicleType?.takeIf { it.isNotBlank() }
        if (loadVehicle != null) {
            val normalizedLoadVehicle = normalizeForMatch(loadVehicle)
            if (normalizedLoadVehicle.contains(selected)) return true
            if (vehicleClassTokens.contains(selected)) {
                return VehicleCompatibility.getCompatibility(selectedVehicle, loadVehicle) >= 0.5f
            }
            if (trailerTokens.any { selected.contains(it) || it.contains(selected) }) {
                return normalizedLoadVehicle.contains(selected)
            }
            return false
        }

        val weightKg = load.effectiveWeightKg() ?: return false
        return when (selected) {
            "KAMYONET" -> weightKg <= 3500
            "KAMYON" -> weightKg <= 12000
            else -> false
        }
    }

    fun setVehicleTypeFilter(vehicleType: String) {
        // UI'nın gösterimi için ham değeri saklarız; DAO'ya normalize edilmiş
        // halini observeLoads içinde göndeririz.
        val raw = vehicleType.takeIf { it.isNotBlank() && it != "Hepsi" }
        _filter.update { it.copy(vehicleType = raw) }
    }

    fun setCityFilter(city: String) {
        val raw = city.takeIf { it.isNotBlank() && it != "Hepsi" }
        _filter.update { it.copy(city = raw) }
    }

    fun setSourceGroupFilter(sourceGroup: String) {
        val raw = sourceGroup.takeIf { it.isNotBlank() && it != "Hepsi" }
        _filter.update { it.copy(sourceGroup = raw) }
    }

    fun setDateFilter(dateFilter: LoadDateFilter) {
        _filter.update { it.copy(dateFilter = dateFilter) }
    }

    fun toggleDateFilter() {
        _filter.update { filter ->
            filter.copy(
                dateFilter = if (filter.dateFilter == LoadDateFilter.TODAY) {
                    LoadDateFilter.ALL
                } else {
                    LoadDateFilter.TODAY
                }
            )
        }
    }

    fun setTonnageFilter(min: Int?, max: Int?) {
        // Sıfır veya negatif değerleri "filtre yok" olarak ele al, ters
        // sıralamayı (min > max) otomatik düzelt.
        val sanitizedMin = min?.takeIf { it > 0 }
        val sanitizedMax = max?.takeIf { it > 0 }
        val (lo, hi) = if (sanitizedMin != null && sanitizedMax != null && sanitizedMin > sanitizedMax) {
            sanitizedMax to sanitizedMin
        } else {
            sanitizedMin to sanitizedMax
        }
        _filter.update { it.copy(minTonnage = lo, maxTonnage = hi) }
    }

    fun setMatchProfileFilter(enabled: Boolean) {
        _filter.update { it.copy(onlyMatchingProfile = enabled) }
    }

    fun setOnlyFromMyCity(enabled: Boolean) {
        _filter.update { it.copy(onlyFromMyCity = enabled) }
    }

    fun toggleOnlyFromMyCity() {
        _filter.update { it.copy(onlyFromMyCity = !it.onlyFromMyCity) }
    }

    /**
     * "Bana göre" tek toggle'ı: araç eşleşmesi + şehrimden filtrelerini birlikte
     * açıp kapatır. Her ikisi de açıksa kapatır, değilse ikisini de açar.
     */
    fun toggleBanaGore() {
        _filter.update {
            val bothOn = it.onlyMatchingProfile && it.onlyFromMyCity
            it.copy(
                onlyMatchingProfile = !bothOn,
                onlyFromMyCity = !bothOn
            )
        }
    }

    fun setSortOrder(sortOrder: SortOrder) {
        _filter.update { it.copy(sortBy = sortOrder) }
    }

    fun clearFilters() {
        _filter.value = LoadListFilter()
    }

    fun refreshLoads() {
        _uiState.update { it.copy(isLoading = false) }
    }

    fun recordInteraction(load: LoadOfferEntity, interactionType: InteractionType) {
        if (parseFeedbackActions.contains(interactionType)) {
            _uiState.update { state ->
                state.copy(parseFeedback = state.parseFeedback + (load.id to interactionType))
            }
            // İşaret yerelde anında görünür (yukarıdaki state), sampleId varsa
            // ayrıca best-effort backend'e bağlanır. Opt-in ve fail-closed
            // kontrolleri dispatcher içindedir; ağ hatası akışı bozmaz.
            ParserSampleFeedback.recordFeedback(
                context = getApplication(),
                sampleId = load.sampleId,
                offerIndex = load.offerIndex,
                interactionType = interactionType
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            // Disk dolu vb. yazma hatasi uygulamayi cokertmesin (beta4 sahada
            // SQLiteFullException ile crash loop yasadi, 2026-06-30).
            runCatching {
                userInteractionDao.insert(
                    UserInteractionEntity(
                        loadId = load.id,
                        fromCity = load.fromCity,
                        toCity = load.toCity,
                        vehicleType = load.vehicleType,
                        tonnage = load.effectiveMaxTonnage(),
                        price = load.priceTry,
                        action = interactionType.name
                    )
                )
            }
        }
    }

    /**
     * İlan detayı açıldığında sample'a bir kez OPENED bağlar. sampleId yoksa
     * veya kimliksiz örnek tercihi kapalıysa hiçbir şey gönderilmez.
     */
    fun recordSampleOpened(load: LoadOfferEntity) {
        ParserSampleFeedback.recordOpened(getApplication(), load.sampleId)
    }

    private fun isCanonicalVehicleClass(token: String): Boolean {
        return token in setOf("KAMYONET", "KAMYON", "TIR", "CEKICI", "LOWBED", "PANELVAN", "PICKUP", "ROMORK")
    }

    private fun normalizeForMatch(value: String): String {
        return normalizeLoadFilterText(value)
    }

    private companion object {
        const val LIST_INTERACTION_LIMIT = 2_000
        const val LIST_PARSE_LOG_LIMIT = 2_000
        const val SCORE_INTERACTION_LIMIT = 50
        const val CANCELLATION_CHECK_INTERVAL = 64
        const val DAY_BOUNDARY_MIN_DELAY_MILLIS = 1_000L
        const val DAY_BOUNDARY_MAX_DELAY_MILLIS = 60_000L
    }
}

internal fun LoadOfferEntity.matchesSharedQuery(query: LoadQueryParameters): Boolean {
    if (createdAt < query.createdAfterMillis) return false
    if (query.minTonnage != null && tonnageTon != null && tonnageTon < query.minTonnage) return false
    if (query.maxTonnage != null && tonnageTon != null && tonnageTon > query.maxTonnage) return false
    val city = query.city ?: return true
    return (listOf(fromCity,toCity,fromDistrict.orEmpty(),toDistrict.orEmpty()) + routeStops.orEmpty().flatMap { listOf(it.city.orEmpty(),it.district.orEmpty()) })
        .any { normalizeLoadFilterText(it).contains(city) }
}
