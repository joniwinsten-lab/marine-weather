package fi.veneappi.app.billing

import com.android.billingclient.api.ProductDetails

/** Primary buy offer for Play one-time products (purchase-option model in Billing 7+). */
fun ProductDetails.primaryOneTimeOffer(): ProductDetails.OneTimePurchaseOfferDetails? =
    oneTimePurchaseOfferDetailsList?.firstOrNull { !it.formattedPrice.isNullOrBlank() }
        ?: oneTimePurchaseOfferDetails?.takeUnless { it.formattedPrice.isNullOrBlank() }

fun ProductDetails.lifetimeFormattedPrice(): String? =
    primaryOneTimeOffer()?.formattedPrice

/** Best-effort recurring price for paywall (skips zero-price trial phases when possible). */
fun ProductDetails.subscriptionFormattedPrice(): String? {
    val offers = subscriptionOfferDetails.orEmpty()
    for (offer in offers) {
        val phases = offer.pricingPhases?.pricingPhaseList.orEmpty()
        val paid = phases.lastOrNull { it.priceAmountMicros > 0L }
        if (paid != null && !paid.formattedPrice.isNullOrBlank()) {
            return paid.formattedPrice
        }
        phases.firstOrNull { !it.formattedPrice.isNullOrBlank() }?.formattedPrice?.let { return it }
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
