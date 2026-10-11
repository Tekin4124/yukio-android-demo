package com.example.test.data.remote

import com.example.test.ai.InteractionType
import com.example.test.data.local.ParserSampleEventDao
import com.example.test.data.local.ParserSampleEventEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Saf JVM testi: gerçek Room yerine bellek içi sahte DAO ve sahte gönderici.
 * Consent kapalı/açık ağ davranışı, OPENED idempotency, latest-wins feedback
 * ve sınırlı retry burada doğrulanır.
 */
class ParserSampleFeedbackDispatcherTest {

    private class FakeDao : ParserSampleEventDao {
        val rows = mutableListOf<ParserSampleEventEntity>()
        private var nextId = 1L

        override suspend fun insertIgnore(event: ParserSampleEventEntity): Long {
            if (rows.any {
                    it.sampleId == event.sampleId &&
                        it.offerIndex == event.offerIndex &&
                        it.kind == event.kind
                }
            ) {
                return -1L
            }
            val withId = event.copy(id = nextId++)
            rows.add(withId)
            return withId.id
        }

        override suspend fun findSlot(
            sampleId: String,
            offerIndex: Int,
            kind: String
        ): ParserSampleEventEntity? = rows.firstOrNull {
            it.sampleId == sampleId && it.offerIndex == offerIndex && it.kind == kind
        }

        override suspend fun updateSlot(id: Long, eventType: String, state: String, now: Long) {
            replace(id) { it.copy(eventType = eventType, state = state, attempts = 0, updatedAt = now) }
        }

        override suspend fun pending(
            limit: Int,
            maxAttempts: Int,
            state: String
        ): List<ParserSampleEventEntity> = rows
            .filter { it.state == state && it.attempts < maxAttempts }
            .sortedBy { it.updatedAt }
            .take(limit)

        override suspend fun markSent(id: Long, now: Long) {
            replace(id) { it.copy(state = ParserSampleEventEntity.STATE_SENT, updatedAt = now) }
        }

        override suspend fun recordFailedAttempt(id: Long, now: Long) {
            replace(id) { it.copy(attempts = it.attempts + 1, updatedAt = now) }
        }

        override suspend fun deleteById(id: Long) {
            rows.removeAll { it.id == id }
        }

        override suspend fun deleteAllPending(): Int {
            val before = rows.size
            rows.removeAll { it.state == ParserSampleEventEntity.STATE_PENDING }
            return before - rows.size
        }

        override suspend fun deleteAll() {
            rows.clear()
        }

        override suspend fun countAll(): Int = rows.size

        override suspend fun deleteOldest(count: Int) {
            rows.sortedWith(
                compareByDescending<ParserSampleEventEntity> {
                    it.state == ParserSampleEventEntity.STATE_SENT
                }.thenBy { it.updatedAt }
            ).take(count).forEach { victim -> rows.removeAll { it.id == victim.id } }
        }

        private fun replace(id: Long, transform: (ParserSampleEventEntity) -> ParserSampleEventEntity) {
            val index = rows.indexOfFirst { it.id == id }
            if (index >= 0) rows[index] = transform(rows[index])
        }
    }

    private class Harness(
        optedIn: Boolean = true,
        var outcome: ParserSampleFeedbackDispatcher.SendOutcome =
            ParserSampleFeedbackDispatcher.SendOutcome.SUCCESS
    ) {
        var optedIn = optedIn
        val dao = FakeDao()
        val sentRequests = mutableListOf<ParserSampleEventRequest>()
        var time = 1_000L
        val dispatcher = ParserSampleFeedbackDispatcher(
            isOptedIn = { optedIn },
            dao = dao,
            send = { request ->
                sentRequests.add(request)
                outcome
            },
            now = { time }
        )
    }

    @Test
    fun `consent kapaliyken hicbir olay kuyruklanmaz ve aga cikmaz`() = runBlocking {
        val harness = Harness(optedIn = false)
        harness.dispatcher.recordOpened("a".repeat(22))
        harness.dispatcher.recordFeedback("a".repeat(22), 0, InteractionType.PARSE_WRONG)
        harness.dispatcher.flush()
        assertEquals(0, harness.dao.rows.size)
        assertEquals(0, harness.sentRequests.size)
    }

    @Test
    fun `sampleId yoksa fail-closed hicbir sey yapilmaz`() = runBlocking {
        val harness = Harness()
        harness.dispatcher.recordOpened(null)
        harness.dispatcher.recordOpened("")
        harness.dispatcher.recordOpened("kısa")
        harness.dispatcher.recordOpened("!".repeat(22))
        harness.dispatcher.recordOpened("a".repeat(65))
        harness.dispatcher.recordFeedback(null, 0, InteractionType.PARSE_CORRECT)
        assertEquals(0, harness.dao.rows.size)
        assertEquals(0, harness.sentRequests.size)
    }

    @Test
    fun `offer index sinir disindaysa feedback kuyruklanmaz`() = runBlocking {
        val harness = Harness()
        val sampleId = "a".repeat(22)
        harness.dispatcher.recordFeedback(sampleId, null, InteractionType.PARSE_CORRECT)
        harness.dispatcher.recordFeedback(sampleId, -1, InteractionType.PARSE_WRONG)
        harness.dispatcher.recordFeedback(sampleId, 40, InteractionType.PARSE_CORRECT)
        assertEquals(0, harness.dao.rows.size)
        assertEquals(0, harness.sentRequests.size)
    }

    @Test
    fun `OPENED sample basina bir kez gonderilir`() = runBlocking {
        val harness = Harness()
        val sampleId = "a".repeat(22)
        harness.dispatcher.recordOpened(sampleId)
        harness.dispatcher.recordOpened(sampleId)
        harness.dispatcher.recordOpened(sampleId)
        assertEquals(1, harness.dao.rows.size)
        assertEquals(1, harness.sentRequests.size)
        assertEquals("OPENED", harness.sentRequests[0].event)
        assertNull(harness.sentRequests[0].offerIndex)
        assertEquals(ParserSampleEventEntity.STATE_SENT, harness.dao.rows[0].state)
    }

    @Test
    fun `tekrarlanan feedbackte son tiklama kazanir tek satir kalir`() = runBlocking {
        val harness = Harness()
        val sampleId = "a".repeat(22)
        harness.dispatcher.recordFeedback(sampleId, 1, InteractionType.PARSE_WRONG)
        harness.dispatcher.recordFeedback(sampleId, 1, InteractionType.PARSE_CORRECT)
        val feedbackRows = harness.dao.rows.filter { it.kind == ParserSampleEventEntity.KIND_FEEDBACK }
        assertEquals(1, feedbackRows.size)
        assertEquals("FEEDBACK_CORRECT", feedbackRows[0].eventType)
        assertEquals("FEEDBACK_WRONG", harness.sentRequests[0].event)
        assertEquals("FEEDBACK_CORRECT", harness.sentRequests[1].event)
        assertEquals(1, harness.sentRequests[1].offerIndex)
    }

    @Test
    fun `es zamanli feedback tek slotta kalir ve son kesin tiklama kazanir`() = runBlocking {
        val harness = Harness(outcome = ParserSampleFeedbackDispatcher.SendOutcome.RETRYABLE)
        val sampleId = "a".repeat(22)
        (0 until 30).map { index ->
            async(Dispatchers.Default) {
                harness.dispatcher.recordFeedback(
                    sampleId,
                    1,
                    if (index % 2 == 0) InteractionType.PARSE_WRONG
                    else InteractionType.PARSE_CORRECT
                )
            }
        }.awaitAll()

        harness.dispatcher.recordFeedback(sampleId, 1, InteractionType.PARSE_CORRECT)
        val feedbackRows = harness.dao.rows.filter {
            it.kind == ParserSampleEventEntity.KIND_FEEDBACK
        }
        assertEquals(1, feedbackRows.size)
        assertEquals("FEEDBACK_CORRECT", feedbackRows.single().eventType)
    }

    @Test
    fun `eksik cikardi mesaj seviyesinde offerIndex olmadan gider`() = runBlocking {
        val harness = Harness()
        harness.dispatcher.recordFeedback("a".repeat(22), 3, InteractionType.PARSE_MISSING)
        assertEquals(1, harness.sentRequests.size)
        assertEquals("FEEDBACK_MISSING", harness.sentRequests[0].event)
        assertNull(harness.sentRequests[0].offerIndex)
        assertEquals(
            ParserSampleEventEntity.MESSAGE_LEVEL_OFFER_INDEX,
            harness.dao.rows[0].offerIndex
        )
    }

    @Test
    fun `ag hatasinda satir PENDING kalir ve deneme sinirina kadar tekrarlanir`() = runBlocking {
        val harness = Harness(outcome = ParserSampleFeedbackDispatcher.SendOutcome.RETRYABLE)
        harness.dispatcher.recordOpened("a".repeat(22))
        assertEquals(ParserSampleEventEntity.STATE_PENDING, harness.dao.rows[0].state)
        assertEquals(1, harness.dao.rows[0].attempts)

        repeat(10) { harness.dispatcher.flush() }
        // MAX_ATTEMPTS'e ulaşınca pending() satırı artık döndürmez; ağ sonsuz
        // kez yoklanmaz ve tablo satırı sınırda kalır.
        assertEquals(ParserSampleEventEntity.MAX_ATTEMPTS, harness.dao.rows[0].attempts)
        assertEquals(ParserSampleEventEntity.MAX_ATTEMPTS, harness.sentRequests.size)

        // Deneme sınırı aşıldıktan sonra ağ düzelse bile satır yeniden
        // gönderilmez; bu bilinçli üst sınır davranışıdır.
        harness.outcome = ParserSampleFeedbackDispatcher.SendOutcome.SUCCESS
        harness.dispatcher.flush()
        assertEquals(ParserSampleEventEntity.MAX_ATTEMPTS, harness.sentRequests.size)
    }

    @Test
    fun `sunucu reddi satiri siler tekrar denenmez`() = runBlocking {
        val harness = Harness(outcome = ParserSampleFeedbackDispatcher.SendOutcome.REJECTED)
        harness.dispatcher.recordOpened("a".repeat(22))
        assertEquals(0, harness.dao.rows.size)
        harness.dispatcher.flush()
        assertEquals(1, harness.sentRequests.size)
    }

    @Test
    fun `opt-out bekleyen satirlari aninda temizler`() = runBlocking {
        val harness = Harness(outcome = ParserSampleFeedbackDispatcher.SendOutcome.RETRYABLE)
        harness.dispatcher.recordOpened("a".repeat(22))
        harness.dispatcher.recordFeedback("b".repeat(22), 0, InteractionType.PARSE_WRONG)
        assertTrue(harness.dao.rows.isNotEmpty())

        harness.optedIn = false
        harness.dispatcher.onOptOut()
        assertEquals(0, harness.dao.rows.size)
        val sentBefore = harness.sentRequests.size
        harness.dispatcher.flush()
        assertEquals(sentBefore, harness.sentRequests.size)
    }

    @Test
    fun `gonderici exception firlatirsa akis bozulmaz retryable sayilir`() = runBlocking {
        val dao = FakeDao()
        var calls = 0
        val dispatcher = ParserSampleFeedbackDispatcher(
            isOptedIn = { true },
            dao = dao,
            send = { calls++; throw RuntimeException("ağ koptu") },
            now = { 5L }
        )
        dispatcher.recordOpened("a".repeat(22))
        assertEquals(1, calls)
        assertEquals(1, dao.rows[0].attempts)
        assertEquals(ParserSampleEventEntity.STATE_PENDING, dao.rows[0].state)
    }
}
