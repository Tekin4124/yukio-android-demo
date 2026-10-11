package com.example.test.data

import com.example.test.data.local.LoadOfferEntity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FirstRunTestOfferTest {
    @Test
    fun `predicate accepts only the narrow local demo signature`() {
        assertTrue(testOffer().isFirstRunTestOffer())
        assertTrue(
            testOffer(
                sourceSender = FirstRunTestOfferStore.LEGACY_TEST_SOURCE_SENDER
            ).isFirstRunTestOffer()
        )

        // A real WhatsApp group is never considered a demo merely because it
        // happens to use the same visible group title.
        assertFalse(
            testOffer(
                sourceSender = "GİZLİ",
                rawMessage = "İstanbul → Ankara 10 ton",
                notes = "Gerçek ilan"
            ).isFirstRunTestOffer()
        )
        assertFalse(testOffer(phone = "05320000000").isFirstRunTestOffer())
        assertFalse(testOffer(rawMessage = "TEST DIŞI İLAN #1").isFirstRunTestOffer())
        assertFalse(testOffer(notes = "Gerçek ilan").isFirstRunTestOffer())
    }

    @Test
    fun `old active notification cannot remove demos seeded by a later reset`() {
        val oldGenerationSeededAt = 1_000L
        val notificationPostedAt = 2_000L
        val resetGenerationSeededAt = 3_000L

        assertTrue(
            shouldRemoveFirstRunTestOffers(
                seededAtMillis = oldGenerationSeededAt,
                receivedAtMillis = notificationPostedAt
            )
        )
        assertFalse(
            shouldRemoveFirstRunTestOffers(
                seededAtMillis = resetGenerationSeededAt,
                receivedAtMillis = notificationPostedAt
            )
        )
        assertFalse(
            shouldRemoveFirstRunTestOffers(
                seededAtMillis = resetGenerationSeededAt,
                receivedAtMillis = resetGenerationSeededAt
            )
        )
        assertTrue(
            shouldRemoveFirstRunTestOffers(
                seededAtMillis = resetGenerationSeededAt,
                receivedAtMillis = resetGenerationSeededAt + 1L
            )
        )
        assertFalse(
            shouldRemoveFirstRunTestOffers(
                seededAtMillis = null,
                receivedAtMillis = notificationPostedAt
            )
        )
    }

    @Test
    fun `real message removal cannot interleave halfway through seed`() = runBlocking {
        val gate = FirstRunTestOfferOperationGate()
        val seedEntered = CompletableDeferred<Unit>()
        val finishSeed = CompletableDeferred<Unit>()
        val removalAttempted = CompletableDeferred<Unit>()
        var seededAtMillis: Long? = null
        var demoCount = 0

        val seedJob = launch {
            gate.withLock {
                seededAtMillis = 1_000L
                seedEntered.complete(Unit)
                finishSeed.await()
                demoCount = 5
            }
        }
        seedEntered.await()

        val removalJob = launch {
            removalAttempted.complete(Unit)
            gate.withLock {
                if (shouldRemoveFirstRunTestOffers(seededAtMillis, 2_000L)) {
                    demoCount = 0
                }
            }
        }
        removalAttempted.await()
        yield()

        // The removal coroutine reached the gate but cannot perform the delete
        // while the five-row seed operation owns it.
        assertFalse(removalJob.isCompleted)
        finishSeed.complete(Unit)
        joinAll(seedJob, removalJob)
        assertTrue(demoCount == 0)
    }

    private fun testOffer(
        sourceSender: String = FirstRunTestOfferStore.TEST_SOURCE_SENDER,
        rawMessage: String = "TEST İLANI #1 | KOCAELİ → ANKARA",
        notes: String? = "Test ilanı #1 · Gerçek iletişim bilgisi içermez.",
        phone: String = ""
    ) = LoadOfferEntity(
        fromCity = "KOCAELİ",
        fromDistrict = null,
        toCity = "ANKARA",
        toDistrict = null,
        tonnageTon = 22,
        vehicleType = "TIR TENTE",
        priceTry = 38_000,
        phone = phone,
        notes = notes,
        sourceGroup = FirstRunTestOfferStore.TEST_SOURCE_GROUP,
        sourceSender = sourceSender,
        rawMessage = rawMessage,
        createdAt = 1_000L
    )
}
