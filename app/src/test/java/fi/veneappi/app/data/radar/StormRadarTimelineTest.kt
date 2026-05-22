package fi.veneappi.app.data.radar

import org.junit.Assert.assertEquals
import org.junit.Test

class StormRadarTimelineTest {
    @Test
    fun offsets_cover_past_now_and_future() {
        val offsets = StormRadarTimeline.offsetsMinutes
        assertEquals(-180, offsets.first())
        assertEquals(0, offsets[StormRadarTimeline.nowFrameIndex])
        assertEquals(180, offsets.last())
        assertEquals(30, offsets[1] - offsets[0])
        assertEquals(13, offsets.size)
        assertEquals(6, StormRadarTimeline.nowFrameIndex)
    }
}
