package com.example.test.update

internal data class InAppUpdatePromptRecord(
    val versionCode: Int,
    val promptedAtMillis: Long
)

/**
 * Avoids showing the Google Play update consent dialog on every app resume
 * after the user has postponed the same version.
 */
internal object InAppUpdatePromptPolicy {
    internal const val REMINDER_INTERVAL_MILLIS = 24L * 60L * 60L * 1_000L

    fun shouldPrompt(
        availableVersionCode: Int,
        previous: InAppUpdatePromptRecord?,
        nowMillis: Long
    ): Boolean {
        if (availableVersionCode <= 0) return false
        if (previous == null || previous.versionCode != availableVersionCode) return true

        val elapsed = nowMillis - previous.promptedAtMillis
        return elapsed >= REMINDER_INTERVAL_MILLIS
    }
}
