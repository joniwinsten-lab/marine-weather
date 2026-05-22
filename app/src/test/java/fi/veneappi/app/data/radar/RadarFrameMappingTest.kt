package fi.veneappi.app.data.radar

import fi.veneappi.app.data.lightning.LightningSourceId
import fi.veneappi.app.data.lightning.LightningStrike
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RadarFrameMappingTest {
    @Test
    fun filter_lightning_shows_only_strikes_before_frame_time() {
        val frame =
            RadarAnimationFrame(
                sourceId = RadarSourceId.FMI,
                kind = RadarDisplayKind.WMS_TILES,
                timeIso = "2026-05-16T12:00:00Z",
                timeLabel = "12:00",
                wmsTileUrlTemplate = "http://example.com",
            )
        val strikes =
            listOf(
                strike(11, 0),
                strike(12, 0),
                strike(12, 30),
                strike(13, 0),
            )
        val visible = filterLightningForRadarFrame(strikes, frame)
        assertEquals(listOf("11:00", "12:00"), visible.map { hourMinute(it) })
    }

    @Test
    fun filter_lightning_empty_for_forecast_frame() {
        val frame =
            RadarAnimationFrame(
                sourceId = RadarSourceId.FMI,
                kind = RadarDisplayKind.WMS_TILES,
                timeIso = "2026-05-16T13:00:00Z",
                timeLabel = "13:00",
                offsetMinutesFromNow = 30,
                wmsTileUrlTemplate = "http://example.com",
            )
        val strikes = listOf(strike(12, 0))
        assertTrue(filterLightningForRadarFrame(strikes, frame).isEmpty())
    }

    private fun strike(
        hour: Int,
        minute: Int,
    ): LightningStrike {
        val iso = "2026-05-16T${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}:00Z"
        return LightningStrike(
            latitude = 60.0,
            longitude = 24.0,
            observedAtEpochMs = java.time.Instant.parse(iso).toEpochMilli(),
            source = LightningSourceId.FMI,
        )
    }

    private fun hourMinute(s: LightningStrike): String =
        java.time.Instant.ofEpochMilli(s.observedAtEpochMs).toString().substring(11, 16)

    @Test
    fun minutes_before_now_uses_zero_offset_frame() {
        val frames =
            listOf(
                frameAt("2026-05-16T10:30:00Z", -90),
                frameAt("2026-05-16T12:00:00Z", 0),
                frameAt("2026-05-16T13:00:00Z", 60),
            )
        assertEquals(90, minutesBeforeNow(frames, 0))
        assertEquals(0, minutesBeforeNow(frames, 1))
        assertEquals(60, minutesAfterNow(frames, 2))
        assertNull(minutesBeforeNow(emptyList(), 0))
    }

    @Test
    fun radar_frame_role_uses_offset() {
        val frames =
            listOf(
                frameAt("2026-05-16T11:00:00Z", -15),
                frameAt("2026-05-16T12:00:00Z", 0),
                frameAt("2026-05-16T12:30:00Z", 30),
            )
        assertEquals(RadarFrameRole.OBSERVATION, radarFrameRole(frames, 0))
        assertEquals(RadarFrameRole.NOW, radarFrameRole(frames, 1))
        assertEquals(RadarFrameRole.FORECAST, radarFrameRole(frames, 2))
    }

    private fun frameAt(
        iso: String,
        offset: Int,
    ) = RadarAnimationFrame(
        sourceId = RadarSourceId.FMI,
        kind = RadarDisplayKind.WMS_TILES,
        timeIso = iso,
        timeLabel = "t",
        offsetMinutesFromNow = offset,
        wmsTileUrlTemplate = "http://example.com",
    )
}
