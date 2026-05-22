package fi.veneappi.app.billing

import kotlinx.coroutines.flow.StateFlow

/** True when the user owns the one-time “route planning + weather along route” purchase. */
interface PremiumAccess {
    val isPremium: StateFlow<Boolean>
}
