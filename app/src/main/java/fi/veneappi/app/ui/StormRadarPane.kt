package fi.veneappi.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import fi.veneappi.app.R
import fi.veneappi.app.data.radar.RadarFrameRole
import fi.veneappi.app.data.radar.minutesAfterNow
import fi.veneappi.app.data.radar.minutesBeforeNow
import fi.veneappi.app.data.radar.nowFrameIndex
import fi.veneappi.app.data.radar.radarFrameRole
import fi.veneappi.app.ui.map.MapPane
import fi.veneappi.app.ui.map.STORM_MAP_ZOOM
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun StormRadarPane(
    mapUi: VeneappiUiState,
    stormUi: StormMapUiState,
    onLongPressMap: (Double, Double) -> Unit,
    onMyLocation: () -> Unit,
    onRadarEnabled: (Boolean) -> Unit,
    onLightningEnabled: (Boolean) -> Unit,
    onRefreshRadar: (Double, Double) -> Unit,
    onRefreshLightning: () -> Unit,
    onToggleAnimation: () -> Unit,
    onStepRadarFrame: (Int) -> Unit,
    onSetRadarFrameIndex: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapLabel = stringResource(R.string.content_map)

    LaunchedEffect(mapUi.latitude, mapUi.longitude) {
        onRefreshRadar(mapUi.latitude, mapUi.longitude)
    }

    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                delay(RADAR_REFRESH_MS)
                onRefreshRadar(mapUi.latitude, mapUi.longitude)
            }
        }
    }

    LaunchedEffect(lifecycle, stormUi.lightningEnabled) {
        if (!stormUi.lightningEnabled) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                onRefreshLightning()
                delay(LIGHTNING_POLL_MS)
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = stormUi.radarEnabled,
                onClick = { onRadarEnabled(!stormUi.radarEnabled) },
                label = { Text(stringResource(R.string.storm_radar_chip_radar)) },
            )
            FilterChip(
                selected = stormUi.lightningEnabled,
                onClick = { onLightningEnabled(!stormUi.lightningEnabled) },
                label = { Text(stringResource(R.string.storm_radar_chip_lightning)) },
            )
            if (stormUi.radarEnabled && stormUi.radarAnimationFrames.size >= 2) {
                IconButton(onClick = { onStepRadarFrame(-1) }) {
                    Icon(
                        imageVector = Icons.Default.SkipPrevious,
                        contentDescription = stringResource(R.string.storm_radar_step_back),
                    )
                }
                IconButton(onClick = onToggleAnimation) {
                    Icon(
                        imageVector =
                            if (stormUi.radarAnimationPlaying) {
                                Icons.Default.Pause
                            } else {
                                Icons.Default.PlayArrow
                            },
                        contentDescription =
                            if (stormUi.radarAnimationPlaying) {
                                stringResource(R.string.storm_radar_animation_pause)
                            } else {
                                stringResource(R.string.storm_radar_animation_play)
                            },
                    )
                }
                IconButton(onClick = { onStepRadarFrame(1) }) {
                    Icon(
                        imageVector = Icons.Default.SkipNext,
                        contentDescription = stringResource(R.string.storm_radar_step_forward),
                    )
                }
            }
        }
        Box(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
        ) {
            MapPane(
                latitude = mapUi.latitude,
                longitude = mapUi.longitude,
                routeGeometry = emptyList(),
                onLongPress = onLongPressMap,
                onMapClick = null,
                traficomPlanningRasterEnabled = false,
                isStormMap = true,
                initialZoom = STORM_MAP_ZOOM,
                stormRadarOverlay = if (stormUi.radarEnabled) stormUi.radarOverlay else null,
                lightningStrikes =
                    if (stormUi.lightningEnabled) {
                        stormUi.visibleLightningStrikes
                    } else {
                        emptyList()
                    },
                onMyLocation = onMyLocation,
                mapRecenterSignal = mapUi.mapRecenterSignal,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .semantics { contentDescription = mapLabel },
            )
            val showTimeline =
                stormUi.radarEnabled && stormUi.radarAnimationFrames.size >= 2
            val hudBottom =
                if (showTimeline) {
                    StormHudBottomWithSliderPadding
                } else {
                    StormHudBottomPadding
                }
            StormOverlayHud(
                stormUi = stormUi,
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 8.dp, bottom = hudBottom),
            )
            if (showTimeline) {
                StormTimelineSlider(
                    stormUi = stormUi,
                    onSelectIndex = onSetRadarFrameIndex,
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = StormSliderBottomPadding)
                            .fillMaxWidth(0.72f)
                            .widthIn(max = 300.dp),
                )
            }
        }
    }
}

@Composable
private fun StormTimelineSlider(
    stormUi: StormMapUiState,
    onSelectIndex: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val frames = stormUi.radarAnimationFrames
    if (frames.size < 2) return
    val sliderLabel = stringResource(R.string.storm_radar_time_slider)
    val index = stormUi.radarAnimationIndex.coerceIn(0, frames.lastIndex)
    val nowIdx = nowFrameIndex(frames)
    val nowFrac =
        if (frames.size > 1) {
            nowIdx.toFloat() / (frames.size - 1).toFloat()
        } else {
            0f
        }
    val nowMarker = stringResource(R.string.storm_radar_now_marker)
    Surface(
        modifier = modifier.semantics { contentDescription = sliderLabel },
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            Slider(
                value = index.toFloat(),
                onValueChange = { onSelectIndex(it.roundToInt()) },
                valueRange = 0f..frames.lastIndex.toFloat(),
                steps = (frames.size - 2).coerceAtLeast(0),
                modifier = Modifier.fillMaxWidth(),
            )
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(12.dp),
            ) {
                Row(Modifier.fillMaxWidth()) {
                    Spacer(Modifier.fillMaxWidth(nowFrac))
                    Text(
                        text = nowMarker,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.storm_radar_slider_observations),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(R.string.storm_radar_slider_forecast),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StormOverlayHud(
    stormUi: StormMapUiState,
    modifier: Modifier = Modifier,
) {
    val timeFmt = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneOffset.UTC)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (stormUi.radarEnabled) {
                val overlay = stormUi.radarOverlay
                val source =
                    overlay?.sourceLabel
                        ?: stringResource(R.string.storm_radar_time_loading)
                val time =
                    overlay?.timeLabel
                        ?: stringResource(R.string.storm_radar_time_loading)
                Text(
                    text = stringResource(R.string.storm_radar_source_time, source, time),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (stormUi.radarAnimationFrames.size >= 2) {
                    val frames = stormUi.radarAnimationFrames
                    val frameIdx = stormUi.radarAnimationIndex
                    val role = radarFrameRole(frames, frameIdx)
                    val positionText =
                        when (role) {
                            RadarFrameRole.FORECAST -> {
                                val ahead = minutesAfterNow(frames, frameIdx) ?: 0
                                stringResource(R.string.storm_radar_showing_forecast, time, ahead)
                            }
                            RadarFrameRole.NOW ->
                                stringResource(R.string.storm_radar_showing_now, time)
                            RadarFrameRole.OBSERVATION -> {
                                val ago = minutesBeforeNow(frames, frameIdx) ?: 0
                                val agoLabel =
                                    stringResource(R.string.storm_radar_observation_ago, ago)
                                stringResource(R.string.storm_radar_showing_observation, time, agoLabel)
                            }
                            null -> stringResource(R.string.storm_radar_time_loading)
                        }
                    Text(
                        text = positionText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val animHint =
                        if (stormUi.radarAnimationPlaying) {
                            stringResource(R.string.storm_radar_animation_on)
                        } else {
                            stringResource(R.string.storm_radar_animation_off)
                        }
                    Text(
                        text = animHint,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (stormUi.lightningEnabled) {
                val lightningLabel =
                    stormUi.lightningFetchedAtMs?.let { ms ->
                        stringResource(
                            R.string.storm_lightning_updated_utc,
                            timeFmt.format(Instant.ofEpochMilli(ms)),
                            stormUi.visibleLightningStrikes.size,
                            stormUi.allLightningStrikes.size,
                        )
                    } ?: stringResource(R.string.storm_lightning_loading)
                Text(
                    text = lightningLabel,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                stormUi.lightningError?.let { err ->
                    Text(
                        text = err,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 2,
                    )
                }
            }
        }
    }
}

private val StormSliderBottomPadding = 8.dp
private val StormHudBottomPadding = 56.dp
private val StormHudBottomWithSliderPadding = 118.dp
private const val RADAR_REFRESH_MS = 5 * 60 * 1000L
private const val LIGHTNING_POLL_MS = 60_000L
