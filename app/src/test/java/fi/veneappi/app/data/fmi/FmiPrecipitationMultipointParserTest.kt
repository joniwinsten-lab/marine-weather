package fi.veneappi.app.data.fmi

import org.junit.Assert.assertEquals
import org.junit.Test

class FmiPrecipitationMultipointParserTest {
    @Test
    fun parse_extracts_precipitation_steps() {
        val xml =
            """
            <gmlcov:positions>
                60.20 24.90 1778918400
                60.20 24.90 1778920200
            </gmlcov:positions>
            <gml:doubleOrNilReasonTupleList>
                0.5
                1.2
            </gml:doubleOrNilReasonTupleList>
            """.trimIndent()
        val steps = FmiPrecipitationMultipointParser.parse(xml)
        assertEquals(2, steps.size)
        assertEquals(0.5, steps[0].amountMm, 0.001)
        assertEquals(1.2, steps[1].amountMm, 0.001)
    }
}
