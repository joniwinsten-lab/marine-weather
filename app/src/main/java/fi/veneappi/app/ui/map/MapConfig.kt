package fi.veneappi.app.ui.map

object MapConfig {
    const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"

    /** Default compare-map zoom (Helsinki harbour area). */
    const val DEFAULT_COMPARE_ZOOM = 12.5

    const val VECTOR_TILES_TEMPLATE = "https://tiles.openfreemap.org/planet/{z}/{x}/{y}.pbf"

    const val NE2_RASTER_TEMPLATE = "https://tiles.openfreemap.org/natural_earth/ne2sr/{z}/{x}/{y}.png"
}
