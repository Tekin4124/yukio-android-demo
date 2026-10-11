package com.example.test.ui.screens

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.billingclient.api.BillingClient
import com.example.test.billing.BillingConnectionState
import com.example.test.billing.BillingOffer
import com.example.test.billing.BillingPricePhase
import com.example.test.BuildConfig
import com.example.test.billing.EntitlementRuntime
import com.example.test.billing.PaywallPurchaseUi
import com.example.test.billing.paywallPurchaseUi
import com.example.test.billing.subscriptionStatusPresentation
import com.example.test.telemetry.ContentlessTelemetry
import com.example.test.telemetry.TelemetryEvents

@Composable
fun SubscriptionScreen(
    onManagePrivacyAndProfile: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val entitlement by EntitlementRuntime.entitlement.collectAsStateWithLifecycle()
    val catalog by EntitlementRuntime.catalog.collectAsStateWithLifecycle()
    val connection by EntitlementRuntime.billingConnectionState.collectAsStateWithLifecycle()
    val purchasesAvailable by EntitlementRuntime.purchasesAvailable.collectAsStateWithLifecycle()
    val offer = selectPurchasableOffer(catalog.offers)
    val recurringPhase = offer?.pricingPhases?.lastOrNull()
    val recurringPrice = recurringPhase?.let(::recurringPriceLabel)
    val purchaseUi = paywallPurchaseUi(
        purchasesAvailable = purchasesAvailable,
        connection = connection,
        catalog = catalog,
        selectedOffer = offer,
        priceLabel = recurringPrice,
    )
    var localError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        ContentlessTelemetry.record(context, TelemetryEvents.funnel("paywall_viewed"))
    }

    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Yükio Premium",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))
            val statusText = subscriptionStatusPresentation(
                snapshot = entitlement,
                nowEpochMillis = System.currentTimeMillis(),
                trialDays = BuildConfig.TRIAL_DAYS,
            )
            Text(
                text = statusText.headline,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyLarge
            )
            statusText.detail?.let { detail ->
                Spacer(Modifier.height(6.dp))
                Text(
                    text = detail,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Premium ile şunlar açık kalır:",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(6.dp))
            Text("• WhatsApp gruplarındaki ilanları otomatik süzme")
            Text("• Araç ve rota profiline uygun yük bildirimleri")
            Text("• 30 günlük ilan geçmişi ve gelişmiş filtreler")
            Text("• Aynı Play hesabıyla satın almayı geri yükleme")
            Spacer(Modifier.height(28.dp))

            when (val ui = purchaseUi) {
                is PaywallPurchaseUi.Ready -> {
                    Text(
                        text = ui.priceLabel,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Fiyat ve yenileme koşulları Google Play tarafından gösterilir.",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "Plan, gösterilen dönem sonunda otomatik yenilenir; Google Play " +
                            "abonelik ayarlarından istediğin zaman iptal edebilirsin.",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                PaywallPurchaseUi.SalesNotOpenYet -> Text(
                    text = "Abonelik satışı henüz açılmadı. Hazır olduğunda fiyatı " +
                        "burada, doğrudan Google Play üzerinden göreceksin.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium
                )
                PaywallPurchaseUi.Loading -> Text(
                    text = "Google Play abonelik bilgisi yükleniyor…",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium
                )
                PaywallPurchaseUi.PlayUnavailable -> Text(
                    text = "Google Play'e bağlanılamadı. İnternet bağlantını kontrol edip " +
                        "geri yüklemeyi dene.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium
                )
                is PaywallPurchaseUi.Unavailable -> Text(
                    text = ui.message,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            localError?.let { message ->
                Spacer(Modifier.height(12.dp))
                Text(message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            }

            Spacer(Modifier.height(20.dp))
            if (purchaseUi !is PaywallPurchaseUi.SalesNotOpenYet) {
                Button(
                    onClick = {
                        val readyUi = purchaseUi as? PaywallPurchaseUi.Ready
                        if (activity == null || readyUi == null) {
                            localError = "Google Play abonelik bilgisi henüz hazır değil."
                            ContentlessTelemetry.record(
                                context,
                                TelemetryEvents.funnel("purchase_failed")
                            )
                            return@Button
                        }
                        ContentlessTelemetry.record(
                            context,
                            TelemetryEvents.funnel("purchase_started")
                        )
                        val result = EntitlementRuntime.launchPurchase(
                            activity,
                            readyUi.offer.offerToken
                        )
                        if (result?.responseCode != BillingClient.BillingResponseCode.OK) {
                            localError =
                                "Google Play satın alma ekranı açılamadı. Lütfen tekrar dene."
                            ContentlessTelemetry.record(
                                context,
                                TelemetryEvents.funnel("purchase_failed")
                            )
                        } else {
                            localError = null
                        }
                    },
                    enabled = purchaseUi is PaywallPurchaseUi.Ready &&
                        connection == BillingConnectionState.READY,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (purchaseUi is PaywallPurchaseUi.Ready) "Abone ol"
                        else "Google Play'e bağlanıyor"
                    )
                }
                Spacer(Modifier.height(10.dp))
            }
            OutlinedButton(
                onClick = {
                    localError = null
                    EntitlementRuntime.restorePurchases()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Satın almayı geri yükle")
            }

            TextButton(onClick = onManagePrivacyAndProfile) {
                Text("Profil ve gizlilik ayarları")
            }
            entitlement.supportCode?.let { code ->
                Text("Destek kodu: $code", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/**
 * Google Play does not guarantee offer ordering. Never launch an arbitrary
 * first item when multiple base plans are eligible. A single base plan may
 * expose both its standard offer and promotions; prefer the unique standard
 * offer so the price shown by this screen matches the offer being launched.
 */
internal fun selectPurchasableOffer(offers: List<BillingOffer>): BillingOffer? {
    if (offers.isEmpty()) return null
    if (offers.any { it.basePlanId.isBlank() || it.offerToken.isBlank() }) return null
    if (offers.map { it.productId }.distinct().size != 1) return null
    if (offers.map { it.basePlanId }.distinct().size != 1) return null
    if (offers.size == 1) return offers.single()
    return offers.singleOrNull { it.offerId == null }
}

internal fun recurringPriceLabel(phase: BillingPricePhase): String {
    val suffix = when (phase.billingPeriod.uppercase()) {
        "P1D" -> " / gün"
        "P1W" -> " / hafta"
        "P1M" -> " / ay"
        "P3M" -> " / 3 ay"
        "P6M" -> " / 6 ay"
        "P1Y" -> " / yıl"
        else -> ""
    }
    return phase.formattedPrice + suffix
}
