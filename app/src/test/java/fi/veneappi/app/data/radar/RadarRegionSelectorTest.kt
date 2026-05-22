package fi.veneappi.app.data.radar

import org.junit.Assert.assertEquals
import org.junit.Test

class RadarRegionSelectorTest {
    @Test
    fun helsinki_prefers_fmi() {
        assertEquals(RadarSourceId.FMI, RadarRegionSelector.preferredSource(60.15, 24.89))
    }

    @Test
    fun stockholm_prefers_met_nordic() {
        assertEquals(RadarSourceId.MET_NORDIC, RadarRegionSelector.preferredSource(59.33, 18.06))
    }
}
