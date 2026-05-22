package fi.veneappi.app.data.radar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class StormTimelineFramesTest {
    @Test
    fun enrich_applies_forecast_geo_overlay() {
        val now =
            RadarAnimationFrame(
                sourceId = RadarSourceId.FMI,
                kind = RadarDisplayKind.WMS_TILES,
                timeIso = "2026-05-16T12:00:00Z",
                timeLabel = "12:00",
                offsetMinutesFromNow = 0,
                wmsTileUrlTemplate = "http://now",
            )
        val future =
            RadarAnimationFrame(
                sourceId = RadarSourceId.FMI,
                kind = RadarDisplayKind.WMS_TILES,
                timeIso = "2026-05-16T12:30:00Z",
                timeLabel = "12:30",
                offsetMinutesFromNow = 30,
                wmsTileUrlTemplate = "http://future-broken",
            )
        val bounds = RadarGeoBounds(62.0, 22.0, 58.0, 28.0)
        val enriched =
            enrichStormTimelineWithForecast(
                listOf(now, future),
                mapOf(
                    30 to
                        ForecastRasterOverlay(
                            fileUri = "file:///tmp/fc30.png",
                            bounds = bounds,
                        ),
                ),
            )
        val fc = enriched[1]
        assertEquals(RadarDisplayKind.GEO_IMAGE, fc.kind)
        assertEquals("file:///tmp/fc30.png", fc.geoImageUrl)
        assertEquals(bounds, fc.geoBounds)
        assertNull(fc.wmsTileUrlTemplate)
    }

    @Test
    fun enrich_falls_back_to_now_observation_when_no_overlay() {
        val now =
            RadarAnimationFrame(
                sourceId = RadarSourceId.FMI,
                kind = RadarDisplayKind.WMS_TILES,
                timeIso = "2026-05-16T12:00:00Z",
                timeLabel = "12:00",
                offsetMinutesFromNow = 0,
                wmsTileUrlTemplate = "http://now",
            )
        val future =
            RadarAnimationFrame(
                sourceId = RadarSourceId.FMI,
                kind = RadarDisplayKind.WMS_TILES,
                timeIso = "2026-05-16T14:00:00Z",
                timeLabel = "14:00",
                offsetMinutesFromNow = 120,
                wmsTileUrlTemplate = "http://broken",
            )
        val enriched = enrichStormTimelineWithForecast(listOf(now, future), emptyMap())
        assertEquals(FmiRadarConfig.WMS_TILE_URL_TEMPLATE, enriched[1].wmsTileUrlTemplate)
        assertEquals(RadarDisplayKind.WMS_TILES, enriched[1].kind)
    }
}
