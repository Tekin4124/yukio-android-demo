package com.example.test.notification

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

internal data class NotificationHealthStatus(
    val listenerEnabled: Boolean,
    val batteryUnrestricted: Boolean,
    val appNotificationsEnabled: Boolean
) {
    val automaticChecksPassed: Boolean
        get() = listenerEnabled && batteryUnrestricted && appNotificationsEnabled
}

internal data class WhatsAppInstallation(
    val packageName: String,
    val label: String
)

internal fun readNotificationHealthStatus(context: Context): NotificationHealthStatus {
    val appContext = context.applicationContext
    val powerManager = appContext.getSystemService(PowerManager::class.java)
    val batteryUnrestricted =
        powerManager?.isIgnoringBatteryOptimizations(appContext.packageName) ?: true
    val appNotificationsEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED &&
            NotificationManagerCompat.from(appContext).areNotificationsEnabled()
    } else {
        NotificationManagerCompat.from(appContext).areNotificationsEnabled()
    }
    return NotificationHealthStatus(
        listenerEnabled = NotificationManagerCompat
            .getEnabledListenerPackages(appContext)
            .contains(appContext.packageName),
        batteryUnrestricted = batteryUnrestricted,
        appNotificationsEnabled = appNotificationsEnabled
    )
}

internal fun installedWhatsAppApps(context: Context): List<WhatsAppInstallation> {
    val packages = listOf(
        WhatsAppInstallation("com.whatsapp", "WhatsApp"),
        WhatsAppInstallation("com.whatsapp.w4b", "WhatsApp Business")
    )
    return packages.filter { installation ->
        runCatching {
            context.packageManager.getApplicationInfo(installation.packageName, 0)
        }.isSuccess
    }
}

internal fun openNotificationListenerSettings(context: Context) {
    startSettings(
        context,
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
    )
}

internal fun openBatteryOptimizationSettings(context: Context) {
    // Deliberately opens the user-controlled allowlist instead of declaring
    // REQUEST_IGNORE_BATTERY_OPTIMIZATIONS. This avoids adding a Play-reviewed
    // special permission while still taking the user to the relevant setting.
    startSettings(
        context,
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
        fallback = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}")
        )
    )
}

internal fun openOwnNotificationSettings(context: Context) {
    startSettings(
        context,
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
        fallback = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.parse("package:${context.packageName}")
        )
    )
}

internal fun openWhatsAppNotificationSettings(context: Context, packageName: String) {
    startSettings(
        context,
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
        fallback = context.packageManager.getLaunchIntentForPackage(packageName)
    )
}

internal fun launchWhatsApp(context: Context, packageName: String) {
    context.packageManager.getLaunchIntentForPackage(packageName)?.let { intent ->
        startSettings(context, intent)
    }
}

private fun startSettings(context: Context, intent: Intent, fallback: Intent? = null) {
    val prepared = intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(prepared) }
        .onFailure {
            fallback?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let { fallbackIntent ->
                runCatching { context.startActivity(fallbackIntent) }
            }
        }
}
