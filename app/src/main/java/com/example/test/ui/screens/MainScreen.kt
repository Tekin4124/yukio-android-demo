@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)

package com.example.test.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExtendedFloatingActionButton
import com.example.test.data.remote.ManualListingsRepository
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.text.KeyboardOptions
import kotlinx.coroutines.withTimeoutOrNull
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.test.BuildConfig
import com.example.test.ai.InteractionType
import com.example.test.ai.MatchResult
import com.example.test.ai.RiskLevel
import com.example.test.data.local.LoadOfferEntity
import com.example.test.data.local.UserProfileEntity
import com.example.test.data.local.deliveryStops
import com.example.test.data.local.displayTonnage
import com.example.test.data.local.effectiveMaxTonnage
import com.example.test.data.local.pickupStops
import com.example.test.ui.components.EmptyState
import com.example.test.ui.components.LoadCard
import com.example.test.ui.components.RoutePreview
import com.example.test.ui.components.YukioStartupLoading
import com.example.test.ui.format.displayDeliveryStops
import com.example.test.ui.format.displayPickupStops
import com.example.test.ui.format.displayPlace
import com.example.test.ui.format.displayRoute
import com.example.test.ui.format.formatPlaceName
import com.example.test.ui.format.formatVehicleType
import com.example.test.telemetry.ContentlessTelemetry
import com.example.test.telemetry.TelemetryEvents
import com.example.test.viewmodel.LoadDateFilter
import com.example.test.viewmodel.LoadListFilter
import com.example.test.viewmodel.LoadListUiState
import com.example.test.viewmodel.LoadListViewModel
import com.example.test.viewmodel.SortOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
private fun detailThemeColor(dark: Color, light: Color): Color =
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) dark else light

// Koyu tema renkleri son sağlam tasarımdaki değerlerdir. Beyaz tema bunların
// yerine Material renklerini kullanır; tema eklemek eski görünümü değiştirmemeli.
private val DetailBackground: Color
    @Composable get() = detailThemeColor(Color(0xFF090B0C), MaterialTheme.colorScheme.background)
private val DetailSurface: Color
    @Composable get() = detailThemeColor(Color(0xFF111417), MaterialTheme.colorScheme.surface)
private val DetailSurfaceHigh: Color
    @Composable get() = detailThemeColor(Color(0xFF171B1E), MaterialTheme.colorScheme.surfaceContainerHigh)
private val DetailBorder: Color
    @Composable get() = detailThemeColor(Color(0xFF263036), MaterialTheme.colorScheme.outlineVariant)
private val DetailTextPrimary: Color
    @Composable get() = detailThemeColor(Color(0xFFE8ECEE), MaterialTheme.colorScheme.onSurface)
private val DetailTextMuted: Color
    @Composable get() = detailThemeColor(Color(0xFF9AA4AA), MaterialTheme.colorScheme.onSurfaceVariant)
private val DetailTextSubtle: Color
    @Composable get() = detailThemeColor(Color(0xFF68747B), MaterialTheme.colorScheme.outline)
private val DetailAccent: Color
    @Composable get() = detailThemeColor(Color(0xFF8DB3A7), MaterialTheme.colorScheme.primary)
private val DetailAccentContainer: Color
    @Composable get() = detailThemeColor(Color(0xFF182622), MaterialTheme.colorScheme.primaryContainer)
private val DetailAmber: Color
    @Composable get() = detailThemeColor(Color(0xFFBDAA74), MaterialTheme.colorScheme.secondary)
private val DetailDanger: Color
    @Composable get() = detailThemeColor(Color(0xFFD47777), MaterialTheme.colorScheme.error)
private val DetailSourceHighlight: Color
    @Composable get() = detailThemeColor(Color(0x6639CFFF), Color(0x333B82F6))
private val DetailOnAccent: Color
    @Composable get() = detailThemeColor(Color(0xFF07110E), MaterialTheme.colorScheme.onPrimary)

@Composable
fun MainScreen(
    openLoadId: Long? = null,
    topBarUsesStatusInsets: Boolean = true,
    onOpenLoadHandled: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    onNavigateToDebugPanel: () -> Unit = {},
    onCreateListing: () -> Unit = {},
    onOpenLoadDetails: (LoadOfferEntity) -> Unit = {},
) {
    LoadListScreen(
        openLoadId = openLoadId,
        topBarUsesStatusInsets = topBarUsesStatusInsets,
        onOpenLoadHandled = onOpenLoadHandled,
        onNavigateToProfile = onNavigateToProfile,
        onNavigateToDebugPanel = onNavigateToDebugPanel,
        onCreateListing = onCreateListing,
        onOpenLoadDetails = onOpenLoadDetails,
    )
}

@Composable
fun NotificationPermissionScreen(onRequestPermission: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background) {
        EmptyState(
            title = "Bildirim izni gerekli",
            message = "WhatsApp ilanlarını yakalamak için bildirim erişimini açman gerekiyor.",
            icon = Icons.Default.Notifications,
            actionLabel = "Ayarlara Git",
            onAction = onRequestPermission,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun LoadListScreen(
    viewModel: LoadListViewModel = viewModel(),
    openLoadId: Long? = null,
    topBarUsesStatusInsets: Boolean = true,
    onOpenLoadHandled: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    onNavigateToDebugPanel: () -> Unit = {},
    onCreateListing: () -> Unit = {},
    onOpenLoadDetails: (LoadOfferEntity) -> Unit = {},
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val sharedError by ManualListingsRepository.error.collectAsStateWithLifecycle()
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                ManualListingsRepository.refresh()
                delay(30000)
            }
        }
    }


    if (uiState.isLoading && uiState.loads.isEmpty()) {
        YukioStartupLoading()
        return
    }

    var showDemoInput by remember { mutableStateOf(false) }
    if(showDemoInput) DemoInputDialog(onDismiss={showDemoInput=false})
    var showVehicleDialog by remember { mutableStateOf(false) }
    var showCityDialog by remember { mutableStateOf(false) }
    var showSourceGroupDialog by remember { mutableStateOf(false) }
    var showDebugPasswordDialog by remember { mutableStateOf(false) }
    var debugPasswordInput by remember { mutableStateOf("") }
    var debugPasswordError by remember { mutableStateOf(false) }
    val debugPanelEnabled = false

    LaunchedEffect(openLoadId, uiState.loads) {
        val loadId = openLoadId ?: return@LaunchedEffect
        val load = uiState.loads.firstOrNull { it.id == loadId } ?: return@LaunchedEffect
        onOpenLoadDetails(load)
        viewModel.recordInteraction(load, InteractionType.VIEWED)
        ContentlessTelemetry.record(context, TelemetryEvents.funnel("match_viewed"))
        onOpenLoadHandled()
    }

    if (showVehicleDialog) {
        VehicleTypeDialog(
            selected = uiState.filter.vehicleType ?: "Hepsi",
            options = uiState.availableVehicleTypes,
            onSelected = {
                viewModel.setVehicleTypeFilter(it)
                showVehicleDialog = false
            },
            onDismiss = { showVehicleDialog = false }
        )
    }

    if (showCityDialog) {
        CityFilterDialog(
            selected = uiState.filter.city ?: "Hepsi",
            options = uiState.availableCities,
            onSelected = {
                viewModel.setCityFilter(it)
                showCityDialog = false
            },
            onDismiss = { showCityDialog = false }
        )
    }

    if (showSourceGroupDialog) {
        SourceGroupFilterDialog(
            selected = uiState.filter.sourceGroup ?: "Hepsi",
            options = uiState.availableSourceGroups,
            onSelected = {
                viewModel.setSourceGroupFilter(it)
                showSourceGroupDialog = false
            },
            onDismiss = { showSourceGroupDialog = false }
        )
    }

    if (debugPanelEnabled && showDebugPasswordDialog) {
        AlertDialog(
            onDismissRequest = {
                showDebugPasswordDialog = false
                debugPasswordInput = ""
                debugPasswordError = false
            },
            title = { Text("Şifre") },
            text = {
                Column {
                    OutlinedTextField(
                        value = debugPasswordInput,
                        onValueChange = {
                            debugPasswordInput = it
                            debugPasswordError = false
                        },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        isError = debugPasswordError,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (debugPasswordError) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Hatalı şifre",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (debugPasswordInput == "1234") {
                        showDebugPasswordDialog = false
                        debugPasswordInput = ""
                        debugPasswordError = false
                        onNavigateToDebugPanel()
                    } else {
                        debugPasswordError = true
                    }
                }) { Text("Tamam") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDebugPasswordDialog = false
                    debugPasswordInput = ""
                    debugPasswordError = false
                }) { Text("İptal") }
            }
        )
    }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onCreateListing,
                icon = { Icon(Icons.Default.Add, null) }, text = { Text("İlan ekle") })
        },
        topBar = {
            TopAppBar(
                modifier = if (debugPanelEnabled) {
                    Modifier.debugLongPress { showDebugPasswordDialog = true }
                } else {
                    Modifier
                },
                title = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                    ) {
                        Text(
                            text = "Yükio Demo",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (uiState.userProfile != null) {
                                "${uiState.loads.size} aktif · ${uiState.filteredLoads.size} size uygun"
                            } else {
                                "${uiState.loads.size} aktif · ${uiState.filteredLoads.size} gösteriliyor"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                actions = {
                    TextButton(onClick={showDemoInput=true}){Text("API")}
                    val refreshScope = rememberCoroutineScope()
                    IconButton(onClick = { refreshScope.launch { ManualListingsRepository.refresh() } }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Yükio ilanlarını yenile")
                    }
                    IconButton(onClick = onNavigateToProfile) {
                        Icon(Icons.Default.Person, contentDescription = "Profil")
                    }
                },
                windowInsets = if (topBarUsesStatusInsets) {
                    TopAppBarDefaults.windowInsets
                } else {
                    WindowInsets(0.dp)
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            if (sharedError != null) {
                item { Text(sharedError.orEmpty(), modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }

            stickyHeader {
                LoadListHeader(
                    uiState = uiState,
                    onCityClick = { showCityDialog = true },
                    onVehicleClick = { showVehicleDialog = true },
                    onSourceGroupClick = { showSourceGroupDialog = true },
                    onDateFilterClick = { viewModel.toggleDateFilter() },
                    onBanaGoreToggle = { viewModel.toggleBanaGore() },
                    onSortOrderSelected = { viewModel.setSortOrder(it) },
                    onClearFilters = { viewModel.clearFilters() }
                )
            }

            if (!uiState.isProfileSet) {
                item(key = "profile-fallback-banner") {
                    MissingProfileBanner(
                        onAddProfileClick = onNavigateToProfile,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }

            if (uiState.filteredLoads.isEmpty()) {
                item {
                    val todayOnly = uiState.filter.dateFilter == LoadDateFilter.TODAY
                    val noLoadsTitle = if (todayOnly) "Bugün gelen ilan yok" else "Henüz ilan yok"
                    val noLoadsMessage = if (todayOnly) {
                        "Eski ilanlar kayıtlı duruyor. Tüm kayıtları açarak arşivi görebilirsin."
                    } else {
                        "WhatsApp gruplarından yakalanan yükler burada listelenecek."
                    }
                    EmptyState(
                        title = if (uiState.loads.isEmpty()) noLoadsTitle else "Filtreye uygun ilan yok",
                        message = if (uiState.loads.isEmpty()) {
                            noLoadsMessage
                        } else {
                            "Filtreleri gevşeterek daha fazla ilan görebilirsin."
                        },
                        actionLabel = if (uiState.loads.isEmpty() && todayOnly) {
                            "Tüm Kayıtları Göster"
                        } else if (uiState.loads.isEmpty()) {
                            null
                        } else {
                            "Filtreleri Temizle"
                        },
                        secondaryAction = uiState.loads.isNotEmpty(),
                        onAction = if (uiState.loads.isEmpty() && todayOnly) {
                            { viewModel.setDateFilter(LoadDateFilter.ALL) }
                        } else if (uiState.loads.isEmpty()) {
                            null
                        } else {
                            { viewModel.clearFilters() }
                        },
                        modifier = Modifier
                            .fillParentMaxHeight(0.74f)
                            .padding(horizontal = 16.dp)
                    )
                }
            } else {
                items(uiState.filteredLoads, key = { it.id }) { load ->
                    LoadCard(
                        load = load,
                        matchResult = uiState.matchResults[load.id],
                        parserSource = uiState.parserSources[load.id],
                        onClick = {
                            onOpenLoadDetails(load)
                            viewModel.recordInteraction(load, InteractionType.VIEWED)
                            ContentlessTelemetry.record(
                                context,
                                TelemetryEvents.funnel("match_viewed")
                            )
                        },
                        onCallClick = {
                            if (load.phone.isNotBlank()) {
                                viewModel.recordInteraction(load, InteractionType.CALLED)
                                ContentlessTelemetry.record(
                                    context,
                                    TelemetryEvents.funnel("call_tapped")
                                )
                                Unit // Demo contacts are never dialled.
                            }
                        },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MissingProfileBanner(
    onAddProfileClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
        ),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Default.LocalShipping,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = "Aracını kaydet, eşleşme skorları gelsin",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        softWrap = true
                    )
                    Text(
                        text = "Profilini ekledikten sonra ilanları aracına ve tercih ettiğin güzergahlara göre süzebilirsin.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.74f),
                        softWrap = true
                    )
                }
            }
            Button(
                onClick = onAddProfileClick,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Aracımı Ekle", maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun LoadListHeader(
    uiState: LoadListUiState,
    onCityClick: () -> Unit,
    onVehicleClick: () -> Unit,
    onSourceGroupClick: () -> Unit,
    onDateFilterClick: () -> Unit,
    onBanaGoreToggle: () -> Unit,
    onSortOrderSelected: (SortOrder) -> Unit,
    onClearFilters: () -> Unit
) {
    var moreFiltersExpanded by remember { mutableStateOf(false) }
    Surface(
        color = MaterialTheme.colorScheme.background.copy(alpha = 0.97f),
        tonalElevation = 1.dp,
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterPill(
                label = dateFilterLabel(uiState.filter.dateFilter),
                selected = uiState.filter.dateFilter == LoadDateFilter.TODAY,
                onClick = onDateFilterClick
            )
            if (uiState.userProfile != null) {
                val banaGoreOn = uiState.filter.onlyMatchingProfile && uiState.filter.onlyFromMyCity
                val myCityCount = uiState.userProfile.currentCities.size
                val banaGoreLabel = if (myCityCount == 0) "Bana göre*" else "Bana göre"
                FilterPill(
                    label = banaGoreLabel,
                    selected = banaGoreOn,
                    onClick = onBanaGoreToggle
                )
            }
            val manualCount = listOfNotNull(
                uiState.filter.city,
                uiState.filter.vehicleType,
                uiState.filter.sourceGroup
            ).size + if (uiState.filter.sortBy != SortOrder.NEWEST) 1 else 0
            Box {
                FilterPill(
                    label = if (manualCount > 0) "Filtre · $manualCount" else "Filtre",
                    selected = manualCount > 0,
                    onClick = { moreFiltersExpanded = true }
                )
                DropdownMenu(
                    expanded = moreFiltersExpanded,
                    onDismissRequest = { moreFiltersExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Şehir: ${uiState.filter.city ?: "Hepsi"}", softWrap = true) },
                        onClick = {
                            moreFiltersExpanded = false
                            onCityClick()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Araç: ${uiState.filter.vehicleType ?: "Hepsi"}", softWrap = true) },
                        onClick = {
                            moreFiltersExpanded = false
                            onVehicleClick()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Grup: ${uiState.filter.sourceGroup ?: "Hepsi"}", softWrap = true) },
                        onClick = {
                            moreFiltersExpanded = false
                            onSourceGroupClick()
                        }
                    )
                    HorizontalDivider()
                    SortOrder.entries.forEach { sortOrder ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = if (uiState.filter.sortBy == sortOrder) {
                                        "✓ ${sortOrderLabel(sortOrder)}"
                                    } else {
                                        sortOrderLabel(sortOrder)
                                    }
                                )
                            },
                            onClick = {
                                moreFiltersExpanded = false
                                onSortOrderSelected(sortOrder)
                            }
                        )
                    }
                }
            }
            if (uiState.hasActiveFilter()) {
                FilterPill(
                    label = "Temizle",
                    selected = false,
                    onClick = onClearFilters,
                    accent = true
                )
            }
        }
    }
}

private fun sortOrderLabel(sortOrder: SortOrder): String = when (sortOrder) {
    SortOrder.NEWEST -> "Sırala: En yeni"
    SortOrder.MATCH_SCORE -> "Sırala: En uygun"
    SortOrder.PRICE_HIGH -> "Sırala: Fiyat yüksek"
    SortOrder.DISTANCE_NEAR -> "Sırala: Mesafe yakın"
}

@Composable
private fun FilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    accent: Boolean = false
) {
    val accentColor = MaterialTheme.colorScheme.error
    val bg = when {
        accent -> accentColor.copy(alpha = 0.12f)
        selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val borderColor = when {
        accent -> accentColor.copy(alpha = 0.45f)
        selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
        else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    }
    val textColor = when {
        accent -> accentColor
        selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        modifier = Modifier
            .heightIn(min = 28.dp)
            .clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        color = bg,
        border = BorderStroke(width = 1.dp, color = borderColor)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false
            )
        }
    }
}

@Composable
fun LoadDetailScreen(
    load: LoadOfferEntity,
    matchResult: MatchResult?,
    parseFeedback: InteractionType?,
    onParseFeedback: (InteractionType) -> Unit,
    onCallClick: () -> Unit,
    onDismiss: () -> Unit,
    siblingLoads: List<LoadOfferEntity> = emptyList()
) {
    val dateText = remember(load.createdAt) {
        SimpleDateFormat("dd MMM yyyy HH:mm", Locale("tr", "TR")).format(Date(load.createdAt))
    }
    var showRawMessage by remember { mutableStateOf(false) }

    BackHandler(onBack = onDismiss)

        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(DetailBackground),
            color = DetailBackground,
            contentColor = DetailTextPrimary
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
            ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DetailTopBarV2()
                DetailHeroV2(load = load)
                if (load.priceTry != null) {
                    DetailPriceCardV2(load = load)
                }
                DetailInfoCardV2(load = load, dateText = dateText)
                DetailRawMessageBlockV2(
                    load = load,
                    siblingLoads = siblingLoads,
                    expanded = showRawMessage,
                    onToggle = { showRawMessage = !showRawMessage }
                )
                DetailParseFeedbackV2(
                    selected = parseFeedback,
                    onSelected = onParseFeedback
                )
            }

            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter),
                color = DetailBackground.copy(alpha = 0.96f),
                tonalElevation = 0.dp
            ) {
                Button(
                    onClick = onCallClick,
                    enabled = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 14.dp)
                        .heightIn(min = 50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DetailAccent,
                        contentColor = DetailOnAccent
                    )
                ) {
                    Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "DEMO",
                        style = MaterialTheme.typography.titleSmall,
                        fontSize = detailFixedSp(15),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = load.phone.takeIf { it.isNotBlank() } ?: "Telefon yok",
                        style = MaterialTheme.typography.labelMedium,
                        fontSize = detailFixedSp(13),
                        color = DetailOnAccent.copy(alpha = 0.72f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}


@Composable
private fun DetailParseFeedbackV2(
    selected: InteractionType?,
    onSelected: (InteractionType) -> Unit
) {
    DetailCardV2(title = "İlan bilgileri doğru mu?") {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ParseFeedbackOptionV2(
                label = "Doğru",
                selected = selected == InteractionType.PARSE_CORRECT,
                onClick = { onSelected(InteractionType.PARSE_CORRECT) }
            )
            ParseFeedbackOptionV2(
                label = "Yanlış",
                selected = selected == InteractionType.PARSE_WRONG,
                onClick = { onSelected(InteractionType.PARSE_WRONG) }
            )
            ParseFeedbackOptionV2(
                label = "Eksik",
                selected = selected == InteractionType.PARSE_MISSING,
                onClick = { onSelected(InteractionType.PARSE_MISSING) }
            )
        }
    }
}

@Composable
private fun ParseFeedbackOptionV2(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(999.dp),
        color = if (selected) DetailAccentContainer else DetailSurfaceHigh,
        border = BorderStroke(
            1.dp,
            if (selected) DetailAccent.copy(alpha = 0.55f) else DetailBorder
        )
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) DetailAccent else DetailTextMuted,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun DetailExpandableTextBlockV2(
    expanded: Boolean,
    collapsedLabel: String,
    expandedLabel: String,
    body: String,
    onToggle: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onToggle),
        shape = RoundedCornerShape(10.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, DetailBorder)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (expanded) expandedLabel else collapsedLabel,
                style = MaterialTheme.typography.labelMedium,
                fontSize = detailFixedSp(13),
                color = DetailTextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (expanded) {
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = detailFixedSp(11),
                    color = DetailTextMuted,
                    softWrap = true
                )
            }
        }
    }
}

@Composable
private fun DetailRawMessageBlockV2(
    load: LoadOfferEntity,
    expanded: Boolean,
    onToggle: () -> Unit,
    siblingLoads: List<LoadOfferEntity> = emptyList()
) {
    val sourceLines = remember(load.id, load.rawMessage, siblingLoads.size) {
        highlightedRawMessageLines(load, siblingLoads)
    }
    val scrollState = rememberScrollState()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, DetailBorder)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button, onClick = onToggle),
                text = if (load.sourceGroup == com.example.test.data.remote.SHARED_LISTING_SOURCE) { if(expanded) "İlan metnini gizle" else "Yayınlanan ilan metnini göster" } else { if(expanded) "Orijinal WhatsApp mesajını gizle" else "Orijinal WhatsApp mesajını göster" },
                style = MaterialTheme.typography.labelMedium,
                fontSize = detailFixedSp(13),
                color = DetailTextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (expanded) {
                // Güvenilir kaynak satırı bulunamadıysa sahte vurgu yapılmaz;
                // bunun bilinçli olduğu kullanıcıya açıkça söylenir.
                if (sourceLines.isNotEmpty() && sourceLines.none { it.highlighted }) {
                    Text(
                        text = "Bu ilan için kaynak satır işareti bulunamadı; " +
                            "mesajın tamamı işaretsiz gösteriliyor.",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = detailFixedSp(12),
                        color = DetailTextMuted,
                        softWrap = true
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(DetailSurfaceHigh.copy(alpha = 0.72f))
                        .verticalScroll(scrollState)
                        .padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    sourceLines.forEach { line ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(6.dp),
                            color = if (line.highlighted) DetailSourceHighlight else Color.Transparent
                        ) {
                            Text(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp),
                                text = wrapLongRawTokens(line.text),
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = detailFixedSp(12),
                                color = if (line.highlighted) DetailTextPrimary else DetailTextMuted,
                                fontWeight = if (line.highlighted) FontWeight.SemiBold else FontWeight.Normal,
                                softWrap = true,
                                overflow = TextOverflow.Visible
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailTopBarV2() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "İlan detayı",
            style = MaterialTheme.typography.labelLarge,
            fontSize = detailFixedSp(13),
            fontWeight = FontWeight.Bold,
            color = DetailTextPrimary,
            maxLines = 1
        )
    }
}

@Composable
private fun DetailHeroV2(load: LoadOfferEntity) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = DetailSurface,
        border = BorderStroke(1.dp, DetailBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DetailRouteStop(
                label = "Alış",
                value = load.displayPickupStops(),
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "→",
                style = MaterialTheme.typography.titleMedium,
                fontSize = detailFixedSp(18),
                fontWeight = FontWeight.SemiBold,
                color = DetailAccent,
                maxLines = 1
            )
            DetailRouteStop(
                label = "Teslim",
                value = load.displayDeliveryStops(),
                modifier = Modifier.weight(1f),
                alignEnd = true
            )
        }
    }
}

@Composable
private fun DetailRouteStop(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    alignEnd: Boolean = false
) {
    Column(
        modifier = modifier,
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = detailFixedSp(11),
            color = DetailTextSubtle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontSize = detailFixedSp(18),
            fontWeight = FontWeight.SemiBold,
            color = DetailTextPrimary,
            maxLines = 2,
            softWrap = true,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DetailPriceCardV2(load: LoadOfferEntity) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = DetailAccentContainer,
        border = BorderStroke(1.dp, DetailAccent.copy(alpha = 0.35f))
    ) {
        Text(
            text = "%,d ₺".format(load.priceTry ?: 0),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            style = MaterialTheme.typography.titleMedium,
            fontSize = detailFixedSp(20),
            fontWeight = FontWeight.Bold,
            color = DetailAccent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DetailInfoCardV2(
    load: LoadOfferEntity,
    dateText: String
) {
    val fields = detailFieldsForLoad(load, dateText)
    val sourceGroup = fields.firstOrNull { it.label == "Grup" }
    val infoFields = fields.filterNot { it.label == "Grup" }
    if (fields.isNotEmpty()) {
        DetailCardV2(title = "Bilgiler") {
            if (infoFields.isNotEmpty()) {
                DetailFieldGridV2(fields = infoFields)
            }
            sourceGroup?.let { source ->
                if (infoFields.isNotEmpty()) {
                    HorizontalDivider(color = DetailBorder)
                }
                DetailSourceGroupRowV2(groupName = source.value)
            }
        }
    }
}

@Composable
private fun DetailSourceGroupRowV2(groupName: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = DetailSurfaceHigh
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Grup",
                style = MaterialTheme.typography.labelSmall,
                fontSize = detailFixedSp(11),
                color = DetailTextSubtle,
                maxLines = 1
            )
            Text(
                text = groupName,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                fontSize = detailFixedSp(13),
                fontWeight = FontWeight.Medium,
                color = DetailTextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false
            )
        }
    }
}

@Composable
private fun DetailInfoRowV2(
    left: Pair<String, String>?,
    right: Pair<String, String>?
) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        if (left != null) {
            DetailInfoItemV2(left.first, left.second, Modifier.weight(1f))
        } else {
            Spacer(Modifier.weight(1f))
        }
        if (right != null) {
            DetailInfoItemV2(right.first, right.second, Modifier.weight(1f), alignEnd = true)
        } else {
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun DetailFieldGridV2(fields: List<DetailFieldV2>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        var pending: DetailFieldV2? = null
        fields.forEach { field ->
            if (field.isWide) {
                pending?.let {
                    DetailInfoRowV2(left = it.label to it.value, right = null)
                    pending = null
                }
                DetailInfoFullWidthItemV2(field = field)
            } else {
                val previous = pending
                if (previous == null) {
                    pending = field
                } else {
                    DetailInfoRowV2(
                        left = previous.label to previous.value,
                        right = field.label to field.value
                    )
                    pending = null
                }
            }
        }
        pending?.let { DetailInfoRowV2(left = it.label to it.value, right = null) }
    }
}

@Composable
private fun DetailInfoFullWidthItemV2(field: DetailFieldV2) {
    DetailInfoItemV2(
        label = field.label,
        value = field.value,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun DetailInfoItemV2(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    alignEnd: Boolean = false
) {
    Column(
        modifier = modifier,
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = detailFixedSp(11),
            color = DetailTextSubtle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontSize = detailFixedSp(15),
            color = DetailTextPrimary,
            fontWeight = FontWeight.Medium,
            maxLines = if (alignEnd) 2 else 3,
            overflow = TextOverflow.Ellipsis,
            softWrap = true
        )
    }
}

@Composable
private fun DetailCardV2(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = DetailSurface,
        border = BorderStroke(1.dp, DetailBorder)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontSize = detailFixedSp(12),
                fontWeight = FontWeight.SemiBold,
                color = DetailTextSubtle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            content()
        }
    }
}

@Composable
private fun detailFixedSp(value: Int): TextUnit {
    return value.sp
}

internal data class RawMessageDisplayLine(
    val text: String,
    val highlighted: Boolean
)

private data class RawMessageSourceLine(
    val text: String,
    val paragraph: Int
)

internal fun highlightedRawMessageLines(
    load: LoadOfferEntity,
    siblingLoads: List<LoadOfferEntity> = emptyList()
): List<RawMessageDisplayLine> {
    val originalLines = load.rawMessage
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .split('\n')

    var paragraph = 0
    val sourceLines = buildList<RawMessageSourceLine> {
        originalLines.forEach { line ->
            if (line.isBlank()) {
                paragraph++
            } else {
                val containsPhonePlaceholder = RawPhonePlaceholder.containsMatchIn(line)
                val cleanLine = RawPhonePlaceholder.replace(line, "").trimEnd()
                if (cleanLine.isBlank()) {
                    if (containsPhonePlaceholder &&
                        lastOrNull()?.paragraph == paragraph &&
                        isRawContactLabel(last().text)
                    ) {
                        removeAt(lastIndex)
                    }
                } else if (!(containsPhonePlaceholder && isRawContactLabel(cleanLine))) {
                    add(RawMessageSourceLine(text = cleanLine, paragraph = paragraph))
                }
            }
        }
    }

    if (sourceLines.isEmpty()) return emptyList()

    val lines = sourceLines.map { it.text }
    val foreignTerms = foreignRouteTerms(load, siblingLoads)
    val sourceIndexes = if (lines.size == 1) {
        setOf(0)
    } else {
        rawMessageSelectedSourceIndexes(load, lines, foreignTerms)
    }
    val highlightedIndexes =
        rawMessageHighlightBlockIndexes(load, sourceLines, sourceIndexes, foreignTerms)
    return sourceLines.mapIndexed { index, line ->
        RawMessageDisplayLine(text = line.text, highlighted = index in highlightedIndexes)
    }
}

/**
 * Aynı mesajdaki DİĞER ilanların rota terimleri. Seçilen ilanın kendi
 * terimleri çıkarılır; kalanları içeren satırlar başka ilana aittir ve
 * asla bu ilan için boyanmaz.
 */
private fun foreignRouteTerms(
    load: LoadOfferEntity,
    siblingLoads: List<LoadOfferEntity>
): Set<String> {
    val own = (rawPickupTerms(load) + rawDeliveryTerms(load)).toSet()
    return siblingLoads
        .flatMap { sibling ->
            rawPickupTerms(sibling) + rawDeliveryTerms(sibling)
        }
        .toSet() - own
}

/**
 * Satır başka bir ilana mı ait? Üç kanıt sırayla denenir:
 * 1. Kardeş ilan rota terimi içeriyor ama seçilen ilanınkini içermiyor.
 * 2. "X → Y" / "X - Y" rota biçiminde ve bu ilana dair hiçbir iz taşımıyor.
 * 3. Eski anahtar kelime + bilinen şehir sezgiseli (kardeş bilgisi yokken).
 */
private fun isForeignRouteLine(
    load: LoadOfferEntity,
    line: String,
    foreignTerms: Set<String>
): Boolean {
    val normalizedLine = normalizeRawMessageSearch(line)
    if (normalizedLine.isBlank()) return false

    // Kardeş ilanın ili geçiyor ama bu ilanın varış ili geçmiyorsa satır
    // diğer ilana aittir. (Ortak çıkış ili tek başına satırı bizim yapmaz:
    // "İSTANBUL → KONYA", İstanbul→Ankara ilanının satırı değildir.)
    val containsOwnTo = rawDeliveryTerms(load)
        .any { containsRawTerm(normalizedLine, it) }
    if (foreignTerms.any { containsRawTerm(normalizedLine, it) }) {
        return !containsOwnTo
    }

    val containsOwnFrom = rawPickupTerms(load)
        .any { containsRawTerm(normalizedLine, it) }
    if (containsOwnTo || containsOwnFrom) return false

    val routeShape = Regex("""\p{L}{3,}\s*(?:→|=>|->|-->|—|–|-)\s*\p{L}{3,}""")
        .containsMatchIn(line)
    if (routeShape && rawMessageLineScore(load, line) == 0) return true

    return looksLikeDifferentRouteLine(load, line)
}

private val RawPhonePlaceholder = Regex(
    pattern = """\[\s*TELEFON(?:_\d+)?\s*]""",
    option = RegexOption.IGNORE_CASE
)

private fun isRawContactLabel(line: String): Boolean {
    val normalized = normalizeRawMessageSearch(line)
    return normalized.isBlank() || normalized in setOf(
        "ILETISIM", "IRTIBAT", "TELEFON", "TEL", "GSM", "ARA", "ARAYIN"
    )
}

private const val RAW_HIGHLIGHT_MAX_EXTRA_LINES = 4

private fun rawMessageHighlightBlockIndexes(
    load: LoadOfferEntity,
    lines: List<RawMessageSourceLine>,
    sourceIndexes: Set<Int>,
    foreignTerms: Set<String>
): Set<Int> {
    if (sourceIndexes.isEmpty()) return emptySet()

    val start = sourceIndexes.min()
    var end = sourceIndexes.max()
    val paragraph = lines[start].paragraph

    // Rota satırının devamındaki araç/tonaj/fiyat satırlarını kapsa; ama
    // başka ilana ait satırda dur ve sınırsız aşağı boyama yapma.
    while (end < lines.lastIndex && end - sourceIndexes.max() < RAW_HIGHLIGHT_MAX_EXTRA_LINES) {
        val next = end + 1
        if (lines[next].paragraph != paragraph) break
        if (next > sourceIndexes.max() &&
            isForeignRouteLine(load, lines[next].text, foreignTerms)
        ) {
            break
        }
        end = next
    }

    return (start..end).filterNot { index ->
        index != start && isForeignRouteLine(load, lines[index].text, foreignTerms)
    }.toSet()
}

private fun rawMessageSelectedSourceIndexes(
    load: LoadOfferEntity,
    lines: List<String>,
    foreignTerms: Set<String>
): Set<Int> {
    val directRouteLines = lines.mapIndexedNotNull { index, line ->
        val normalizedLine = normalizeRawMessageSearch(line)
        val hasFrom = rawPickupTerms(load).any { containsRawTerm(normalizedLine, it) }
        val hasTo = rawDeliveryTerms(load).any { containsRawTerm(normalizedLine, it) }
        if (hasFrom && hasTo) index to rawMessageLineScore(load, line) else null
    }

    directRouteLines
        .maxWithOrNull(compareBy<Pair<Int, Int>> { it.second }.thenBy { -it.first })
        ?.takeIf { it.second >= 5 }
        ?.let { return setOf(it.first) }

    var bestIndexes = emptySet<Int>()
    var bestScore = 0
    val maxWindowSize = 5
    for (start in lines.indices) {
        if (lines[start].isBlank()) continue
        val endLimit = minOf(lines.lastIndex, start + maxWindowSize - 1)
        for (end in start..endLimit) {
            val blockLines = lines.subList(start, end + 1)
            if (blockLines.all { it.isBlank() }) continue
            val block = blockLines.joinToString(" ")
            val blockScore = rawMessageLineScore(load, block)
            val indexes = (start..end)
                .filter { index ->
                    val line = lines[index]
                    line.isNotBlank() &&
                        rawMessageLineScore(load, line) > 0 &&
                        !isForeignRouteLine(load, line, foreignTerms)
                }
                .toSet()
            if (indexes.isEmpty() || blockScore < 5) continue

            val narrowerThanCurrent = bestIndexes.isEmpty() || indexes.size < bestIndexes.size
            if (blockScore > bestScore || (blockScore == bestScore && narrowerThanCurrent)) {
                bestScore = blockScore
                bestIndexes = indexes
            }
        }
    }

    return bestIndexes
}

private fun rawMessageLineScore(load: LoadOfferEntity, line: String): Int {
    val normalizedLine = normalizeRawMessageSearch(line)
    if (normalizedLine.isBlank()) return 0

    val fromTerms = rawPickupTerms(load)
    val toTerms = rawDeliveryTerms(load)
    val hasFrom = fromTerms.any { containsRawTerm(normalizedLine, it) }
    val hasTo = toTerms.any { containsRawTerm(normalizedLine, it) }

    var score = 0
    if (hasFrom) score += 3
    if (hasTo) score += 4
    if (hasFrom && hasTo) score += 5

    load.pickupStops().mapNotNull { it.district }.distinct().forEach { district ->
        if (containsRawTerm(normalizedLine, district)) score += 2
    }
    load.deliveryStops().mapNotNull { it.district }.distinct().forEach { district ->
        if (containsRawTerm(normalizedLine, district)) score += 3
    }

    val vehicleMatches = rawVehicleTerms(load.vehicleType)
        .count { containsRawTerm(normalizedLine, it) }
        .coerceAtMost(4)
    score += vehicleMatches

    load.priceTry?.let { price ->
        val digits = line.filter { it.isDigit() }
        if (digits.contains(price.toString())) {
            score += 3
        } else if (price >= 1000 && price % 1000 == 0) {
            val shortPrice = (price / 1000).toString()
            if (Regex("""(^|[^0-9])${Regex.escape(shortPrice)}([^0-9]|$)""").containsMatchIn(line)) {
                score += 2
            }
        }
    }

    load.effectiveMaxTonnage()?.let { ton ->
        val tonPattern = Regex("""(^|[^0-9])${Regex.escape(ton.toString())}\s*(TON|TN|T)?([^A-Z0-9]|$)""")
        if (tonPattern.containsMatchIn(normalizedLine)) score += 2
    }

    return score
}

private fun looksLikeDifferentRouteLine(load: LoadOfferEntity, line: String): Boolean {
    val normalizedLine = normalizeRawMessageSearch(line)
    val routeLike = Regex("""\b(YUKLEME|YUKLER|ALINIR|CIKIS|BOSALTMA|INDIRME|INDIRIR|->|-->|-)\b""")
        .containsMatchIn(normalizedLine)
    if (!routeLike) return false

    val hasSelectedTo = rawDeliveryTerms(load).any { containsRawTerm(normalizedLine, it) }
    val hasSelectedFrom = rawPickupTerms(load).any { containsRawTerm(normalizedLine, it) }
    return !hasSelectedTo && !hasSelectedFrom && rawLocationTokenCount(normalizedLine) >= 2
}

private fun rawPickupTerms(load: LoadOfferEntity): List<String> =
    load.pickupStops()
        .flatMap { stop -> rawLocationTerms(stop.city, stop.district) }
        .distinct()

private fun rawDeliveryTerms(load: LoadOfferEntity): List<String> =
    load.deliveryStops()
        .flatMap { stop -> rawLocationTerms(stop.city, stop.district) }
        .distinct()

private fun rawLocationTerms(city: String?, district: String?): List<String> {
    return buildList {
        city?.let { value ->
            add(value)
            when (normalizeRawMessageSearch(value)) {
                "ISTANBUL" -> add("IST")
                "IZMIR" -> add("IZM")
                "ANKARA" -> add("ANK")
            }
        }
        district?.let { add(it) }
    }
        .map(::normalizeRawMessageSearch)
        .filter { it.length >= 3 }
        .distinct()
}

private fun rawVehicleTerms(vehicleType: String?): List<String> {
    return normalizeRawMessageSearch(vehicleType.orEmpty())
        .split(" ")
        .filter { it.length >= 3 }
        .filterNot { it in setOf("ARAC", "DORSE", "YUK", "KASA") }
        .distinct()
}

private fun containsRawTerm(normalizedLine: String, term: String): Boolean {
    val normalizedTerm = normalizeRawMessageSearch(term)
    if (normalizedTerm.isBlank()) return false
    return " $normalizedLine ".contains(" $normalizedTerm ")
}

private fun rawLocationTokenCount(normalizedLine: String): Int {
    val knownHints = setOf(
        "ISTANBUL", "IST", "ANKARA", "ANK", "IZMIR", "IZM", "KOCAELI", "GEBZE",
        "BURSA", "ADANA", "KONYA", "TEKIRDAG", "CORLU", "SAKARYA", "DUDULLU",
        "HADIMKOY", "TUZLA", "ESENYURT", "CATALCA", "BOLU", "DILOVASI"
    )
    return normalizedLine.split(" ").count { it in knownHints }
}

private fun normalizeRawMessageSearch(value: String): String {
    return value
        .uppercase(Locale("tr", "TR"))
        .replace("İ", "I")
        .replace("İ", "I")
        .replace("Ş", "S")
        .replace("Ğ", "G")
        .replace("Ü", "U")
        .replace("Ö", "O")
        .replace("Ç", "C")
        .replace("Ã„Â°", "I")
        .replace("Ã…Â", "S")
        .replace("Ã„Â", "G")
        .replace("ÃƒÅ“", "U")
        .replace("Ãƒâ€“", "O")
        .replace("Ãƒâ€¡", "C")
        .replace(Regex("[^A-Z0-9]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")
}

private fun wrapLongRawTokens(value: String): String {
    return value
        .split(Regex("(?<=\\s)|(?=\\s)"))
        .joinToString("") { token ->
            if (token.length <= 28 || token.any { it.isWhitespace() }) {
                token
            } else {
                token.chunked(24).joinToString("\u200B")
            }
        }
}

private fun Modifier.debugLongPress(onLongPress: () -> Unit): Modifier = pointerInput(onLongPress) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        val releasedBeforeTimeout = withTimeoutOrNull(3000L) {
            waitForUpOrCancellation()
            true
        }
        if (releasedBeforeTimeout != true) {
            onLongPress()
            do {
                val event = awaitPointerEvent()
            } while (event.changes.any { it.pressed })
        }
    }
}

private fun detailRelativeTime(createdAt: Long): String {
    val diffMs = (System.currentTimeMillis() - createdAt).coerceAtLeast(0L)
    val minutes = diffMs / 60_000L
    return when {
        minutes < 1 -> "şimdi"
        minutes < 60 -> "$minutes dk önce"
        minutes < 24 * 60 -> "${minutes / 60} sa önce"
        minutes < 48 * 60 -> "dün"
        else -> "${minutes / (24 * 60)} gün önce"
    }
}

private fun loadingDayLabel(createdAt: Long): String {
    val diffMs = (System.currentTimeMillis() - createdAt).coerceAtLeast(0L)
    val days = diffMs / (24 * 60 * 60 * 1000L)
    return when (days) {
        0L -> "Bugün"
        1L -> "Dün"
        else -> "${days} gün önce"
    }
}

private data class DetailFieldV2(
    val label: String,
    val value: String
) {
    val isWide: Boolean
        get() = value.length > 34
}

private fun detailFieldsForLoad(
    load: LoadOfferEntity,
    dateText: String
): List<DetailFieldV2> {
    val labels = mapOf(
        "gonderici_il" to "Alış il",
        "gonderici_ilce" to "Alış ilçe",
        "alici_il" to "Varış il",
        "alici_ilce" to "Varış ilçe",
        "arac_tipi" to "Araç/Dorse",
        "tonaj" to "Tonaj",
        "miktar" to "Miktar",
        "miktar_birimi" to "Miktar birimi",
        "agirlik" to "Ağırlık",
        "yuk_cinsi" to "Yük cinsi",
        "fiyat" to "Fiyat",
        "fiyat_notu" to "Fiyat notu",
        "yuklemeTarihi" to "Yükleme tarihi",
        "sourceGroup" to "Grup"
    )
    // Güzergâh üstteki rota kartında zaten gösteriliyor. Yükleme tarihi de detay
    // kartında kullanılmıyor; bu alanları tekrar etmek Bilgiler bölümünü kalabalıklaştırıyordu.
    val hiddenKeys = setOf(
        "yuk_detay",
        "sourceSender",
        "gonderici_il",
        "gonderici_ilce",
        "alici_il",
        "alici_ilce",
        "yuklemeTarihi"
    )

    val fields = parsedJsonFieldsForLoad(load)
        .mapNotNull { (key, value) ->
            if (key in hiddenKeys) return@mapNotNull null
            val label = labels[key] ?: key
            value.takeIf { it.isNotBlank() }?.let { DetailFieldV2(label, formatDetailFieldValue(key, it)) }
        }
        .toMutableList()

    dateText.takeIf { it.isNotBlank() }?.let { fields.add(DetailFieldV2("Kayıt", it)) }

    return fields.distinctBy { "${it.label}\u0000${it.value}" }
}

private fun formatDetailFieldValue(key: String, value: String): String {
    return when (key) {
        "gonderici_il",
        "gonderici_ilce",
        "alici_il",
        "alici_ilce" -> formatPlaceName(value)
        "arac_tipi" -> formatVehicleType(value)
        "miktar_birimi",
        "yuk_cinsi",
        "yuklemeTarihi",
        "fiyat_notu" -> formatTurkishDetailText(value)
        else -> value
    }
}

private fun parsedJsonFieldsForLoad(load: LoadOfferEntity): LinkedHashMap<String, String> {
    val fields = linkedMapOf<String, String>()

    fun putClean(key: String, value: String?) {
        val clean = value?.trim()?.takeIf { it.isNotBlank() } ?: return
        fields[key] = clean
    }

    fun putIfMissing(key: String, value: String?) {
        if (!fields.containsKey(key)) putClean(key, value)
    }

    putClean("gonderici_il", load.fromCity)
    putClean("gonderici_ilce", load.fromDistrict)
    putClean("alici_il", load.toCity)
    putClean("alici_ilce", load.toDistrict)
    putClean("arac_tipi", load.vehicleType?.let(::formatVehicleType))

    val displayWeight = load.displayTonnage().takeUnless { it == "Tonaj yok" }
    putClean("agirlik", displayWeight)
    putClean("fiyat", load.priceTry?.toString())
    putClean("sourceGroup", load.sourceGroup)

    load.notes.orEmpty()
        .split("|")
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .forEach { part ->
            when {
                part.startsWith("Yuk:", ignoreCase = true) ->
                    putIfMissing("yuk_cinsi", part.substringAfter(":").trim())
                part.startsWith("Miktar:", ignoreCase = true) -> {
                    val quantity = part.substringAfter(":").trim()
                    val (amount, unit) = splitQuantityText(quantity)
                    putIfMissing("miktar", amount ?: quantity)
                    putIfMissing("miktar_birimi", unit)
                }
                part.startsWith("Agirlik:", ignoreCase = true) ->
                    putIfMissing("agirlik", part.substringAfter(":").trim())
                part.startsWith("Tarih:", ignoreCase = true) ->
                    putIfMissing("yuklemeTarihi", part.substringAfter(":").trim())
                part.contains("KDV", ignoreCase = true) || part.startsWith("+") ->
                    putIfMissing("fiyat_notu", part)
            }
        }

    return fields
}

private val DetailTokenNames = mapOf(
    "ACIK" to "Açık",
    "AGIRLIK" to "Ağırlık",
    "AKSAM" to "Akşam",
    "ARAC" to "Araç",
    "BUGUN" to "Bugün",
    "CIMENTO" to "Çimento",
    "DAHIL" to "Dahil",
    "DEMIR" to "Demir",
    "DOKME" to "Dökme",
    "ESYA" to "Eşya",
    "FRIGO" to "Frigo",
    "GIDA" to "Gıda",
    "HEMEN" to "Hemen",
    "KAMYON" to "Kamyon",
    "KAMYONET" to "Kamyonet",
    "KAPALI" to "Kapalı",
    "KDV" to "KDV",
    "KG" to "kg",
    "KOLI" to "Koli",
    "KOMUR" to "Kömür",
    "MAKINE" to "Makine",
    "MALZEME" to "Malzeme",
    "MERMER" to "Mermer",
    "NAKIT" to "Nakit",
    "PARCA" to "Parça",
    "PARCALI" to "Parçalı",
    "PALET" to "Palet",
    "PALETLI" to "Paletli",
    "SABAH" to "Sabah",
    "SIMDI" to "Şimdi",
    "TIR" to "Tır",
    "TON" to "ton",
    "YARIN" to "Yarın",
    "YUK" to "Yük"
)

private fun formatTurkishDetailText(value: String): String {
    return value
        .trim()
        .split(Regex("(?<=\\s)|(?=\\s)|(?<=[/+\\-])|(?=[/+\\-])"))
        .joinToString("") { token -> formatTurkishDetailToken(token) }
        .replace(Regex("\\s+"), " ")
        .trim()
}

private fun formatTurkishDetailToken(token: String): String {
    if (token.isBlank() || token.any { it.isDigit() }) return token
    val key = token
        .uppercase(Locale("tr", "TR"))
        .replace("İ", "I")
        .replace("Ş", "S")
        .replace("Ğ", "G")
        .replace("Ü", "U")
        .replace("Ö", "O")
        .replace("Ç", "C")
    DetailTokenNames[key]?.let { return it }
    return token.lowercase(Locale("tr", "TR")).replaceFirstChar { char ->
        if (char.isLowerCase()) char.titlecase(Locale("tr", "TR")) else char.toString()
    }
}

private fun splitQuantityText(value: String): Pair<String?, String?> {
    val match = Regex("""^\s*(\d+(?:[.,]\d+)?)\s+(.+?)\s*$""").find(value)
    if (match != null) {
        return match.groupValues[1] to match.groupValues[2].trim().uppercase()
    }
    return value.takeIf { it.any(Char::isDigit) } to null
}

@Composable
fun VehicleTypeDialog(
    selected: String,
    options: List<String>,
    onSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Araç türü seç", softWrap = true) },
        text = {
            LazyColumn {
                items(options) { type ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                        .clickable(role = Role.Button, onClick = { onSelected(type) }),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = type == selected, onClick = { onSelected(type) })
                        Spacer(Modifier.width(8.dp))
                        Text(type, softWrap = true)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Kapat") } }
    )
}

@Composable
fun CityFilterDialog(
    selected: String,
    options: List<String>,
    onSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredOptions = options.filter { it.contains(searchQuery, ignoreCase = true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Şehir seç", softWrap = true) },
        text = {
            Column {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Şehir ara") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    items(filteredOptions) { city ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                        .clickable(role = Role.Button, onClick = { onSelected(city) }),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = city == selected, onClick = { onSelected(city) })
                            Spacer(Modifier.width(8.dp))
                            Text(city, softWrap = true)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Kapat") } }
    )
}

@Composable
fun SourceGroupFilterDialog(
    selected: String,
    options: List<String>,
    onSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredOptions = options.filter { it.contains(searchQuery, ignoreCase = true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Grup seç", softWrap = true) },
        text = {
            Column {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Grup ara") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    items(filteredOptions) { group ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .clickable(role = Role.Button, onClick = { onSelected(group) }),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = group == selected, onClick = { onSelected(group) })
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = group,
                                modifier = Modifier.weight(1f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                softWrap = true
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Kapat") } }
    )
}

@Composable
fun AdvancedFilterSheet(
    filter: LoadListFilter,
    userProfile: UserProfileEntity?,
    onMatchProfileChanged: (Boolean) -> Unit,
    onTonnageChanged: (Int?, Int?) -> Unit,
    onClearFilters: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    var minTonnage by remember(filter.minTonnage) { mutableStateOf(filter.minTonnage?.toString().orEmpty()) }
    var maxTonnage by remember(filter.maxTonnage) { mutableStateOf(filter.maxTonnage?.toString().orEmpty()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text(
                text = "Gelişmiş Filtreler",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                softWrap = true
            )

            if (userProfile != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Aracıma uygun olanlar", style = MaterialTheme.typography.titleSmall, softWrap = true)
                        Text(
                            "${userProfile.vehicleType} • Max ${userProfile.maxTonnage} ton",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            softWrap = true
                        )
                    }
                    Switch(
                        checked = filter.onlyMatchingProfile,
                        onCheckedChange = onMatchProfileChanged,
                        colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary)
                    )
                }
                HorizontalDivider()
            }

            Text("Tonaj aralığı", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = minTonnage,
                    onValueChange = {
                        minTonnage = it
                        onTonnageChanged(it.toIntOrNull(), maxTonnage.toIntOrNull())
                    },
                    label = { Text("Min") },
                    suffix = { Text("ton") },
                    modifier = Modifier.width(150.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary)
                )
                OutlinedTextField(
                    value = maxTonnage,
                    onValueChange = {
                        maxTonnage = it
                        onTonnageChanged(minTonnage.toIntOrNull(), it.toIntOrNull())
                    },
                    label = { Text("Max") },
                    suffix = { Text("ton") },
                    modifier = Modifier.width(150.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary)
                )
            }

            OutlinedButton(
                onClick = {
                    onClearFilters()
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Clear, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Tüm filtreleri temizle", softWrap = true)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

private fun LoadListUiState.hasActiveFilter(): Boolean =
    filter.vehicleType != null ||
            filter.city != null ||
            filter.sourceGroup != null ||
            filter.dateFilter != LoadDateFilter.TODAY ||
            filter.onlyMatchingProfile ||
            filter.onlyFromMyCity ||
            filter.minTonnage != null ||
            filter.maxTonnage != null ||
            filter.sortBy != SortOrder.NEWEST

private fun dateFilterLabel(dateFilter: LoadDateFilter): String = when (dateFilter) {
    LoadDateFilter.TODAY -> "Bugün gelenler"
    LoadDateFilter.ALL -> "Tüm kayıtlar"
}
