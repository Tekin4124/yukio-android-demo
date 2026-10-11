package com.example.test.billing

import java.util.concurrent.TimeUnit

/** Google Play state after it has been verified by the backend. */
enum class PlaySubscriptionState {
    NONE,
    PENDING,
    ACTIVE,
    IN_GRACE_PERIOD,
    CANCELED,
    ON_HOLD,
    PAUSED,
    EXPIRED,
    REVOKED,
    VERIFICATION_PENDING,
}

data class VerifiedSubscription(
    val state: PlaySubscriptionState = PlaySubscriptionState.NONE,
    val accessAllowed: Boolean = false,
    val productId: String? = null,
    val expiresAtEpochMillis: Long? = null,
    val supportCode: String? = null,
    val autoRenewing: Boolean? = null,
)

data class TrialRecord(
    val firstSuccessfulParseAtEpochMillis: Long? = null,
    // Sunucunun bildirdiği deneme sözleşmesi. Dolu olduğunda cihaz saatinden
    // türetilen yerel hesap yerine bu esas alınır; cihaz saati yalnız "şu an"
    // karşılaştırması için kullanılır.
    val backendTrialStartedAtEpochMillis: Long? = null,
    val backendTrialEndsAtEpochMillis: Long? = null,
    val backendManualGrantUntilEpochMillis: Long? = null,
)

enum class EntitlementStatus {
    TRIAL_NOT_STARTED,
    TRIAL_ACTIVE,
    TRIAL_EXPIRED,
    SUBSCRIPTION_ACTIVE,
    SUBSCRIPTION_GRACE_PERIOD,
    SUBSCRIPTION_CANCELED,
    SUBSCRIPTION_VERIFICATION_PENDING,
    FREE_ACCESS_ACTIVE,
}

data class EntitlementSnapshot(
    val status: EntitlementStatus,
    val hasAccess: Boolean,
    val trialStartedAtEpochMillis: Long? = null,
    val trialEndsAtEpochMillis: Long? = null,
    val subscriptionState: PlaySubscriptionState = PlaySubscriptionState.NONE,
    val subscriptionExpiresAtEpochMillis: Long? = null,
    val productId: String? = null,
    val supportCode: String? = null,
    val freeAccessEndsAtEpochMillis: Long? = null,
)

sealed interface SupportIdentityState {
    data object Loading : SupportIdentityState
    data class Ready(val supportCode: String) : SupportIdentityState
    data object Unavailable : SupportIdentityState
}

internal fun supportIdentityStateOf(supportCode: String?): SupportIdentityState {
    val normalized = supportCode
        ?.trim()
        ?.uppercase()
        ?.takeIf {
            it.matches(
                Regex("YUK-[0-9A-HJKMNP-TV-Z]{4}-[0-9A-HJKMNP-TV-Z]{4}-[0-9A-HJKMNP-TV-Z]{4}"),
            )
        }
        ?: return SupportIdentityState.Unavailable
    return SupportIdentityState.Ready(normalized)
}

/**
 * Pure entitlement policy shared by UI/background entry points.
 *
 * The trial does not start at install time. It begins only after the parser
 * returns at least one load, so an outage or irrelevant message cannot consume
 * a driver's trial.
 */
object EntitlementStateMachine {
    val TRIAL_DURATION_MILLIS: Long = TimeUnit.DAYS.toMillis(30)

    fun evaluate(
        nowEpochMillis: Long,
        trial: TrialRecord,
        subscription: VerifiedSubscription,
        trialDurationMillis: Long = TRIAL_DURATION_MILLIS,
    ): EntitlementSnapshot {
        require(trialDurationMillis > 0) { "Trial duration must be positive" }
        val subscriptionHasAccess = subscription.accessAllowed &&
            (subscription.expiresAtEpochMillis?.let { it > nowEpochMillis } ?: false)

        if (subscriptionHasAccess) {
            val subscriptionStatus = when (subscription.state) {
                PlaySubscriptionState.ACTIVE -> EntitlementStatus.SUBSCRIPTION_ACTIVE
                PlaySubscriptionState.IN_GRACE_PERIOD ->
                    EntitlementStatus.SUBSCRIPTION_GRACE_PERIOD
                PlaySubscriptionState.CANCELED -> EntitlementStatus.SUBSCRIPTION_CANCELED
                else -> null
            }

            if (subscriptionStatus != null) {
                return EntitlementSnapshot(
                    status = subscriptionStatus,
                    hasAccess = true,
                    subscriptionState = subscription.state,
                    subscriptionExpiresAtEpochMillis = subscription.expiresAtEpochMillis,
                    productId = subscription.productId,
                    supportCode = subscription.supportCode,
                )
            }
        }

        val manualGrantUntil = trial.backendManualGrantUntilEpochMillis
        if (manualGrantUntil != null && manualGrantUntil > nowEpochMillis) {
            return EntitlementSnapshot(
                status = EntitlementStatus.FREE_ACCESS_ACTIVE,
                hasAccess = true,
                subscriptionState = subscription.state,
                subscriptionExpiresAtEpochMillis = subscription.expiresAtEpochMillis,
                productId = subscription.productId,
                supportCode = subscription.supportCode,
                freeAccessEndsAtEpochMillis = manualGrantUntil,
            )
        }

        // Sunucu sözleşmesi (varsa) yerel kayda göre önceliklidir: yeniden
        // kurulumda veya cihaz saati oynadığında deneme bitişi kaymaz.
        val trialStart = trial.backendTrialStartedAtEpochMillis
            ?: trial.firstSuccessfulParseAtEpochMillis
        if (trialStart == null && trial.backendTrialEndsAtEpochMillis == null) {
            return EntitlementSnapshot(
                status = EntitlementStatus.TRIAL_NOT_STARTED,
                hasAccess = true,
                subscriptionState = subscription.state,
                subscriptionExpiresAtEpochMillis = subscription.expiresAtEpochMillis,
                productId = subscription.productId,
                supportCode = subscription.supportCode,
            )
        }

        val trialEnd = trial.backendTrialEndsAtEpochMillis
            ?: saturatingAdd(requireNotNull(trialStart), trialDurationMillis)
        if (nowEpochMillis < trialEnd) {
            return EntitlementSnapshot(
                status = EntitlementStatus.TRIAL_ACTIVE,
                hasAccess = true,
                trialStartedAtEpochMillis = trialStart,
                trialEndsAtEpochMillis = trialEnd,
                subscriptionState = subscription.state,
                subscriptionExpiresAtEpochMillis = subscription.expiresAtEpochMillis,
                productId = subscription.productId,
                supportCode = subscription.supportCode,
            )
        }

        val status = if (subscription.state == PlaySubscriptionState.VERIFICATION_PENDING) {
            EntitlementStatus.SUBSCRIPTION_VERIFICATION_PENDING
        } else {
            EntitlementStatus.TRIAL_EXPIRED
        }
        return EntitlementSnapshot(
            status = status,
            hasAccess = false,
            trialStartedAtEpochMillis = trialStart,
            trialEndsAtEpochMillis = trialEnd,
            subscriptionState = subscription.state,
            subscriptionExpiresAtEpochMillis = subscription.expiresAtEpochMillis,
            productId = subscription.productId,
            supportCode = subscription.supportCode,
        )
    }

    private fun saturatingAdd(left: Long, right: Long): Long =
        if (left > Long.MAX_VALUE - right) Long.MAX_VALUE else left + right
}
