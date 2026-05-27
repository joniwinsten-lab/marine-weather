package fi.veneappi.app.data.net

import fi.veneappi.app.domain.SourceId
import fi.veneappi.app.domain.UnifiedForecast

data class SourceWeatherOutcome(
    val result: Result<UnifiedForecast>,
    val servedFromCache: Boolean,
)

data class WeatherLoadReport(
    val bySource: Map<SourceId, SourceWeatherOutcome>,
) {
    fun forecasts(): Map<SourceId, Result<UnifiedForecast>> =
        bySource.mapValues { it.value.result }

    val servedFromCache: Set<SourceId> =
        bySource.filterValues { it.servedFromCache }.keys

    val anyServedFromCache: Boolean = servedFromCache.isNotEmpty()

    val allSourcesFailed: Boolean =
        bySource.isNotEmpty() && bySource.values.all { it.result.isFailure }
}
