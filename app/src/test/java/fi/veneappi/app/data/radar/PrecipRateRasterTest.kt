package fi.veneappi.app.data.radar

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrecipRateRasterTest {
    @Test
    fun outerEdgeAlpha_fadesAtBorder() {
        val center = PrecipRateRaster.outerEdgeAlphaFactor(256, 512, 0.12)
        val edge = PrecipRateRaster.outerEdgeAlphaFactor(0, 512, 0.12)
        assertTrue(center > 0.99)
        assertTrue(edge < 0.05)
    }

    @Test
    fun smoothGrid_reducesSharpCellContrast() {
        val grid =
            arrayOf(
                doubleArrayOf(0.0, 0.0, 4.0),
                doubleArrayOf(0.0, 0.0, 4.0),
                doubleArrayOf(0.0, 0.0, 4.0),
            )
        val smoothed = PrecipRateRaster.smoothGrid(grid, 1)
        assertTrue(smoothed[1][1] in 0.5..2.5)
        assertTrue(smoothed[0][0] < 0.5)
    }

    @Test
    fun hasSignificantPrecip_rejectsDrizzleNoise() {
        val drizzle =
            Array(10) {
                DoubleArray(10) { 0.15 }
            }
        assertFalse(PrecipRateRaster.hasSignificantPrecip(drizzle))
    }

    @Test
    fun hasSignificantPrecip_acceptsLocalizedRain() {
        val rain =
            Array(10) { row ->
                DoubleArray(10) { col ->
                    if (row in 3..6 && col in 3..6) 2.5 else 0.0
                }
            }
        assertTrue(PrecipRateRaster.hasSignificantPrecip(rain))
    }
}
