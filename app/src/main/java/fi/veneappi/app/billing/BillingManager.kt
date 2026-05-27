package fi.veneappi.app.billing

import android.app.Activity
import android.app.Application
import android.os.Handler
import android.os.Looper
import android.util.Log
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

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
            .enableAutoServiceReconnection()
            .build()

    private val mainHandler = Handler(Looper.getMainLooper())
    private val billingScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val productQueryInFlight = AtomicBoolean(false)
    private var productQueryRetry: Runnable? = null

    private val ready = MutableStateFlow(false)
    val billingReady: StateFlow<Boolean> = ready.asStateFlow()

    private val routePremiumOwned = MutableStateFlow(effectiveRoutePremium(false))
    override val isPremium: StateFlow<Boolean> = routePremiumOwned.asStateFlow()

    private val inAppProduct = MutableStateFlow<ProductDetails?>(null)
    val routePremiumInAppProduct: StateFlow<ProductDetails?> = inAppProduct.asStateFlow()

    private val subscriptionProduct = MutableStateFlow<ProductDetails?>(null)
    val routePremiumSubscriptionProduct: StateFlow<ProductDetails?> = subscriptionProduct.asStateFlow()

    private val productsUnavailable = MutableStateFlow(false)
    val routePremiumProductsUnavailable: StateFlow<Boolean> = productsUnavailable.asStateFlow()

    private val productQueryDiagnostic = MutableStateFlow("")
    val routePremiumProductQueryDiagnostic: StateFlow<String> = productQueryDiagnostic.asStateFlow()

    private val productQueryFinished = MutableStateFlow(false)
    val routePremiumProductQueryFinished: StateFlow<Boolean> = productQueryFinished.asStateFlow()

    private var productQueryAttempts = 0
    private var connectionRequested = false

    fun startConnection() {
        if (connectionRequested && client.isReady) {
            billingScope.launch { queryRouteProductDetails() }
            return
        }
        connectionRequested = true
        productQueryDiagnostic.value = "connecting to Play Billing…"
        client.startConnection(
            object : BillingClientStateListener {
                override fun onBillingSetupFinished(result: BillingResult) {
                    val ok = result.responseCode == BillingClient.BillingResponseCode.OK
                    ready.value = ok
                    log(
                        "setup finished code=${result.responseCode} debugMessage=${result.debugMessage}",
                    )
                    if (ok) {
                        productQueryAttempts = 0
                        billingScope.launch { queryRouteProductDetails() }
                        syncPurchasesAndAcknowledge()
                    } else {
                        finishProductQuery(
                            diagnostic =
                                "v${BuildConfig.VERSION_NAME}; setup failed: " +
                                    "code=${result.responseCode} ${result.debugMessage.orEmpty()}",
                            unavailable = true,
                        )
                    }
                }

                override fun onBillingServiceDisconnected() {
                    ready.value = false
                    finishProductQuery(
                        diagnostic = "v${BuildConfig.VERSION_NAME}; billing disconnected",
                        unavailable = true,
                    )
                    cancelProductQueryRetry()
                }
            },
        )
    }

    fun endConnection() {
        cancelProductQueryRetry()
        runCatching { client.endConnection() }
    }

    fun refreshRouteProductDetails() {
        if (!client.isReady) {
            productQueryDiagnostic.value =
                "v${BuildConfig.VERSION_NAME}; billing not ready — waiting for Google Play"
            productQueryFinished.value = false
            startConnection()
            return
        }
        cancelProductQueryRetry()
        if (productQueryInFlight.get()) {
            return
        }
        productQueryAttempts = 0
        billingScope.launch { queryRouteProductDetails() }
    }

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

    fun launchRoutePremiumInAppPurchase(activity: Activity): Boolean {
        if (!client.isReady) return false
        val details = inAppProduct.value ?: return false
        val offer = details.primaryOneTimeOffer() ?: return false
        val offerToken = offer.offerToken ?: return false
        val productParams =
            BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(details)
                .setOfferToken(offerToken)
                .build()
        val flowParams =
            BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(listOf(productParams))
                .build()
        val result = client.launchBillingFlow(activity, flowParams)
        return result.responseCode == BillingClient.BillingResponseCode.OK
    }

    fun launchRoutePremiumSubscriptionPurchase(activity: Activity): Boolean {
        if (!client.isReady) return false
        val details = subscriptionProduct.value ?: return false
        val offer = details.primarySubscriptionOffer() ?: return false
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

    private suspend fun queryRouteProductDetails() {
        if (!client.isReady) return
        if (!productQueryInFlight.compareAndSet(false, true)) return
        productQueryFinished.value = false
        productQueryDiagnostic.value = "querying Play products…"
        try {
            withTimeout(PRODUCT_QUERY_TIMEOUT_MS) {
                var inApp: ProductDetails? = inAppProduct.value
                var sub: ProductDetails? = subscriptionProduct.value
                val unfetchedParts = mutableListOf<String>()

                if (routePremiumInAppSku.isNotBlank()) {
                    val (result, details) = querySingleProduct(routePremiumInAppSku, BillingClient.ProductType.INAPP)
                    if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                        unfetchedParts.add("$routePremiumInAppSku:HTTP_${result.responseCode}")
                    } else {
                        inApp = details.productDetailsList.orEmpty().firstOrNull { it.productId == routePremiumInAppSku }
                        details.unfetchedProductList.orEmpty().forEach { item ->
                            unfetchedParts.add("${item.productId}:${unfetchedStatusLabel(item.statusCode)}")
                        }
                    }
                }

                if (routePremiumSubSku.isNotBlank()) {
                    val (result, details) = querySingleProduct(routePremiumSubSku, BillingClient.ProductType.SUBS)
                    if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                        unfetchedParts.add("$routePremiumSubSku:HTTP_${result.responseCode}")
                    } else {
                        sub = details.productDetailsList.orEmpty().firstOrNull { it.productId == routePremiumSubSku }
                        details.unfetchedProductList.orEmpty().forEach { item ->
                            unfetchedParts.add("${item.productId}:${unfetchedStatusLabel(item.statusCode)}")
                        }
                    }
                }

                inAppProduct.value = inApp
                subscriptionProduct.value = sub
                val hasLifetime = inApp?.lifetimeFormattedPrice() != null
                val hasSub = sub?.subscriptionFormattedPrice() != null
                productsUnavailable.value = !hasLifetime && !hasSub

                val lifetimeOfferCount = inApp?.oneTimePurchaseOfferDetailsList?.size ?: 0
                val subOfferCount = sub?.subscriptionOfferDetails?.size ?: 0
                val diag =
                    buildString {
                        append("v${BuildConfig.VERSION_NAME}; billingOk; ")
                        append("lifetime=${routePremiumInAppSku} ")
                        append(if (inApp == null) "missing" else "offers=$lifetimeOfferCount price=$hasLifetime; ")
                        append("monthly=${routePremiumSubSku} ")
                        append(if (sub == null) "missing" else "offers=$subOfferCount price=$hasSub")
                        if (unfetchedParts.isNotEmpty()) {
                            append("; unfetched=")
                            append(unfetchedParts.joinToString())
                        }
                    }
                finishProductQuery(diagnostic = diag, unavailable = !hasLifetime && !hasSub)
                log("queryProductDetails ok attempt=$productQueryAttempts $diag")
                if (!hasLifetime && !hasSub) {
                    logWarn("no prices from Play — install from closed test, same Google account, products Active")
                    scheduleProductRetryIfNeeded(BillingClient.BillingResponseCode.OK)
                }
            }
        } catch (t: kotlinx.coroutines.TimeoutCancellationException) {
            finishProductQuery(
                diagnostic =
                    "v${BuildConfig.VERSION_NAME}; query timeout (${PRODUCT_QUERY_TIMEOUT_MS / 1000}s). " +
                        "Huawei/tablet: use Play Store closed-test install + Google account. " +
                        "Try: clear Play Store cache, reopen app.",
                unavailable = true,
            )
            logWarn(productQueryDiagnostic.value)
        } catch (t: Throwable) {
            finishProductQuery(
                diagnostic = "v${BuildConfig.VERSION_NAME}; query crashed: ${t.message}",
                unavailable = true,
            )
            logWarn(productQueryDiagnostic.value)
        } finally {
            productQueryInFlight.set(false)
        }
    }

    private suspend fun querySingleProduct(
        productId: String,
        productType: String,
    ): Pair<BillingResult, com.android.billingclient.api.QueryProductDetailsResult> =
        withContext(Dispatchers.Main) {
            val product =
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(productId)
                    .setProductType(productType)
                    .build()
            val params =
                QueryProductDetailsParams.newBuilder()
                    .setProductList(listOf(product))
                    .build()
            suspendCancellableCoroutine { cont ->
                client.queryProductDetailsAsync(params) { result, productDetailsResult ->
                    if (cont.isActive) {
                        cont.resume(result to productDetailsResult)
                    }
                }
            }
        }

    private fun finishProductQuery(
        diagnostic: String,
        unavailable: Boolean,
    ) {
        productQueryAttempts++
        productQueryDiagnostic.value = diagnostic
        productQueryFinished.value = true
        productsUnavailable.value = unavailable
    }

    private fun scheduleProductRetryIfNeeded(responseCode: Int) {
        if (productQueryAttempts >= MAX_PRODUCT_QUERY_ATTEMPTS) return
        if (responseCode == BillingClient.BillingResponseCode.DEVELOPER_ERROR) return
        cancelProductQueryRetry()
        val delayMs = PRODUCT_QUERY_RETRY_MS * productQueryAttempts
        val runnable =
            Runnable {
                productQueryRetry = null
                if (client.isReady) {
                    billingScope.launch { queryRouteProductDetails() }
                }
            }
        productQueryRetry = runnable
        mainHandler.postDelayed(runnable, delayMs)
    }

    private fun cancelProductQueryRetry() {
        productQueryRetry?.let { mainHandler.removeCallbacks(it) }
        productQueryRetry = null
    }

    private fun log(message: String) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, message)
        }
    }

    private fun logWarn(message: String) {
        Log.w(TAG, message)
    }

    private companion object {
        const val TAG = "VeneappiBilling"
        const val MAX_PRODUCT_QUERY_ATTEMPTS = 3
        const val PRODUCT_QUERY_RETRY_MS = 3_000L
        const val PRODUCT_QUERY_TIMEOUT_MS = 20_000L
    }
}
