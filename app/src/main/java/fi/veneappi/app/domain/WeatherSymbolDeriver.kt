package fi.veneappi.app.domain

import java.time.Instant
import java.time.ZoneId

/** Derives FMI smart-symbol codes when the provider does not supply one (e.g. SMHI). */
object WeatherSymbolDeriver {
    fun fmiCode(
        precipitationMm: Double?,
        thunderProb: Double?,
        instantUtc: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Int {
        val precip = precipitationMm ?: 0.0
        val thunder = thunderProb ?: 0.0
        val night = isNight(instantUtc, zone)
        val dayCode =
            when {
                thunder >= 30 -> 33
                precip >= 4 -> 32
                precip >= 1 -> 26
                precip >= 0.1 -> 11
                else -> 4
            }
        return if (night) dayCode + 100 else dayCode
    }

    fun resolveCode(point: UnifiedTimePoint?, zone: ZoneId = ZoneId.systemDefault()): Int? {
        if (point == null) return null
        point.weatherSymbolCode?.let { return it }
        return fmiCode(
            precipitationMm = point.precipitationMmPerH,
            thunderProb = point.thunderProbPercent,
            instantUtc = point.instantUtc,
            zone = zone,
        )
    }

    private fun isNight(
        instantUtc: Long,
        zone: ZoneId,
    ): Boolean {
        val hour = Instant.ofEpochMilli(instantUtc).atZone(zone).hour
        return hour < 6 || hour >= 21
    }
}
