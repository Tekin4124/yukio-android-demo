package com.example.test.billing

data class BillingPricePhase(
    val formattedPrice: String,
    val priceCurrencyCode: String,
    val priceAmountMicros: Long,
    val billingPeriod: String,
    val recurrenceMode: Int,
    val billingCycleCount: Int,
)

data class BillingOffer(
    val productId: String,
    val title: String,
    val description: String,
    val basePlanId: String,
    val offerId: String?,
    val offerToken: String,
    val offerTags: List<String>,
    val pricingPhases: List<BillingPricePhase>,
)

data class BillingCatalogState(
    val isLoading: Boolean = false,
    val offers: List<BillingOffer> = emptyList(),
    val errorCode: Int? = null,
)

enum class BillingConnectionState {
    DISCONNECTED,
    CONNECTING,
    READY,
}

