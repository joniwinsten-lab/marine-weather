package fi.veneappi.app.data.radar

/**
 * Picks primary radar source from map centre (v2: FMI east / MET Nordic west & Sweden).
 */
object RadarRegionSelector {
  fun preferredSource(
        lat: Double,
        lon: Double,
    ): RadarSourceId =
        when {
            lon >= 20.0 && lat in 54.5..72.5 -> RadarSourceId.FMI
            lon < 19.5 && lat in 54.5..69.5 -> RadarSourceId.MET_NORDIC
            else -> RadarSourceId.FMI
        }
}
