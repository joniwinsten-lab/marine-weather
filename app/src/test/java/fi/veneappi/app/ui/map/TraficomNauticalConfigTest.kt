package fi.veneappi.app.ui.map

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TraficomNauticalConfigTest {
    @Test
    fun isCurrentSource_acceptsNationalMosaic() {
        assertTrue(
            TraficomNauticalConfig.isCurrentSource(
                "https://julkinen.traficom.fi/rasteripalvelu/wmts/rest/Traficom:Merikarttasarjat%20public/default/WGS84_Pseudo-Mercator/WGS84_Pseudo-Mercator:{z}/{y}/{x}?format=image/png",
            ),
        )
    }

    @Test
    fun isCurrentSource_rejectsLegacySeriesB() {
        assertFalse(
            TraficomNauticalConfig.isCurrentSource(
                "https://julkinen.traficom.fi/rasteripalvelu/wmts/rest/Traficom:Merikarttasarja%20B/default/WGS84_Pseudo-Mercator/WGS84_Pseudo-Mercator:{z}/{y}/{x}?format=image/png",
            ),
        )
    }
}
