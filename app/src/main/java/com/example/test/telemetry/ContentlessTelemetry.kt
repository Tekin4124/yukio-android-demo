package com.example.test.telemetry

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.example.test.BuildConfig
import com.example.test.data.ConsentStore
import java.util.TreeMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

private val Context.telemetryMilestoneDataStore by preferencesDataStore(name = "telemetry_milestones")

internal data class TelemetryEnvelope(val events: List<TelemetryEvent>)

private interface TelemetryApi {
    @POST("v1/telemetry/events")
    suspend fun send(@Body envelope: TelemetryEnvelope): Response<Unit>
}

/**
 * Best-effort, contentless and identifier-free product telemetry.
 *
 * No timestamp, installation/device id, message data or billing token is added.
 * Events remain in memory only until a small aggregate batch is sent. Collection
 * is disabled at build time by default and also requires the user's separate opt-in.
 */
internal object ContentlessTelemetry {
    private const val MAX_BATCH_EVENTS = 50
    private const val MAX_COUNT_PER_EVENT = 20
    private const val FLUSH_THRESHOLD = 20
    private const val FLUSH_DELAY_MS = 10_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val pending = LinkedHashMap<String, TelemetryEvent>()
    private var flushJob: Job? = null
    @Volatile private var applicationContext: Context? = null

    private val api: TelemetryApi by lazy {
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
        Retrofit.Builder()
            .baseUrl(BuildConfig.BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TelemetryApi::class.java)
    }

    fun record(context: Context, event: TelemetryEvent) {
        if (!BuildConfig.TELEMETRY_ENABLED) return
        val appContext = context.applicationContext
        applicationContext = appContext
        scope.launch {
            if (!ConsentStore(appContext).isProductAnalyticsEnabled.first()) return@launch
            enqueue(event)
        }
    }

    fun recordOnce(context: Context, milestone: TelemetryMilestone, event: TelemetryEvent) {
        if (!BuildConfig.TELEMETRY_ENABLED) return
        val appContext = context.applicationContext
        applicationContext = appContext
        scope.launch {
            if (!ConsentStore(appContext).isProductAnalyticsEnabled.first()) return@launch
            if (TelemetryMilestoneStore(appContext).markIfFirst(milestone)) enqueue(event)
        }
    }

    suspend fun clearLocalState(context: Context) {
        mutex.withLock {
            pending.clear()
            flushJob?.cancel()
            flushJob = null
        }
        TelemetryMilestoneStore(context.applicationContext).clear()
    }

    private suspend fun enqueue(event: TelemetryEvent) {
        var flushNow = false
        mutex.withLock {
            val key = eventKey(event)
            val current = pending[key]
            val nextCount = ((current?.count ?: 0) + event.count)
                .coerceAtMost(MAX_COUNT_PER_EVENT)
            pending[key] = event.copy(count = nextCount)
            flushNow = pending.values.sumOf { it.count } >= FLUSH_THRESHOLD ||
                pending.size >= MAX_BATCH_EVENTS
            if (!flushNow && flushJob == null) {
                flushJob = scope.launch {
                    delay(FLUSH_DELAY_MS)
                    flush()
                }
            }
        }
        if (flushNow) flush()
    }

    private suspend fun flush() {
        val batch = mutex.withLock {
            flushJob?.cancel()
            flushJob = null
            if (pending.isEmpty()) return
            val selected = pending.entries.take(MAX_BATCH_EVENTS)
            selected.forEach { pending.remove(it.key) }
            selected.map { it.value }
        }

        val context = applicationContext
        if (context == null || !ConsentStore(context).isProductAnalyticsEnabled.first()) return

        val response = runCatching { api.send(TelemetryEnvelope(batch)) }.getOrNull()
        if (response == null || response.code() >= 500) {
            mutex.withLock {
                batch.forEach { event ->
                    val key = eventKey(event)
                    val current = pending[key]
                    pending[key] = event.copy(
                        count = ((current?.count ?: 0) + event.count)
                            .coerceAtMost(MAX_COUNT_PER_EVENT)
                    )
                }
                if (flushJob == null) {
                    flushJob = scope.launch {
                        delay(FLUSH_DELAY_MS * 3)
                        flush()
                    }
                }
            }
        }
    }

    private fun eventKey(event: TelemetryEvent): String {
        val sortedDimensions = TreeMap(event.dimensions)
        return buildString {
            append(event.name)
            sortedDimensions.forEach { (key, value) ->
                append('|').append(key).append('=').append(value)
            }
        }
    }
}

private class TelemetryMilestoneStore(private val context: Context) {
    private val mutex = Mutex()

    suspend fun markIfFirst(milestone: TelemetryMilestone): Boolean = mutex.withLock {
        val key = booleanPreferencesKey("sent_${milestone.storageKey}")
        var first = false
        context.telemetryMilestoneDataStore.edit { preferences ->
            if (preferences[key] != true) {
                preferences[key] = true
                first = true
            }
        }
        first
    }

    suspend fun clear() {
        context.telemetryMilestoneDataStore.edit { it.clear() }
    }
}
