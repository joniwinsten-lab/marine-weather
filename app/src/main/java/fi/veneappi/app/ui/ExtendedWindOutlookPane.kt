package fi.veneappi.app.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fi.veneappi.app.R
import fi.veneappi.app.domain.ForecastSampler
import fi.veneappi.app.domain.SourceId
import fi.veneappi.app.domain.UnifiedTimePoint
import fi.veneappi.app.domain.WindUnit
import fi.veneappi.app.domain.msToKnots
import fi.veneappi.app.ui.map.MapPane
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private const val EXT_WIND_DAY_COUNT = 13

/**
 * 12+ day outlook with map (long-press = forecast point), matching Compare split weights where possible.
 */
@Composable
fun ExtendedWindOutlookPane(
    ui: VeneappiUiState,
    windUnit: WindUnit,
    onWindUnit: (WindUnit) -> Unit,
    onRefresh: () -> Unit,
    onLongPressMap: (Double, Double) -> Unit,
    onMyLocation: () -> Unit,
    traficomPlanningChart: Boolean,
    onTraficomPlanningChartChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val zone = ZoneId.systemDefault()
    val locale =
        LocalConfiguration.current.locales[0]
            ?: Locale.US
    val dateFmt = remember(locale) { DateTimeFormatter.ofPattern("EEE d.M.", locale) }
    val today = remember(zone) { ZonedDateTime.now(zone).toLocalDate() }
    val (metDaily, smhiDaily, fmiDaily) =
        remember(ui.forecasts, zone) {
            val metPts = ui.forecasts[SourceId.MET_NORWAY]?.getOrNull()?.points.orEmpty()
            val smhiPts = ui.forecasts[SourceId.SMHI]?.getOrNull()?.points.orEmpty()
            val fmiPts = ui.forecasts[SourceId.FMI]?.getOrNull()?.points.orEmpty()
            Triple(
                ForecastSampler.sampleDailyNearLocalNoon(metPts, zone, EXT_WIND_DAY_COUNT),
                ForecastSampler.sampleDailyNearLocalNoon(smhiPts, zone, EXT_WIND_DAY_COUNT),
                ForecastSampler.sampleDailyNearLocalNoon(fmiPts, zone, EXT_WIND_DAY_COUNT),
            )
        }
    val hasAnyForecast =
        metDaily.any { it != null } || smhiDaily.any { it != null } || fmiDaily.any { it != null }
    val twoPane =
        LocalConfiguration.current.screenWidthDp >= UiBreakpoints.TWO_PANE_MIN_WIDTH_DP

    if (twoPane) {
        Row(modifier = modifier.fillMaxSize()) {
            ExtendedWindMapColumn(
                ui = ui,
                traficomPlanningChart = traficomPlanningChart,
                onTraficomPlanningChartChange = onTraficomPlanningChartChange,
                onLongPressMap = onLongPressMap,
                onMyLocation = onMyLocation,
                modifier =
                    Modifier
                        .weight(0.56f)
                        .fillMaxHeight(),
            )
            BoxWithConstraints(
                modifier =
                    Modifier
                        .weight(0.44f)
                        .fillMaxHeight(),
            ) {
                val tableContentWidth = maxWidth.coerceIn(280.dp, 520.dp)
                ExtendedWindOutlookScrollColumn(
                    ui = ui,
                    windUnit = windUnit,
                    onWindUnit = onWindUnit,
                    onRefresh = onRefresh,
                    today = today,
                    dateFmt = dateFmt,
                    metDaily = metDaily,
                    smhiDaily = smhiDaily,
                    fmiDaily = fmiDaily,
                    hasAnyForecast = hasAnyForecast,
                    tableContentWidth = tableContentWidth,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    } else {
        Column(modifier = modifier.fillMaxSize()) {
            ExtendedWindMapColumn(
                ui = ui,
                traficomPlanningChart = traficomPlanningChart,
                onTraficomPlanningChartChange = onTraficomPlanningChartChange,
                onLongPressMap = onLongPressMap,
                onMyLocation = onMyLocation,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(0.52f),
            )
            BoxWithConstraints(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(0.48f),
            ) {
                val tableContentWidth = maxWidth.coerceIn(280.dp, 520.dp)
                ExtendedWindOutlookScrollColumn(
                    ui = ui,
                    windUnit = windUnit,
                    onWindUnit = onWindUnit,
                    onRefresh = onRefresh,
                    today = today,
                    dateFmt = dateFmt,
                    metDaily = metDaily,
                    smhiDaily = smhiDaily,
                    fmiDaily = fmiDaily,
                    hasAnyForecast = hasAnyForecast,
                    tableContentWidth = tableContentWidth,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun ExtendedWindMapColumn(
    ui: VeneappiUiState,
    traficomPlanningChart: Boolean,
    onTraficomPlanningChartChange: (Boolean) -> Unit,
    onLongPressMap: (Double, Double) -> Unit,
    onMyLocation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mapLabel = stringResource(R.string.content_map)
    Column(modifier = modifier) {
        FilterChip(
            selected = traficomPlanningChart,
            onClick = { onTraficomPlanningChartChange(!traficomPlanningChart) },
            label = { Text(stringResource(R.string.map_traficom_overlay)) },
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
        MapPane(
            latitude = ui.latitude,
            longitude = ui.longitude,
            routeGeometry = ui.routeGeometry,
            routeStart = ui.routeStart,
            routeEnd = ui.routeEnd,
            harbors = emptyList(),
            onLongPress = onLongPressMap,
            onMapClick = null,
            traficomPlanningRasterEnabled = traficomPlanningChart,
            onMyLocation = onMyLocation,
            mapRecenterSignal = ui.mapRecenterSignal,
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .semantics { contentDescription = mapLabel },
        )
    }
}

@Composable
private fun ExtendedWindOutlookScrollColumn(
    ui: VeneappiUiState,
    windUnit: WindUnit,
    onWindUnit: (WindUnit) -> Unit,
    onRefresh: () -> Unit,
    today: LocalDate,
    dateFmt: DateTimeFormatter,
    metDaily: List<UnifiedTimePoint?>,
    smhiDaily: List<UnifiedTimePoint?>,
    fmiDaily: List<UnifiedTimePoint?>,
    hasAnyForecast: Boolean,
    tableContentWidth: Dp,
    modifier: Modifier = Modifier,
) {
    val refreshDesc = stringResource(R.string.weather_refresh)
    val scroll = rememberScrollState()
    val hScroll = rememberScrollState()

    Column(
        modifier =
            modifier
                .widthIn(max = UiBreakpoints.EXTENDED_WIND_TABLE_MAX_WIDTH_DP.dp)
                .fillMaxWidth()
                .verticalScroll(scroll)
                .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.route_ext_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text =
                        stringResource(
                            R.string.route_ext_coords,
                            ui.latitude,
                            ui.longitude,
                        ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (ui.loadingWeather) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp,
                )
            }
            FilterChip(
                selected = windUnit == WindUnit.MetersPerSecond,
                onClick = { onWindUnit(WindUnit.MetersPerSecond) },
                label = { Text(stringResource(R.string.units_ms), style = MaterialTheme.typography.labelSmall) },
                modifier = Modifier.height(32.dp),
            )
            FilterChip(
                selected = windUnit == WindUnit.Knots,
                onClick = { onWindUnit(WindUnit.Knots) },
                label = { Text(stringResource(R.string.units_kn), style = MaterialTheme.typography.labelSmall) },
                modifier = Modifier.height(32.dp),
            )
            IconButton(
                onClick = onRefresh,
                enabled = !ui.loadingWeather,
                modifier = Modifier.semantics { contentDescription = refreshDesc },
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null)
            }
        }
        Text(
            text = stringResource(R.string.route_ext_subtitle),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
        )
        Text(
            text = stringResource(R.string.route_ext_method),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        if (!hasAnyForecast && !ui.loadingWeather) {
            Text(
                text = stringResource(R.string.route_ext_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 12.dp),
            )
        }
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(hScroll),
        ) {
            Column(
                modifier = Modifier.width(tableContentWidth),
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.route_ext_col_day),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1.05f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.source_met_norway),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.source_smhi),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.source_fmi),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                for (dayIndex in 0 until EXT_WIND_DAY_COUNT) {
                    val date = today.plusDays(dayIndex.toLong())
                    val dayLabel =
                        if (dayIndex == 0) {
                            stringResource(R.string.route_ext_day_today)
                        } else {
                            date.format(dateFmt)
                        }
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text(
                            text = dayLabel,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.weight(1.05f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        WindOutlookCell(
                            point = metDaily.getOrNull(dayIndex),
                            windUnit = windUnit,
                            modifier = Modifier.weight(1f),
                        )
                        WindOutlookCell(
                            point = smhiDaily.getOrNull(dayIndex),
                            windUnit = windUnit,
                            modifier = Modifier.weight(1f),
                        )
                        WindOutlookCell(
                            point = fmiDaily.getOrNull(dayIndex),
                            windUnit = windUnit,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.route_ext_attribution),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WindOutlookCell(
    point: UnifiedTimePoint?,
    windUnit: WindUnit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (point == null || point.windSpeedMs == null) {
            Text(
                text = stringResource(R.string.route_ext_no_forecast),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            return@Column
        }
        val speed =
            when (windUnit) {
                WindUnit.MetersPerSecond -> "%.1f".format(point.windSpeedMs)
                WindUnit.Knots -> "%.0f".format(point.windSpeedMs.msToKnots() ?: 0.0)
            }
        val unit =
            when (windUnit) {
                WindUnit.MetersPerSecond -> stringResource(R.string.units_ms)
                WindUnit.Knots -> stringResource(R.string.units_kn)
            }
        Text(
            text = "$speed $unit",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        point.windFromDeg?.let { deg ->
            Text(
                text = "${deg.roundToInt()}°",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        point.windGustMs?.let { gust ->
            val gustNum =
                when (windUnit) {
                    WindUnit.MetersPerSecond -> "%.1f".format(gust)
                    WindUnit.Knots -> "%.0f".format(gust.msToKnots() ?: gust)
                }
            val gustUnit =
                when (windUnit) {
                    WindUnit.MetersPerSecond -> stringResource(R.string.units_ms)
                    WindUnit.Knots -> stringResource(R.string.units_kn)
                }
            Text(
                text = stringResource(R.string.weather_gust_max, "$gustNum $gustUnit"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
