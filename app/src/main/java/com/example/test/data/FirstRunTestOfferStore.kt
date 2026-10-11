package com.example.test.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.test.data.local.LoadOfferDao
import com.example.test.data.local.LoadOfferEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private val Context.firstRunTestOfferDataStore by
    preferencesDataStore(name = "first_run_test_offers")

/**
 * Serializes demo seeding and removal inside the app process. Both the Application
 * seed job and NotificationListenerService use this same gate, so a real message
 * cannot delete the demos halfway through a concurrent five-row seed.
 */
internal class FirstRunTestOfferOperationGate {
    private val mutex = Mutex()

    suspend fun <T> withLock(block: suspend () -> T): T = mutex.withLock { block() }
}

internal val firstRunTestOfferOperationGate = FirstRunTestOfferOperationGate()

/**
 * Controls the five clearly-labelled offers shown on a fresh install. They are
 * never re-created after a real logistics notification has been received.
 */
class FirstRunTestOfferStore(context: Context) {
    private val appContext = context.applicationContext

    internal suspend fun shouldSeed(): Boolean =
        appContext.firstRunTestOfferDataStore.data.first().let { preferences ->
            !preferences[KEY_REAL_OFFER_RECEIVED].orFalse() &&
                !preferences[KEY_TEST_OFFERS_SHOWN].orFalse()
        }

    internal suspend fun ensureGenerationStartedAt(): Long {
        val existing = appContext.firstRunTestOfferDataStore.data
            .first()[KEY_TEST_OFFERS_SEEDED_AT]
        if (existing != null) return existing

        val fallback = runCatching {
            appContext.packageManager
                .getPackageInfo(appContext.packageName, 0)
                .firstInstallTime
        }.getOrNull()
            ?.takeIf { it > 0L }
            ?: System.currentTimeMillis()
        appContext.firstRunTestOfferDataStore.edit { preferences ->
            if (preferences[KEY_TEST_OFFERS_SEEDED_AT] == null) {
                preferences[KEY_TEST_OFFERS_SEEDED_AT] = fallback
            }
        }
        return appContext.firstRunTestOfferDataStore.data
            .first()[KEY_TEST_OFFERS_SEEDED_AT] ?: fallback
    }

    internal suspend fun markShown() {
        appContext.firstRunTestOfferDataStore.edit { preferences ->
            preferences[KEY_TEST_OFFERS_SHOWN] = true
        }
    }

    suspend fun clear() {
        firstRunTestOfferOperationGate.withLock {
            appContext.firstRunTestOfferDataStore.edit { preferences ->
                preferences.clear()
                // Start the new generation before Room is cleared/seeded. A
                // real notification racing immediately after reset can then
                // suppress the demos, while an older replay cannot.
                preferences[KEY_TEST_OFFERS_SEEDED_AT] = System.currentTimeMillis()
            }
        }
    }

    /**
     * Removes demos only for a real notification posted after the current demo
     * generation was seeded. NotificationListenerService replays existing active
     * notifications when it reconnects; their old postTime must not erase a new
     * generation created by a reset.
     */
    suspend fun removeAfterFirstRealOffer(
        loadOfferDao: LoadOfferDao,
        receivedAtMillis: Long
    ): Boolean = firstRunTestOfferOperationGate.withLock {
        // This also closes the remove-before-seed race on a fresh install:
        // package firstInstallTime is the fallback generation boundary.
        val seededAtMillis = ensureGenerationStartedAt()
        if (!shouldRemoveFirstRunTestOffers(seededAtMillis, receivedAtMillis)) {
            return@withLock false
        }

        loadOfferDao.deleteFirstRunTestOffers(
            sourceGroup = TEST_SOURCE_GROUP,
            sourceSender = TEST_SOURCE_SENDER,
            legacySourceSender = LEGACY_TEST_SOURCE_SENDER,
            rawPrefix = TEST_RAW_PREFIX,
            notesPrefix = TEST_NOTES_PREFIX
        )
        appContext.firstRunTestOfferDataStore.edit { mutablePreferences ->
            mutablePreferences[KEY_REAL_OFFER_RECEIVED] = true
        }
        true
    }

    private fun Boolean?.orFalse() = this ?: false

    companion object {
        const val TEST_SOURCE_GROUP = "Yükio Test İlanları"
        const val TEST_SOURCE_SENDER = "__YUKIO_FIRST_RUN_TEST_OFFER_V1__"
        const val LEGACY_TEST_SOURCE_SENDER = "Yükio"
        const val TEST_RAW_PREFIX = "TEST İLANI #"
        const val TEST_NOTES_PREFIX = "Test ilanı #"
        val KEY_REAL_OFFER_RECEIVED = booleanPreferencesKey("real_offer_received")
        val KEY_TEST_OFFERS_SHOWN = booleanPreferencesKey("test_offers_shown")
        val KEY_TEST_OFFERS_SEEDED_AT = longPreferencesKey("test_offers_seeded_at")
    }
}

internal fun shouldRemoveFirstRunTestOffers(
    seededAtMillis: Long?,
    receivedAtMillis: Long
): Boolean = seededAtMillis != null &&
    seededAtMillis >= 0L &&
    receivedAtMillis > seededAtMillis

fun LoadOfferEntity.isFirstRunTestOffer(): Boolean =
    sourceGroup == FirstRunTestOfferStore.TEST_SOURCE_GROUP &&
        (
            sourceSender == FirstRunTestOfferStore.TEST_SOURCE_SENDER ||
                sourceSender == FirstRunTestOfferStore.LEGACY_TEST_SOURCE_SENDER
            ) &&
        phone.isEmpty() &&
        rawMessage.startsWith(FirstRunTestOfferStore.TEST_RAW_PREFIX) &&
        notes.orEmpty().startsWith(FirstRunTestOfferStore.TEST_NOTES_PREFIX)
