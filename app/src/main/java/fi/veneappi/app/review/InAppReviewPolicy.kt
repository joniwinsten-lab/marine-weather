package fi.veneappi.app.review

/** When to show Google Play in-app review (no side effects). */
object InAppReviewPolicy {
    const val MIN_LAUNCH_COUNT = 3
    const val MIN_POSITIVE_ENGAGEMENTS = 2

    fun isEligible(
        launchCount: Int,
        positiveEngagementCount: Int,
        reviewFlowAlreadyRequested: Boolean,
    ): Boolean {
        if (reviewFlowAlreadyRequested) return false
        if (launchCount < MIN_LAUNCH_COUNT) return false
        if (positiveEngagementCount < MIN_POSITIVE_ENGAGEMENTS) return false
        return true
    }
}
