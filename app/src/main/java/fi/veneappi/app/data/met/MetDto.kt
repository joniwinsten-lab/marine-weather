package fi.veneappi.app.data.met

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MetFeature(
    val type: String? = null,
    val geometry: MetGeometry? = null,
    val properties: MetProperties,
)

@Serializable
data class MetGeometry(
    val type: String? = null,
    val coordinates: List<Double> = emptyList(),
)

@Serializable
data class MetProperties(
    val meta: MetMeta? = null,
    val timeseries: List<MetTimeseries> = emptyList(),
)

@Serializable
data class MetMeta(
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class MetTimeseries(
    val time: String,
    val data: MetData = MetData(),
)

@Serializable
data class MetData(
    val instant: MetInstant? = null,
    @SerialName("next_1_hours") val next1Hours: MetNextHours? = null,
)

@Serializable
data class MetInstant(
    val details: MetInstantDetails? = null,
)

@Serializable
data class MetInstantDetails(
    @SerialName("air_temperature") val airTemperature: Double? = null,
    @SerialName("wind_speed") val windSpeed: Double? = null,
    @SerialName("wind_from_direction") val windFromDirection: Double? = null,
    @SerialName("wind_speed_of_gust") val windSpeedOfGust: Double? = null,
)

@Serializable
data class MetNextHours(
    val details: MetNextDetails? = null,
)

@Serializable
data class MetNextDetails(
    @SerialName("precipitation_amount") val precipitationAmount: Double? = null,
    @SerialName("probability_of_precipitation") val probabilityOfPrecipitation: Double? = null,
    @SerialName("probability_of_thunder") val probabilityOfThunder: Double? = null,
)
