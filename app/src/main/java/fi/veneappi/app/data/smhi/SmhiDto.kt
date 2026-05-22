package fi.veneappi.app.data.smhi

import kotlinx.serialization.Serializable

@Serializable
data class SmhiPointResponse(
    val createdTime: String? = null,
    val referenceTime: String? = null,
    val geometry: SmhiGeometry? = null,
    val timeSeries: List<SmhiTimeSeriesEntry> = emptyList(),
)

@Serializable
data class SmhiGeometry(
    val type: String? = null,
    val coordinates: List<Double> = emptyList(),
)

@Serializable
data class SmhiTimeSeriesEntry(
    val time: String,
    val data: SmhiData = SmhiData(),
)

@Serializable
data class SmhiData(
    val air_temperature: Double? = null,
    val wind_from_direction: Double? = null,
    val wind_speed: Double? = null,
    val wind_speed_of_gust: Double? = null,
    val thunderstorm_probability: Double? = null,
    val precipitation_amount_mean_deterministic: Double? = null,
    val precipitation_amount_mean: Double? = null,
)
