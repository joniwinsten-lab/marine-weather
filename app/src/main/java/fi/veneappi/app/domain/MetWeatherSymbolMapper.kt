package fi.veneappi.app.domain

/** Maps MET Norway `symbol_code` strings to FMI smart-symbol integers for icon CDN. */
object MetWeatherSymbolMapper {
    private val dayCodes =
        mapOf(
            "clearsky" to 1,
            "fair" to 2,
            "partlycloudy" to 4,
            "mostlycloudy" to 6,
            "cloudy" to 7,
            "fog" to 9,
            "lightrain" to 11,
            "freezingdrizzle" to 14,
            "freezingrain" to 17,
            "lightsleet" to 22,
            "sleet" to 23,
            "lightsleetshowers" to 24,
            "sleetshowers" to 25,
            "lightrainshowers" to 26,
            "rainshowers" to 29,
            "heavyrainshowers" to 30,
            "rain" to 32,
            "heavyrain" to 34,
            "thunder" to 33,
            "rainandthunder" to 33,
            "heavyrainandthunder" to 33,
            "lightrainandthunder" to 33,
            "sleetandthunder" to 33,
            "lightsnowshowers" to 72,
            "snowshowers" to 73,
            "lightsnow" to 71,
            "snow" to 75,
            "heavysnow" to 77,
            "snowandthunder" to 86,
        )

    fun fmiCode(fromMetSymbolCode: String): Int {
        val lowered = fromMetSymbolCode.lowercase()
        val isNight = lowered.contains("_night") || lowered.contains("polartwilight")
        val base =
            lowered
                .replace("_day", "")
                .replace("_night", "")
                .replace("_polartwilight", "")

        dayCodes[base]?.let { day ->
            return if (isNight) day + 100 else day
        }

        val sortedKeys = dayCodes.keys.sortedByDescending { it.length }
        for (key in sortedKeys) {
            if (base.startsWith(key)) {
                val day = dayCodes.getValue(key)
                return if (isNight) day + 100 else day
            }
        }
        return if (isNight) 107 else 7
    }
}
