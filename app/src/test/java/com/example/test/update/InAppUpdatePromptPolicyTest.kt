package com.example.test.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InAppUpdatePromptPolicyTest {

    @Test
    fun `new available version is prompted immediately`() {
        val previous = InAppUpdatePromptRecord(
            versionCode = 42,
            promptedAtMillis = 10_000L
        )

        assertTrue(
            InAppUpdatePromptPolicy.shouldPrompt(
                availableVersionCode = 43,
                previous = previous,
                nowMillis = 11_000L
            )
        )
    }

    @Test
    fun `same version is not prompted again inside reminder interval`() {
        val previous = InAppUpdatePromptRecord(
            versionCode = 43,
            promptedAtMillis = 10_000L
        )

        assertFalse(
            InAppUpdatePromptPolicy.shouldPrompt(
                availableVersionCode = 43,
                previous = previous,
                nowMillis = 10_000L +
                    InAppUpdatePromptPolicy.REMINDER_INTERVAL_MILLIS - 1L
            )
        )
    }

    @Test
    fun `same version is prompted after reminder interval`() {
        val previous = InAppUpdatePromptRecord(
            versionCode = 43,
            promptedAtMillis = 10_000L
        )

        assertTrue(
            InAppUpdatePromptPolicy.shouldPrompt(
                availableVersionCode = 43,
                previous = previous,
                nowMillis = 10_000L +
                    InAppUpdatePromptPolicy.REMINDER_INTERVAL_MILLIS
            )
        )
    }

    @Test
    fun `invalid available version is never prompted`() {
        assertFalse(
            InAppUpdatePromptPolicy.shouldPrompt(
                availableVersionCode = 0,
                previous = null,
                nowMillis = 10_000L
            )
        )
    }
}
