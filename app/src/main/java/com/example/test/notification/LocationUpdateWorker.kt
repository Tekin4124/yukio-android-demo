package com.example.test.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Upgrade-safe tombstone for the beta8 periodic location worker.
 *
 * Beta9 removes location permissions and cancels the unique work from
 * MainActivity. Keeping a no-op implementation also makes an already queued
 * WorkManager row complete safely if it runs before cancellation.
 */
class LocationUpdateWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = Result.success()
}
