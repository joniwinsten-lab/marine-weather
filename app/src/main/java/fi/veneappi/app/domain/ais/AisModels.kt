package fi.veneappi.app.domain.ais

import fi.veneappi.app.domain.GeoMath
import org.maplibre.android.geometry.LatLng

/** Visible map bounds for AIS filtering and refresh. */
data class MapViewport(
    val southLatitude: Double,
    val southLongitude: Double,
    val northLatitude: Double,
    val northLongitude: Double,
    val zoom: Double,
    val centerLatitude: Double,
) {
    val centerLongitude: Double
        get() = (southLongitude + northLongitude) / 2.0

    companion object {
        fun fromLatLngBounds(
            southWest: LatLng,
            northEast: LatLng,
            zoom: Double,
        ): MapViewport =
            MapViewport(
                southLatitude = southWest.latitude,
                southLongitude = southWest.longitude,
                northLatitude = northEast.latitude,
                northLongitude = northEast.longitude,
                zoom = zoom,
                centerLatitude = (southWest.latitude + northEast.latitude) / 2.0,
            )

        /** Fallback before MapLibre reports visible bounds (map centre from app state). */
        fun aroundCenter(
            latitude: Double,
            longitude: Double,
            zoom: Double,
            paddingDegrees: Double = 0.22,
        ): MapViewport =
            MapViewport(
                southLatitude = latitude - paddingDegrees,
                southLongitude = longitude - paddingDegrees,
                northLatitude = latitude + paddingDegrees,
                northLongitude = longitude + paddingDegrees,
                zoom = zoom,
                centerLatitude = latitude,
            )
    }
}

/** Merged AIS position + vessel metadata for map display and detail sheet. */
data class AisVesselDisplay(
    val mmsi: Int,
    val latitude: Double,
    val longitude: Double,
    val name: String?,
    val callSign: String?,
    val destination: String?,
    val imo: Int?,
    val draughtTenthsM: Int?,
    val shipTypeCode: Int?,
    val etaRaw: Int?,
    val navStatusCode: Int?,
    val sogKn: Double?,
    val cogDeg: Double?,
    val headingDeg: Int?,
    /** Last AIS fix time (epoch ms). Used for stale detection and dead reckoning. */
    val lastSeenEpochMs: Long? = null,
) {
    val displayLabel: String
        get() {
            val trimmed = name?.trim().orEmpty()
            return trimmed.ifEmpty { "MMSI $mmsi" }
        }

    val courseBearingDeg: Double?
        get() {
            if (sogKn != null && sogKn >= AisConfig.MIN_SOG_FOR_VECTOR_KN) {
                val cog = cogDeg
                if (cog != null && cog.isFinite() && cog >= 0 && cog < 360) return cog
            }
            val heading = headingDeg
            if (heading != null && heading in 0..359) return heading.toDouble()
            return null
        }

    val showsCourseVector: Boolean
        get() = sogKn != null && sogKn >= AisConfig.MIN_SOG_FOR_VECTOR_KN && courseBearingDeg != null

    fun courseVectorEnd(
        minutes: Double = AisConfig.COURSE_VECTOR_MINUTES,
    ): Pair<Double, Double>? {
        val sog = sogKn ?: return null
        if (sog < AisConfig.MIN_SOG_FOR_VECTOR_KN) return null
        val bearing = courseBearingDeg ?: return null
        val distanceNm = sog * minutes / 60.0
        if (distanceNm <= 0.0001) return null
        return GeoMath.destinationPoint(latitude, longitude, bearing, distanceNm)
    }
}

object AisViewportFilter {
    fun contains(
        latitude: Double,
        longitude: Double,
        viewport: MapViewport,
        paddingDegrees: Double = 0.08,
    ): Boolean {
        val minLat = minOf(viewport.southLatitude, viewport.northLatitude) - paddingDegrees
        val maxLat = maxOf(viewport.southLatitude, viewport.northLatitude) + paddingDegrees
        val minLon = minOf(viewport.southLongitude, viewport.northLongitude) - paddingDegrees
        val maxLon = maxOf(viewport.southLongitude, viewport.northLongitude) + paddingDegrees
        return latitude in minLat..maxLat && longitude in minLon..maxLon
    }

    fun filter(
        vessels: List<AisVesselDisplay>,
        viewport: MapViewport,
        maxCount: Int = AisConfig.MAX_VESSELS_ON_MAP,
        paddingDegrees: Double = 0.08,
    ): List<AisVesselDisplay> {
        val minLat = minOf(viewport.southLatitude, viewport.northLatitude) - paddingDegrees
        val maxLat = maxOf(viewport.southLatitude, viewport.northLatitude) + paddingDegrees
        val minLon = minOf(viewport.southLongitude, viewport.northLongitude) - paddingDegrees
        val maxLon = maxOf(viewport.southLongitude, viewport.northLongitude) + paddingDegrees

        val inBounds =
            vessels.filter { v ->
                v.latitude in minLat..maxLat && v.longitude in minLon..maxLon
            }
        if (inBounds.size <= maxCount) return inBounds

        return inBounds
            .sortedBy { v ->
                GeoMath.haversineMeters(
                    viewport.centerLatitude,
                    viewport.centerLongitude,
                    v.latitude,
                    v.longitude,
                )
            }
            .take(maxCount)
    }
}
