package fi.veneappi.app.data.ais

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AisSubscriptionManagerTest {
    @Test
    fun subscriptionDelta_computesAddAndRemove() {
        val (toRemove, toAdd) =
            AisSubscriptionManager.subscriptionDelta(
                subscribed = setOf(1, 2, 3),
                wanted = setOf(2, 3, 4),
            )
        assertThat(toRemove).containsExactly(1)
        assertThat(toAdd).containsExactly(4)
    }

    @Test
    fun subscriptionDelta_noChangesWhenEqual() {
        val (toRemove, toAdd) =
            AisSubscriptionManager.subscriptionDelta(
                subscribed = setOf(10, 20),
                wanted = setOf(10, 20),
            )
        assertThat(toRemove).isEmpty()
        assertThat(toAdd).isEmpty()
    }
}
