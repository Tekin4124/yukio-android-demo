@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package com.example.test.ui.screens

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AirportShuttle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.RvHookup
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.ViewStream
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.work.WorkManager
import com.example.test.BuildConfig
import com.example.test.billing.EntitlementRuntime
import com.example.test.billing.PaywallPurchaseUi
import com.example.test.billing.SupportIdentityState
import com.example.test.billing.paywallPurchaseUi
import com.example.test.billing.subscriptionStatusPresentation
import com.example.test.data.ConsentStore
import com.example.test.data.AppThemeMode
import com.example.test.data.FirstRunTestOfferStore
import com.example.test.data.NotificationHealthSetupStore
import com.example.test.data.local.AppDatabase
import com.example.test.data.local.FirstRunTestOfferData
import com.example.test.data.local.RouteItem
import com.example.test.data.local.WhatsappSourceEntity
import com.example.test.data.local.WhatsappSourceMode
import com.example.test.data.remote.ParserSampleFeedback
import com.example.test.telemetry.ContentlessTelemetry
import com.example.test.viewmodel.UserProfileViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
private fun profileThemeColor(dark: Color, light: Color): Color =
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) dark else light

// Profilin koyu paleti son sağlam tasarımdan birebir korunur. Açık tema yalnız
// açık mod seçildiğinde Material renklerine geçer.
private val ProfileBackground: Color
    @Composable get() = profileThemeColor(Color(0xFF090B0C), MaterialTheme.colorScheme.background)
private val ProfileSurface: Color
    @Composable get() = profileThemeColor(Color(0xFF111417), MaterialTheme.colorScheme.surface)
private val ProfileSurfaceHigh: Color
    @Composable get() = profileThemeColor(Color(0xFF171B1E), MaterialTheme.colorScheme.surfaceContainerHigh)
private val ProfileBorder: Color
    @Composable get() = profileThemeColor(Color(0xFF263036), MaterialTheme.colorScheme.outlineVariant)
private val ProfileText: Color
    @Composable get() = profileThemeColor(Color(0xFFE8ECEE), MaterialTheme.colorScheme.onSurface)
private val ProfileMuted: Color
    @Composable get() = profileThemeColor(Color(0xFF9AA4AA), MaterialTheme.colorScheme.onSurfaceVariant)
private val ProfileSubtle: Color
    @Composable get() = profileThemeColor(Color(0xFF68747B), MaterialTheme.colorScheme.outline)
private val ProfileAccent: Color
    @Composable get() = profileThemeColor(Color(0xFF8DB3A7), MaterialTheme.colorScheme.primary)
private val ProfileAccentContainer: Color
    @Composable get() = profileThemeColor(Color(0xFF182622), MaterialTheme.colorScheme.primaryContainer)
private val ProfileAmber: Color
    @Composable get() = profileThemeColor(Color(0xFFBDAA74), MaterialTheme.colorScheme.secondary)
private val ProfileDanger: Color
    @Composable get() = profileThemeColor(Color(0xFFD47777), MaterialTheme.colorScheme.error)

object VehicleConstants {
    val vehicleTypes = listOf("Tır", "Kamyon", "Kamyonet", "Çekici", "Lowbed")
    val trailerTypes = listOf("13.60", "Tente", "Açık", "Frigo", "Konteyner", "Platform")
    val loadTypes = listOf("Palet", "Koli", "Dökme", "Sıvı", "Tehlikeli", "Soğuk Zincir", "Canlı", "Proje")
    val allCities = listOf(
        "Adana", "Adıyaman", "Afyonkarahisar", "Ağrı", "Aksaray", "Amasya",
        "Ankara", "Antalya", "Ardahan", "Artvin", "Aydın", "Balıkesir",
        "Bartın", "Batman", "Bayburt", "Bilecik", "Bingöl", "Bitlis",
        "Bolu", "Burdur", "Bursa", "Çanakkale", "Çankırı", "Çorum",
        "Denizli", "Diyarbakır", "Düzce", "Edirne", "Elazığ", "Erzincan",
        "Erzurum", "Eskişehir", "Gaziantep", "Giresun", "Gümüşhane",
        "Hakkari", "Hatay", "Iğdır", "Isparta", "İstanbul", "İzmir",
        "Kahramanmaraş", "Karabük", "Karaman", "Kars", "Kastamonu",
        "Kayseri", "Kilis", "Kırıkkale", "Kırklareli", "Kırşehir",
        "Kocaeli", "Konya", "Kütahya", "Malatya", "Manisa", "Mardin",
        "Mersin", "Muğla", "Muş", "Nevşehir", "Niğde", "Ordu",
        "Osmaniye", "Rize", "Sakarya", "Samsun", "Siirt", "Sinop",
        "Sivas", "Şanlıurfa", "Şırnak", "Tekirdağ", "Tokat", "Trabzon",
        "Tunceli", "Uşak", "Van", "Yalova", "Yozgat", "Zonguldak"
    )
    val popularCities = allCities
    val regions = listOf("Marmara", "İç Anadolu", "Ege", "Karadeniz", "Akdeniz", "Doğu Anadolu", "Güneydoğu")
}

@Composable
fun VehicleProfileScreen(
    viewModel: UserProfileViewModel = viewModel(),
    themeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    onOpenNotificationHealth: () -> Unit,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val consentStore = remember(appContext) { ConsentStore(appContext) }
    val notificationHealthSetupStore = remember(appContext) {
        NotificationHealthSetupStore(appContext)
    }
    val database = remember(appContext) { AppDatabase.getInstance(appContext) }
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val productAnalyticsEnabled by consentStore.isProductAnalyticsEnabled
        .collectAsStateWithLifecycle(initialValue = false)
    val parserSamplesEnabled by consentStore.isParserSamplesEnabled
        .collectAsStateWithLifecycle(initialValue = false)
    val snackbarHostState = remember { SnackbarHostState() }
    val screenBackground = ProfileBackground
    var showVehiclePicker by remember { mutableStateOf(false) }
    var showTrailerPicker by remember { mutableStateOf(false) }
    var showTonnagePicker by remember { mutableStateOf(false) }
    var showCityPicker by remember { mutableStateOf(false) }
    var showRouteFromPicker by remember { mutableStateOf(false) }
    var showRouteToPicker by remember { mutableStateOf(false) }
    var showWhatsappSourceInput by remember { mutableStateOf(false) }
    var showPrivacyDeleteDialog by remember { mutableStateOf(false) }
    var pendingRouteFrom by remember { mutableStateOf<String?>(null) }
    var currentCity by remember(uiState.routes) {
        mutableStateOf(uiState.routes.firstOrNull()?.fromCity ?: "İstanbul")
    }
    var showCurrentCityPicker by remember { mutableStateOf(false) }

    if (showCurrentCityPicker) {
        OptionPickerDialog(
            title = "Bulunduğun ili ekle",
            options = VehicleConstants.popularCities.filterNot { city ->
                uiState.currentCities.any { it.equals(city, ignoreCase = true) }
            },
            selected = "",
            onSelected = { city ->
                viewModel.addCurrentCity(city)
                showCurrentCityPicker = false
            },
            onDismiss = { showCurrentCityPicker = false }
        )
    }

    if (showVehiclePicker) {
        OptionPickerDialog(
            title = "Araç tipi seç",
            options = VehicleConstants.vehicleTypes,
            selected = uiState.vehicleType,
            onSelected = {
                viewModel.updateVehicleType(it)
                showVehiclePicker = false
            },
            onDismiss = { showVehiclePicker = false }
        )
    }

    if (showTrailerPicker) {
        OptionPickerDialog(
            title = "Dorse seç",
            options = VehicleConstants.trailerTypes,
            selected = uiState.trailerType.orEmpty(),
            onSelected = {
                viewModel.updateTrailerType(it)
                showTrailerPicker = false
            },
            onDismiss = { showTrailerPicker = false }
        )
    }

    if (showTonnagePicker) {
        TonnagePickerDialog(
            value = uiState.maxTonnage,
            onSelected = {
                viewModel.updateMaxTonnage(it)
                showTonnagePicker = false
            },
            onDismiss = { showTonnagePicker = false }
        )
    }

    if (showCityPicker) {
        OptionPickerDialog(
            title = "Mevcut konum",
            options = VehicleConstants.popularCities,
            selected = currentCity,
            onSelected = {
                currentCity = it
                showCityPicker = false
            },
            onDismiss = { showCityPicker = false }
        )
    }

    if (showRouteFromPicker) {
        OptionPickerDialog(
            title = "Alış şehri",
            options = VehicleConstants.popularCities,
            selected = "",
            onSelected = { city ->
                pendingRouteFrom = city
                showRouteFromPicker = false
                showRouteToPicker = true
            },
            onDismiss = { showRouteFromPicker = false }
        )
    }

    if (showRouteToPicker) {
        OptionPickerDialog(
            title = "Teslim şehri",
            options = VehicleConstants.popularCities.filter { it != pendingRouteFrom },
            selected = "",
            onSelected = { city ->
                pendingRouteFrom?.let { from -> viewModel.addRoute(from, city) }
                pendingRouteFrom = null
                showRouteToPicker = false
            },
            onDismiss = {
                showRouteToPicker = false
                pendingRouteFrom = null
            }
        )
    }

    if (showWhatsappSourceInput) {
        TextInputDialog(
            title = "WhatsApp grubu",
            value = "",
            placeholder = "Örn. Kanaryalar",
            onSave = { groupName ->
                viewModel.allowWhatsappSource(groupName)
                showWhatsappSourceInput = false
            },
            onDismiss = { showWhatsappSourceInput = false }
        )
    }

    if (showPrivacyDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDeleteDialog = false },
            containerColor = ProfileSurface,
            titleContentColor = ProfileText,
            textContentColor = ProfileText,
            title = {
                Text(
                    text = "Veriler silinsin mi?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
                    text = {
                        Text(
                            text = "Bu işlem tüm yerel ilanları, tekrar deneme kuyruğunu, parse loglarını, " +
                                "kaynak tercihlerini ve araç profilini siler. İsteğe bağlı analiz ve kalite " +
                                "paylaşımı tercihlerin de kapatılır. Daha önce sunucuya gönderilmiş filtrelenmiş " +
                                "örnekler cihaz hesabına bağlı olmadığı için bu düğmeyle uzaktan bulunup silinemez. " +
                                "Bu işlem geri alınamaz.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ProfileMuted
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showPrivacyDeleteDialog = false
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                consentStore.resetOptionalPreferences()
                                notificationHealthSetupStore.clear()
                                FirstRunTestOfferStore(appContext).clear()
                                ContentlessTelemetry.clearLocalState(appContext)
                                val workManager = WorkManager.getInstance(appContext)
                                workManager.cancelUniqueWork("failed_message_retry").result.get()
                                workManager.cancelUniqueWork("failed_message_retry_once").result.get()
                                database.failedMessageDao().deleteAll()
                                database.loadOfferDao().deleteAll()
                                database.parseDebugLogDao().deleteAll()
                                database.userInteractionDao().deleteAll()
                                database.whatsappSourceDao().deleteAll()
                                database.userProfileDao().deleteAll()
                                database.parserSampleEventDao().deleteAll()
                                context.cacheDir.deleteRecursively()
                                FirstRunTestOfferData.seedIfNeeded(appContext)
                            }
                            Toast.makeText(context, "Veriler silindi", Toast.LENGTH_SHORT).show()
                            (context as? Activity)?.recreate()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ProfileDanger,
                        contentColor = Color.White
                    )
                ) {
                    Text("Evet, sil")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPrivacyDeleteDialog = false }) {
                    Text("Vazgeç")
                }
            }
        )
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = screenBackground
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 0.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                ProfileHeroV2(
                    vehicleType = uiState.vehicleType,
                    trailerType = uiState.trailerType,
                    maxTonnage = uiState.maxTonnage
                )
            }

            item {
                ProfileCardV2(title = "GÖRÜNÜM") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThemeModeButton(
                            label = "Siyah",
                            selected = themeMode == AppThemeMode.DARK,
                            modifier = Modifier.weight(1f),
                            onClick = { onThemeModeChange(AppThemeMode.DARK) }
                        )
                        ThemeModeButton(
                            label = "Beyaz",
                            selected = themeMode == AppThemeMode.LIGHT,
                            modifier = Modifier.weight(1f),
                            onClick = { onThemeModeChange(AppThemeMode.LIGHT) }
                        )
                    }
                }
            }

            item {
                ProfileCardV2(title = "ARAÇ") {
                    ProfileRowV2(
                        icon = Icons.Default.LocalShipping,
                        label = "Tip",
                        value = uiState.vehicleType.ifBlank { "Seçilmedi" },
                        onClick = { showVehiclePicker = true }
                    )
                    ProfileRowV2(
                        icon = Icons.Default.RvHookup,
                        label = "Dorse",
                        value = uiState.trailerType ?: "Seçilmedi",
                        onClick = { showTrailerPicker = true }
                    )
                    ProfileRowV2(
                        icon = Icons.Default.ViewStream,
                        label = "Tonaj",
                        value = "${uiState.maxTonnage} ton",
                        onClick = { showTonnagePicker = true }
                    )
                }
            }

            item {
                ProfileCardV2(title = "GÜZERGAH TERCİHLERİ") {
                    Text(
                        text = "Şu an bulunduğun iller + sık çalıştığın güzergahlar. Ana ekrandaki “Bana göre” filtresi bunlara göre eşleştirir.",
                        style = MaterialTheme.typography.bodySmall,
                        color = ProfileMuted,
                        softWrap = true
                    )

                    // Manuel eklenen "şu an buradayım" illeri — chip listesi
                    if (uiState.currentCities.isNotEmpty()) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            uiState.currentCities.forEach { city ->
                                CurrentCityChip(
                                    label = city,
                                    onRemove = { viewModel.removeCurrentCity(city) }
                                )
                            }
                        }
                    }

                    // Güzergah listesi — chip'lerle aynı FlowRow stilinde
                    if (uiState.routes.isNotEmpty()) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            uiState.routes.forEach { route ->
                                RoutePreferenceRow(
                                    route = route,
                                    onRemove = { viewModel.removeRoute(route) }
                                )
                            }
                        }
                    }

                    if (uiState.currentCities.isEmpty() && uiState.routes.isEmpty()) {
                        Text(
                            text = "Henüz şehir veya güzergah eklemedin.",
                            style = MaterialTheme.typography.bodySmall,
                            color = ProfileSubtle,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }

                    // İki ekleme butonu yan yana — daha temiz
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlineAddButton(
                            text = "+ Şehir",
                            modifier = Modifier.weight(1f),
                            onClick = { showCurrentCityPicker = true }
                        )
                        OutlineAddButton(
                            text = "+ Güzergah",
                            modifier = Modifier.weight(1f),
                            onClick = { showRouteFromPicker = true }
                        )
                    }
                }
            }

            item {
                WhatsappSourcesCard(
                    sources = uiState.whatsappSources,
                    onAddManualSource = { showWhatsappSourceInput = true },
                    onAllowSource = { viewModel.allowWhatsappSource(it) },
                    onBlockSource = { viewModel.blockWhatsappSource(it) },
                    onAutoSource = { viewModel.setWhatsappSourceAuto(it) },
                    onDeleteSource = { viewModel.deleteWhatsappSource(it) }
                )
            }

            item {
                ProfileCardV2(title = "BİLDİRİMLER") {
                    ProfileSwitchRowV2(
                        icon = Icons.Default.Check,
                        label = "Aracıma uygun yük geldiğinde",
                        checked = uiState.notifyOnVehicleMatch,
                        onCheckedChange = { viewModel.setNotifyOnVehicleMatch(it) }
                    )
                    HorizontalDivider(color = ProfileBorder)
                    ProfileRowV2(
                        icon = Icons.Default.NotificationsActive,
                        label = "Bildirim sağlığı",
                        value = "Kontrol et",
                        onClick = onOpenNotificationHealth
                    )
                }
            }

            item {
                ProfileCardV2(title = "DEMO") {
                    Text("Bu sürüm ücretsiz yerel demodur. Satın alma, Destek ID ve üretim sunucusu bağlantısı yoktur.",
                        style = MaterialTheme.typography.bodySmall)
                }
            }

            item {
                ProfileCardV2(title = "GİZLİLİK") {
                    ProfileSwitchRowV2(
                        icon = Icons.Default.ViewStream,
                        label = "Kullanım istatistiği",
                        subtitle = "İlan bilgisi gönderilmez.",
                        checked = productAnalyticsEnabled,
                        onCheckedChange = { enabled ->
                            scope.launch {
                                consentStore.setProductAnalyticsEnabled(enabled)
                                if (!enabled) ContentlessTelemetry.clearLocalState(appContext)
                            }
                        }
                    )
                    HorizontalDivider(color = ProfileBorder)
                    ProfileSwitchRowV2(
                        icon = Icons.Default.ViewStream,
                        label = "İlan kalitesine katkı",
                        subtitle = "İsim ve telefon silinir.",
                        checked = parserSamplesEnabled,
                        onCheckedChange = { enabled ->
                            scope.launch {
                                consentStore.setParserSamplesEnabled(enabled)
                                // Kapatınca yeni örnek gönderimi anında durur;
                                // bekleyen etkileşim kuyruğu da temizlenir.
                                if (!enabled) ParserSampleFeedback.onOptOut(appContext)
                            }
                        }
                    )
                    HorizontalDivider(color = ProfileBorder)
                    TextButton(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.PRIVACY_URL))
                                )
                            }.onFailure {
                                Toast.makeText(
                                    context,
                                    "Gizlilik politikası açılamadı",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Gizlilik politikasını aç")
                    }
                    HorizontalDivider(color = ProfileBorder)
                    Text(
                        text = "Tüm yerel veriler, aydınlatmayı okuduğuna dair cihaz kaydı ve " +
                            "isteğe bağlı analiz tercihin silinir. İşlem geri alınamaz.",
                        style = MaterialTheme.typography.bodySmall,
                        color = ProfileMuted,
                        softWrap = true
                    )
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clickable(role = Role.Button) { showPrivacyDeleteDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        color = ProfileDanger.copy(alpha = 0.10f),
                        border = BorderStroke(1.dp, ProfileDanger.copy(alpha = 0.55f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = null,
                                tint = ProfileDanger,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Verilerimi ve gizlilik tercihlerimi sil",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium,
                                color = ProfileDanger,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2
                            )
                        }
                    }
                }
            }

        }
    }
}

@Composable
private fun OptionPickerDialog(
    title: String,
    options: List<String>,
    selected: String,
    onSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ProfileSurface,
        titleContentColor = ProfileText,
        textContentColor = ProfileText,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            if (options.isEmpty()) {
                Text(
                    text = "Eklenecek seçenek kalmadı.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ProfileMuted
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(options) { option ->
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 44.dp)
                                .clickable(role = Role.Button) { onSelected(option) },
                            shape = RoundedCornerShape(10.dp),
                            color = if (option == selected) ProfileAccentContainer else ProfileSurfaceHigh,
                            border = BorderStroke(
                                1.dp,
                                if (option == selected) ProfileAccent.copy(alpha = 0.45f) else ProfileBorder
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = option,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = ProfileText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (option == selected) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = ProfileAccent,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Kapat")
            }
        }
    )
}

@Composable
private fun TonnagePickerDialog(
    value: Int,
    onSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var selected by remember(value) { mutableStateOf(value) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ProfileSurface,
        titleContentColor = ProfileText,
        textContentColor = ProfileText,
        title = { Text("Tonaj seç", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = "$selected ton",
                    style = MaterialTheme.typography.headlineSmall,
                    color = ProfileAccent,
                    fontWeight = FontWeight.Black
                )
                Slider(
                    value = selected.toFloat(),
                    onValueChange = { selected = it.toInt() },
                    valueRange = 1f..50f,
                    steps = 48
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("1 ton", color = ProfileMuted, style = MaterialTheme.typography.labelSmall)
                    Text("50 ton", color = ProfileMuted, style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSelected(selected) }) {
                Text("Seç")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Vazgeç")
            }
        }
    )
}

@Composable
private fun DimensionPickerDialog(
    height: String,
    width: String,
    length: String,
    onSave: (String, String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var h by remember(height) { mutableStateOf(height) }
    var w by remember(width) { mutableStateOf(width) }
    var l by remember(length) { mutableStateOf(length) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ProfileSurface,
        titleContentColor = ProfileText,
        textContentColor = ProfileText,
        title = { Text("Hacim / ölçüler", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = h,
                    onValueChange = { h = it },
                    label = { Text("Yükseklik") },
                    suffix = { Text("m") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = profileTextFieldColors()
                )
                OutlinedTextField(
                    value = w,
                    onValueChange = { w = it },
                    label = { Text("Genişlik") },
                    suffix = { Text("m") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = profileTextFieldColors()
                )
                OutlinedTextField(
                    value = l,
                    onValueChange = { l = it },
                    label = { Text("Uzunluk") },
                    suffix = { Text("m") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = profileTextFieldColors()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(h, w, l) }) {
                Text("Kaydet")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Vazgeç")
            }
        }
    )
}

@Composable
private fun TextInputDialog(
    title: String,
    value: String,
    placeholder: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember(value) { mutableStateOf(value) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ProfileSurface,
        titleContentColor = ProfileText,
        textContentColor = ProfileText,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(placeholder) },
                singleLine = true,
                colors = profileTextFieldColors()
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(text) }) {
                Text("Kaydet")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Vazgeç")
            }
        }
    )
}

@Composable
private fun ThemeModeButton(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clickable(role = Role.RadioButton, onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) ProfileAccentContainer else ProfileSurfaceHigh,
        border = BorderStroke(
            1.dp,
            if (selected) ProfileAccent.copy(alpha = 0.55f) else ProfileBorder
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = ProfileAccent,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .size(18.dp)
                )
            }
            Text(
                text = label,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) ProfileAccent else ProfileText,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ProfileHeroV2(
    vehicleType: String,
    trailerType: String?,
    maxTonnage: Int
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            modifier = Modifier.size(72.dp),
            shape = RoundedCornerShape(999.dp),
            color = ProfileSurfaceHigh,
            border = BorderStroke(1.dp, ProfileBorder)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = getVehicleIcon(vehicleType),
                    contentDescription = null,
                    modifier = Modifier.size(34.dp),
                    tint = ProfileAccent
                )
            }
        }
        Text(
            text = if (vehicleType.isBlank()) "Araç profilini oluştur" else "$vehicleType aracım",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = ProfileText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = listOfNotNull(
                vehicleType.takeIf { it.isNotBlank() },
                trailerType,
                "$maxTonnage ton"
            ).joinToString(" · ").ifBlank { "Tip · dorse · tonaj bilgilerini seç" },
            style = MaterialTheme.typography.bodySmall,
            color = ProfileMuted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            softWrap = true
        )
    }
}


@Composable
private fun ProfileCardV2(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = ProfileSurface,
        tonalElevation = 0.dp,
        border = BorderStroke(1.dp, ProfileBorder)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = ProfileAmber,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            content()
        }
    }
}

@Composable
private fun ProfileRowV2(
    icon: ImageVector,
    label: String,
    value: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clickable(role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = ProfileAccent
        )
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = ProfileText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = ProfileMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "›",
            style = MaterialTheme.typography.titleSmall,
            color = ProfileSubtle,
            maxLines = 1
        )
    }
}

@Composable
private fun ProfileSwitchRowV2(
    icon: ImageVector,
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
    labelMaxLines: Int = 1,
    subtitleMaxLines: Int = 1
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = ProfileAccent
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = ProfileText,
                maxLines = labelMaxLines,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = ProfileSubtle,
                    maxLines = subtitleMaxLines,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = ProfileText,
                checkedTrackColor = ProfileAccent.copy(alpha = 0.72f),
                uncheckedThumbColor = ProfileMuted,
                uncheckedTrackColor = ProfileSurfaceHigh
            )
        )
    }
}

@Composable
private fun RoutePreferenceRow(
    route: RouteItem,
    onRemove: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = ProfileSurfaceHigh,
        border = BorderStroke(1.dp, ProfileBorder)
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 32.dp)
                .padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "${route.fromCity} → ${route.toCity}",
                style = MaterialTheme.typography.labelMedium,
                color = ProfileText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Sil",
                    tint = ProfileMuted,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
private fun OutlineAddButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .heightIn(min = 40.dp)
            .clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, ProfileAccent.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                color = ProfileAccent,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun CurrentCityChip(
    label: String,
    onRemove: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = ProfileSurfaceHigh,
        border = BorderStroke(1.dp, ProfileBorder)
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 32.dp)
                .padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = ProfileText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Sil",
                    tint = ProfileMuted,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
private fun WhatsappSourcesCard(
    sources: List<WhatsappSourceEntity>,
    onAddManualSource: () -> Unit,
    onAllowSource: (WhatsappSourceEntity) -> Unit,
    onBlockSource: (WhatsappSourceEntity) -> Unit,
    onAutoSource: (WhatsappSourceEntity) -> Unit,
    onDeleteSource: (WhatsappSourceEntity) -> Unit
) {
    ProfileCardV2(title = "WHATSAPP GRUPLARI") {
        Text(
            text = "Otomatik yakalanan grupları buradan kapatabilir, özel grup adını elle dinlemeye alabilirsin.",
            style = MaterialTheme.typography.bodySmall,
            color = ProfileMuted,
            softWrap = true
        )

        if (sources.isEmpty()) {
            Text(
                text = "Henüz izlenen grup yok.",
                style = MaterialTheme.typography.bodySmall,
                color = ProfileSubtle,
                modifier = Modifier.padding(vertical = 2.dp)
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                sources.forEach { source ->
                    WhatsappSourceRow(
                        source = source,
                        onAllow = { onAllowSource(source) },
                        onBlock = { onBlockSource(source) },
                        onAuto = { onAutoSource(source) },
                        onDelete = { onDeleteSource(source) }
                    )
                }
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .clickable(role = Role.Button) { onAddManualSource() },
            shape = RoundedCornerShape(10.dp),
            color = Color.Transparent,
            border = BorderStroke(1.dp, ProfileAccent.copy(alpha = 0.45f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "+ Grup adı ekle",
                    style = MaterialTheme.typography.labelLarge,
                    color = ProfileAccent,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun WhatsappSourceRow(
    source: WhatsappSourceEntity,
    onAllow: () -> Unit,
    onBlock: () -> Unit,
    onAuto: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = ProfileSurfaceHigh,
        border = BorderStroke(1.dp, ProfileBorder)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = source.groupName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = ProfileText,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = whatsappSourceSubtitle(source),
                        style = MaterialTheme.typography.labelSmall,
                        color = ProfileMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                SourceModeBadge(source.mode)
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (source.mode != WhatsappSourceMode.ALLOW) {
                    SourceActionChip("Dinle", onAllow)
                }
                if (source.mode != WhatsappSourceMode.BLOCK) {
                    SourceActionChip("Dinleme", onBlock, danger = true)
                }
                if (source.mode != WhatsappSourceMode.AUTO) {
                    SourceActionChip("Otomatik", onAuto)
                }
                // Listeden tamamen kaldır. Kalıcı yasak değil — grup tekrar mesaj
                // atarsa yeniden görünür. "Dinleme" (kırmızı) ile karışmasın diye nötr.
                SourceActionChip("Sil", onDelete, muted = true)
            }
        }
    }
}

@Composable
private fun SourceModeBadge(mode: String) {
    val (label, color) = when (mode) {
        WhatsappSourceMode.ALLOW -> "Elle" to ProfileAccent
        WhatsappSourceMode.BLOCK -> "Kapalı" to ProfileDanger
        else -> "Oto" to ProfileAmber
    }
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = color.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.35f))
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
private fun SourceActionChip(
    label: String,
    onClick: () -> Unit,
    danger: Boolean = false,
    muted: Boolean = false
) {
    val color = when {
        muted -> ProfileMuted
        danger -> ProfileDanger
        else -> ProfileAccent
    }
    Surface(
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, color.copy(alpha = 0.38f))
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

private fun whatsappSourceSubtitle(source: WhatsappSourceEntity): String {
    val countText = if (source.seenCount > 0) "${source.seenCount} kez görüldü" else "Elle eklendi"
    val decisionText = when (source.lastDecision) {
        "ALLOWED" -> "son karar: dinlendi"
        "BLOCKED" -> "son karar: kesildi"
        else -> "kural bekliyor"
    }
    return "$countText · $decisionText"
}

@Composable
private fun RegionChipV2(
    text: String,
    muted: Boolean = false,
    onClick: () -> Unit = {}
) {
    Surface(
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (muted) Color.Transparent else ProfileAccentContainer,
        border = BorderStroke(1.dp, if (muted) ProfileBorder else ProfileAccent.copy(alpha = 0.24f))
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            color = if (muted) ProfileSubtle else ProfileAccent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun calculateVolumeLabel(height: String, width: String, length: String): String {
    val h = height.toFloatOrNull()
    val w = width.toFloatOrNull()
    val l = length.toFloatOrNull()
    val volume = if (h != null && w != null && l != null) h * w * l else null
    return volume?.let { "%.0f m³".format(it) } ?: "Girilmedi"
}

private fun nextOption(options: List<String>, current: String): String {
    if (options.isEmpty()) return current
    val index = options.indexOf(current)
    return options[(index + 1).floorMod(options.size)]
}

private fun Int.floorMod(other: Int): Int = ((this % other) + other) % other

@Composable
private fun profileTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    focusedContainerColor = ProfileSurface,
    unfocusedContainerColor = ProfileSurface,
    cursorColor = MaterialTheme.colorScheme.primary,
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
    unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant
)

fun getVehicleIcon(type: String): ImageVector {
    return when (type) {
        "Tır", "TIR" -> Icons.Default.LocalShipping
        "Kamyon" -> Icons.Default.LocalShipping
        "Kamyonet" -> Icons.Default.AirportShuttle
        "Çekici" -> Icons.Default.RvHookup
        "Lowbed" -> Icons.Default.ViewStream
        else -> Icons.Default.DirectionsCar
    }
}
