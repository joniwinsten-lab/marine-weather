package fi.veneappi.app.data.met

import fi.veneappi.app.domain.MetWeatherSymbolMapper
import fi.veneappi.app.domain.UnifiedForecast
import fi.veneappi.app.domain.UnifiedTimePoint
import fi.veneappi.app.domain.WeatherSources
import java.time.OffsetDateTime

object MetMapper {
    fun toUnified(feature: MetFeature, fetchedAtUtc: Long): UnifiedForecast {
        val model =
            buildString {
                feature.properties.meta?.updatedAt?.let { append("updated=$it") }
            }.ifBlank { null }
        val points =
            feature.properties.timeseries.mapNotNull { ts ->
                val instant =
                    runCatching {
                        OffsetDateTime.parse(ts.time).toInstant().toEpochMilli()
                    }.getOrNull() ?: return@mapNotNull null
                val d = ts.data.instant?.details
                val n1 = ts.data.next1Hours?.details
                val symbolCode =
                    ts.data.next1Hours?.summary?.symbolCode
                        ?.let { MetWeatherSymbolMapper.fmiCode(it) }
                UnifiedTimePoint(
                    instantUtc = instant,
                    airTempC = d?.airTemperature,
                    windSpeedMs = d?.windSpeed,
                    windFromDeg = d?.windFromDirection,
                    windGustMs = d?.windSpeedOfGust,
                    precipitationMmPerH = n1?.precipitationAmount,
                    thunderProbPercent = n1?.probabilityOfThunder,
                    weatherSymbolCode = symbolCode,
                )
            }
        return UnifiedForecast(
            source = WeatherSources.MetNorway,
            fetchedAtUtc = fetchedAtUtc,
            modelInfo = model,
            points = points,
        )
    }
}
