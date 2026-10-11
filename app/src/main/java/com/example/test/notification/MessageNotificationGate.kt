package com.example.test.notification

/**
 * A parser response may contain several independent offers, but one WhatsApp
 * source message must produce at most one Android notification.
 *
 * A failed/non-matching attempt does not close the gate, so later offers still
 * get a chance to match the user's profile.
 */
internal class MessageNotificationGate {
    private var notificationSent = false

    suspend fun tryNotify(attempt: suspend () -> Boolean): Boolean {
        if (notificationSent) return false
        val notified = attempt()
        if (notified) notificationSent = true
        return notified
    }
}
