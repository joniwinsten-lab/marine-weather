package fi.veneappi.app.data.radar

import fi.veneappi.app.data.fmi.FmiPrecipitationStep
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class PrecipTimelineInterpolatorTest {
    @Test
    fun gribRatesAt_interpolates_between_hourly_fields() {
        val t0 = Instant.parse("2026-05-16T10:00:00Z")
        val t1 = Instant.parse("2026-05-16T11:00:00Z")
        val fields =
            listOf(
                FmiHarmonieGribParser.PrecipField(
                    validity = t0,
                    amountMm = arrayOf(doubleArrayOf(0.0, 2.0), doubleArrayOf(0.0, 0.0)),
                    bounds = RadarGeoBounds(62.0, 22.0, 58.0, 28.0),
                ),
                FmiHarmonieGribParser.PrecipField(
                    validity = t1,
                    amountMm = arrayOf(doubleArrayOf(0.0, 4.0), doubleArrayOf(0.0, 0.0)),
                    bounds = RadarGeoBounds(62.0, 22.0, 58.0, 28.0),
                ),
            )
        val mid = Instant.parse("2026-05-16T10:30:00Z")
        val rates = PrecipTimelineInterpolator.gribRatesAt(fields, mid)!!
        assertEquals(3.0, rates[0][1], 0.01)
    }

    @Test
    fun wfsRatesAt_interpolates_between_steps() {
        val t0 = 1_000_000L
        val t1 = t0 + 30 * 60 * 1000L
        val series =
            arrayOf(
                arrayOf(
                    listOf(
                        FmiPrecipitationStep(t0, 1.0),
                        FmiPrecipitationStep(t1, 3.0),
                    ),
                ),
            )
        val target = Instant.ofEpochMilli(t0 + 900_000)
        val rates = PrecipTimelineInterpolator.wfsRatesAt(series, target, stepMinutes = 30)
        assertEquals(4.0, rates[0][0], 0.01)
    }
}
