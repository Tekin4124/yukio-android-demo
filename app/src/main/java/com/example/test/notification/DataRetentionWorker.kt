package com.example.test.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.test.data.local.AppDatabase
import timber.log.Timber

/**
 * Günde bir çalışıp süresi dolmuş kayıtları siler. Bunlar olmadan
 * parse_debug_logs (her WhatsApp bildirimi için bir satır), load_offers ve
 * user_interactions tabloları sınırsız büyüyor; aylar içinde uygulama
 * yavaşlıyor ve gereksiz kişisel veri ayak izi birikiyordu.
 *
 * Saklama süreleri:
 *  - İlanlar 30 gün: ilan tazeliği saat mertebesinde, 30 gün fazlasıyla yeter.
 *  - Parse logları 14 gün: teşhis için son iki hafta yeterli.
 *  - Kullanıcı etkileşimleri 90 gün: skor motoru zaten en yeni 50 kaydı kullanıyor.
 */
class DataRetentionWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val database = AppDatabase.getInstance(applicationContext)
        val now = System.currentTimeMillis()

        val deletedLoads = database.loadOfferDao()
            .deleteOlderThan(now - LOAD_RETENTION_DAYS * DAY_MS)
        val deletedLogs = database.parseDebugLogDao()
            .deleteOlderThan(now - LOG_RETENTION_DAYS * DAY_MS)
        database.userInteractionDao()
            .deleteOlderThan(now - INTERACTION_RETENTION_DAYS * DAY_MS)

        Timber.tag(TAG).d(
            "Temizlik: %d ilan, %d parse logu silindi",
            deletedLoads, deletedLogs
        )
        return Result.success()
    }

    private companion object {
        const val TAG = "DataRetention"
        const val DAY_MS = 24L * 60L * 60L * 1000L
        const val LOAD_RETENTION_DAYS = 30L
        const val LOG_RETENTION_DAYS = 14L
        const val INTERACTION_RETENTION_DAYS = 90L
    }
}
