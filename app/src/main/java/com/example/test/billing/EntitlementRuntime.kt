package com.example.test.billing

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.BillingResult
import com.example.test.identity.AndroidKeystoreInstallationIdentity
import com.example.test.identity.InstallationIdentityProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Local demo access only. No Play purchase, billing API, or server token. */
object EntitlementRuntime {
    private var identity: InstallationIdentityProvider? = null
    private var noticeAccepted = false
    private val demoEntitlement = MutableStateFlow(
        EntitlementSnapshot(status = EntitlementStatus.FREE_ACCESS_ACTIVE, hasAccess = true)
    )
    val entitlement: StateFlow<EntitlementSnapshot> = demoEntitlement.asStateFlow()
    val catalog: StateFlow<BillingCatalogState> = MutableStateFlow(BillingCatalogState()).asStateFlow()
    val billingConnectionState: StateFlow<BillingConnectionState> =
        MutableStateFlow(BillingConnectionState.DISCONNECTED).asStateFlow()
    val purchasesAvailable: StateFlow<Boolean?> = MutableStateFlow<Boolean?>(false).asStateFlow()
    val supportIdentity: StateFlow<SupportIdentityState> =
        MutableStateFlow<SupportIdentityState>(SupportIdentityState.Unavailable).asStateFlow()

    fun initialize(context: Context) { identity = AndroidKeystoreInstallationIdentity(context) }
    fun activateAfterNotice() { noticeAccepted = true }
    fun installationIdentityProvider(): InstallationIdentityProvider = requireNotNull(identity)
    fun retryBackendStatus() = Unit
    fun refreshBackendStatusIfStale() = Unit
    suspend fun recordSuccessfulParse() = Unit
    suspend fun recordNotificationReceived() = Unit
    fun restorePurchases() = Unit
    fun hasAccessOrDefault(): Boolean = noticeAccepted
    fun accessTokenOrNull(): String? = null
    fun launchPurchase(activity: Activity, offerToken: String): BillingResult? = null
}
