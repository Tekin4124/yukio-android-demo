package com.example.test.data.remote

import android.content.Context
import com.example.test.ai.InteractionType
import com.example.test.data.ConsentStore
import com.example.test.data.local.AppDatabase
import com.example.test.data.local.ParserSampleEventDao
import com.example.test.data.local.ParserSampleEventEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

/**
 * Kimliksiz parser örneği etkileşimlerinin best-effort gönderimi.
 *
 * Kurallar:
 * - Opt-in kapalıysa hiçbir olay ağa çıkmaz ve kuyruğa yazılmaz (fail-closed).
 * - sampleId yoksa (backend örneği kabul etmediyse) hiçbir şey yapılmaz.
 * - OPENED sample başına bir kez gönderilir; feedback'te son tıklama kazanır
 *   (sunucu da latest-wins upsert yapar, her tıklama yeni satır üretmez).
 * - Ağ hatası kullanıcı akışını bozmaz: satır PENDING kalır, en fazla
 *   MAX_ATTEMPTS kez denenir; kalıcı red (4xx) satırı siler.
 * - Kuyruk işlemleri tek Mutex altında sıralanır; eşzamanlı tıklamalarda son
 *   geri bildirim deterministik olarak kazanır.
 * - Kuyruk tablosu boyut sınırlıdır (MAX_ROWS), disk büyüyemez.
 */
class ParserSampleFeedbackDispatcher(
    private val isOptedIn: suspend () -> Boolean,
    private val dao: ParserSampleEventDao,
    private val send: suspend (ParserSampleEventRequest) -> SendOutcome,
    private val now: () -> Long = System::currentTimeMillis
) {

    enum class SendOutcome { SUCCESS, RETRYABLE, REJECTED }

    private val mutex = Mutex()

    suspend fun recordOpened(sampleId: String?) = mutex.withLock {
        val id = validSampleId(sampleId) ?: return@withLock
        if (!isOptedIn()) return@withLock
        val enqueued = dao.enqueue(
            sampleId = id,
            offerIndex = ParserSampleEventEntity.MESSAGE_LEVEL_OFFER_INDEX,
            kind = ParserSampleEventEntity.KIND_OPENED,
            eventType = "OPENED",
            now = now()
        )
        if (enqueued) flushLocked(DEFAULT_FLUSH_LIMIT)
    }

    suspend fun recordFeedback(
        sampleId: String?,
        offerIndex: Int?,
        interactionType: InteractionType
    ) = mutex.withLock {
        val id = validSampleId(sampleId) ?: return@withLock
        val eventType = when (interactionType) {
            InteractionType.PARSE_CORRECT -> "FEEDBACK_CORRECT"
            InteractionType.PARSE_WRONG -> "FEEDBACK_WRONG"
            InteractionType.PARSE_MISSING -> "FEEDBACK_MISSING"
            else -> return@withLock
        }
        if (!isOptedIn()) return@withLock
        // Yanlış/Doğru belirli ilana bağlanır; "Eksik çıkardı" mesaj/sample
        // seviyesi bir sinyaldir ve offerIndex taşımaz.
        val slotIndex = if (interactionType == InteractionType.PARSE_MISSING) {
            ParserSampleEventEntity.MESSAGE_LEVEL_OFFER_INDEX
        } else {
            offerIndex?.takeIf { it in MIN_OFFER_INDEX..MAX_OFFER_INDEX }
                ?: return@withLock
        }
        val enqueued = dao.enqueue(
            sampleId = id,
            offerIndex = slotIndex,
            kind = ParserSampleEventEntity.KIND_FEEDBACK,
            eventType = eventType,
            now = now()
        )
        if (enqueued) flushLocked(DEFAULT_FLUSH_LIMIT)
    }

    /** Tercih kapatıldığında gönderilmiş olanlar dahil yerel outbox silinir. */
    suspend fun onOptOut() = mutex.withLock {
        dao.deleteAll()
    }

    suspend fun flush(limit: Int = DEFAULT_FLUSH_LIMIT) = mutex.withLock {
        flushLocked(limit.coerceIn(1, MAX_FLUSH_LIMIT))
    }

    private suspend fun flushLocked(limit: Int) {
        if (!isOptedIn()) return
        val pending = dao.pending(limit)
        for (event in pending) {
            // Tercih gönderim sırasında kapandıysa kalan yerel izleri de sil.
            if (!isOptedIn()) {
                dao.deleteAll()
                return
            }
            val request = ParserSampleEventRequest(
                sampleId = event.sampleId,
                event = event.eventType,
                offerIndex = event.offerIndex
                    .takeIf { it != ParserSampleEventEntity.MESSAGE_LEVEL_OFFER_INDEX }
            )
            when (runCatching { send(request) }.getOrDefault(SendOutcome.RETRYABLE)) {
                SendOutcome.SUCCESS -> dao.markSent(event.id, now())
                SendOutcome.REJECTED -> dao.deleteById(event.id)
                SendOutcome.RETRYABLE -> {
                    dao.recordFailedAttempt(event.id, now())
                    // Ağ zaten sorunluysa kalanları zorlamayıp sonraki
                    // flush'a bırakmak pil/veri açısından daha doğru.
                    return
                }
            }
        }
    }

    private fun validSampleId(value: String?): String? {
        val candidate = value ?: return null
        return candidate.takeIf { SAMPLE_ID_PATTERN.matches(it) }
    }

    private companion object {
        val SAMPLE_ID_PATTERN = Regex("""^[A-Za-z0-9_-]{16,64}$""")
        const val MIN_OFFER_INDEX = 0
        const val MAX_OFFER_INDEX = 39
        const val DEFAULT_FLUSH_LIMIT = 10
        const val MAX_FLUSH_LIMIT = 50
    }
}

/** Uygulama içi tekil bağlayıcı: gerçek DAO + Retrofit + ConsentStore. */
object ParserSampleFeedback {
    private const val TAG = "ParserSampleFeedback"

    @Volatile private var dispatcher: ParserSampleFeedbackDispatcher? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun dispatcherFor(context: Context): ParserSampleFeedbackDispatcher {
        val appContext = context.applicationContext
        return dispatcher ?: synchronized(this) {
            dispatcher ?: ParserSampleFeedbackDispatcher(
                isOptedIn = { ConsentStore(appContext).isParserSamplesEnabled.first() },
                dao = AppDatabase.getInstance(appContext).parserSampleEventDao(),
                send = { request ->
                    val response = RetrofitClient.apiService.postParserSampleEvent(request)
                    when {
                        response.isSuccessful ->
                            ParserSampleFeedbackDispatcher.SendOutcome.SUCCESS
                        response.code() in 400..499 && response.code() != 429 ->
                            ParserSampleFeedbackDispatcher.SendOutcome.REJECTED
                        else -> ParserSampleFeedbackDispatcher.SendOutcome.RETRYABLE
                    }
                }
            ).also { dispatcher = it }
        }
    }

    fun recordOpened(context: Context, sampleId: String?) {
        if (sampleId.isNullOrBlank()) return
        scope.launch {
            runCatching { dispatcherFor(context).recordOpened(sampleId) }
                .onFailure { Timber.tag(TAG).d("OPENED gonderilemedi") }
        }
    }

    fun recordFeedback(
        context: Context,
        sampleId: String?,
        offerIndex: Int?,
        interactionType: InteractionType
    ) {
        if (sampleId.isNullOrBlank()) return
        scope.launch {
            runCatching {
                dispatcherFor(context).recordFeedback(sampleId, offerIndex, interactionType)
            }.onFailure { Timber.tag(TAG).d("Feedback gonderilemedi") }
        }
    }

    fun onOptOut(context: Context) {
        scope.launch {
            runCatching { dispatcherFor(context).onOptOut() }
                .onFailure { Timber.tag(TAG).d("Opt-out temizligi basarisiz") }
        }
    }

    suspend fun flushPending(context: Context) {
        runCatching { dispatcherFor(context).flush() }
            .onFailure { Timber.tag(TAG).d("Flush basarisiz") }
    }
}
