package fi.veneappi.app.data.radar

import java.time.Instant

fun enrichStormTimelineWithForecast(
    frames: List<RadarAnimationFrame>,
    forecastOverlays: Map<Int, ForecastRasterOverlay>,
): List<RadarAnimationFrame> {
    if (frames.isEmpty()) return frames
    val nowIdx = nowFrameIndex(frames)
    val nowFrame = frames[nowIdx]
    return frames.map { frame ->
        if (frame.offsetMinutesFromNow == 0) {
            return@map frame.copy(
                kind = RadarDisplayKind.WMS_TILES,
                wmsTileUrlTemplate = FmiRadarConfig.WMS_TILE_URL_TEMPLATE,
                geoImageUrl = null,
                geoBounds = null,
            )
        }
        if (frame.offsetMinutesFromNow < 0) return@map frame
        val overlay = forecastOverlays[frame.offsetMinutesFromNow]
        if (overlay != null) {
            frame.copy(
                kind = RadarDisplayKind.GEO_IMAGE,
                wmsTileUrlTemplate = null,
                geoImageUrl = overlay.fileUri,
                geoBounds = overlay.bounds,
            )
        } else {
            // No model precip — show latest radar mosaic (not a fixed past TIME=).
            frame.copy(
                kind = RadarDisplayKind.WMS_TILES,
                wmsTileUrlTemplate = FmiRadarConfig.WMS_TILE_URL_TEMPLATE,
                geoImageUrl = null,
                geoBounds = null,
            )
        }
    }
}
