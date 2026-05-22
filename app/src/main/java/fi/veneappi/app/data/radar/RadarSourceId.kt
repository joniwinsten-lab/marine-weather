package fi.veneappi.app.data.radar

enum class RadarSourceId {
    FMI,
    MET_NORDIC,
    SMHI,
}

enum class RadarDisplayKind {
    WMS_TILES,
    GEO_IMAGE,
}

data class RadarGeoBounds(
    val northLat: Double,
    val westLon: Double,
    val southLat: Double,
    val eastLon: Double,
) {
    /** MapLibre [ImageSource] order: top-left, top-right, bottom-right, bottom-left. */
    fun toMapLibreCoordinates(): Array<DoubleArray> =
        arrayOf(
            doubleArrayOf(westLon, northLat),
            doubleArrayOf(eastLon, northLat),
            doubleArrayOf(eastLon, southLat),
            doubleArrayOf(westLon, southLat),
        )
}

data class RadarAnimationFrame(
    val sourceId: RadarSourceId,
    val kind: RadarDisplayKind,
    val timeIso: String,
    val timeLabel: String,
    /** Minutes relative to latest observation (negative past, 0 now, positive forecast). */
    val offsetMinutesFromNow: Int = 0,
    /** WMS template with `{time}` placeholder, or full template when time embedded. */
    val wmsTileUrlTemplate: String? = null,
    /** Remote PNG URL for geo-referenced overlay (MET / SMHI). */
    val geoImageUrl: String? = null,
    val geoBounds: RadarGeoBounds? = null,
)

data class ActiveRadarOverlay(
    val sourceId: RadarSourceId,
    val kind: RadarDisplayKind,
    val sourceLabel: String,
    val timeLabel: String,
    val wmsTileUrlTemplate: String? = null,
    val geoImageUrl: String? = null,
    val geoBounds: RadarGeoBounds? = null,
    val animationFrames: List<RadarAnimationFrame> = emptyList(),
)
