package fi.veneappi.app.domain

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.abs

data class HourlySampleRow(
    val targetInstantUtc: Long,
    val point: UnifiedTimePoint?,
    val hourIndex: Int,
)

data class DailySampleRow(
    /** Calendar-day offset from today (0 = today). */
    val dayOffset: Int,
    val date: LocalDate,
    val point: UnifiedTimePoint?,
)

object ForecastSampler {
    /**
     * Picks the forecast step closest to each target time
     * [referenceMillis + offsetHours * 3600_000] for offsets 0, 3, 6, 12.
     */
    fun sampleAtOffsets(
        points: List<UnifiedTimePoint>,
        referenceMillis: Long = System.currentTimeMillis(),
        offsetsHours: IntArray = intArrayOf(0, 3, 6, 12),
    ): List<UnifiedTimePoint?> {
        if (points.isEmpty()) return offsetsHours.map { null }
        return offsetsHours.map { h ->
            val target = referenceMillis + h * 3_600_000L
            points.minByOrNull { kotlin.math.abs(it.instantUtc - target) }
        }
    }

    /** Nearest forecast step to each target instant (epoch millis). */
    fun sampleAtTargetMillis(
        points: List<UnifiedTimePoint>,
        targetsMillis: List<Long>,
    ): List<UnifiedTimePoint?> {
        if (points.isEmpty()) return targetsMillis.map { null }
        return targetsMillis.map { target ->
            points.minByOrNull { kotlin.math.abs(it.instantUtc - target) }
        }
    }

    /**
     * One sample per local calendar day, for [dayOffset] = 0 … [numDays]-1 starting from "today"
     * in [zone]. Prefers the step closest to **local noon** among points that fall on that day.
     * If there is no forecast step on that calendar day, returns null (e.g. source horizon shorter than the table).
     */
    fun sampleDailyNearLocalNoon(
        points: List<UnifiedTimePoint>,
        zone: ZoneId,
        numDays: Int = 13,
    ): List<UnifiedTimePoint?> {
        if (numDays <= 0) return emptyList()
        if (points.isEmpty()) return List(numDays) { null }
        val today: LocalDate = ZonedDateTime.now(zone).toLocalDate()
        return (0 until numDays).map { dayOffset ->
            val date = today.plusDays(dayOffset.toLong())
            val noonMillis =
                date
                    .atTime(LocalTime.NOON)
                    .atZone(zone)
                    .toInstant()
                    .toEpochMilli()
            val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
            val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val inDay = points.filter { it.instantUtc >= dayStart && it.instantUtc < dayEnd }
            if (inDay.isEmpty()) {
                return@map null
            }
            inDay.minByOrNull { abs(it.instantUtc - noonMillis) }
        }
    }

    /**
     * Nearest forecast step for each of the next [hours] whole hours from now in [zone].
     * Steps are chosen monotonically forward in time (same logic as iOS).
     */
    fun sampleHourlyNext(
        points: List<UnifiedTimePoint>,
        hours: Int = 24,
        referenceMillis: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<HourlySampleRow> {
        if (hours <= 0 || points.isEmpty()) return emptyList()
        val reference = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(referenceMillis), zone)
        val hourStart =
            reference
                .withMinute(0)
                .withSecond(0)
                .withNano(0)
        val targets =
            (0 until hours).map { offset ->
                hourStart.plusHours(offset.toLong()).toInstant().toEpochMilli()
            }
        var minInstant = 0L
        return targets.mapIndexed { index, target ->
            val eligible = points.filter { it.instantUtc >= minInstant }
            val pool = eligible.ifEmpty { points }
            val point = pool.minByOrNull { abs(it.instantUtc - target) }
            if (point != null) {
                minInstant = point.instantUtc
            }
            HourlySampleRow(
                targetInstantUtc = target,
                point = point,
                hourIndex = index,
            )
        }
    }

    /**
     * One sample per local day with [numDays] rows. When [skipToday] is true, the first row is tomorrow.
     */
    fun sampleDailyWithLabels(
        points: List<UnifiedTimePoint>,
        numDays: Int = 7,
        zone: ZoneId = ZoneId.systemDefault(),
        skipToday: Boolean = true,
    ): List<DailySampleRow> {
        if (numDays <= 0) return emptyList()
        val startOffset = if (skipToday) 1 else 0
        val samples =
            sampleDailyNearLocalNoon(
                points = points,
                zone = zone,
                numDays = numDays + startOffset,
            ).drop(startOffset)
        val today = ZonedDateTime.now(zone).toLocalDate()
        return samples.mapIndexed { index, point ->
            val dayOffset = index + startOffset
            DailySampleRow(
                dayOffset = dayOffset,
                date = today.plusDays(dayOffset.toLong()),
                point = point,
            )
        }
    }
}
