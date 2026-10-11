package com.example.test.notification

import org.junit.Assert.assertEquals
import org.junit.Test

class WhatsappNotificationPrivacyTest {

    @Test
    fun cleansWhatsappGroupTitleWithoutSenderName() {
        val title = "\u200EAYDEM LOJİSTİK (4 mesaj): ~\u202FBora Çakıcı"

        assertEquals("AYDEM LOJİSTİK", WhatsappNotificationPrivacy.cleanSourceName(title))
    }

    @Test
    fun cleansPlainGroupTitleWithoutSenderName() {
        val title = "Kyk erkek yurdu: ~\u202FUnal"

        assertEquals("Kyk erkek yurdu", WhatsappNotificationPrivacy.cleanSourceName(title))
    }

    @Test
    fun stripsOnlyKnownSenderPrefixFromMessage() {
        val cleaned = WhatsappNotificationPrivacy.stripKnownSenderPrefix(
            message = "Bora Çakıcı: Gebze Ankara 22 ton tente",
            senderCandidates = listOf("Bora Çakıcı")
        )

        assertEquals("Gebze Ankara 22 ton tente", cleaned)
    }

    @Test
    fun keepsRouteLikeColonWhenSenderDoesNotMatch() {
        val cleaned = WhatsappNotificationPrivacy.stripKnownSenderPrefix(
            message = "İstanbul: Gebze 22 ton tente",
            senderCandidates = listOf("Bora Çakıcı")
        )

        assertEquals("İstanbul: Gebze 22 ton tente", cleaned)
    }
}
