package fi.veneappi.app.data.radar

import fi.veneappi.app.data.lightning.LightningStrike
import java.time.Instant

fun RadarAnimationFrame.toActiveOverlay(sourceLabel: String): ActiveRadarOverlay =
    ActiveRadarOverlay(
        sourceId = sourceId,
        kind = kind,
        sourceLabel = sourceLabel,
        timeLabel = timeLabel,
        wmsTileUrlTemplate =
            when (kind) {
                RadarDisplayKind.WMS_TILES ->
                    wmsTileUrlTemplate ?: FmiRadarTimeSeries.wmsUrlForTime(timeIso)
                else -> wmsTileUrlTemplate
            },
        geoImageUrl = geoImageUrl,
        geoBounds = geoBounds,
    )

fun RadarAnimationFrame.epochMs(): Long? = runCatching { Instant.parse(timeIso).toEpochMilli() }.getOrNull()

/** Strikes observed at or before the radar frame time (within fetched lookback). */
fun filterLightningForRadarFrame(
    strikes: List<LightningStrike>,
    frame: RadarAnimationFrame,
    lookbackBeforeFrameMs: Long = LIGHTNING_FRAME_LOOKBACK_MS,
): List<LightningStrike> {
    if (frame.offsetMinutesFromNow > 0) return emptyList()
    val frameMs = frame.epochMs() ?: return strikes
    return strikes.filter { s ->
        s.observedAtEpochMs <= frameMs &&
            s.observedAtEpochMs >= frameMs - lookbackBeforeFrameMs
    }
}

const val LIGHTNING_FRAME_LOOKBACK_MS = 2 * 60 * 60 * 1000L

enum class RadarFrameRole {
    OBSERVATION,
    NOW,
    FORECAST,
}

fun nowFrameIndex(frames: List<RadarAnimationFrame>): Int {
    val idx = frames.indexOfFirst { it.offsetMinutesFromNow == 0 }
    if (idx >= 0) return idx
    return StormRadarTimeline.nowFrameIndex.coerceIn(0, frames.lastIndex.coerceAtLeast(0))
}

fun radarFrameRole(
    frames: List<RadarAnimationFrame>,
    index: Int,
): RadarFrameRole? {
    if (frames.isEmpty()) return null
    return radarFrameRole(frames[index.coerceIn(0, frames.lastIndex)])
}

fun radarFrameRole(frame: RadarAnimationFrame): RadarFrameRole =
    when {
        frame.offsetMinutesFromNow > 0 -> RadarFrameRole.FORECAST
        frame.offsetMinutesFromNow == 0 -> RadarFrameRole.NOW
        else -> RadarFrameRole.OBSERVATION
    }

/** Whole minutes before the "now" frame (0 on now). */
fun minutesBeforeNow(
    frames: List<RadarAnimationFrame>,
    index: Int,
): Int? {
    if (frames.isEmpty()) return null
    val nowMs = frames[nowFrameIndex(frames)].epochMs() ?: return null
    val frameMs = frames.getOrNull(index.coerceIn(0, frames.lastIndex))?.epochMs() ?: return null
    return ((nowMs - frameMs) / 60_000L).toInt().coerceAtLeast(0)
}

/** Whole minutes after the "now" frame (0 on now). */
fun minutesAfterNow(
    frames: List<RadarAnimationFrame>,
    index: Int,
): Int? {
    if (frames.isEmpty()) return null
    val nowMs = frames[nowFrameIndex(frames)].epochMs() ?: return null
    val frameMs = frames.getOrNull(index.coerceIn(0, frames.lastIndex))?.epochMs() ?: return null
    return ((frameMs - nowMs) / 60_000L).toInt().coerceAtLeast(0)
}
