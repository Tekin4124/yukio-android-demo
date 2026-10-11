package com.example.test

import android.app.Application
import com.example.test.billing.EntitlementRuntime
import com.example.test.data.ConsentStore
import com.example.test.data.local.DebugSeedData
import com.example.test.data.local.FirstRunTestOfferData
import com.example.test.notification.MatchNotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber

class YukBulucuApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Only enable verbose logging in debug builds. DebugTree forwards every
        // Timber.d() to logcat, which on a release device leaks notification
        // payloads, phone numbers and parser bodies to anyone with READ_LOGS.
        if (BuildConfig.DEBUG && Timber.forest().isEmpty()) {
            Timber.plant(Timber.DebugTree())
        }

        // Araca uygun yük bildirimleri için kanal kayıt — idempotent.
        MatchNotificationHelper.ensureChannel(this)

        // Keep local entitlement StateFlows available immediately, but do not contact Play or the
        // backend before the privacy notice has been acknowledged.
        EntitlementRuntime.initialize(this)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            if (ConsentStore(this@YukBulucuApp).isAccepted.first()) {
                EntitlementRuntime.activateAfterNotice()
            }
        }

        if (BuildConfig.DEBUG) {
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                DebugSeedData.seedIfNeeded(this@YukBulucuApp)
            }
        } else {
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                FirstRunTestOfferData.seedIfNeeded(this@YukBulucuApp)
            }
        }
    }
}
