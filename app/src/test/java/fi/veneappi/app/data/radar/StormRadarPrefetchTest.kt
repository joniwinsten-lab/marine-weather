package fi.veneappi.app.data.radar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StormRadarPrefetchTest {
    @Test
    fun locationKey_roundsToHundredthsOfDegree() {
        val a = StormRadarPrefetch.locationKey(60.1453, 24.9884)
        val b = StormRadarPrefetch.locationKey(60.1499, 24.9812)
        assertEquals(a, b)
    }

    @Test
    fun isExpired_afterTtl() {
        val entry =
            StormRadarPrefetch(
                locationKey = "1,2",
                fetchedAtMs = 0L,
                frames = emptyList(),
                latestOverlay = null,
                sourceLabel = "FMI",
                lightningStrikes = emptyList(),
                lightningFetchedAtMs = 0L,
                lightningError = null,
            )
        assertFalse(entry.isExpired(StormRadarPrefetch.STALE_AFTER_MS - 1))
        assertTrue(entry.isExpired(StormRadarPrefetch.STALE_AFTER_MS + 1))
    }
}
