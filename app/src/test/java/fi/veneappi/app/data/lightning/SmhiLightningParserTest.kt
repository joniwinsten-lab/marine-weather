package fi.veneappi.app.data.lightning

import java.time.ZoneOffset
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmhiLightningParserTest {
    @Test
    fun parses_csv_rows_within_lookback() {
        val now = ZonedDateTime.now(ZoneOffset.UTC)
        val csv =
            """
            version;year;month;day;hours;minutes;seconds;nanoseconds;lat;lon;peakCurrent
            0;${now.year};${now.monthValue};${now.dayOfMonth};${now.hour};${now.minute};${now.second};0;61.65;14.67;6
            0;2020;1;1;0;0;0;0;60.0;18.0;4
            """.trimIndent()
        val lookback = System.currentTimeMillis() - 60 * 60 * 1000
        val strikes = SmhiLightningParser.parse(csv, lookback)
        assertEquals(1, strikes.size)
        assertTrue(strikes.all { it.source == LightningSourceId.SMHI })
    }
}
