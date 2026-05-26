package fi.veneappi.app.domain.ais

object AisConfig {
    const val DIGITRAFFIC_BASE_URL = "https://meri.digitraffic.fi/api/ais/v1"
    const val DIGITRAFFIC_USER = "MarineWeather/0.3.0 (fi.veneappi.app)"
    const val REST_POLL_INTERVAL_SECONDS = 60L
    const val VIEWPORT_REFRESH_DEBOUNCE_MS = 450L
    const val MAX_VESSELS_ON_MAP = 600
    const val COURSE_VECTOR_MINUTES = 2.0
    const val MIN_SOG_FOR_VECTOR_KN = 0.4
}
