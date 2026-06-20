package fi.veneappi.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Anchor
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fi.veneappi.app.R
import fi.veneappi.app.domain.GeoMath
import fi.veneappi.app.domain.Harbor
import fi.veneappi.app.domain.HarborKind
import fi.veneappi.app.ui.map.MapPane

@Composable
fun HarborsPane(
    vm: MainViewModel,
    traficomPlanningChart: Boolean,
    onTraficomPlanningChartChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ui by vm.ui.collectAsState(initial = VeneappiUiState())
    val harborsUi by vm.harborsUi.collectAsState(initial = HarborsUiState())
    val uri = LocalUriHandler.current
    val context = LocalContext.current
    var showDisclaimer by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.refreshHarbors()
    }

    if (showDisclaimer) {
        AlertDialog(
            onDismissRequest = { showDisclaimer = false },
            confirmButton = {
                TextButton(onClick = { showDisclaimer = false }) {
                    Text("OK")
                }
            },
            title = { Text(stringResource(R.string.disclaimer_nav_title)) },
            text = { Text(stringResource(R.string.disclaimer_nav_body)) },
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
        ) {
            MapPane(
                latitude = ui.latitude,
                longitude = ui.longitude,
                routeGeometry = emptyList(),
                routeStart = null,
                routeEnd = null,
                harbors = harborsUi.items,
                onLongPress = { la, lo ->
                    vm.setMapLocation(la, lo)
                    vm.refreshHarbors()
                },
                onMapClick = null,
                traficomPlanningRasterEnabled = traficomPlanningChart,
                onMyLocation = {
                    vm.recenterToDeviceLocation(context) { vm.refreshHarbors() }
                },
                modifier = Modifier.fillMaxSize(),
            )
            Row(
                modifier =
                    Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .padding(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Anchor,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        stringResource(R.string.nav_harbors),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { showDisclaimer = true }) {
                        Icon(Icons.Default.Info, contentDescription = stringResource(R.string.disclaimer_nav_title))
                    }
                    FilledTonalButton(
                        onClick = { vm.refreshHarbors() },
                        enabled = !harborsUi.loading,
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                        Text(stringResource(R.string.harbors_refresh))
                    }
                }
            }
            FilterChip(
                selected = traficomPlanningChart,
                onClick = { onTraficomPlanningChartChange(!traficomPlanningChart) },
                label = { Text(stringResource(R.string.map_traficom_overlay)) },
                modifier =
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 6.dp, top = 48.dp),
            )
            if (harborsUi.loading) {
                CircularProgressIndicator(
                    modifier =
                        Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                )
            }
        }
        Column(
            modifier =
                Modifier
                    .weight(0.42f)
                    .fillMaxWidth(),
        ) {
            harborsUi.error?.let { err ->
                Text(
                    stringResource(R.string.harbors_error, err),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
            Text(
                stringResource(R.string.harbors_osm_note),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(8.dp),
            ) {
                if (!harborsUi.loading && harborsUi.items.isEmpty() && harborsUi.error == null) {
                    item {
                        Text(
                            stringResource(R.string.harbors_empty),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(harborsUi.items, key = { it.osmKey }) { h ->
                    HarborCard(
                        harbor = h,
                        centerLat = ui.latitude,
                        centerLon = ui.longitude,
                        onOpenWebsite = { url -> runCatching { uri.openUri(url) } },
                        onDial = { phone ->
                            val cleaned = phone.filter { it.isDigit() || it == '+' }
                            if (cleaned.isNotEmpty()) {
                                runCatching { uri.openUri("tel:$cleaned") }
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun HarborCard(
    harbor: Harbor,
    centerLat: Double,
    centerLon: Double,
    onOpenWebsite: (String) -> Unit,
    onDial: (String) -> Unit,
) {
    val distM = GeoMath.haversineMeters(centerLat, centerLon, harbor.latitude, harbor.longitude)
    val distNm = GeoMath.metersToNauticalMiles(distM)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(harbor.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                harborKindLabel(harbor.kind),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                stringResource(R.string.harbors_distance_nm, distNm),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            harbor.operator?.let { op ->
                Text(
                    stringResource(R.string.harbor_operator, op),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            harbor.capacity?.let { c ->
                Text(
                    stringResource(R.string.harbor_capacity, c),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            harbor.mooring?.let { m ->
                Text(
                    stringResource(R.string.harbor_mooring, m),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            harbor.fee?.let { f ->
                Text(
                    stringResource(R.string.harbor_fee, f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            harbor.description?.let { d ->
                Text(d, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                harbor.phone?.let { p ->
                    TextButton(onClick = { onDial(p) }) {
                        Text(stringResource(R.string.harbor_call))
                    }
                }
                harbor.website?.let { w ->
                    TextButton(onClick = { onOpenWebsite(w) }) {
                        Text(stringResource(R.string.harbor_open_website))
                    }
                }
            }
        }
    }
}

@Composable
private fun harborKindLabel(kind: HarborKind): String =
    when (kind) {
        HarborKind.Marina -> stringResource(R.string.harbor_kind_marina)
        HarborKind.GuestHarbor -> stringResource(R.string.harbor_kind_guest)
        HarborKind.HarbourArea -> stringResource(R.string.harbor_kind_harbour_area)
        HarborKind.SeaMarkHarbour -> stringResource(R.string.harbor_kind_seamark)
        HarborKind.BoatFuel -> stringResource(R.string.harbor_kind_fuel)
        HarborKind.Other -> stringResource(R.string.harbor_kind_other)
    }
