package fi.veneappi.app.data.fmi

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FmiParserTest {
    @Test
    fun parses_positions_and_tuples() {
        val xml =
            """
            <root xmlns:gmlcov="http://www.opengis.net/gmlcov/1.0" xmlns:gml="http://www.opengis.net/gml/3.2">
              <gmlcov:positions>
                60.17000 24.94000  1700000000
                60.17000 24.94000  1700003600
              </gmlcov:positions>
              <gml:doubleOrNilReasonTupleList>
                10.0 2.0 90.0
                9.0 3.0 100.0
              </gml:doubleOrNilReasonTupleList>
            </root>
            """.trimIndent()

        val points = FmiMultipointParser.parse(xml)
        assertThat(points).hasSize(2)
        assertThat(points[0].airTempC).isEqualTo(10.0)
        assertThat(points[0].windSpeedMs).isEqualTo(2.0)
        assertThat(points[0].windFromDeg).isEqualTo(90.0)
        assertThat(points[1].airTempC).isEqualTo(9.0)
    }
}
