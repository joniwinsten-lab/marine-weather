package fi.veneappi.app.ui

import fi.veneappi.app.domain.ForecastStaleLevel

data class WeatherConnectivityStatus(
    val isOnline: Boolean = true,
    val anyFromCache: Boolean = false,
    val allSourcesFailed: Boolean = false,
    val staleLevel: ForecastStaleLevel = ForecastStaleLevel.Fresh,
    val oldestFetchedUtc: Long? = null,
)
