package fi.veneappi.app.domain.ais

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AisVesselMotionTest {
    private val baseVessel =
        AisVesselDisplay(
            mmsi = 230982000,
            latitude = 60.0,
            longitude = 24.0,
            name = "FINNMAID",
            callSign = null,
            destination = null,
            imo = null,
            draughtTenthsM = null,
            shipTypeCode = null,
            etaRaw = null,
            navStatusCode = 0,
            sogKn = 10.0,
            cogDeg = 90.0,
            headingDeg = null,
            lastSeenEpochMs = 1_000_000L,
        )

    @Test
    fun isActive_whenRecentFix() {
        assertThat(AisVesselMotion.isActive(baseVessel, nowEpochMs = 1_000_000L + 60_000L)).isTrue()
    }

    @Test
    fun isActive_whenStaleFix() {
        assertThat(
            AisVesselMotion.isActive(
                baseVessel,
                nowEpochMs = 1_000_000L + AisVesselMotion.STALE_MS + 1,
            ),
        ).isFalse()
    }

    @Test
    fun displayPosition_stationaryVessel_noDrift() {
        val vessel = baseVessel.copy(sogKn = 0.2)
        val pos = AisVesselMotion.displayPosition(vessel, nowEpochMs = 1_000_000L + 60_000L)
        assertThat(pos).isEqualTo(60.0 to 24.0)
    }

    @Test
    fun displayPosition_movingVessel_driftsEast() {
        val pos =
            AisVesselMotion.displayPosition(
                baseVessel,
                nowEpochMs = 1_000_000L + 60_000L,
            )
        assertThat(pos).isNotNull()
        assertThat(pos!!.first).isWithin(0.0001).of(60.0)
        assertThat(pos.second).isGreaterThan(24.0)
    }

    @Test
    fun displayPosition_capsDriftAtTwoMinutes() {
        val posAtCap =
            AisVesselMotion.displayPosition(
                baseVessel,
                nowEpochMs = 1_000_000L + AisVesselMotion.MAX_DRIFT_MS,
            )
        val posBeyondCap =
            AisVesselMotion.displayPosition(
                baseVessel,
                nowEpochMs = 1_000_000L + AisVesselMotion.MAX_DRIFT_MS + 120_000L,
            )
        assertThat(posAtCap).isEqualTo(posBeyondCap)
    }

    @Test
    fun applyMqttUpdate_location_mergesFields() {
        val updated =
            baseVessel.applyMqttUpdate(
                AisMqttLocationUpdate(
                    mmsi = 230982000,
                    latitude = 61.0,
                    longitude = 25.0,
                    sogKn = 5.0,
                    cogDeg = 180.0,
                    headingDeg = 179,
                    navStatusCode = 1,
                    lastSeenEpochMs = 2_000_000L,
                ),
            )
        assertThat(updated.latitude).isWithin(0.0001).of(61.0)
        assertThat(updated.name).isEqualTo("FINNMAID")
        assertThat(updated.lastSeenEpochMs).isEqualTo(2_000_000L)
    }

    @Test
    fun needsLiveMapTick_whenMovingActiveVessel() {
        assertThat(AisVesselMotion.needsLiveMapTick(listOf(baseVessel), nowEpochMs = 1_000_500L))
            .isTrue()
    }
}
