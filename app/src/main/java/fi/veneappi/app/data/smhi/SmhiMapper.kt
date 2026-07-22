package fi.veneappi.app.data.smhi

import fi.veneappi.app.domain.UnifiedForecast
import fi.veneappi.app.domain.UnifiedTimePoint
import fi.veneappi.app.domain.WeatherSources
import fi.veneappi.app.domain.WeatherSymbolDeriver
import java.time.OffsetDateTime

object SmhiMapper {
    fun toUnified(response: SmhiPointResponse, fetchedAtUtc: Long): UnifiedForecast {
        val model =
            buildString {
                append("SNOW1g")
                response.referenceTime?.let { append(" ref=$it") }
            }
        val points =
            response.timeSeries.mapNotNull { ts ->
                val instant =
                    runCatching {
                        OffsetDateTime.parse(ts.time).toInstant().toEpochMilli()
                    }.getOrNull() ?: return@mapNotNull null
                val d = ts.data
                val precip =
                    d.precipitation_amount_mean_deterministic
                        ?: d.precipitation_amount_mean
                UnifiedTimePoint(
                    instantUtc = instant,
                    airTempC = d.air_temperature,
                    windSpeedMs = d.wind_speed,
                    windFromDeg = d.wind_from_direction,
                    windGustMs = d.wind_speed_of_gust,
                    precipitationMmPerH = precip,
                    thunderProbPercent = d.thunderstorm_probability,
                    weatherSymbolCode =
                        WeatherSymbolDeriver.fmiCode(
                            precipitationMm = precip,
                            thunderProb = d.thunderstorm_probability,
                            instantUtc = instant,
                        ),
                )
            }
        return UnifiedForecast(
            source = WeatherSources.Smhi,
            fetchedAtUtc = fetchedAtUtc,
            modelInfo = model,
            points = points,
        )
    }
}
