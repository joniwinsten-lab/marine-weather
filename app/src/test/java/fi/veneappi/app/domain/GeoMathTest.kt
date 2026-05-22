package fi.veneappi.app.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GeoMathTest {
    @Test
    fun haversine_helsinki_tallinn_orderOfMagnitude() {
        val m =
            GeoMath.haversineMeters(
                60.17,
                24.94,
                59.44,
                24.75,
            )
        assertThat(m).isGreaterThan(70_000.0)
        assertThat(m).isLessThan(120_000.0)
    }

    @Test
    fun greatCircle_endpoints() {
        val pts = GeoMath.greatCirclePoints(60.0, 25.0, 59.0, 26.0, segments = 4)
        assertThat(pts.first().first).isWithin(1e-6).of(60.0)
        assertThat(pts.last().first).isWithin(1e-6).of(59.0)
    }
}
