package fi.veneappi.app.billing

import com.android.billingclient.api.ProductDetails

/** Primary buy offer for Play one-time products (purchase-option model in Billing 7+). */
fun ProductDetails.primaryOneTimeOffer(): ProductDetails.OneTimePurchaseOfferDetails? {
    val fromList = oneTimePurchaseOfferDetailsList.orEmpty()
    if (fromList.isNotEmpty()) {
        fromList.firstOrNull { !it.formattedPrice.isNullOrBlank() }?.let { return it }
    }
    return oneTimePurchaseOfferDetails?.takeUnless { it.formattedPrice.isNullOrBlank() }
}

fun ProductDetails.lifetimeFormattedPrice(): String? =
    primaryOneTimeOffer()?.formattedPrice

/** Best-effort recurring price for paywall (prefers paid recurring phase, then any formatted phase). */
fun ProductDetails.subscriptionFormattedPrice(): String? {
    val offers = subscriptionOfferDetails.orEmpty()
    if (offers.isEmpty()) return null
    for (offer in offers) {
        val phases = offer.pricingPhases?.pricingPhaseList.orEmpty()
        val paid = phases.lastOrNull { it.priceAmountMicros > 0L }
        if (paid != null && !paid.formattedPrice.isNullOrBlank()) {
            return paid.formattedPrice
        }
    }
    for (offer in offers) {
        for (phase in offer.pricingPhases?.pricingPhaseList.orEmpty()) {
            if (!phase.formattedPrice.isNullOrBlank()) {
                return phase.formattedPrice
            }
        }
    }
    return null
}

fun ProductDetails.primarySubscriptionOffer(): ProductDetails.SubscriptionOfferDetails? {
    val offers = subscriptionOfferDetails.orEmpty()
    if (offers.isEmpty()) return null
    return offers.firstOrNull { offer ->
        offer.pricingPhases?.pricingPhaseList?.any { it.priceAmountMicros > 0L } == true
    } ?: offers.first()
}
