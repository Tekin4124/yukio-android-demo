package com.example.test.notification

import com.example.test.data.local.WhatsappSourceMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WhatsappNotificationListenerFilterTest {

    private val listener = WhatsappNotificationListener()

    @Test
    fun `duz tireli ilce rotasi yuk ve fiyat baglamiyla kabul edilir`() {
        assertTrue(listener.looksLogistic("FETHİYE - MALTEPE 1 SCOOTER 3000₺"))
    }

    @Test
    fun `duz tire ve fiyat tek basina lojistik sinyal sayilmaz`() {
        assertFalse(listener.looksLogistic("SATILIK - KİRALIK 1 ÜRÜN 3000₺"))
        assertFalse(listener.looksLogistic("AKŞAM - SABAH 3000₺"))
    }

    @Test
    fun `notr kaynak lojistik mesaj sinyaliyle kabul edilir`() {
        val messageLooksLogistic =
            listener.hasStrongLogisticMessageSignal("FETHİYE - MALTEPE 1 SCOOTER 3000₺")
        assertTrue(
            resolveWhatsappSourceAllowed(
                manualMode = null,
                sourceLooksLogistic = false,
                messageLooksLogistic = messageLooksLogistic
            )
        )
    }

    @Test
    fun `notr kaynak siradan fiyat mesajiyla acilmaz`() {
        assertFalse(
            listener.hasStrongLogisticMessageSignal(
                "SATILIK - KİRALIK 1 ÜRÜN 3000₺"
            )
        )
    }

    @Test
    fun `manuel block mesaj sinyalinden her zaman ustundur`() {
        assertFalse(
            resolveWhatsappSourceAllowed(
                manualMode = WhatsappSourceMode.BLOCK,
                sourceLooksLogistic = true,
                messageLooksLogistic = true
            )
        )
    }

    @Test
    fun `manuel allow notr kaynak ve mesaji kabul eder`() {
        assertTrue(
            resolveWhatsappSourceAllowed(
                manualMode = WhatsappSourceMode.ALLOW,
                sourceLooksLogistic = false,
                messageLooksLogistic = false
            )
        )
    }
}
