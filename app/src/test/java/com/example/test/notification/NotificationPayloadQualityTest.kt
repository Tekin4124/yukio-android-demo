package com.example.test.notification

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPayloadQualityTest {
    @Test
    fun `unicode ve uc nokta ile biten metin muhtemel kesik sayilir`() {
        assertTrue(NotificationPayloadQuality.isProbablyTruncated("Ankara Istanbul 24 ton\u2026"))
        assertTrue(NotificationPayloadQuality.isProbablyTruncated("Ankara Istanbul 24 ton...  "))
    }

    @Test
    fun `normal noktalama kesik sayilmaz`() {
        assertFalse(NotificationPayloadQuality.isProbablyTruncated("Ankara Istanbul 24 ton."))
        assertFalse(NotificationPayloadQuality.isProbablyTruncated("Ankara Istanbul 24 ton"))
    }
}
