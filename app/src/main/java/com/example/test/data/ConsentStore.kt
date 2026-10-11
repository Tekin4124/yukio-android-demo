package com.example.test.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.consentDataStore by preferencesDataStore(name = "consent")
private val KEY_LEGACY_CONSENT_ACCEPTED = booleanPreferencesKey("consent_accepted_v1")
private val KEY_PRIVACY_NOTICE_ACKNOWLEDGED = booleanPreferencesKey("privacy_notice_acknowledged_v2")
private val KEY_PRODUCT_ANALYTICS_OPT_IN = booleanPreferencesKey("product_analytics_opt_in_v1")

// İçeriksiz ürün analitiğinden BAĞIMSIZ, varsayılan kapalı ayrı tercih:
// kimliksiz parser örneklerinin paylaşımı. Fail-closed: anahtar yoksa kapalı.
private val KEY_PARSER_SAMPLES_OPT_IN = booleanPreferencesKey("anonymous_parser_samples_opt_in_v1")

class ConsentStore(private val context: Context) {
    /**
     * This flag records that the disclosure was shown; it is not an explicit-consent record.
     * The v2 key intentionally makes existing beta users see the materially revised notice once.
     */
    val isAccepted: Flow<Boolean> =
        context.consentDataStore.data.map { it[KEY_PRIVACY_NOTICE_ACKNOWLEDGED] ?: false }

    val isProductAnalyticsEnabled: Flow<Boolean> =
        context.consentDataStore.data.map { it[KEY_PRODUCT_ANALYTICS_OPT_IN] ?: false }

    val isParserSamplesEnabled: Flow<Boolean> =
        context.consentDataStore.data.map { it[KEY_PARSER_SAMPLES_OPT_IN] ?: false }

    suspend fun acknowledgeNotice(
        productAnalyticsEnabled: Boolean = false,
        parserSamplesEnabled: Boolean = false
    ) {
        context.consentDataStore.edit { prefs ->
            prefs[KEY_PRIVACY_NOTICE_ACKNOWLEDGED] = true
            prefs[KEY_PRODUCT_ANALYTICS_OPT_IN] = false
            prefs[KEY_PARSER_SAMPLES_OPT_IN] = false
        }
    }

    suspend fun setProductAnalyticsEnabled(enabled: Boolean) {
        context.consentDataStore.edit { prefs ->
            prefs[KEY_PRODUCT_ANALYTICS_OPT_IN] = false
        }
    }

    suspend fun setParserSamplesEnabled(enabled: Boolean) {
        context.consentDataStore.edit { prefs ->
            prefs[KEY_PARSER_SAMPLES_OPT_IN] = false
        }
    }

    /**
     * Local data reset disables every optional sharing choice but retains the
     * local record that the mandatory privacy notice was shown. The latter is
     * not consent and retaining it avoids leaving an already-active runtime in
     * a contradictory "notice not acknowledged" state until process restart.
     */
    suspend fun resetOptionalPreferences() {
        context.consentDataStore.edit { prefs ->
            prefs[KEY_PRODUCT_ANALYTICS_OPT_IN] = false
            prefs[KEY_PARSER_SAMPLES_OPT_IN] = false
            prefs[KEY_LEGACY_CONSENT_ACCEPTED] = false
        }
    }

    suspend fun revoke() {
        context.consentDataStore.edit { prefs ->
            prefs[KEY_PRIVACY_NOTICE_ACKNOWLEDGED] = false
            prefs[KEY_PRODUCT_ANALYTICS_OPT_IN] = false
            prefs[KEY_PARSER_SAMPLES_OPT_IN] = false
            prefs[KEY_LEGACY_CONSENT_ACCEPTED] = false
        }
    }
}
