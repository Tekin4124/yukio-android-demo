
package com.example.test

import android.Manifest
import android.net.Uri
import com.google.gson.Gson
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.test.viewmodel.LoadListViewModel
import com.example.test.data.local.LoadOfferEntity
import com.example.test.ai.InteractionType
import com.example.test.ui.screens.LoadDetailScreen
import com.example.test.ui.screens.ManualListingScreen
import com.example.test.ui.screens.ReferralScreen
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.test.data.ConsentStore
import com.example.test.data.AppThemeMode
import com.example.test.data.NotificationHealthSetupStore
import com.example.test.data.ThemePreferenceStore
import com.example.test.billing.EntitlementRuntime
import com.example.test.billing.EntitlementStatus
import com.example.test.billing.PlaySubscriptionState
import com.example.test.notification.DataRetentionWorker
import com.example.test.notification.MessageRetryWorker
import com.example.test.notification.NotificationHealthStatus
import com.example.test.notification.readNotificationHealthStatus
import com.example.test.telemetry.ContentlessTelemetry
import com.example.test.telemetry.TelemetryEvents
import com.example.test.telemetry.TelemetryMilestone
import com.example.test.telemetry.TelemetryPermission
import com.example.test.telemetry.TelemetryPermissionState
import com.example.test.update.InAppUpdateUiState
import com.example.test.update.PlayInAppUpdateController
import com.example.test.ui.screens.ConsentScreen
import com.example.test.ui.screens.MainScreen
import com.example.test.ui.screens.MapScreen
import com.example.test.ui.screens.NotificationHealthScreen
import com.example.test.ui.screens.ParseDebugPanelScreen
import com.example.test.ui.screens.SubscriptionScreen
import com.example.test.ui.screens.VehicleProfileScreen
import com.example.test.ui.components.YukioStartupLoading
import com.example.test.ui.theme.TestTheme
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber

sealed class AppRoute(val route: String) {
    data object ManualListing : AppRoute("manual_listing")
    data object Detail : AppRoute("load_detail")
    data object Referral : AppRoute("referral")
    data object Main : AppRoute("main")
    data object Map : AppRoute("map")
    data object Profile : AppRoute("profile")
    data object NotificationHealth : AppRoute("notification_health")
    data object ParsePanel : AppRoute("parse_panel")
}

class MainActivity : ComponentActivity() {

    private lateinit var inAppUpdateController: PlayInAppUpdateController
    private val inAppUpdateLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
            // Google Play owns the consent/result UI. If the user postpones,
            // the controller applies a reminder cooldown before asking again.
        }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            ContentlessTelemetry.record(
                this,
                TelemetryEvents.permissionState(
                    TelemetryPermission.POST_NOTIFICATIONS,
                    if (granted) TelemetryPermissionState.GRANTED else TelemetryPermissionState.DENIED
                )
            )
        }

    // MatchNotificationHelper PendingIntent'i ile gelen ilan id'si — bildirime
    // tıklanınca AppNavHost bunu okuyup ilanı açar.
    private val pendingOpenLoadId = kotlinx.coroutines.flow.MutableStateFlow<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        if (BuildConfig.DEBUG && Timber.forest().isEmpty()) {
            Timber.plant(Timber.DebugTree())
        }
        inAppUpdateController = PlayInAppUpdateController(
            activity = this,
            updateLauncher = inAppUpdateLauncher
        )
        scheduleMessageRetryWorker()
        // Beta9 harita girişi kapalı ve konum izni istemiyor. Önceki sürümden
        // kalmış periyodik konum işini de güncellemede durdur.
        WorkManager.getInstance(this).cancelUniqueWork("location_city_update")
        scheduleDataRetentionWorker()
        readOpenLoadIdFromIntent(intent)

        setContent {
            val context = LocalContext.current
            val appContext = context.applicationContext
            val themePreferenceStore = remember(appContext) { ThemePreferenceStore(appContext) }
            val themeMode by remember(themePreferenceStore) {
                themePreferenceStore.themeMode
            }.collectAsStateWithLifecycle(initialValue = AppThemeMode.DARK)
            val darkTheme = themeMode == AppThemeMode.DARK
            val scope = rememberCoroutineScope()

            SideEffect {
                val systemBarStyle = if (darkTheme) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                this@MainActivity.enableEdgeToEdge(
                    statusBarStyle = systemBarStyle,
                    navigationBarStyle = systemBarStyle
                )
            }

            TestTheme(darkTheme = darkTheme) {
                val consentStore = remember(appContext) { ConsentStore(appContext) }
                val notificationHealthSetupStore = remember(appContext) {
                    NotificationHealthSetupStore(appContext)
                }
                val accepted by remember(consentStore) {
                    consentStore.isAccepted.map { accepted -> accepted as Boolean? }
                }.collectAsStateWithLifecycle(initialValue = null)
                val notificationHealthSetupComplete by remember(notificationHealthSetupStore) {
                    notificationHealthSetupStore.isCurrentSetupComplete.map { complete ->
                        complete as Boolean?
                    }
                }.collectAsStateWithLifecycle(initialValue = null)
                val inAppUpdateUiState by inAppUpdateController.uiState
                    .collectAsStateWithLifecycle()

                Box(modifier = Modifier.fillMaxSize()) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        when (accepted) {
                        null -> YukioStartupLoading(message = "Yükio açılıyor")
                        false -> ConsentScreen(
                            onContinue = { productAnalyticsEnabled, parserSamplesEnabled ->
                                scope.launch {
                                    consentStore.acknowledgeNotice(
                                        productAnalyticsEnabled = productAnalyticsEnabled,
                                        parserSamplesEnabled = parserSamplesEnabled
                                    )
                                    EntitlementRuntime.activateAfterNotice()
                                    if (productAnalyticsEnabled) {
                                        ContentlessTelemetry.recordOnce(
                                            appContext,
                                            TelemetryMilestone.ONBOARDING_COMPLETED,
                                            TelemetryEvents.funnel("onboarding_completed")
                                        )
                                    }
                                }
                            }
                        )
                        true -> {
                            LaunchedEffect(Unit) {
                                ContentlessTelemetry.record(
                                    appContext,
                                    TelemetryEvents.appOpen(Build.VERSION.SDK_INT)
                                )
                                ContentlessTelemetry.record(
                                    appContext,
                                    TelemetryEvents.permissionState(
                                        TelemetryPermission.NOTIFICATION_LISTENER,
                                        if (
                                            readNotificationHealthStatus(appContext)
                                                .listenerEnabled
                                        ) {
                                            TelemetryPermissionState.GRANTED
                                        } else {
                                            TelemetryPermissionState.DENIED
                                        }
                                    )
                                )
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    val notificationGranted = ContextCompat.checkSelfPermission(
                                        appContext,
                                        Manifest.permission.POST_NOTIFICATIONS
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (notificationGranted) {
                                        ContentlessTelemetry.record(
                                            appContext,
                                            TelemetryEvents.permissionState(
                                                TelemetryPermission.POST_NOTIFICATIONS,
                                                TelemetryPermissionState.GRANTED
                                            )
                                        )
                                    } else {
                                        // Permissions are optional; enable them from notification settings.
                                    }
                                }
                            }
                            AppNavHost(
                                themeMode = themeMode,
                                onThemeModeChange = { mode ->
                                    scope.launch { themePreferenceStore.setThemeMode(mode) }
                                }
                            )
                        }
                        }
                    }
                    if (inAppUpdateUiState == InAppUpdateUiState.READY_TO_INSTALL) {
                        InAppUpdateReadyDialog(
                            onInstall = inAppUpdateController::completeDownloadedUpdate,
                            onLater = inAppUpdateController::dismissDownloadedPrompt
                        )
                    }
                }
            }
        }
        // Store update checks are disabled for the public demo.
    }

    override fun onResume() {
        super.onResume()
        if (::inAppUpdateController.isInitialized) {
            inAppUpdateController.onResume()
        }
    }

    override fun onDestroy() {
        if (::inAppUpdateController.isInitialized) {
            inAppUpdateController.close()
        }
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readOpenLoadIdFromIntent(intent)
    }

    private fun readOpenLoadIdFromIntent(intent: Intent?) {
        val id = intent?.getLongExtra("openLoadId", -1L) ?: -1L
        if (id > 0L) {
            pendingOpenLoadId.value = id
        }
    }

    @Composable
    fun observePendingOpenLoadId(): Long? {
        val value by pendingOpenLoadId.collectAsStateWithLifecycle()
        return value
    }

    fun consumePendingOpenLoadId() {
        pendingOpenLoadId.value = null
    }

    private fun scheduleMessageRetryWorker() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<MessageRetryWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "failed_message_retry",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    private fun scheduleDataRetentionWorker() {
        // Günde bir eski ilan/log/etkileşim kayıtlarını siler; tablolar
        // sınırsız büyüyüp uygulamayı yavaşlatmasın.
        val request = PeriodicWorkRequestBuilder<DataRetentionWorker>(1, TimeUnit.DAYS)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "data_retention",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}

@Composable
private fun InAppUpdateReadyDialog(
    onInstall: () -> Unit,
    onLater: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onLater,
        title = {
            Text("Güncelleme hazır")
        },
        text = {
            Text("Yeni sürüm indirildi. Yeniden başlatıp güncellemeyi tamamlayabilirsin.")
        },
        confirmButton = {
            TextButton(onClick = onInstall) {
                Text("Şimdi güncelle")
            }
        },
        dismissButton = {
            TextButton(onClick = onLater) {
                Text("Daha sonra")
            }
        }
    )
}

@Composable
fun AppNavHost(
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit
) {
    val navController: NavHostController = rememberNavController()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val entitlement by EntitlementRuntime.entitlement.collectAsStateWithLifecycle()
    var notificationHealth by remember(context) {
        mutableStateOf(readNotificationHealthStatus(context))
    }

    DisposableEffect(context, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // Async + throttled: admin tarafından tanımlanan ücretsiz
                // erişim, kullanıcı uygulamaya döndüğünde force-close gerekmeden alınır.
                EntitlementRuntime.refreshBackendStatusIfStale()
                val newHealth = readNotificationHealthStatus(context)
                if (!notificationHealth.listenerEnabled && newHealth.listenerEnabled) {
                    ContentlessTelemetry.record(
                        context,
                        TelemetryEvents.funnel("notification_access_enabled")
                    )
                }
                notificationHealth = newHealth
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(entitlement.status, entitlement.subscriptionState, entitlement.hasAccess) {
        val activeSubscription = when (entitlement.status) {
            EntitlementStatus.SUBSCRIPTION_ACTIVE,
            EntitlementStatus.SUBSCRIPTION_CANCELED,
            EntitlementStatus.SUBSCRIPTION_GRACE_PERIOD -> true
            else -> false
        }
        if (activeSubscription) {
            ContentlessTelemetry.recordOnce(
                context,
                TelemetryMilestone.PURCHASE_SUCCEEDED,
                TelemetryEvents.funnel("purchase_succeeded")
            )
        }
        val eventName = when {
            entitlement.subscriptionState == PlaySubscriptionState.ACTIVE && entitlement.hasAccess ->
                "subscription_active"
            entitlement.subscriptionState == PlaySubscriptionState.CANCELED && entitlement.hasAccess ->
                "subscription_active"
            entitlement.subscriptionState == PlaySubscriptionState.IN_GRACE_PERIOD ->
                "subscription_grace"
            entitlement.subscriptionState == PlaySubscriptionState.ON_HOLD ||
                entitlement.subscriptionState == PlaySubscriptionState.PAUSED -> "subscription_hold"
            entitlement.subscriptionState == PlaySubscriptionState.EXPIRED ||
                entitlement.subscriptionState == PlaySubscriptionState.REVOKED ||
                (entitlement.subscriptionState == PlaySubscriptionState.CANCELED && !entitlement.hasAccess) ->
                "subscription_expired"
            else -> null
        }
        eventName?.let { name ->
            ContentlessTelemetry.record(context, TelemetryEvents.funnel(name))
        }
    }

    NavHost(
        navController = navController,
        startDestination = AppRoute.Main.route
    ) {
        composable(AppRoute.Main.route) {
            val openLoadId by it.savedStateHandle
                .getStateFlow<Long?>("openLoadId", null)
                .collectAsStateWithLifecycle()
            // Notification PendingIntent ile gelen openLoadId varsa savedStateHandle'a yaz.
            val activity = context as? MainActivity
            val pendingFromIntent = activity?.observePendingOpenLoadId()
            LaunchedEffect(pendingFromIntent) {
                val pid = pendingFromIntent
                if (pid != null && pid > 0L) {
                    it.savedStateHandle["openLoadId"] = pid
                    activity?.consumePendingOpenLoadId()
                }
            }

            if (!entitlement.hasAccess) {
                SubscriptionScreen(
                    onManagePrivacyAndProfile = {
                        navController.navigate(AppRoute.Profile.route)
                    }
                )
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    if (BuildConfig.APPLICATION_ID != "com.yukio.demo" && !notificationHealth.automaticChecksPassed) {
                        BildirimSagligiBanner(
                            status = notificationHealth,
                            onOpenHealth = {
                                navController.navigate(AppRoute.NotificationHealth.route)
                            }
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        MainScreen(
                            onOpenLoadDetails = { load ->
                                it.savedStateHandle["detail_load_json"] = Gson().toJson(load)
                                navController.navigate(AppRoute.Detail.route)
                            },
                            onCreateListing = { navController.navigate(AppRoute.ManualListing.route) },
                            openLoadId = openLoadId,
                            topBarUsesStatusInsets = true,
                            onOpenLoadHandled = {
                                it.savedStateHandle["openLoadId"] = null
                            },
                            onNavigateToProfile = { navController.navigate(AppRoute.Profile.route) },
                            onNavigateToDebugPanel = {
                                if (BuildConfig.DEBUG) {
                                    navController.navigate(AppRoute.ParsePanel.route)
                                }
                            }
                        )
                    }
                }
            }
        }
        composable(AppRoute.Detail.route) { entry ->
            val mainEntry = remember(entry) { navController.getBackStackEntry(AppRoute.Main.route) }
            val mainViewModel: LoadListViewModel = viewModel(viewModelStoreOwner = mainEntry)
            val state by mainViewModel.uiState.collectAsStateWithLifecycle()
            val savedJson = mainEntry.savedStateHandle.get<String>("detail_load_json")
            val savedLoad = remember(savedJson) { runCatching { Gson().fromJson(savedJson, LoadOfferEntity::class.java) }.getOrNull() }
            val load = state.loads.firstOrNull { it.id == savedLoad?.id } ?: savedLoad
            if(load != null) {
                LaunchedEffect(load.id) { mainViewModel.recordSampleOpened(load) }
                LoadDetailScreen(load=load,matchResult=state.matchResults[load.id],parseFeedback=state.parseFeedback[load.id],
                    siblingLoads=state.loads.filter { it.id != load.id && it.rawMessage == load.rawMessage },
                    onParseFeedback={ feedback -> mainViewModel.recordInteraction(load,feedback) },
                    onCallClick={
                        mainViewModel.recordInteraction(load,InteractionType.CALLED)
                        ContentlessTelemetry.record(context,TelemetryEvents.funnel("call_tapped"))
                        Unit // Demo has no dial action.
                    },onDismiss={navController.popBackStack()})
            } else { LaunchedEffect(Unit) { navController.popBackStack() } }
        }
        composable(AppRoute.ManualListing.route) { ManualListingScreen(onBack={navController.popBackStack()}) }
        composable(AppRoute.Referral.route) { ReferralScreen(onBack={navController.popBackStack()}) }
        composable(AppRoute.Map.route) {
            MapScreen(
                onBackClick = { navController.popBackStack() },
                onOpenLoadDetails = { loadId ->
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("openLoadId", loadId)
                    navController.popBackStack(AppRoute.Main.route, false)
                }
            )
        }
        composable(AppRoute.Profile.route) {
            VehicleProfileScreen(
                onOpenReferral = { navController.navigate(AppRoute.Referral.route) },
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange,
                onOpenNotificationHealth = {
                    navController.navigate(AppRoute.NotificationHealth.route)
                },
                onBackClick = { navController.popBackStack() }
            )
        }
        composable(AppRoute.NotificationHealth.route) {
            NotificationHealthScreen(
                onFinish = { navController.popBackStack() },
                onBack = { navController.popBackStack() }
            )
        }
        if (BuildConfig.DEBUG) {
            composable(AppRoute.ParsePanel.route) {
                ParseDebugPanelScreen(onBackClick = { navController.popBackStack() })
            }
        }
    }
}

@Composable
private fun BildirimSagligiBanner(
    status: NotificationHealthStatus,
    onOpenHealth: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (title, detail) = when {
        !status.listenerEnabled ->
            "WhatsApp erişimi kapalı" to "Bu durumda yeni ilanlar yakalanamaz."
        !status.batteryUnrestricted ->
            "Pil kısıtı açık" to "Telefon arka plandaki bildirim dinleyicisini kesebilir."
        else ->
            "Yükio bildirimleri kapalı" to "Uygun yük bulunsa bile sana haber verilemez."
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer
    ) {
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Button(onClick = onOpenHealth) {
                Text("Düzelt")
            }
        }
    }
}
