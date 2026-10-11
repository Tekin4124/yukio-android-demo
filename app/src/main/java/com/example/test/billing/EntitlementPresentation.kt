package com.example.test.billing

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Abonelik/deneme durumunun kullanıcıya gösterilen hali. Teknik terim
 * içermez; tüm metinler sade Türkçedir. Saf fonksiyonlardır ve birim
 * testleriyle doğrulanır.
 */
data class SubscriptionStatusPresentation(
    val headline: String,
    val detail: String?,
    val isWarning: Boolean = false,
)

/** Satın alma alanının paywall'daki tek doğruluk kaynağı. */
sealed interface PaywallPurchaseUi {
    /** Gerçek Play fiyatıyla satın alınabilir. */
    data class Ready(val offer: BillingOffer, val priceLabel: String) : PaywallPurchaseUi

    /** Sunucu satışa henüz izin vermiyor; satın alma gösterilmez. */
    object SalesNotOpenYet : PaywallPurchaseUi

    /** Google Play'den bilgi bekleniyor. */
    object Loading : PaywallPurchaseUi

    /** Google Play'e bağlanılamadı. */
    object PlayUnavailable : PaywallPurchaseUi

    /** Ürün Play tarafında yapılandırılmamış ya da fiyat alınamadı. */
    data class Unavailable(val message: String) : PaywallPurchaseUi
}

fun remainingWholeDays(endsAtEpochMillis: Long, nowEpochMillis: Long): Int {
    val remainingMillis = endsAtEpochMillis - nowEpochMillis
    if (remainingMillis <= 0) return 0
    val dayMillis = TimeUnit.DAYS.toMillis(1)
    return ((remainingMillis + dayMillis - 1) / dayMillis).toInt()
}

fun formatTurkishDate(epochMillis: Long): String =
    SimpleDateFormat("d MMMM yyyy", Locale("tr", "TR")).format(Date(epochMillis))

fun subscriptionStatusPresentation(
    snapshot: EntitlementSnapshot,
    nowEpochMillis: Long,
    trialDays: Int,
): SubscriptionStatusPresentation = when (snapshot.status) {
    EntitlementStatus.TRIAL_NOT_STARTED -> SubscriptionStatusPresentation(
        headline = "Ücretsiz deneme henüz başlamadı",
        detail = "İlk yük ilanın uygulamaya düştüğünde $trialDays günlük deneme " +
            "kendiliğinden başlar. Şu an tüm özellikler açık.",
    )
    EntitlementStatus.TRIAL_ACTIVE -> {
        val end = snapshot.trialEndsAtEpochMillis
        val daysLeft = end?.let { remainingWholeDays(it, nowEpochMillis) }
        SubscriptionStatusPresentation(
            headline = when {
                daysLeft == null -> "Ücretsiz deneme devam ediyor"
                daysLeft <= 1 -> "Ücretsiz denemenin son günü"
                else -> "Ücretsiz deneme devam ediyor · $daysLeft gün kaldı"
            },
            detail = buildString {
                snapshot.trialStartedAtEpochMillis?.let {
                    append("Başlangıç: ${formatTurkishDate(it)}. ")
                }
                end?.let { append("Bitiş: ${formatTurkishDate(it)}.") }
            }.trim().ifEmpty { null },
        )
    }
    EntitlementStatus.TRIAL_EXPIRED -> SubscriptionStatusPresentation(
        headline = "Ücretsiz deneme sona erdi",
        detail = snapshot.trialEndsAtEpochMillis
            ?.let { "$trialDays günlük deneme ${formatTurkishDate(it)} tarihinde bitti." }
            ?: "$trialDays günlük deneme süresi doldu.",
        isWarning = true,
    )
    EntitlementStatus.SUBSCRIPTION_ACTIVE -> SubscriptionStatusPresentation(
        headline = "Premium üyeliğin aktif",
        detail = snapshot.subscriptionExpiresAtEpochMillis
            ?.let { "Sonraki yenileme: ${formatTurkishDate(it)}." },
    )
    EntitlementStatus.SUBSCRIPTION_CANCELED -> SubscriptionStatusPresentation(
        headline = "Premium aktif, yenileme kapalı",
        detail = snapshot.subscriptionExpiresAtEpochMillis
            ?.let {
                "Üyeliğin ${formatTurkishDate(it)} tarihine kadar sürecek, " +
                    "sonrasında yenilenmeyecek."
            },
    )
    EntitlementStatus.SUBSCRIPTION_GRACE_PERIOD -> SubscriptionStatusPresentation(
        headline = "Ödemede sorun görünüyor",
        detail = "Erişimin şimdilik devam ediyor. Google Play'deki ödeme yöntemini " +
            "güncellersen üyeliğin kesintisiz sürer.",
        isWarning = true,
    )
    EntitlementStatus.SUBSCRIPTION_VERIFICATION_PENDING -> SubscriptionStatusPresentation(
        headline = "Ödemen kontrol ediliyor",
        detail = "Google Play ödemen alındı; kısa süre içinde üyeliğin açılır. " +
            "Beklemek dışında bir şey yapman gerekmiyor.",
        isWarning = true,
    )
    EntitlementStatus.FREE_ACCESS_ACTIVE -> SubscriptionStatusPresentation(
        headline = "Ücretsiz erişimin aktif",
        detail = snapshot.freeAccessEndsAtEpochMillis
            ?.let { "Ücretsiz erişimin ${formatTurkishDate(it)} tarihine kadar devam eder." },
    )
}

/**
 * Paywall satın alma alanı kararı. Sıralama bilinçli:
 * 1) Sunucu satışa kapalı diyorsa hiçbir koşulda satın alma gösterilmez.
 * 2) Teklif belirsizse (birden çok plan) güvenli tarafta kalınır.
 * 3) Fiyat yoksa asla uydurma fiyat gösterilmez; dürüst boş durum döner.
 */
fun paywallPurchaseUi(
    purchasesAvailable: Boolean?,
    connection: BillingConnectionState,
    catalog: BillingCatalogState,
    selectedOffer: BillingOffer?,
    priceLabel: String?,
): PaywallPurchaseUi {
    if (purchasesAvailable == false) return PaywallPurchaseUi.SalesNotOpenYet
    if (selectedOffer != null && priceLabel != null) {
        return PaywallPurchaseUi.Ready(selectedOffer, priceLabel)
    }
    if (catalog.offers.isNotEmpty() && selectedOffer == null) {
        return PaywallPurchaseUi.Unavailable(
            "Abonelik seçenekleri güvenli biçimde doğrulanamadı. " +
                "Lütfen daha sonra tekrar dene.",
        )
    }
    if (catalog.isLoading || connection == BillingConnectionState.CONNECTING) {
        return PaywallPurchaseUi.Loading
    }
    if (connection == BillingConnectionState.DISCONNECTED) {
        return PaywallPurchaseUi.PlayUnavailable
    }
    if (catalog.errorCode != null) {
        return PaywallPurchaseUi.Unavailable(
            "Abonelik bilgisi şu an alınamıyor. Lütfen daha sonra tekrar dene.",
        )
    }
    return PaywallPurchaseUi.Unavailable(
        "Abonelik henüz satışa hazır değil. Uygulamayı bir süre sonra " +
            "tekrar açıp deneyebilirsin.",
    )
}
