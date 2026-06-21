package fi.veneappi.app.domain.ais

import fi.veneappi.app.domain.GeoMath

/** Stale / dead-reckoning helpers for AIS map display (ported from web track prototype). */
object AisVesselMotion {
    /** No AIS fix for this long → treat as stale (web: 30 min). */
    const val STALE_MS = 30 * 60 * 1000L

    /** Max dead-reckoned drift without a new fix (web: 2 min). */
    const val MAX_DRIFT_MS = 120_000L

    fun isActive(
        vessel: AisVesselDisplay,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): Boolean {
        val lastSeen = vessel.lastSeenEpochMs ?: return false
        return nowEpochMs - lastSeen < STALE_MS
    }

    fun isMoving(vessel: AisVesselDisplay): Boolean {
        val sog = vessel.sogKn ?: return false
        return sog >= AisConfig.MIN_SOG_FOR_VECTOR_KN
    }

    /**
     * Map position: last AIS fix, optionally dead-reckoned by SOG/COG until [MAX_DRIFT_MS].
     * Returns `(latitude, longitude)` or null when coordinates are missing.
     */
    fun displayPosition(
        vessel: AisVesselDisplay,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): Pair<Double, Double>? {
        if (!vessel.latitude.isFinite() || !vessel.longitude.isFinite()) return null
        if (!isActive(vessel, nowEpochMs) || !isMoving(vessel) || vessel.lastSeenEpochMs == null) {
            return vessel.latitude to vessel.longitude
        }
        val elapsedMs =
            (nowEpochMs - vessel.lastSeenEpochMs)
                .coerceAtLeast(0)
                .coerceAtMost(MAX_DRIFT_MS)
        if (elapsedMs <= 0) return vessel.latitude to vessel.longitude
        val bearing = vessel.courseBearingDeg ?: return vessel.latitude to vessel.longitude
        val sog = vessel.sogKn ?: return vessel.latitude to vessel.longitude
        val distanceNm = sog * (elapsedMs / 3_600_000.0)
        if (distanceNm <= 0.0001) return vessel.latitude to vessel.longitude
        return GeoMath.destinationPoint(vessel.latitude, vessel.longitude, bearing, distanceNm)
    }

    fun courseVectorEndAt(
        vessel: AisVesselDisplay,
        nowEpochMs: Long = System.currentTimeMillis(),
        minutes: Double = AisConfig.COURSE_VECTOR_MINUTES,
    ): Pair<Double, Double>? {
        val pos = displayPosition(vessel, nowEpochMs) ?: return null
        val sog = vessel.sogKn ?: return null
        if (sog < AisConfig.MIN_SOG_FOR_VECTOR_KN) return null
        val bearing = vessel.courseBearingDeg ?: return null
        val distanceNm = sog * minutes / 60.0
        if (distanceNm <= 0.0001) return null
        return GeoMath.destinationPoint(pos.first, pos.second, bearing, distanceNm)
    }

    fun needsLiveMapTick(
        vessels: Collection<AisVesselDisplay>,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): Boolean =
        vessels.any { vessel ->
            isActive(vessel, nowEpochMs) && isMoving(vessel) && vessel.lastSeenEpochMs != null
        }
}
