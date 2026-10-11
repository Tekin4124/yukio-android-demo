package com.example.test.notification

/**
 * Android/WhatsApp does not expose an explicit "truncated" bit. A trailing
 * ellipsis is therefore recorded only as a probable truncation signal.
 * Message text never leaves this helper or enters telemetry.
 */
internal object NotificationPayloadQuality {
    fun isProbablyTruncated(text: String): Boolean {
        val trimmed = text.trimEnd()
        return trimmed.endsWith('\u2026') || trimmed.endsWith("...")
    }
}
