package com.example.test.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.test.BuildConfig
import com.example.test.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AuditExportReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!BuildConfig.DEBUG) return
        if (intent.action != ACTION_EXPORT) return

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val appContext = context.applicationContext
                val database = AppDatabase.getInstance(appContext)
                val file = AuditExporter.export(appContext, database)
                Log.i(TAG, "PATH=${file.absolutePath}")
            } catch (error: Exception) {
                Log.e(TAG, "FAILED", error)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_EXPORT = "com.yukkap.app.DEBUG_AUDIT_EXPORT"
        private const val TAG = "AUDIT_EXPORT"
    }
}
