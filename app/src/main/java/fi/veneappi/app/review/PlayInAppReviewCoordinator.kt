package fi.veneappi.app.review

import android.app.Activity
import android.util.Log
import com.google.android.play.core.review.ReviewManagerFactory
import fi.veneappi.app.data.prefs.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

private const val TAG = "VeneappiReview"
private const val PROMPT_DELAY_MS = 3_000L

/**
 * Optional Play in-app review prompt after the user has seen value (successful weather loads).
 * Failures are swallowed; never blocks weather, billing, or navigation.
 *
 * Policy ([InAppReviewPolicy]): 3 app launches, 2 successful weather loads, once per install.
 * Disabled on the `.friends` applicationId suffix.
 */
class PlayInAppReviewCoordinator(
    private val applicationId: String,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val scope: CoroutineScope,
) {
    private val promptMutex = Mutex()
    private val _reviewEligible = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val reviewEligible: SharedFlow<Unit> = _reviewEligible.asSharedFlow()

    fun recordAppLaunch() {
        if (!isEnabledForThisBuild()) return
        scope.launch {
            runCatching { userPreferencesRepository.recordAppLaunch() }
        }
    }

    fun onPositiveEngagement() {
        if (!isEnabledForThisBuild()) return
        scope.launch {
            runCatching {
                userPreferencesRepository.recordPositiveEngagement()
                if (isCurrentlyEligible()) {
                    _reviewEligible.emit(Unit)
                }
            }
        }
    }

    /** Called when splash is dismissed — catches eligibility from a previous session. */
    fun onAppUiReady(activity: Activity) {
        if (!isEnabledForThisBuild()) return
        scope.launch {
            runCatching {
                if (isCurrentlyEligible()) {
                    tryPromptReview(activity)
                }
            }.onFailure { Log.d(TAG, "onAppUiReady skipped: ${it.message}") }
        }
    }

    fun requestReviewFlow(activity: Activity) {
        if (!isEnabledForThisBuild()) return
        scope.launch {
            runCatching { tryPromptReview(activity) }
                .onFailure { Log.d(TAG, "review skipped: ${it.message}") }
        }
    }

    private suspend fun isCurrentlyEligible(): Boolean {
        val state = userPreferencesRepository.inAppReviewState.first()
        return InAppReviewPolicy.isEligible(
            launchCount = state.launchCount,
            positiveEngagementCount = state.positiveEngagementCount,
            reviewFlowAlreadyRequested = state.reviewFlowRequested,
        )
    }

    private suspend fun tryPromptReview(activity: Activity) {
        promptMutex.withLock {
            if (!isCurrentlyEligible()) return
            delay(PROMPT_DELAY_MS)
            if (activity.isFinishing || activity.isDestroyed) return

            val manager = ReviewManagerFactory.create(activity)
            val reviewInfo = manager.requestReviewFlow().await()
            userPreferencesRepository.markReviewFlowRequested()
            manager.launchReviewFlow(activity, reviewInfo).await()
        }
    }

    private fun isEnabledForThisBuild(): Boolean = !applicationId.endsWith(".friends")
}
