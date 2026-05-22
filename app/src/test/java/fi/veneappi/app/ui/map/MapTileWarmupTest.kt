package fi.veneappi.app.ui.map

import org.junit.Assert.assertTrue
import org.junit.Test

class MapTileWarmupTest {
    @Test
    fun latLonToTileXY_helsinkiZoom12() {
        val warmup = MapTileWarmup()
        val (x, y) = warmup.latLonToTileXY(60.1453, 24.9884, 12)
        assertTrue(x in 1000..3000)
        assertTrue(y in 1000..3000)
    }
}
