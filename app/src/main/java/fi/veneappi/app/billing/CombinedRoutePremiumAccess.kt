package fi.veneappi.app.billing

import fi.veneappi.app.data.prefs.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Route premium = Play one-time purchase (or debug flag) **or** active local trial window.
 */
class CombinedRoutePremiumAccess(
    private val billingManager: BillingManager,
    userPreferencesRepository: UserPreferencesRepository,
    appScope: CoroutineScope,
) : PremiumAccess {
    private val gate = MutableStateFlow(billingManager.isPremium.value)
    override val isPremium: StateFlow<Boolean> = gate.asStateFlow()

    init {
        appScope.launch {
            combine(
                billingManager.isPremium,
                userPreferencesRepository.routeTrialActive,
            ) { fromBilling, trialActive ->
                fromBilling || trialActive
            }.collect { gate.value = it }
        }
    }
}
