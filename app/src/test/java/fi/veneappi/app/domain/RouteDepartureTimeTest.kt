package fi.veneappi.app.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RouteDepartureTimeTest {
    @Test
    fun effectiveDepartureMillis_usesNowWhenScheduled() {
        val now = 1_700_000_000_000L
        assertThat(
            RouteDepartureTime.effectiveDepartureMillis(
                isNow = true,
                scheduledMillis = now + 3_600_000L,
                nowMillis = now,
            ),
        ).isEqualTo(now)
    }

    @Test
    fun clampScheduledMillis_neverBeforeMinimum() {
        val now = RouteDepartureTime.snapToQuarterHour(1_700_000_000_000L)
        val min = RouteDepartureTime.minimumSelectableMillis(now)
        assertThat(RouteDepartureTime.clampScheduledMillis(now - 86_400_000L, now)).isAtLeast(min)
    }
}
