package fi.veneappi.app.data.radar

import fi.veneappi.app.data.lightning.LightningStrike

/** Cached storm radar timeline + lightning for instant tab open. */
data class StormRadarPrefetch(
    val locationKey: String,
    val fetchedAtMs: Long,
    val frames: List<RadarAnimationFrame>,
    val latestOverlay: ActiveRadarOverlay?,
    val sourceLabel: String,
    val lightningStrikes: List<LightningStrike>,
    val lightningFetchedAtMs: Long,
    val lightningError: String?,
) {
    fun isExpired(nowMs: Long = System.currentTimeMillis()): Boolean =
        nowMs - fetchedAtMs >= STALE_AFTER_MS

    /** False when a network failure produced an empty shell that should not block retries. */
    fun hasWarmContent(): Boolean =
        frames.isNotEmpty() || latestOverlay != null || lightningStrikes.isNotEmpty()

    companion object {
        const val STALE_AFTER_MS = 10 * 60 * 1000L

        fun locationKey(
            lat: Double,
            lon: Double,
        ): String {
            val latKey = (lat * 100).toInt()
            val lonKey = (lon * 100).toInt()
            return "$latKey,$lonKey"
        }
    }
}
