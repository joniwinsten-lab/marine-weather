package fi.veneappi.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fi.veneappi.app.R
import fi.veneappi.app.domain.RouteDepartureTime
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteDepartureBar(
    ui: VeneappiUiState,
    onDepartureNow: () -> Unit,
    onDepartureSchedule: () -> Unit,
    onDepartureScheduled: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var pendingDateMillis by remember { mutableStateOf<Long?>(null) }

    val etaHours = ui.routeWeatherEtaHours ?: 0.0
    val arrivalMillis =
        remember(ui.routeDepartureIsNow, ui.routeDepartureMillis, ui.routeClockTick, etaHours) {
            if (etaHours <= 0) {
                null
            } else {
                val depart =
                    RouteDepartureTime.effectiveDepartureMillis(
                        isNow = ui.routeDepartureIsNow,
                        scheduledMillis = ui.routeDepartureMillis,
                    )
                depart + (etaHours * 3_600_000.0).toLong()
            }
        }
    val arrivalLine =
        arrivalMillis?.let { ms ->
            stringResource(
                R.string.route_arrival_fmt,
                RouteDepartureTime.formatLocalDateTimeFull(ms, zone),
            )
        }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.94f),
        tonalElevation = 1.dp,
    ) {
        Column(
            Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    stringResource(R.string.route_departure_title),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                FilterChip(
                    selected = ui.routeDepartureIsNow,
                    onClick = onDepartureNow,
                    label = { Text(stringResource(R.string.route_departure_now)) },
                    modifier = Modifier.height(28.dp),
                )
                FilterChip(
                    selected = !ui.routeDepartureIsNow,
                    onClick = {
                        if (ui.routeDepartureIsNow) {
                            onDepartureSchedule()
                        }
                        showDatePicker = true
                    },
                    label = { Text(stringResource(R.string.route_departure_scheduled)) },
                    modifier = Modifier.height(28.dp),
                )
            }
            if (ui.routeDepartureIsNow) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.route_departure_now),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                    )
                    arrivalLine?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            } else {
                TextButton(onClick = { showDatePicker = true }) {
                    Text(
                        RouteDepartureTime.formatLocalDateTimeFull(ui.routeDepartureMillis, zone),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 2,
                    )
                }
                arrivalLine?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }

    if (showDatePicker) {
        val minSelectable = RouteDepartureTime.minimumSelectableMillis()
        val minDate = Instant.ofEpochMilli(minSelectable).atZone(zone).toLocalDate()
        val dateState =
            rememberDatePickerState(
                initialSelectedDateMillis = ui.routeDepartureMillis,
                selectableDates =
                    object : SelectableDates {
                        override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                            val day = Instant.ofEpochMilli(utcTimeMillis).atZone(zone).toLocalDate()
                            return !day.isBefore(minDate)
                        }
                    },
            )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val pickedDay = dateState.selectedDateMillis ?: minSelectable
                        pendingDateMillis = pickedDay
                        showDatePicker = false
                        showTimePicker = true
                    },
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
        ) {
            DatePicker(state = dateState)
        }
    }

    if (showTimePicker) {
        val baseMillis = pendingDateMillis ?: ui.routeDepartureMillis
        val baseZdt = Instant.ofEpochMilli(baseMillis).atZone(zone)
        val timeState =
            rememberTimePickerState(
                initialHour = baseZdt.hour,
                initialMinute = baseZdt.minute,
                is24Hour = true,
            )
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {
                showTimePicker = false
                pendingDateMillis = null
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val day = Instant.ofEpochMilli(baseMillis).atZone(zone).toLocalDate()
                        var minute = (timeState.minute / 15) * 15
                        var hour = timeState.hour
                        if (minute >= 60) {
                            minute = 0
                            hour = (hour + 1) % 24
                        }
                        var localDt = day.atTime(LocalTime.of(hour, minute)).atZone(zone)
                        var millis = localDt.toInstant().toEpochMilli()
                        millis = RouteDepartureTime.clampScheduledMillis(millis)
                        onDepartureScheduled(millis)
                        showTimePicker = false
                        pendingDateMillis = null
                    },
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showTimePicker = false
                        pendingDateMillis = null
                    },
                ) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
            text = {
                TimePicker(state = timeState)
            },
        )
    }
}
