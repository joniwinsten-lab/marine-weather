package fi.veneappi.app.billing

import com.android.billingclient.api.UnfetchedProduct

fun unfetchedStatusLabel(code: Int): String =
    when (code) {
        UnfetchedProduct.StatusCode.PRODUCT_NOT_FOUND -> "PRODUCT_NOT_FOUND"
        UnfetchedProduct.StatusCode.NO_ELIGIBLE_OFFER -> "NO_ELIGIBLE_OFFER"
        UnfetchedProduct.StatusCode.INVALID_PRODUCT_ID_FORMAT -> "INVALID_PRODUCT_ID_FORMAT"
        UnfetchedProduct.StatusCode.UNKNOWN -> "UNKNOWN"
        else -> "code=$code"
    }
