package fi.veneappi.app.data.marine

/**
 * Verified public marine-forecast pages (default site language).
 */
object MarineServiceUrls {
    fun forCountry(countryCode: String): String =
        when (countryCode) {
            "FI" -> "https://www.ilmatieteenlaitos.fi/saatiedotus-merenkulkijoille"
            "SE" -> "https://www.smhi.se/vader/vader-till-havs/sjorapporten"
            "NO" -> "https://havvarsel.no/"
            "EE" -> "https://www.ilmateenistus.ee/meri/mereilm/"
            else -> "https://www.ilmatieteenlaitos.fi/saatiedotus-merenkulkijoille"
        }
}
