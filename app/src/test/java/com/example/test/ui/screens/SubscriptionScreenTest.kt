package com.example.test.ui.screens

import com.example.test.billing.BillingOffer
import com.example.test.billing.BillingPricePhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class SubscriptionScreenTest {
    @Test
    fun `recurring price uses the period returned by Google Play`() {
        assertEquals("₺279,99 / ay", recurringPriceLabel(phase("P1M")))
        assertEquals("₺2.499,99 / yıl", recurringPriceLabel(phase("P1Y")))
        assertEquals("₺99,99", recurringPriceLabel(phase("P2W")))
    }

    @Test
    fun `single eligible base plan offer is selected`() {
        val offer = offer(basePlanId = "monthly", offerId = null, token = "base")

        assertSame(offer, selectPurchasableOffer(listOf(offer)))
    }

    @Test
    fun `multiple base plans fail closed instead of selecting the first`() {
        val monthly = offer(basePlanId = "monthly", offerId = null, token = "monthly")
        val yearly = offer(basePlanId = "yearly", offerId = null, token = "yearly")

        assertNull(selectPurchasableOffer(listOf(monthly, yearly)))
        assertNull(selectPurchasableOffer(listOf(yearly, monthly)))
    }

    @Test
    fun `unique standard offer wins over promotions in the same base plan`() {
        val promotion = offer(basePlanId = "monthly", offerId = "intro", token = "intro")
        val standard = offer(basePlanId = "monthly", offerId = null, token = "base")

        assertSame(standard, selectPurchasableOffer(listOf(promotion, standard)))
    }

    @Test
    fun `multiple promotional offers without a unique standard fail closed`() {
        val first = offer(basePlanId = "monthly", offerId = "intro", token = "intro")
        val second = offer(basePlanId = "monthly", offerId = "winback", token = "winback")

        assertNull(selectPurchasableOffer(listOf(first, second)))
    }

    private fun phase(period: String) = BillingPricePhase(
        formattedPrice = when (period) {
            "P1Y" -> "₺2.499,99"
            "P2W" -> "₺99,99"
            else -> "₺279,99"
        },
        priceCurrencyCode = "TRY",
        priceAmountMicros = 0,
        billingPeriod = period,
        recurrenceMode = 0,
        billingCycleCount = 0,
    )

    private fun offer(
        basePlanId: String,
        offerId: String?,
        token: String,
    ) = BillingOffer(
        productId = "yukkap_premium",
        title = "YükKap Premium",
        description = "Test",
        basePlanId = basePlanId,
        offerId = offerId,
        offerToken = token,
        offerTags = emptyList(),
        pricingPhases = listOf(phase("P1M")),
    )
}
