package com.example.test.notification

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageNotificationGateTest {
    @Test
    fun keepsCheckingAfterMissAndStopsAfterFirstSuccessfulNotification() = runBlocking {
        val gate = MessageNotificationGate()
        var attempts = 0

        assertFalse(gate.tryNotify {
            attempts++
            false
        })
        assertTrue(gate.tryNotify {
            attempts++
            true
        })
        assertFalse(gate.tryNotify {
            attempts++
            true
        })

        assertEquals(2, attempts)
    }
}
