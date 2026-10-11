package com.example.test.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.test.data.model.CityDensity
import com.example.test.data.model.LoadMarker
import com.example.test.data.model.MarkerType
import com.example.test.ui.components.EmptyState
import com.example.test.ui.format.displayPlace
import com.example.test.ui.format.displayRoute
import com.example.test.ui.format.formatVehicleType
import com.example.test.viewmodel.MapViewModel
import com.example.test.telemetry.ContentlessTelemetry
import com.example.test.telemetry.TelemetryEvents
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.TileOverlay
import com.google.android.gms.maps.model.TileOverlayOptions
import com.google.maps.android.clustering.ClusterItem
import com.google.maps.android.clustering.ClusterManager
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapEffect
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MapsComposeExperimentalApi
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.heatmaps.HeatmapTileProvider
import com.google.maps.android.heatmaps.WeightedLatLng
import kotlinx.coroutines.launch

data class LoadClusterItem(
    private val markerPosition: LatLng,
    private val markerTitle: String,
    private val markerSnippet: String,
    val loadId: Long
) : ClusterItem {
    override fun getPosition(): LatLng = markerPosition
    override fun getTitle(): String = markerTitle
    override fun getSnippet(): String = markerSnippet
    override fun getZIndex(): Float? = null
}

@OptIn(ExperimentalMaterial3Api::class, MapsComposeExperimentalApi::class)
@Composable
fun MapScreen(
    viewModel: MapViewModel = viewModel(),
    onBackClick: () -> Unit,
    onOpenLoadDetails: (Long) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val markerById = remember(uiState.loadMarkers) {
        uiState.loadMarkers.associateBy { it.id }
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val hasLocationPermission = remember(context) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            uiState.userLocation?.latLng ?: LatLng(39.0, 35.0),
            6f
        )
    }
    var hasAutoCenteredDeviceLocation by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.userLocation?.latLng) {
        uiState.userLocation?.let { loc ->
            val isDefaultLocation = loc.latLng.latitude == 39.9334 && loc.latLng.longitude == 32.8597
            if (!isDefaultLocation && !hasAutoCenteredDeviceLocation) {
                cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(loc.latLng, 8f))
                hasAutoCenteredDeviceLocation = true
            }
        }
    }

    var showFilterSheet by remember { mutableStateOf(false) }
    var showDensitySheet by remember { mutableStateOf(false) }
    var cityLoads by remember { mutableStateOf<List<LoadMarker>?>(null) }
    var heatmapOverlay by remember { mutableStateOf<TileOverlay?>(null) }
    var clusterManager by remember { mutableStateOf<ClusterManager<LoadClusterItem>?>(null) }

    val isAnySheetOpen = showFilterSheet || showDensitySheet || cityLoads != null || uiState.selectedMarker != null

    LaunchedEffect(uiState.showHeatmap) {
        if (uiState.showHeatmap) {
            cityLoads = null
            viewModel.selectMarker(null)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            heatmapOverlay?.remove()
            heatmapOverlay = null
            clusterManager = null
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Canlı Harita",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            "${uiState.filterRadius} km • ${uiState.nearbyLoads.size} / ${uiState.loadMarkers.size} yük",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.Close, contentDescription = "Kapat")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshData() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Yenile")
                    }
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                contentPadding = PaddingValues(bottom = 138.dp),
                properties = MapProperties(
                    mapType = MapType.NORMAL,
                    isMyLocationEnabled = hasLocationPermission
                ),
                uiSettings = MapUiSettings(
                    zoomControlsEnabled = false,
                    myLocationButtonEnabled = false,
                    mapToolbarEnabled = false
                )
            ) {
                uiState.userLocation?.let { userLocation ->
                    Marker(
                        state = MarkerState(userLocation.latLng),
                        title = userLocation.city ?: "Konumum"
                    )
                }

                MapEffect(uiState.loadMarkers, uiState.showHeatmap) { googleMap ->
                    val manager = clusterManager ?: ClusterManager<LoadClusterItem>(context, googleMap).also { manager ->
                        googleMap.setOnCameraIdleListener(manager)
                        googleMap.setOnMarkerClickListener(manager)
                        clusterManager = manager
                    }

                    manager.clearItems()
                    if (uiState.showHeatmap) {
                        manager.cluster()
                        return@MapEffect
                    }

                    manager.setOnClusterClickListener { cluster ->
                        val loads = cluster.items
                            .mapNotNull { item -> markerById[item.loadId] }
                            .sortedByDescending { it.createdAt }
                        cityLoads = loads
                        viewModel.selectMarker(null)
                        true
                    }
                    manager.setOnClusterItemClickListener { item ->
                        val selected = markerById[item.loadId]
                        if (selected != null) {
                            val sameCityLoads = uiState.loadMarkers
                                .filter { it.fromCity == selected.fromCity && it.position == selected.position }
                                .sortedByDescending { it.createdAt }
                            if (sameCityLoads.size > 1) {
                                cityLoads = sameCityLoads
                                viewModel.selectMarker(null)
                            } else {
                                cityLoads = null
                                viewModel.selectMarker(selected)
                            }
                        }
                        true
                    }

                    manager.addItems(
                        uiState.loadMarkers.map { marker ->
                            LoadClusterItem(
                                markerPosition = marker.position,
                                markerTitle = marker.title,
                                markerSnippet = "${marker.tonnageLabel ?: marker.tonnage?.let { "$it ton" } ?: "Tonaj yok"} • ${marker.price?.let { "%,d₺".format(it) } ?: "Fiyat yok"}",
                                loadId = marker.id
                            )
                        }
                    )
                    manager.cluster()
                }

                MapEffect(uiState.showHeatmap, uiState.cityDensities) { googleMap ->
                    heatmapOverlay?.remove()
                    heatmapOverlay = null

                    if (uiState.showHeatmap && uiState.cityDensities.isNotEmpty()) {
                        val weightedPoints = uiState.cityDensities.map { density ->
                            WeightedLatLng(density.position, density.loadCount.toDouble())
                        }
                        val provider = HeatmapTileProvider.Builder()
                            .weightedData(weightedPoints)
                            .radius(50)
                            .opacity(0.65)
                            .build()

                        heatmapOverlay = googleMap.addTileOverlay(
                            TileOverlayOptions().tileProvider(provider)
                        )
                    }
                }
            }

            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            AnimatedVisibility(
                visible = !isAnySheetOpen,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .zIndex(2f)
            ) {
                MapControlDock(
                    radius = uiState.filterRadius,
                    nearbyCount = uiState.nearbyLoads.size,
                    totalCount = uiState.loadMarkers.size,
                    showHeatmap = uiState.showHeatmap,
                    onFilterClick = { showFilterSheet = true },
                    onHeatmapClick = { viewModel.toggleHeatmap() },
                    onDensityClick = { showDensitySheet = true },
                    onLocationClick = {
                        uiState.userLocation?.let { loc ->
                            scope.launch {
                                cameraPositionState.animate(
                                    CameraUpdateFactory.newLatLngZoom(loc.latLng, 10f)
                                )
                            }
                        }
                    }
                )
            }

            AnimatedVisibility(
                visible = uiState.selectedMarker != null && cityLoads == null,
                enter = slideInVertically { it },
                exit = slideOutVertically { it },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .zIndex(3f)
            ) {
                uiState.selectedMarker?.let { marker ->
                    MapSelectedLoadCard(
                        marker = marker,
                        onOpenDetails = { onOpenLoadDetails(marker.id) },
                        onClose = { viewModel.selectMarker(null) },
                        onCall = {
                            if (marker.phone.isNotBlank()) {
                                ContentlessTelemetry.record(
                                    context,
                                    TelemetryEvents.funnel("call_tapped")
                                )
                                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${marker.phone}")))
                            }
                        }
                    )
                }
            }

            if (uiState.loadMarkers.isEmpty() && !uiState.isLoading) {
                EmptyState(
                    title = "Haritada yük yok",
                    message = "Yeni WhatsApp ilanları yakalandıkça haritada kümeler halinde görünecek.",
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp)
                )
            }
        }
    }

    if (showFilterSheet) {
        MapFilterBottomSheet(
            currentRadius = uiState.filterRadius,
            onRadiusChange = { viewModel.setFilterRadius(it) },
            onDismiss = { showFilterSheet = false }
        )
    }

    if (showDensitySheet) {
        MapDensityBottomSheet(
            cityDensities = uiState.cityDensities,
            onCityClick = { density ->
                scope.launch {
                    cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(density.position, 9f))
                }
                showDensitySheet = false
            },
            onDismiss = { showDensitySheet = false }
        )
    }

    cityLoads?.let { loads ->
        CityLoadsBottomSheet(
            loads = loads,
            onDismiss = { cityLoads = null },
            onSelectLoad = { marker ->
                cityLoads = null
                viewModel.selectMarker(marker)
            }
        )
    }
}

@Composable
private fun MapControlDock(
    radius: Int,
    nearbyCount: Int,
    totalCount: Int,
    showHeatmap: Boolean,
    onFilterClick: () -> Unit,
    onHeatmapClick: () -> Unit,
    onDensityClick: () -> Unit,
    onLocationClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        tonalElevation = 8.dp,
        shadowElevation = 6.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Görünen alan",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                    Text(
                        text = "$radius km içinde $nearbyCount / $totalCount yük",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (showHeatmap) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    }
                ) {
                    Text(
                        text = if (showHeatmap) "Isı açık" else "Kümeler",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (showHeatmap) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MapDockAction(
                    label = "Filtre",
                    icon = { Icon(Icons.Default.FilterList, contentDescription = null) },
                    onClick = onFilterClick,
                    modifier = Modifier.weight(1f)
                )
                MapDockAction(
                    label = "Sıcaklık",
                    icon = { Icon(Icons.Default.Map, contentDescription = null) },
                    active = showHeatmap,
                    onClick = onHeatmapClick,
                    modifier = Modifier.weight(1f)
                )
                MapDockAction(
                    label = "Bölgeler",
                    icon = { Icon(Icons.Default.LocalShipping, contentDescription = null) },
                    onClick = onDensityClick,
                    modifier = Modifier.weight(1f)
                )
                MapDockAction(
                    label = "Konum",
                    icon = { Icon(Icons.Default.MyLocation, contentDescription = null) },
                    onClick = onLocationClick,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun MapDockAction(
    label: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false
) {
    Surface(
        modifier = modifier
            .height(58.dp)
            .clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = if (active) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        contentColor = if (active) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(modifier = Modifier.size(20.dp), contentAlignment = Alignment.Center) {
                icon()
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun MapSelectedLoadCard(
    marker: LoadMarker,
    onOpenDetails: () -> Unit,
    onClose: () -> Unit,
    onCall: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .clickable(role = Role.Button, onClick = onOpenDetails),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayPlace(marker.fromCity, marker.fromDistrict),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = marker.displayRoute(),
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        softWrap = true
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Kapat")
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MapInfoChip(marker.tonnageLabel ?: marker.tonnage?.let { "$it ton" } ?: "Tonaj yok", modifier = Modifier.weight(1f))
                    MapInfoChip(marker.vehicleType?.let(::formatVehicleType)?.takeIf { it.isNotBlank() } ?: "Araç yok", modifier = Modifier.weight(1f))
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = marker.price?.let { "%,d ₺".format(it) } ?: "Fiyat yok",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        text = "${marker.distanceToUser} km uzakta",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1
                    )
                }
            }

            marker.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                Text(
                    text = notes,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = true
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onOpenDetails,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Detay", maxLines = 1)
                }
                TextButton(
                    onClick = onCall,
                    enabled = marker.phone.isNotBlank(),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(marker.phone.takeIf { it.isNotBlank() } ?: "Telefon yok", maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun MapInfoChip(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapFilterBottomSheet(
    currentRadius: Int,
    onRadiusChange: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()

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
            Text("Filtre Ayarları", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "Mesafe filtresi: $currentRadius km",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleSmall
            )
            Slider(
                value = currentRadius.toFloat(),
                onValueChange = { onRadiusChange(it.toInt()) },
                valueRange = 50f..500f,
                steps = 8
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("50 km", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                Text("500 km", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
            }
            Spacer(Modifier.height(18.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapDensityBottomSheet(
    cityDensities: List<CityDensity>,
    onCityClick: (CityDensity) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("En Yoğun Bölgeler", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (cityDensities.isEmpty()) {
                Text(
                    "Henüz yoğunluk verisi yok.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    softWrap = true
                )
            } else {
                cityDensities.take(10).forEachIndexed { index, density ->
                    MapDensityItem(density = density, rank = index + 1, onClick = { onCityClick(density) })
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
fun MapDensityItem(
    density: CityDensity,
    rank: Int,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("#$rank", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelMedium)
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(density.city, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(
                    "${density.loadCount} yük • ${density.totalTonnage} ton",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                "Ort. ${"%,d".format(density.avgPrice)}₺",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}
