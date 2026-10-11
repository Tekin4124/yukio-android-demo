package com.example.test.viewmodel

import com.example.test.ai.InteractionType
import com.example.test.ai.MatchScoreEngine
import com.example.test.ai.UserInteraction
import com.example.test.data.local.LoadOfferEntity
import com.example.test.data.local.ParseSourceLogProjection
import com.example.test.data.local.RouteItem
import com.example.test.data.local.UserInteractionEntity
import com.example.test.data.local.UserProfileEntity
import com.example.test.data.model.RouteStop
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class LoadListProjectionEngineTest {
    @Test
    fun projectsThreeThousandWideRowsWithRealMatchScoring() = runBlocking {
        // Mirrors Room's ORDER BY createdAt DESC contract and keeps payload sizes close to the
        // multi-line WhatsApp posts seen in production.
        val loads = (3_000 downTo 1).map { index ->
            load(
                id = index.toLong(),
                createdAt = index.toLong(),
                sourceGroup = "Grup ${index % 20}",
                rawMessage = wideMessage(index.toLong())
            )
        }
        val parseLogs = loads.take(2_000).mapIndexed { index, load ->
            ParseSourceLogProjection(
                rawMessage = load.rawMessage,
                sanitizedMessage = load.rawMessage.replace("0532 000 00 00", "[PHONE]"),
                status = if (index % 2 == 0) "success_backend" else "success_ai"
            )
        }
        val interactions = loads.take(2_000).map { load ->
            UserInteractionEntity(
                id = load.id,
                loadId = load.id,
                fromCity = load.fromCity,
                toCity = load.toCity,
                vehicleType = load.vehicleType,
                tonnage = load.tonnageTon,
                price = load.priceTry,
                action = InteractionType.PARSE_CORRECT.name,
                timestamp = load.createdAt
            )
        }
        val profile = profile()
        val scoreEngine = MatchScoreEngine(now = { 10_000L })

        val projection = LoadListProjectionEngine.project(
            snapshot = LoadListSnapshot(
                loads = loads,
                profile = profile,
                filter = LoadListFilter(),
                interactions = interactions,
                parseLogs = parseLogs
            ),
            vehicleMatcher = { _, _ -> true },
            matchCalculator = { scoreLoads, activeProfile, interactionEntities ->
                val scoreInteractions = interactionEntities.take(50).mapNotNull { interaction ->
                    val action = runCatching {
                        InteractionType.valueOf(interaction.action)
                    }.getOrNull() ?: return@mapNotNull null
                    UserInteraction(
                        loadId = interaction.loadId,
                        fromCity = interaction.fromCity,
                        toCity = interaction.toCity,
                        vehicleType = interaction.vehicleType,
                        price = interaction.price,
                        action = action,
                        timestamp = interaction.timestamp
                    )
                }
                scoreLoads.associate { offer ->
                    offer.id to scoreEngine.calculateMatchScore(
                        load = offer,
                        user = requireNotNull(activeProfile),
                        userInteractions = scoreInteractions
                    )
                }
            }
        )

        assertEquals(3_000, projection.loads.size)
        assertEquals(3_000, projection.filteredLoads.size)
        assertEquals(3_000, projection.matchResults.size)
        assertTrue(projection.matchResults.values.all { it.score in 0..100 })
        assertEquals(2_000, projection.parserSources.size)
        assertEquals(2_000, projection.parseFeedback.size)
        assertEquals(20, projection.availableSourceGroups.size)
        assertEquals(listOf("Ankara", "İstanbul"), projection.availableCities)
        // NEWEST must reuse Room's already ordered result rather than allocate/sort it again.
        assertSame(loads, projection.filteredLoads)
    }

    @Test
    fun uiOnlyFilterChangesDoNotEmitNewRoomQueryParameters() = runBlocking {
        val base = LoadListFilter()
        val todayStart = 12_345L
        val queries = flowOf(
            base,
            base.copy(
                vehicleType = "Tır",
                sourceGroup = "Grup 1",
                onlyMatchingProfile = true,
                onlyFromMyCity = true,
                sortBy = SortOrder.MATCH_SCORE
            ),
            base.copy(city = "  Ankara  "),
            base.copy(city = "ankara", vehicleType = "Kamyon"),
            base.copy(city = "Ankara", minTonnage = 5, maxTonnage = 20)
        ).distinctLoadQueryParameters(todayStart).toList()

        assertEquals(
            listOf(
                LoadQueryParameters(null, null, null, createdAfterMillis = todayStart),
                LoadQueryParameters("ANKARA", null, null, createdAfterMillis = todayStart),
                LoadQueryParameters("ANKARA", 5, 20, createdAfterMillis = todayStart)
            ),
            queries
        )
    }

    @Test
    fun todayUsesLocalMidnightWhileAllKeepsTheFullLongRange() {
        val zone = ZoneId.of("Europe/Istanbul")
        val now = ZonedDateTime.of(2026, 7, 25, 18, 42, 13, 0, zone)
            .toInstant()
            .toEpochMilli()
        val expectedStart = ZonedDateTime.of(2026, 7, 25, 0, 0, 0, 0, zone)
            .toInstant()
            .toEpochMilli()
        val expectedNextStart = ZonedDateTime.of(2026, 7, 26, 0, 0, 0, 0, zone)
            .toInstant()
            .toEpochMilli()

        val start = startOfLocalDayMillis(now, zone)

        assertEquals(expectedStart, start)
        assertEquals(expectedNextStart, nextLocalDayStartMillis(now, zone))
        assertEquals(
            expectedStart,
            LoadListFilter(dateFilter = LoadDateFilter.TODAY)
                .toLoadQueryParameters(start)
                .createdAfterMillis
        )
        assertEquals(
            Long.MIN_VALUE,
            LoadListFilter(dateFilter = LoadDateFilter.ALL)
                .toLoadQueryParameters(start)
                .createdAfterMillis
        )
    }

    @Test
    fun midnightRefreshesTodayQueryButDoesNotRestartAllQuery() = runBlocking {
        val filters = MutableStateFlow(LoadListFilter(dateFilter = LoadDateFilter.ALL))
        val dayStarts = MutableStateFlow(100L)
        val queries = mutableListOf<LoadQueryParameters>()
        val collection = launch {
            filters.distinctLoadQueryParameters(dayStarts)
                .take(3)
                .toList(queries)
        }
        yield()

        // ALL remains Long.MIN_VALUE, so a local midnight change is distinct-suppressed.
        dayStarts.value = 200L
        yield()
        filters.value = LoadListFilter(dateFilter = LoadDateFilter.TODAY)
        yield()
        dayStarts.value = 300L
        collection.join()

        assertEquals(
            listOf(
                LoadQueryParameters(null, null, null, Long.MIN_VALUE),
                LoadQueryParameters(null, null, null, 200L),
                LoadQueryParameters(null, null, null, 300L)
            ),
            queries
        )
    }

    @Test
    fun projectionRunsOnItsWorkerDispatcher() = runBlocking {
        val callerThread = Thread.currentThread()
        var projectionThread: Thread? = null

        LoadListProjectionEngine.project(
            snapshot = LoadListSnapshot(
                loads = listOf(load(1, 1, "Grup 1", wideMessage(1))),
                profile = null,
                filter = LoadListFilter(),
                interactions = emptyList(),
                parseLogs = emptyList()
            ),
            vehicleMatcher = { _, _ -> true },
            matchCalculator = { _, _, _ ->
                projectionThread = Thread.currentThread()
                emptyMap()
            }
        )

        assertNotNull(projectionThread)
        assertNotSame(callerThread, projectionThread)
    }

    @Test
    fun cancellationPropagatesThroughProjectionWorker() = runBlocking {
        val scoringStarted = CompletableDeferred<Unit>()
        val scoringCancelled = CompletableDeferred<Unit>()
        val projectionJob = launch {
            LoadListProjectionEngine.project(
                snapshot = LoadListSnapshot(
                    loads = listOf(load(1, 1, "Grup 1", wideMessage(1))),
                    profile = profile(),
                    filter = LoadListFilter(),
                    interactions = emptyList(),
                    parseLogs = emptyList()
                ),
                vehicleMatcher = { _, _ -> true },
                matchCalculator = { _, _, _ ->
                    scoringStarted.complete(Unit)
                    try {
                        awaitCancellation()
                    } finally {
                        scoringCancelled.complete(Unit)
                    }
                }
            )
        }

        scoringStarted.await()
        projectionJob.cancelAndJoin()

        assertTrue(projectionJob.isCancelled)
        assertTrue(scoringCancelled.isCompleted)
    }

    @Test
    fun initialStateShowsLoadingUntilFirstRoomEmissionIsProjected() {
        val state = LoadListUiState()

        assertTrue(state.isLoading)
        assertTrue(state.loads.isEmpty())
        assertFalse(state.filteredLoads.isNotEmpty())
    }

    @Test
    fun availableCitiesIncludesSecondaryPickupAndDeliveryStops() = runBlocking {
        val multiStop = load(1, 1, "Grup 1", "çoklu rota").copy(
            routeStops = listOf(
                RouteStop("ALIS", 0, "Kocaeli", null),
                RouteStop("ALIS", 1, "İstanbul", null),
                RouteStop("TESLIM", 2, "Ankara", null),
                RouteStop("TESLIM", 3, "Giresun", null)
            )
        )

        val projection = LoadListProjectionEngine.project(
            snapshot = LoadListSnapshot(
                loads = listOf(multiStop),
                profile = null,
                filter = LoadListFilter(),
                interactions = emptyList(),
                parseLogs = emptyList()
            ),
            vehicleMatcher = { _, _ -> true },
            matchCalculator = { _, _, _ -> emptyMap() }
        )

        assertEquals(
            listOf("Ankara", "Giresun", "Kocaeli", "İstanbul"),
            projection.availableCities
        )
    }

    private fun load(
        id: Long,
        createdAt: Long,
        sourceGroup: String,
        rawMessage: String
    ) = LoadOfferEntity(
        id = id,
        fromCity = if (id % 2L == 0L) "İstanbul" else "Ankara",
        fromDistrict = null,
        toCity = if (id % 2L == 0L) "Ankara" else "İstanbul",
        toDistrict = null,
        tonnageTon = 10,
        vehicleType = "Tır",
        vehicleCanonical = "tir",
        priceTry = 20_000,
        phone = "",
        notes = null,
        sourceGroup = sourceGroup,
        sourceSender = "test",
        rawMessage = rawMessage,
        createdAt = createdAt
    )

    private fun profile() = UserProfileEntity(
        vehicleType = "Tır",
        trailerType = "Tente",
        maxTonnage = 24,
        height = 2.8f,
        width = 2.45f,
        length = 13.6f,
        fuelConsumption = 28f,
        costPerKm = 18f,
        priceMin = 10_000,
        priceMax = 80_000,
        preferredLoadTypes = listOf("tekstil", "palet"),
        routes = listOf(RouteItem("İstanbul", "Ankara")),
        isVehicleEmpty = true,
        currentCities = listOf("İstanbul"),
        createdAt = 1,
        updatedAt = 2
    )

    private fun wideMessage(id: Long): String = buildString(1_024) {
        append("İstanbul Ankara 10 ton tenteli tır tekstil yükü 0532 000 00 00. ")
        repeat(24) { line ->
            append("Palet ")
            append(line)
            append(": düzenli sevkiyat, yükleme bugün, teslimat yarın; ")
        }
        append("referans=")
        append(id)
    }
}
