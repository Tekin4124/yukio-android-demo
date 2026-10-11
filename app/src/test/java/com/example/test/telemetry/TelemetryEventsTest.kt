package com.example.test.telemetry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelemetryEventsTest {
    @Test
    fun bucketBoundariesAreStable() {
        assertEquals("lt_100", TelemetryEvents.latencyBucket(99))
        assertEquals("100_499", TelemetryEvents.latencyBucket(100))
        assertEquals("500_1499", TelemetryEvents.latencyBucket(500))
        assertEquals("1500_4999", TelemetryEvents.latencyBucket(1_500))
        assertEquals("gte_5000", TelemetryEvents.latencyBucket(5_000))

        assertEquals("0", TelemetryEvents.outputBucket(0))
        assertEquals("1", TelemetryEvents.outputBucket(1))
        assertEquals("2_5", TelemetryEvents.outputBucket(5))
        assertEquals("6_10", TelemetryEvents.outputBucket(10))
        assertEquals("gte_11", TelemetryEvents.outputBucket(11))

        assertEquals("0", TelemetryEvents.candidateBucket(0))
        assertEquals("1_5", TelemetryEvents.candidateBucket(5))
        assertEquals("6_20", TelemetryEvents.candidateBucket(20))
        assertEquals("21_50", TelemetryEvents.candidateBucket(50))
        assertEquals("gte_51", TelemetryEvents.candidateBucket(51))
    }

    @Test
    fun parserSourceMapsOnlyToFiniteProcessorValues() {
        assertEquals(ParseProcessor.SERVER_AI, TelemetryEvents.processorFromParserSource("AI_FALLBACK"))
        assertEquals(
            ParseProcessor.SERVER_DETERMINISTIC,
            TelemetryEvents.processorFromParserSource("BACKEND_ROUTE_FIRST_V53")
        )
        assertEquals(
            ParseProcessor.SERVER_DETERMINISTIC,
            TelemetryEvents.processorFromParserSource(null)
        )
    }

    @Test
    fun notificationCaptureUsesOnlyFiniteContentlessDimensions() {
        val event = TelemetryEvents.notificationCapture(
            layer = NotificationCaptureLayer.HISTORIC_MESSAGES,
            state = NotificationCaptureState.PROBABLY_TRUNCATED
        )

        assertEquals("notification_capture", event.name)
        assertEquals("historic_stack", event.dimensions["layer"])
        assertEquals("probably_truncated", event.dimensions["state"])
        assertEquals(3, event.dimensions.size)
    }

    @Test
    fun productEventsCannotCarryContentOrIdentifiers() {
        val event = TelemetryEvents.parseAttempt(
            processor = ParseProcessor.SERVER_DETERMINISTIC,
            result = ParseTelemetryResult.SUCCESS,
            latencyMillis = 640,
            outputCount = 3
        )
        val forbiddenFragments = listOf(
            "message", "text", "route", "city", "group", "phone", "price",
            "hash", "installation", "device_id", "purchase", "token", "timestamp"
        )

        assertEquals("parse_attempt", event.name)
        assertEquals("v5.4.19", event.dimensions["parser_version"])
        assertEquals(5, event.dimensions.size)
        assertTrue(event.dimensions.values.all { it.length <= 32 })
        assertFalse(
            event.dimensions.keys.any { key ->
                forbiddenFragments.any { fragment -> key.contains(fragment, ignoreCase = true) }
            }
        )
    }
}
