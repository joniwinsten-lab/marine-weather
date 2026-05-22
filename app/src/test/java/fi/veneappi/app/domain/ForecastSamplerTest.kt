package fi.veneappi.app.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ForecastSamplerTest {
    @Test
    fun picks_nearest_time_to_each_offset() {
        val ref = 1_700_000_000_000L
        val h = 3_600_000L
        val points =
            listOf(
                UnifiedTimePoint(
                    instantUtc = ref + 0 * h,
                    airTempC = 10.0,
                    windSpeedMs = 1.0,
                    windFromDeg = 90.0,
                    windGustMs = null,
                    precipitationMmPerH = null,
                    thunderProbPercent = null,
                ),
                UnifiedTimePoint(
                    instantUtc = ref + 3 * h,
                    airTempC = 11.0,
                    windSpeedMs = 2.0,
                    windFromDeg = 100.0,
                    windGustMs = null,
                    precipitationMmPerH = null,
                    thunderProbPercent = null,
                ),
                UnifiedTimePoint(
                    instantUtc = ref + 6 * h,
                    airTempC = 12.0,
                    windSpeedMs = 3.0,
                    windFromDeg = 110.0,
                    windGustMs = null,
                    precipitationMmPerH = null,
                    thunderProbPercent = null,
                ),
                UnifiedTimePoint(
                    instantUtc = ref + 12 * h,
                    airTempC = 13.0,
                    windSpeedMs = 4.0,
                    windFromDeg = 120.0,
                    windGustMs = null,
                    precipitationMmPerH = null,
                    thunderProbPercent = null,
                ),
            )
        val sampled = ForecastSampler.sampleAtOffsets(points, referenceMillis = ref)
        assertThat(sampled[0]?.airTempC).isEqualTo(10.0)
        assertThat(sampled[1]?.airTempC).isEqualTo(11.0)
        assertThat(sampled[2]?.airTempC).isEqualTo(12.0)
        assertThat(sampled[3]?.airTempC).isEqualTo(13.0)
    }

    @Test
    fun picks_nearest_time_for_target_instants() {
        val ref = 1_700_000_000_000L
        val h = 3_600_000L
        val points =
            listOf(
                UnifiedTimePoint(
                    instantUtc = ref,
                    airTempC = 10.0,
                    windSpeedMs = 1.0,
                    windFromDeg = 90.0,
                    windGustMs = null,
                    precipitationMmPerH = null,
                    thunderProbPercent = null,
                ),
                UnifiedTimePoint(
                    instantUtc = ref + 3 * h,
                    airTempC = 11.0,
                    windSpeedMs = 2.0,
                    windFromDeg = 100.0,
                    windGustMs = null,
                    precipitationMmPerH = null,
                    thunderProbPercent = null,
                ),
            )
        val sampled = ForecastSampler.sampleAtTargetMillis(points, listOf(ref + 2 * h))
        assertThat(sampled[0]?.airTempC).isEqualTo(11.0)
    }
}
