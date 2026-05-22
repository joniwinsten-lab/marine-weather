package fi.veneappi.app.data.radar

/**
 * Storm radar scrubber: ±3 h from now, 30 min steps (now in the middle).
 */
object StormRadarTimeline {
    const val HORIZON_MINUTES = 180
    const val STEP_MINUTES = 30

    val offsetsMinutes: List<Int> = buildList {
        var t = -HORIZON_MINUTES
        while (t <= HORIZON_MINUTES) {
            add(t)
            t += STEP_MINUTES
        }
    }

    val nowFrameIndex: Int = offsetsMinutes.indexOf(0)

    val frameCount: Int get() = offsetsMinutes.size
}
