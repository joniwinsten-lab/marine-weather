package fi.veneappi.app.domain

import android.content.Context
import fi.veneappi.app.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import kotlin.math.ceil

object RouteDepartureTime {
    const val SNAP_INTERVAL_MILLIS: Long = 15 * 60 * 1000L

    fun snapToQuarterHour(millis: Long): Long = (millis / SNAP_INTERVAL_MILLIS) * SNAP_INTERVAL_MILLIS

    fun minimumSelectableMillis(nowMillis: Long = System.currentTimeMillis()): Long {
        val snapped = snapToQuarterHour(nowMillis)
        return if (snapped < nowMillis) snapped + SNAP_INTERVAL_MILLIS else snapped
    }

    fun clampScheduledMillis(millis: Long, nowMillis: Long = System.currentTimeMillis()): Long =
        maxOf(snapToQuarterHour(millis), minimumSelectableMillis(nowMillis))

    fun effectiveDepartureMillis(
        isNow: Boolean,
        scheduledMillis: Long,
        nowMillis: Long = System.currentTimeMillis(),
    ): Long = if (isNow) nowMillis else scheduledMillis

    fun formatLocalDateTime(
        context: Context,
        millis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        val instant = Instant.ofEpochMilli(millis)
        val zdt = instant.atZone(zone)
        val today = LocalDate.now(zone)
        val day = zdt.toLocalDate()
        val timeFmt = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withZone(zone)
        val time = timeFmt.format(instant)
        return when (day) {
            today -> time
            today.plusDays(1) -> context.getString(R.string.route_time_tomorrow_fmt, time)
            else -> {
                val dtFmt =
                    DateTimeFormatter.ofPattern("EEE d.M. HH:mm", context.resources.configuration.locales[0])
                        .withZone(zone)
                dtFmt.format(instant)
            }
        }
    }

    fun formatLocalDateTimeFull(
        millis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): String {
        val fmt = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withZone(zone)
        return fmt.format(Instant.ofEpochMilli(millis))
    }

    /** Round minute component up to next 15-minute step for time picker validation. */
    fun ceilMinuteToQuarter(minute: Int): Int {
        val step = 15
        return (ceil((minute + 0.001) / step.toDouble()) * step).toInt().coerceAtMost(45)
    }
}
