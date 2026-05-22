package fi.veneappi.app.data.radar

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FmiHarmonieGribParserTest {
    @Test
    fun parse_sample_grib_if_present() {
        val file = File("/tmp/fmi-fc.grib2")
        if (!file.exists()) return
        val fields = FmiHarmonieGribParser.parseAll(file.readBytes())
        assertTrue("expected GRIB precip fields, got ${fields.size}", fields.isNotEmpty())
        val max = fields.maxOf { it.amountMm.maxOfOrNull { row -> row.maxOrNull() ?: 0.0 } ?: 0.0 }
        assertTrue(max > 0.1)
    }
}
