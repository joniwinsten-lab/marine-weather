package fi.veneappi.app.review

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class InAppReviewPolicyTest {
    @Test
    fun notEligibleBeforeThresholds() {
        assertThat(
            InAppReviewPolicy.isEligible(
                launchCount = 2,
                positiveEngagementCount = 2,
                reviewFlowAlreadyRequested = false,
            ),
        ).isFalse()
        assertThat(
            InAppReviewPolicy.isEligible(
                launchCount = 3,
                positiveEngagementCount = 1,
                reviewFlowAlreadyRequested = false,
            ),
        ).isFalse()
    }

    @Test
    fun eligibleWhenThresholdsMet() {
        assertThat(
            InAppReviewPolicy.isEligible(
                launchCount = 3,
                positiveEngagementCount = 2,
                reviewFlowAlreadyRequested = false,
            ),
        ).isTrue()
    }

    @Test
    fun notEligibleAfterAlreadyRequested() {
        assertThat(
            InAppReviewPolicy.isEligible(
                launchCount = 10,
                positiveEngagementCount = 10,
                reviewFlowAlreadyRequested = true,
            ),
        ).isFalse()
    }
}
