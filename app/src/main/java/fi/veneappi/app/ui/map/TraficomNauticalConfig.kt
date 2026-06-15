package fi.veneappi.app.ui.map

/**
 * Traficom open WMTS nautical planning charts.
 *
 * Uses the national mosaic layer [Merikarttasarjat public] so the correct regional series
 * (A–T) is served automatically as the user pans — no manual series switching needed.
 */
object TraficomNauticalConfig {
    const val TILE_URL_TEMPLATE =
        "https://julkinen.traficom.fi/rasteripalvelu/wmts/rest/" +
            "Traficom:Merikarttasarjat%20public/default/WGS84_Pseudo-Mercator/" +
            "WGS84_Pseudo-Mercator:{z}/{y}/{x}?format=image/png"

    const val MIN_ZOOM = 5f
    /** Service returns HTTP 400 above zoom 15 for the mosaic layer. */
    const val MAX_ZOOM = 15f

    /** WGS84 hint bounds: minLon, minLat, maxLon, maxLat (Finnish coastal waters). */
    val BOUNDS = floatArrayOf(17f, 58f, 32f, 71f)

    fun isCurrentSource(uri: String?): Boolean {
        if (uri.isNullOrBlank()) return false
        return uri.contains("Merikarttasarjat%20public") || uri.contains("Merikarttasarjat public")
    }
}
