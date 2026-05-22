package fi.veneappi.app.domain

enum class SourceId {
    MET_NORWAY,
    SMHI,
    FMI,
}

data class WeatherSource(
    val id: SourceId,
    val attributionUrl: String,
    val licenseUrl: String,
)

object WeatherSources {
    val MetNorway = WeatherSource(
        id = SourceId.MET_NORWAY,
        attributionUrl = "https://api.met.no/",
        licenseUrl = "https://api.met.no/doc/TermsOfService",
    )
    val Smhi = WeatherSource(
        id = SourceId.SMHI,
        attributionUrl = "https://www.smhi.se/",
        licenseUrl = "https://www.smhi.se/en/legal-information/terms-of-use",
    )
    val Fmi = WeatherSource(
        id = SourceId.FMI,
        attributionUrl = "https://www.ilmatieteenlaitos.fi/",
        licenseUrl = "https://en.ilmatieteenlaitos.fi/open-data-licence",
    )

    fun source(id: SourceId): WeatherSource =
        when (id) {
            SourceId.MET_NORWAY -> MetNorway
            SourceId.SMHI -> Smhi
            SourceId.FMI -> Fmi
        }
}

data class UnifiedTimePoint(
    val instantUtc: Long,
    val airTempC: Double?,
    val windSpeedMs: Double?,
    val windFromDeg: Double?,
    val windGustMs: Double?,
    val precipitationMmPerH: Double?,
    val thunderProbPercent: Double?,
)

data class UnifiedForecast(
    val source: WeatherSource,
    val fetchedAtUtc: Long,
    val modelInfo: String?,
    val points: List<UnifiedTimePoint>,
)

/** Wind samples along a route leg (e.g. departure / 33% / 66% / arrival). */
data class RouteSourceWeatherSlots(
    val slots: List<UnifiedTimePoint?>,
    val fetchedAtUtc: Long,
    val modelInfo: String?,
)

data class SourceForecastState(
    val forecast: UnifiedForecast?,
    val errorMessage: String?,
    val loading: Boolean,
)

enum class WindUnit {
    MetersPerSecond,
    Knots,
}

fun Double?.msToKnots(): Double? = this?.times(1.943844)
