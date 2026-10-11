package com.example.test.viewmodel

import com.example.test.ai.InteractionType
import com.example.test.ai.MatchResult
import com.example.test.ai.ProfileLoadEligibility
import com.example.test.data.local.LoadOfferEntity
import com.example.test.data.local.ParseSourceLogProjection
import com.example.test.data.local.UserInteractionEntity
import com.example.test.data.local.UserProfileEntity
import com.example.test.data.local.routeCities
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.util.TreeSet

/**
 * CPU-heavy projection between Room emissions and Compose state.
 *
 * The engine owns its worker dispatcher. Keeping the complete projection behind this boundary
 * makes it difficult to accidentally move a new O(n) operation back to the main thread when the
 * list grows.
 */
internal object LoadListProjectionEngine {
    private val hiddenFormatCharacters =
        Regex("[\\u200B-\\u200F\\u202A-\\u202E\\u2060-\\u206F]")
    private val repeatedWhitespace = Regex("\\s+")
    private val parseFeedbackActions = setOf(
        InteractionType.PARSE_CORRECT,
        InteractionType.PARSE_WRONG,
        InteractionType.PARSE_MISSING
    )

    suspend fun project(
        snapshot: LoadListSnapshot,
        vehicleMatcher: (LoadOfferEntity, String) -> Boolean,
        matchCalculator: suspend (
            List<LoadOfferEntity>,
            UserProfileEntity?,
            List<UserInteractionEntity>
        ) -> Map<Long, MatchResult>,
        dispatcher: CoroutineDispatcher = Dispatchers.Default
    ): LoadListProjection = withContext(dispatcher) {
        val loads = snapshot.loads
        val profile = snapshot.profile
        val filter = snapshot.filter
        val coroutineContext = currentCoroutineContext()

        val vehicleFilteredLoads = filter.vehicleType
            ?.takeIf { it.isNotBlank() && it != "Hepsi" }
            ?.let { selectedVehicle ->
                loads.filterIndexed { index, load ->
                    if (index % CANCELLATION_CHECK_INTERVAL == 0) {
                        coroutineContext.ensureActive()
                    }
                    vehicleMatcher(load, selectedVehicle)
                }
            } ?: loads

        val sourceFilteredLoads = filter.sourceGroup
            ?.takeIf { it.isNotBlank() && it != "Hepsi" }
            ?.let { selectedGroup ->
                vehicleFilteredLoads.filterIndexed { index, load ->
                    if (index % CANCELLATION_CHECK_INTERVAL == 0) {
                        coroutineContext.ensureActive()
                    }
                    load.sourceGroup == selectedGroup
                }
            } ?: vehicleFilteredLoads

        val cityFilteredLoads = if (filter.onlyFromMyCity && profile != null) {
            sourceFilteredLoads.filterIndexed { index, load ->
                if (index % CANCELLATION_CHECK_INTERVAL == 0) {
                    coroutineContext.ensureActive()
                }
                ProfileLoadEligibility.matchesCurrentCity(load, profile)
            }
        } else {
            sourceFilteredLoads
        }

        val matchResults = matchCalculator(
            cityFilteredLoads,
            profile,
            snapshot.interactions
        )

        val profileFilteredLoads = if (filter.onlyMatchingProfile && profile != null) {
            cityFilteredLoads.filterIndexed { index, load ->
                if (index % CANCELLATION_CHECK_INTERVAL == 0) {
                    coroutineContext.ensureActive()
                }
                ProfileLoadEligibility.matchesVehicleAndRoute(load, profile)
            }
        } else {
            cityFilteredLoads
        }

        // Room already guarantees createdAt DESC. List.filter preserves order, so NEWEST can reuse
        // the emitted list instead of sorting thousands of rows for a second time at every launch.
        val sortedLoads = if (filter.sortBy == SortOrder.NEWEST) {
            profileFilteredLoads
        } else {
            LoadListSorter.sort(profileFilteredLoads, filter.sortBy, matchResults)
        }

        coroutineContext.ensureActive()
        LoadListProjection(
            loads = loads,
            filteredLoads = sortedLoads,
            matchResults = matchResults,
            availableCities = collectCities(loads),
            availableSourceGroups = collectSourceGroups(loads),
            parserSources = buildParserSourceMap(cityFilteredLoads, snapshot.parseLogs),
            parseFeedback = buildParseFeedbackMap(snapshot.interactions)
        )
    }

    private suspend fun collectCities(loads: List<LoadOfferEntity>): List<String> {
        val cities = HashSet<String>()
        val coroutineContext = currentCoroutineContext()
        loads.forEachIndexed { index, load ->
            if (index % CANCELLATION_CHECK_INTERVAL == 0) coroutineContext.ensureActive()
            load.routeCities().forEach(cities::add)
        }
        return cities.sorted()
    }

    private suspend fun collectSourceGroups(loads: List<LoadOfferEntity>): List<String> {
        val groups = TreeSet(String.CASE_INSENSITIVE_ORDER)
        val coroutineContext = currentCoroutineContext()
        loads.forEachIndexed { index, load ->
            if (index % CANCELLATION_CHECK_INTERVAL == 0) coroutineContext.ensureActive()
            load.sourceGroup.takeIf(String::isNotBlank)?.let(groups::add)
        }
        return groups.toList()
    }

    private suspend fun buildParserSourceMap(
        loads: List<LoadOfferEntity>,
        parseLogs: List<ParseSourceLogProjection>
    ): Map<Long, String> {
        if (loads.isEmpty() || parseLogs.isEmpty()) return emptyMap()

        val sourceByMessage = HashMap<String, String>(parseLogs.size * 2)
        val coroutineContext = currentCoroutineContext()
        parseLogs.forEachIndexed { index, log ->
            if (index % CANCELLATION_CHECK_INTERVAL == 0) coroutineContext.ensureActive()
            val source = parserSourceFromStatus(log.status) ?: return@forEachIndexed
            putSourceIfPresent(sourceByMessage, log.sanitizedMessage, source)
            putSourceIfPresent(sourceByMessage, log.rawMessage, source)
        }

        val result = HashMap<Long, String>()
        loads.forEachIndexed { index, load ->
            if (index % CANCELLATION_CHECK_INTERVAL == 0) coroutineContext.ensureActive()
            sourceByMessage[normalizeMessageKey(load.rawMessage)]?.let { source ->
                result[load.id] = source
            }
        }
        return result
    }

    private fun putSourceIfPresent(
        target: MutableMap<String, String>,
        message: String,
        source: String
    ) {
        val key = normalizeMessageKey(message)
        if (key.isNotBlank()) target.putIfAbsent(key, source)
    }

    private suspend fun buildParseFeedbackMap(
        interactions: List<UserInteractionEntity>
    ): Map<Long, InteractionType> {
        if (interactions.isEmpty()) return emptyMap()

        val result = LinkedHashMap<Long, InteractionType>()
        val coroutineContext = currentCoroutineContext()
        interactions.forEachIndexed { index, interaction ->
            if (index % CANCELLATION_CHECK_INTERVAL == 0) coroutineContext.ensureActive()
            val type = runCatching { InteractionType.valueOf(interaction.action) }.getOrNull()
            if (type != null && type in parseFeedbackActions) {
                result.putIfAbsent(interaction.loadId, type)
            }
        }
        return result
    }

    private fun parserSourceFromStatus(status: String): String? {
        val normalized = status.lowercase()
        return when {
            "review" in normalized -> "review"
            "ai" in normalized || "openai" in normalized -> "ai"
            "backend" in normalized ||
                "cache" in normalized ||
                normalized.startsWith("success") -> "backend"
            else -> null
        }
    }

    private fun normalizeMessageKey(value: String): String {
        return value
            .replace(hiddenFormatCharacters, "")
            .replace(repeatedWhitespace, " ")
            .trim()
            .lowercase()
    }

    private const val CANCELLATION_CHECK_INTERVAL = 64
}

internal data class LoadListProjection(
    val loads: List<LoadOfferEntity>,
    val filteredLoads: List<LoadOfferEntity>,
    val matchResults: Map<Long, MatchResult>,
    val availableCities: List<String>,
    val availableSourceGroups: List<String>,
    val parserSources: Map<Long, String>,
    val parseFeedback: Map<Long, InteractionType>
)
