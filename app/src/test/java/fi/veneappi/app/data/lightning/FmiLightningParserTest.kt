package fi.veneappi.app.data.lightning

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FmiLightningParserTest {
    @Test
    fun parses_sample_wfs_members() {
        val xml =
            javaClass.classLoader!!
                .getResourceAsStream("fmi_lightning_sample.xml")!!
                .bufferedReader()
                .readText()
        val strikes = FmiLightningParser.parse(xml)
        assertEquals(2, strikes.size)
        assertTrue(strikes.any { it.latitude in 59.0..61.0 && it.longitude in 24.0..25.0 })
        assertTrue(strikes.any { it.latitude in 61.0..63.0 && it.longitude in 21.0..22.0 })
    }
}
