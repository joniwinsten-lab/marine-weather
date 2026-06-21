package fi.veneappi.app.ui.ais

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import fi.veneappi.app.R
import fi.veneappi.app.domain.ais.AisBrowseItem
import fi.veneappi.app.domain.ais.AisFormatting
import fi.veneappi.app.domain.ais.AisTrackListFilter
import fi.veneappi.app.domain.ais.AisTrackPanel
import fi.veneappi.app.domain.ais.AisVesselDisplay
import fi.veneappi.app.domain.ais.AisVesselMotion
import fi.veneappi.app.ui.map.MapPane
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AisTrackPane(
    latitude: Double,
    longitude: Double,
    viewModel: AisTrackViewModel,
    isPremium: Boolean,
    modifier: Modifier = Modifier,
) {
    val panel by viewModel.panel.collectAsState()
    val watchlistRows by viewModel.watchlistRows.collectAsState()
    val browseItems by viewModel.browseItems.collectAsState()
    val vessels by viewModel.vessels.collectAsState()
    val streamMode by viewModel.streamMode.collectAsState()
    val listFilter by viewModel.listFilter.collectAsState()
    val watchlistSearch by viewModel.watchlistSearch.collectAsState()
    val browseSearch by viewModel.browseSearch.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val browseLoading by viewModel.browseLoading.collectAsState()
    val renderGen by viewModel.mapRenderGeneration.collectAsState()
    val recenterSignal by viewModel.mapRecenterSignal.collectAsState()
    val recenterTarget by viewModel.mapRecenterTarget.collectAsState()
    val recenterZoom by viewModel.mapRecenterZoom.collectAsState()
    val userMessage by viewModel.userMessage.collectAsState()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val snackbarHostState = remember { SnackbarHostState() }
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedVessel by remember { mutableStateOf<AisVesselDisplay?>(null) }
    var traficomEnabled by remember { mutableStateOf(false) }

    LaunchedEffect(isPremium) {
        viewModel.setPremiumActive(isPremium)
    }

    DisposableEffect(lifecycle, isPremium) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME ->
                        if (isPremium) viewModel.setSceneActive(true)
                    Lifecycle.Event.ON_PAUSE -> viewModel.setSceneActive(false)
                    else -> Unit
                }
            }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(isPremium, latitude, longitude) {
        if (isPremium) {
            viewModel.ensureFallbackViewport(latitude, longitude)
        }
    }

    LaunchedEffect(isPremium) {
        if (!isPremium) return@LaunchedEffect
        while (true) {
            delay(1_000L)
            viewModel.tickLiveMapRender()
        }
    }

    LaunchedEffect(userMessage) {
        val msg = userMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearUserMessage()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (panel == AisTrackPanel.WATCHLIST) {
                FloatingActionButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.track_add_vessel))
                }
            }
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        ) {
            TrackPanelHeader(
                streamMode = streamMode,
                traficomEnabled = traficomEnabled,
                onTraficomChange = { traficomEnabled = it },
                panel = panel,
                onPanelChange = viewModel::setPanel,
                browseLoading = browseLoading,
                onRefreshBrowse = viewModel::refreshBrowseNow,
            )
            TrackListSection(
                modifier = Modifier.weight(0.42f),
                panel = panel,
                watchlistRows = watchlistRows,
                browseItems = browseItems,
                listFilter = listFilter,
                watchlistSearch = watchlistSearch,
                browseSearch = browseSearch,
                isLoading = isLoading,
                browseLoading = browseLoading,
                onListFilter = viewModel::setListFilter,
                onWatchlistSearch = viewModel::setWatchlistSearch,
                onBrowseSearch = viewModel::setBrowseSearch,
                onWatchlistClick = { row ->
                    row.vessel?.let { selectedVessel = it }
                    viewModel.focusWatchlistVessel(row.entry.mmsi)
                },
                onBrowseClick = { item -> /* preview only on map via publishUi */ },
                onBrowseAdd = viewModel::addBrowseItem,
                onRemove = viewModel::removeFromWatchlist,
            )
            MapPane(
                latitude = latitude,
                longitude = longitude,
                routeGeometry = emptyList(),
                routeStart = null,
                routeEnd = null,
                harbors = emptyList(),
                onLongPress = { _, _ -> },
                onMapClick = null,
                traficomPlanningRasterEnabled = traficomEnabled,
                onMyLocation = null,
                mapRecenterSignal = recenterSignal,
                mapRecenterTargetLatitude = recenterTarget?.first,
                mapRecenterTargetLongitude = recenterTarget?.second,
                mapRecenterTargetZoom = recenterZoom,
                showForecastPin = false,
                aisVessels = vessels,
                aisEnabled = isPremium && vessels.isNotEmpty(),
                aisRenderGeneration = renderGen,
                onMapViewportChange = viewModel::updateViewport,
                onAisVesselSelected = { selectedVessel = it },
                modifier = Modifier.weight(0.58f),
            )
        }
    }

    if (showAddDialog) {
        AddVesselDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { mmsi, nickname ->
                viewModel.addManualVessel(mmsi, nickname)
                showAddDialog = false
            },
        )
    }

    selectedVessel?.let { vessel ->
        AisVesselDetailSheet(vessel = vessel, onDismiss = { selectedVessel = null })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrackPanelHeader(
    streamMode: AisStreamMode,
    traficomEnabled: Boolean,
    onTraficomChange: (Boolean) -> Unit,
    panel: AisTrackPanel,
    onPanelChange: (AisTrackPanel) -> Unit,
    browseLoading: Boolean,
    onRefreshBrowse: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StreamStatusDot(streamMode)
                Text(
                    when (streamMode) {
                        AisStreamMode.Live -> stringResource(R.string.track_mqtt_live)
                        AisStreamMode.Connecting -> stringResource(R.string.track_mqtt_connecting)
                        AisStreamMode.RestOnly -> stringResource(R.string.track_mqtt_rest)
                        AisStreamMode.Error -> stringResource(R.string.track_mqtt_error)
                        AisStreamMode.Off -> stringResource(R.string.track_mqtt_off)
                    },
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.track_marine_chart), style = MaterialTheme.typography.labelMedium)
                Switch(checked = traficomEnabled, onCheckedChange = onTraficomChange)
            }
        }
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = panel == AisTrackPanel.WATCHLIST,
                onClick = { onPanelChange(AisTrackPanel.WATCHLIST) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            ) { Text(stringResource(R.string.track_panel_watchlist)) }
            SegmentedButton(
                selected = panel == AisTrackPanel.BROWSE,
                onClick = { onPanelChange(AisTrackPanel.BROWSE) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            ) { Text(stringResource(R.string.track_panel_browse)) }
        }
        if (panel == AisTrackPanel.BROWSE) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.track_browse_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onRefreshBrowse, enabled = !browseLoading) {
                    if (browseLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Outlined.Refresh, contentDescription = stringResource(R.string.track_refresh_nearby))
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackListSection(
    panel: AisTrackPanel,
    watchlistRows: List<AisWatchlistRow>,
    browseItems: List<AisBrowseItem>,
    listFilter: AisTrackListFilter,
    watchlistSearch: String,
    browseSearch: String,
    isLoading: Boolean,
    browseLoading: Boolean,
    onListFilter: (AisTrackListFilter) -> Unit,
    onWatchlistSearch: (String) -> Unit,
    onBrowseSearch: (String) -> Unit,
    onWatchlistClick: (AisWatchlistRow) -> Unit,
    onBrowseClick: (AisBrowseItem) -> Unit,
    onBrowseAdd: (AisBrowseItem) -> Unit,
    onRemove: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (panel == AisTrackPanel.WATCHLIST) {
            OutlinedTextField(
                value = watchlistSearch,
                onValueChange = onWatchlistSearch,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.track_search_watchlist)) },
                singleLine = true,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = listFilter == AisTrackListFilter.ALL,
                    onClick = { onListFilter(AisTrackListFilter.ALL) },
                    label = { Text(stringResource(R.string.track_filter_all)) },
                )
                FilterChip(
                    selected = listFilter == AisTrackListFilter.ACTIVE,
                    onClick = { onListFilter(AisTrackListFilter.ACTIVE) },
                    label = { Text(stringResource(R.string.track_filter_active)) },
                )
                FilterChip(
                    selected = listFilter == AisTrackListFilter.STALE,
                    onClick = { onListFilter(AisTrackListFilter.STALE) },
                    label = { Text(stringResource(R.string.track_filter_stale)) },
                )
            }
            if (isLoading && watchlistRows.isEmpty()) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (watchlistRows.isEmpty()) {
                Text(
                    stringResource(R.string.track_empty_watchlist),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(watchlistRows, key = { it.entry.mmsi }) { row ->
                        WatchlistRowItem(
                            row = row,
                            onClick = { onWatchlistClick(row) },
                            onRemove = { onRemove(row.entry.mmsi) },
                        )
                    }
                }
            }
        } else {
            OutlinedTextField(
                value = browseSearch,
                onValueChange = onBrowseSearch,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.track_search_browse)) },
                singleLine = true,
            )
            if (browseLoading && browseItems.isEmpty()) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (browseItems.isEmpty()) {
                Text(
                    stringResource(R.string.track_empty_browse),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    items(browseItems, key = { it.mmsi }) { item ->
                        BrowseRowItem(
                            item = item,
                            onClick = { onBrowseClick(item) },
                            onAdd = { onBrowseAdd(item) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WatchlistRowItem(
    row: AisWatchlistRow,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    val vessel = row.vessel
    val active = vessel?.let { AisVesselMotion.isActive(it) } == true
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(row.entry.displayLabel, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                buildString {
                    append("MMSI ${row.entry.mmsi}")
                    vessel?.sogKn?.let { append(" · ${AisFormatting.formatSpeedKn(it)}") }
                    if (!active) append(" · ${stringResource(R.string.track_no_signal)}")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onRemove) {
            Text(stringResource(R.string.track_remove))
        }
    }
}

@Composable
private fun BrowseRowItem(
    item: AisBrowseItem,
    onClick: () -> Unit,
    onAdd: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(item.displayLabel, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "MMSI ${item.mmsi}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onAdd) {
            Text(stringResource(R.string.track_add_to_watchlist))
        }
    }
}

@Composable
private fun StreamStatusDot(streamMode: AisStreamMode) {
    val color =
        when (streamMode) {
            AisStreamMode.Live -> androidx.compose.ui.graphics.Color(0xFF43A047)
            AisStreamMode.RestOnly -> androidx.compose.ui.graphics.Color(0xFF2E7D32)
            AisStreamMode.Connecting -> androidx.compose.ui.graphics.Color(0xFFF57C00)
            AisStreamMode.Error -> androidx.compose.ui.graphics.Color(0xFFC62828)
            AisStreamMode.Off -> MaterialTheme.colorScheme.outline
        }
    androidx.compose.foundation.Canvas(Modifier.size(10.dp)) {
        drawCircle(color = color)
    }
}

@Composable
private fun AddVesselDialog(
    onDismiss: () -> Unit,
    onAdd: (mmsi: Int, nickname: String?) -> Unit,
) {
    var mmsiText by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.track_add_vessel)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = mmsiText,
                    onValueChange = { mmsiText = it.filter { ch -> ch.isDigit() }.take(9) },
                    label = { Text("MMSI") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = nickname,
                    onValueChange = { nickname = it },
                    label = { Text(stringResource(R.string.track_nickname_optional)) },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val mmsi = mmsiText.toIntOrNull()
                    if (mmsi != null && mmsiText.length == 9) {
                        onAdd(mmsi, nickname.trim().ifEmpty { null })
                    }
                },
            ) { Text(stringResource(android.R.string.ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
        },
    )
}
