package fi.veneappi.app.ui.ais

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import fi.veneappi.app.R
import fi.veneappi.app.domain.Harbor
import fi.veneappi.app.domain.ais.AisVesselDisplay
import fi.veneappi.app.ui.map.MapPane
import kotlinx.coroutines.delay

/**
 * Map pane plus AIS toggle chip — optional layer; defaults keep existing maps unchanged.
 */
@Composable
fun MapWithAisChrome(
    latitude: Double,
    longitude: Double,
    routeGeometry: List<Pair<Double, Double>>,
    routeStart: Pair<Double, Double>?,
    routeEnd: Pair<Double, Double>?,
    harbors: List<Harbor>,
    onLongPress: (lat: Double, lon: Double) -> Unit,
    onMapClick: ((lat: Double, lon: Double) -> Unit)?,
    traficomPlanningRasterEnabled: Boolean,
    onMyLocation: (() -> Unit)?,
    mapRecenterSignal: Long,
    aisViewModel: AisMapViewModel,
    isPremium: Boolean,
    modifier: Modifier = Modifier,
    mapModifier: Modifier = Modifier.fillMaxSize(),
) {
    val vessels by aisViewModel.vessels.collectAsState()
    val aisEnabled by aisViewModel.isEnabled.collectAsState()
    val streamMode by aisViewModel.streamMode.collectAsState()
    val renderGen by aisViewModel.mapRenderGeneration.collectAsState()
    var showPremiumHint by remember { mutableStateOf(false) }
    var selectedVessel by remember { mutableStateOf<AisVesselDisplay?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle

    LaunchedEffect(isPremium) {
        if (!isPremium) {
            aisViewModel.setEnabled(false, premium = false)
        }
    }

    LaunchedEffect(aisEnabled, isPremium, latitude, longitude) {
        if (aisEnabled && isPremium) {
            aisViewModel.ensureFallbackViewport(latitude, longitude)
        }
    }

    DisposableEffect(lifecycle) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_RESUME -> aisViewModel.setSceneActive(true)
                    Lifecycle.Event.ON_PAUSE -> aisViewModel.setSceneActive(false)
                    else -> Unit
                }
            }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(aisEnabled, isPremium, streamMode) {
        if (!aisEnabled || !isPremium || streamMode != AisStreamMode.Live) return@LaunchedEffect
        while (true) {
            delay(1_000L)
            aisViewModel.tickLiveMapRender()
        }
    }

    Box(modifier = modifier) {
        MapPane(
            latitude = latitude,
            longitude = longitude,
            routeGeometry = routeGeometry,
            routeStart = routeStart,
            routeEnd = routeEnd,
            harbors = harbors,
            onLongPress = onLongPress,
            onMapClick = onMapClick,
            traficomPlanningRasterEnabled = traficomPlanningRasterEnabled,
            onMyLocation = onMyLocation,
            mapRecenterSignal = mapRecenterSignal,
            aisVessels = vessels,
            aisEnabled = aisEnabled && isPremium,
            aisRenderGeneration = renderGen,
            onMapViewportChange = { aisViewModel.updateViewport(it) },
            onAisVesselSelected = { selectedVessel = it },
            modifier = mapModifier,
        )
        AisMapChip(
            viewModel = aisViewModel,
            premium = isPremium,
            onNeedPremium = { showPremiumHint = true },
            onEnabled = { aisViewModel.refreshNow() },
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp, end = 8.dp),
        )
    }

    if (showPremiumHint) {
        AlertDialog(
            onDismissRequest = { showPremiumHint = false },
            title = { Text(stringResource(R.string.ais_premium_required_title)) },
            text = { Text(stringResource(R.string.ais_premium_required_body)) },
            confirmButton = {
                TextButton(onClick = { showPremiumHint = false }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
        )
    }

    selectedVessel?.let { vessel ->
        AisVesselDetailSheet(vessel = vessel, onDismiss = { selectedVessel = null })
    }
}
