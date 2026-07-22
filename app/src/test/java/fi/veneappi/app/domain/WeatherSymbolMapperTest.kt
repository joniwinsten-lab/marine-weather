package fi.veneappi.app.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WeatherSymbolMapperTest {
    @Test
    fun met_clearsky_day_maps_to_1() {
        assertThat(MetWeatherSymbolMapper.fmiCode("clearsky_day")).isEqualTo(1)
    }

    @Test
    fun met_partlycloudy_night_maps_to_104() {
        assertThat(MetWeatherSymbolMapper.fmiCode("partlycloudy_night")).isEqualTo(104)
    }

    @Test
    fun deriver_uses_thunder_threshold() {
        val code =
            WeatherSymbolDeriver.fmiCode(
                precipitationMm = 0.0,
                thunderProb = 40.0,
                instantUtc = 1_700_000_000_000L,
            )
        assertThat(code % 100).isEqualTo(33)
    }

    @Test
    fun cdn_normalizes_missing_day_code() {
        assertThat(FmiWeatherSymbol.normalizedCDNCode(3)).isEqualTo(2)
        assertThat(FmiWeatherSymbol.normalizedCDNCode(103)).isEqualTo(102)
    }
}
