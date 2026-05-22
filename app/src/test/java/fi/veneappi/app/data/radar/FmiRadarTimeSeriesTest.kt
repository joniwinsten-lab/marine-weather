package fi.veneappi.app.data.radar

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Test

class FmiRadarTimeSeriesTest {
    @Test
    fun buildFrames_formats_time_labels() {
        val end = Instant.parse("2026-05-16T12:00:00Z")
        val frames = FmiRadarTimeSeries.buildFrames(end, frameCount = 3, stepMinutes = 5)
        assertEquals(3, frames.size)
        assertEquals("11:50", frames.first().timeLabel)
        assertEquals("12:00", frames.last().timeLabel)
    }

    @Test
    fun buildStormTimelineFrames_spans_3h_past_and_future() {
        val now = Instant.parse("2026-05-16T12:00:00Z")
        val frames = FmiRadarTimeSeries.buildStormTimelineFrames(now)
        assertEquals(StormRadarTimeline.frameCount, frames.size)
        assertEquals(-180, frames.first().offsetMinutesFromNow)
        assertEquals(0, frames[StormRadarTimeline.nowFrameIndex].offsetMinutesFromNow)
        assertEquals(180, frames.last().offsetMinutesFromNow)
    }

    @Test
    fun buildStormTimelineFrames_now_uses_latest_mosaic_without_time_param() {
        val now = Instant.parse("2026-05-16T12:00:00Z")
        val frames = FmiRadarTimeSeries.buildStormTimelineFrames(now)
        val nowFrame = frames[StormRadarTimeline.nowFrameIndex]
        assertEquals(0, nowFrame.offsetMinutesFromNow)
        assertEquals(FmiRadarConfig.WMS_TILE_URL_TEMPLATE, nowFrame.wmsTileUrlTemplate)
    }
}
