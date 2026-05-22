package fi.veneappi.app.billing

import android.app.Activity
import android.app.Application
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.PendingPurchasesParams
import fi.veneappi.app.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class BillingManager(
    application: Application,
) : PremiumAccess {
    private val routePremiumInAppSku: String = BuildConfig.BILLING_ROUTE_PREMIUM_INAPP
    private val routePremiumSubSku: String = BuildConfig.BILLING_ROUTE_PREMIUM_SUB

    private fun effectiveRoutePremium(playStoreReportsOwned: Boolean): Boolean =
        BuildConfig.DEBUG_ROUTE_PREMIUM_UNLOCKED || playStoreReportsOwned

    private val purchasesListener =
        PurchasesUpdatedListener { _, _ ->
            syncPurchasesAndAcknowledge()
        }

    private val client: BillingClient =
        BillingClient.newBuilder(application)
            .setListener(purchasesListener)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder().enableOneTimeProducts().build(),
            )
            .build()

    private val ready = MutableStateFlow(false)
    val billingReady: StateFlow<Boolean> = ready.asStateFlow()

    private val routePremiumOwned = MutableStateFlow(effectiveRoutePremium(false))
    override val isPremium: StateFlow<Boolean> = routePremiumOwned.asStateFlow()

    private val inAppProduct = MutableStateFlow<ProductDetails?>(null)
    val routePremiumInAppProduct: StateFlow<ProductDetails?> = inAppProduct.asStateFlow()

    private val subscriptionProduct = MutableStateFlow<ProductDetails?>(null)
    val routePremiumSubscriptionProduct: StateFlow<ProductDetails?> = subscriptionProduct.asStateFlow()

    fun startConnection() {
        client.startConnection(
            object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    val ok = result.responseCode == BillingClient.BillingResponseCode.OK
                    ready.value = ok
                    if (ok) {
                        queryRouteProductDetails()
                        syncPurchasesAndAcknowledge()
                    }
                }

                override fun onBillingServiceDisconnected() {
                    ready.value = false
                }
            },
        )
    }

    fun endConnection() {
        runCatching { client.endConnection() }
    }

    /** Re-query Play for active in-app + subscription purchases (e.g. Restore). */
    fun syncPurchasesAndAcknowledge() {
        if (!client.isReady) return
        val inAppParams =
            QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        client.queryPurchasesAsync(inAppParams) { inAppResult, inAppPurchases ->
            val inAppOk = inAppResult.responseCode == BillingClient.BillingResponseCode.OK
            val inAppList = if (inAppOk) inAppPurchases.orEmpty() else emptyList()
            val inAppOwned = ownsRouteInApp(inAppList)
            val subParams =
                QueryPurchasesParams.newBuilder()
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build()
            client.queryPurchasesAsync(subParams) { subResult, subPurchases ->
                val subOk = subResult.responseCode == BillingClient.BillingResponseCode.OK
                val subList = if (subOk) subPurchases.orEmpty() else emptyList()
                val subOwned = ownsRouteSubscription(subList)
                acknowledgeRoutePurchases(inAppList, subList)
                routePremiumOwned.value = effectiveRoutePremium(inAppOwned || subOwned)
            }
        }
    }

    private fun ownsRouteInApp(purchases: List<Purchase>): Boolean {
        if (routePremiumInAppSku.isBlank()) return false
        for (p in purchases) {
            if (routePremiumInAppSku !in p.products) continue
            if (p.purchaseState != Purchase.PurchaseState.PURCHASED) continue
            return true
        }
        return false
    }

    private fun ownsRouteSubscription(purchases: List<Purchase>): Boolean {
        if (routePremiumSubSku.isBlank()) return false
        for (p in purchases) {
            if (routePremiumSubSku !in p.products) continue
            if (p.purchaseState != Purchase.PurchaseState.PURCHASED) continue
            return true
        }
        return false
    }

    private fun acknowledgeRoutePurchases(inApp: List<Purchase>, subs: List<Purchase>) {
        for (p in inApp + subs) {
            if (p.purchaseState != Purchase.PurchaseState.PURCHASED) continue
            if (p.isAcknowledged) continue
            val idMatch =
                (routePremiumInAppSku.isNotBlank() && routePremiumInAppSku in p.products) ||
                    (routePremiumSubSku.isNotBlank() && routePremiumSubSku in p.products)
            if (!idMatch) continue
            val ackParams =
                AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(p.purchaseToken)
                    .build()
            client.acknowledgePurchase(ackParams) { }
        }
    }
    /** One-time route premium (managed product). */
    fun launchRoutePremiumInAppPurchase(activity: Activity): Boolean {
        if (!client.isReady) return false
        val details = inAppProduct.value ?: return false
        val productParams =
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(details)
                .build()
        val flowParams =
            BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(listOf(productParams))
                .build()
        val result = client.launchBillingFlow(activity, flowParams)
        return result.responseCode == BillingClient.BillingResponseCode.OK
    }

    /** Monthly (or primary) subscription from Play product’s first offer. */
    fun launchRoutePremiumSubscriptionPurchase(activity: Activity): Boolean {
        if (!client.isReady) return false
        val details = subscriptionProduct.value ?: return false
        val offer =
            details.subscriptionOfferDetails?.firstOrNull()
                ?: return false
        val productParams =
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(details)
                .setOfferToken(offer.offerToken)
                .build()
        val flowParams =
            BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(listOf(productParams))
                .build()
        val result = client.launchBillingFlow(activity, flowParams)
        return result.responseCode == BillingClient.BillingResponseCode.OK
    }

    private fun queryRouteProductDetails() {
        val products = ArrayList<QueryProductDetailsParams.Product>(2)
        if (routePremiumInAppSku.isNotBlank()) {
            products.add(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(routePremiumInAppSku)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build(),
            )
        }
        if (routePremiumSubSku.isNotBlank()) {
            products.add(
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(routePremiumSubSku)
                    .setProductType(BillingClient.ProductType.SUBS)
                    .build(),
            )
        }
        if (products.isEmpty()) return
        val params =
            QueryProductDetailsParams.newBuilder()
                .setProductList(products)
                .build()
        client.queryProductDetailsAsync(params) { result, list ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                inAppProduct.value = null
                subscriptionProduct.value = null
                return@queryProductDetailsAsync
            }
            var inApp: ProductDetails? = null
            var sub: ProductDetails? = null
            for (d in list.orEmpty()) {
                when (d.productType) {
                    BillingClient.ProductType.INAPP -> if (d.productId == routePremiumInAppSku) inApp = d
                    BillingClient.ProductType.SUBS -> if (d.productId == routePremiumSubSku) sub = d
                }
            }
            inAppProduct.value = inApp
            subscriptionProduct.value = sub
        }
    }
}
