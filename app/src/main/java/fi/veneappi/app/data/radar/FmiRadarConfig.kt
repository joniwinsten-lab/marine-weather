package fi.veneappi.app.data.radar

/**
 * FMI OpenWMS radar reflectivity (dBZ) as EPSG:3857 WMS tiles for MapLibre [bbox] substitution.
 * @see <a href="https://en.ilmatieteenlaitos.fi/open-data-manual-radar-data">FMI radar open data</a>
 */
object FmiRadarConfig {
    /** WMS tile edge length in px — 512 for sharper overlay than default 256. */
    const val TILE_SIZE = 512

    const val WMS_TILE_URL_TEMPLATE =
        "https://openwms.fmi.fi/geoserver/wms?" +
            "service=WMS&version=1.3.0&request=GetMap" +
            "&layers=Radar:suomi_dbz_eureffin&styles=&format=image/png&transparent=true" +
            "&crs=EPSG:3857&bbox={bbox-epsg-3857}&width=" + TILE_SIZE + "&height=" + TILE_SIZE

    const val CAPABILITIES_URL =
        "https://openwms.fmi.fi/geoserver/wms?service=WMS&version=1.3.0&request=GetCapabilities"

    const val ATTRIBUTION =
        "Radar © Finnish Meteorological Institute (CC BY 4.0). Not for operational navigation."

    /** Approximate Finland + Baltic radar coverage (lon min, lat min, lon max, lat max). */
    val TILE_BOUNDS = floatArrayOf(18f, 55f, 34f, 72f)
}
