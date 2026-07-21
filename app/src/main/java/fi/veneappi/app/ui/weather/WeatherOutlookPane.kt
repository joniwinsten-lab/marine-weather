package fi.veneappi.app.ui.weather

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fi.veneappi.app.R
import fi.veneappi.app.domain.DailySampleRow
import fi.veneappi.app.domain.ForecastSampler
import fi.veneappi.app.domain.HourlySampleRow
import fi.veneappi.app.domain.SourceId
import fi.veneappi.app.domain.UnifiedTimePoint
import fi.veneappi.app.domain.WindUnit
import fi.veneappi.app.domain.msToKnots
import fi.veneappi.app.ui.VeneappiUiState
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun WeatherOutlookPane(
    ui: VeneappiUiState,
    windUnit: WindUnit,
    selectedSource: SourceId,
    onSourceChange: (SourceId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val zone = ZoneId.systemDefault()
    val locale = LocalConfiguration.current.locales[0] ?: Locale.US
    val timeFmt = remember(locale) { DateTimeFormatter.ofPattern("HH:mm", locale) }
    val weekdayFmt = remember(locale) { DateTimeFormatter.ofPattern("EEE", locale) }
    val points = ui.forecasts[selectedSource]?.getOrNull()?.points.orEmpty()
    val hourlyRows =
        remember(points, zone) {
            ForecastSampler.sampleHourlyNext(points = points, hours = 24, zone = zone)
        }
    val dailyRows =
        remember(points, zone) {
            ForecastSampler
                .sampleDailyWithLabels(points = points, numDays = 7, zone = zone, skipToday = true)
                .filter { it.point != null }
        }
    val windUnitLabel =
        when (windUnit) {
            WindUnit.MetersPerSecond -> stringResource(R.string.units_ms)
            WindUnit.Knots -> stringResource(R.string.units_kn)
        }
    val hasData = hourlyRows.any { it.point != null }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.weather_screen_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                stringResource(R.string.weather_map_hint, ui.latitude, ui.longitude),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                stringResource(R.string.weather_symbols_attribution),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                stringResource(R.string.weather_source_picker),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = selectedSource == SourceId.MET_NORWAY,
                    onClick = { onSourceChange(SourceId.MET_NORWAY) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                    label = {
                        Text(
                            stringResource(R.string.source_met_norway),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
                SegmentedButton(
                    selected = selectedSource == SourceId.SMHI,
                    onClick = { onSourceChange(SourceId.SMHI) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                    label = {
                        Text(
                            stringResource(R.string.source_smhi),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
                SegmentedButton(
                    selected = selectedSource == SourceId.FMI,
                    onClick = { onSourceChange(SourceId.FMI) },
                    shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                    label = {
                        Text(
                            stringResource(R.string.source_fmi),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
            }
            if (selectedSource == SourceId.FMI) {
                Text(
                    stringResource(R.string.weather_fmi_horizon_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        when {
            ui.loadingWeather && !hasData -> {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                    Text(
                        stringResource(R.string.weather_loading),
                        modifier = Modifier.padding(start = 12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            !hasData -> {
                Text(
                    stringResource(R.string.weather_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> {
                WeatherSectionHeader(
                    title = stringResource(R.string.weather_section_24h),
                    windUnitLabel = windUnitLabel,
                )
                hourlyRows.forEachIndexed { index, row ->
                    WeatherForecastRow(
                        label = hourlyRowLabel(row, timeFmt),
                        point = row.point,
                        windUnit = windUnit,
                        highlighted = row.hourIndex == 0,
                    )
                    if (index < hourlyRows.lastIndex) {
                        HorizontalDivider()
                    }
                }

                if (dailyRows.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp))
                    WeatherSectionHeader(
                        title = stringResource(R.string.weather_section_7d),
                        windUnitLabel = windUnitLabel,
                    )
                    dailyRows.forEachIndexed { index, row ->
                        WeatherForecastRow(
                            label = dailyRowLabel(row, weekdayFmt),
                            point = row.point,
                            windUnit = windUnit,
                            highlighted = false,
                        )
                        if (index < dailyRows.lastIndex) {
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WeatherSectionHeader(
    title: String,
    windUnitLabel: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            modifier = Modifier.weight(0.22f),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            stringResource(R.string.weather_col_temp),
            modifier = Modifier.weight(0.14f),
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(R.string.weather_col_wind, windUnitLabel),
            modifier = Modifier.weight(0.34f),
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(R.string.weather_col_precip),
            modifier = Modifier.weight(0.14f),
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.End,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WeatherForecastRow(
    label: String,
    point: UnifiedTimePoint?,
    windUnit: WindUnit,
    highlighted: Boolean,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            modifier = Modifier.weight(0.22f),
            style =
                if (highlighted) {
                    MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                } else {
                    MaterialTheme.typography.bodyMedium
                },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            point?.airTempC?.let { "${it.roundToInt()}°" } ?: "—",
            modifier = Modifier.weight(0.14f),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            formatWindCell(point, windUnit),
            modifier = Modifier.weight(0.34f),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            point?.precipitationMmPerH?.let { if (it < 0.05) "0" else "%.1f".format(it) } ?: "—",
            modifier = Modifier.weight(0.14f),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End,
        )
    }
}

@Composable
private fun hourlyRowLabel(
    row: HourlySampleRow,
    timeFmt: DateTimeFormatter,
): String {
    if (row.hourIndex == 0) {
        return stringResource(R.string.weather_now)
    }
    val instant = java.time.Instant.ofEpochMilli(row.targetInstantUtc)
    return timeFmt.format(instant.atZone(ZoneId.systemDefault()))
}

@Composable
private fun dailyRowLabel(
    row: DailySampleRow,
    weekdayFmt: DateTimeFormatter,
): String =
    when (row.dayOffset) {
        0 -> stringResource(R.string.weather_day_today)
        1 -> stringResource(R.string.weather_day_tomorrow)
        else -> weekdayFmt.format(row.date)
    }

@Composable
private fun formatWindCell(
    point: UnifiedTimePoint?,
    windUnit: WindUnit,
): String {
    if (point?.windSpeedMs == null) return "—"
    val speed =
        when (windUnit) {
            WindUnit.MetersPerSecond -> "%.0f".format(point.windSpeedMs)
            WindUnit.Knots -> "%.0f".format(point.windSpeedMs.msToKnots() ?: 0.0)
        }
    val card = point.windFromDeg?.let { cardinalFromDegrees(it) }.orEmpty()
    return if (card.isEmpty()) speed else "$speed $card"
}

private fun cardinalFromDegrees(deg: Double): String {
    val dirs =
        listOf(
            "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
            "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW",
        )
    val x = ((deg % 360.0) + 360.0) % 360.0
    val idx = ((x + 11.25) / 22.5).toInt() % 16
    return dirs[idx]
}
