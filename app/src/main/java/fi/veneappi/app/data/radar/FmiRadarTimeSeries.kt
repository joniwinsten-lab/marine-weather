package fi.veneappi.app.data.radar

import fi.veneappi.app.data.FmiInstantFormat
import java.time.Instant
import java.time.temporal.ChronoUnit

object FmiRadarTimeSeries {
    fun wmsUrlForTime(timeIso: String): String =
        FmiRadarConfig.WMS_TILE_URL_TEMPLATE + "&TIME=${encodeTime(timeIso)}"

    fun wmsTemplateWithPlaceholder(): String =
        FmiRadarConfig.WMS_TILE_URL_TEMPLATE + "&TIME={time}"

    fun buildFrames(
        endInclusive: Instant,
        frameCount: Int = 12,
        stepMinutes: Long = 5,
    ): List<RadarAnimationFrame> {
        val start = endInclusive.minus((frameCount - 1) * stepMinutes, ChronoUnit.MINUTES)
        return (0 until frameCount).map { i ->
            val t = start.plus(i * stepMinutes, ChronoUnit.MINUTES)
            frameAt(t, offsetMinutesFromNow = 0)
        }
    }

    fun buildStormTimelineFrames(now: Instant): List<RadarAnimationFrame> =
        StormRadarTimeline.offsetsMinutes.map { offset ->
            val t = now.plus(offset.toLong(), ChronoUnit.MINUTES)
            frameAt(t, offsetMinutesFromNow = offset)
        }

    private fun frameAt(
        t: Instant,
        offsetMinutesFromNow: Int,
    ): RadarAnimationFrame {
        val iso = FmiInstantFormat.toParam(t)
        // "Nyt" uses latest mosaic (no TIME=) — exact timestamp often has no WMS tiles yet.
        val wmsUrl =
            if (offsetMinutesFromNow == 0) {
                FmiRadarConfig.WMS_TILE_URL_TEMPLATE
            } else {
                wmsUrlForTime(iso)
            }
        return RadarAnimationFrame(
            sourceId = RadarSourceId.FMI,
            kind = RadarDisplayKind.WMS_TILES,
            timeIso = iso,
            timeLabel = FmiInstantFormat.toDisplayHHmm(t),
            offsetMinutesFromNow = offsetMinutesFromNow,
            wmsTileUrlTemplate = wmsUrl,
        )
    }

    fun parseDimensionEnd(dimension: String): Instant? {
        val endIso = dimension.split("/").getOrNull(1)?.trim() ?: return null
        return runCatching { Instant.parse(endIso) }.getOrNull()
    }

    private fun encodeTime(iso: String): String =
        java.net.URLEncoder.encode(iso, Charsets.UTF_8)
}
