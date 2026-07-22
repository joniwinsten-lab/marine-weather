package fi.veneappi.app.domain

/**
 * FMI smart weather symbols — https://www.ilmatieteenlaitos.fi/saamerkkien-selitykset
 * CDN PNGs: https://cdn.fmi.fi/symbol-images/smartsymbol/v3/p/{code}.png
 */
object FmiWeatherSymbol {
    private val availableDayCodes =
        intArrayOf(
            1, 2, 4, 6, 7, 9, 11, 14, 17, 21, 24, 27,
            31, 32, 33, 34, 35, 36, 37, 38, 39,
            41, 42, 43, 44, 45, 46, 47, 48, 49,
            51, 52, 53, 54, 55, 56, 57, 58, 59,
            61, 64, 67, 71, 74, 77,
        )

    fun imageUrl(code: Int): String =
        "https://cdn.fmi.fi/symbol-images/smartsymbol/v3/p/${normalizedCDNCode(code)}.png"

    fun normalizedCDNCode(code: Int): Int {
        val isNight = code >= 100
        val dayCode = if (isNight) code - 100 else code
        val normalizedDay = nearestAvailableDayCode(dayCode)
        return if (isNight) normalizedDay + 100 else normalizedDay
    }

    private fun nearestAvailableDayCode(dayCode: Int): Int {
        if (availableDayCodes.contains(dayCode)) return dayCode
        val lower = availableDayCodes.lastOrNull { it <= dayCode }
        return lower ?: availableDayCodes.firstOrNull() ?: 7
    }
}
