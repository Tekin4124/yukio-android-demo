package com.example.test.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.notificationHealthSetupDataStore by
    preferencesDataStore(name = "notification_health_setup")

/**
 * Makes every installed v38 user see the new reliability checklist once.
 * A future materially different checklist can bump [CURRENT_SETUP_VERSION].
 */
class NotificationHealthSetupStore(context: Context) {
    private val appContext = context.applicationContext

    val isCurrentSetupComplete: Flow<Boolean> =
        appContext.notificationHealthSetupDataStore.data.map { preferences ->
            (preferences[KEY_COMPLETED_VERSION] ?: 0) >= CURRENT_SETUP_VERSION
        }

    suspend fun markCurrentSetupComplete() {
        appContext.notificationHealthSetupDataStore.edit { preferences ->
            preferences[KEY_COMPLETED_VERSION] = CURRENT_SETUP_VERSION
        }
    }

    suspend fun clear() {
        appContext.notificationHealthSetupDataStore.edit { it.clear() }
    }

    private companion object {
        const val CURRENT_SETUP_VERSION = 38
        val KEY_COMPLETED_VERSION = intPreferencesKey("completed_version")
    }
}
