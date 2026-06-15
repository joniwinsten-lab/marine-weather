package fi.veneappi.app.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.android.billingclient.api.ProductDetails
import fi.veneappi.app.BuildConfig
import fi.veneappi.app.R
import fi.veneappi.app.billing.lifetimeFormattedPrice
import fi.veneappi.app.billing.subscriptionFormattedPrice

@Composable
fun RoutePremiumPaywall(
    billingReady: Boolean,
    productsUnavailable: Boolean,
    productQueryFinished: Boolean,
    billingDiagnostic: String = "",
    inAppProduct: ProductDetails?,
    subscriptionProduct: ProductDetails?,
    showTrialOffer: Boolean,
    onStartTrial: () -> Unit,
    onBuyLifetime: () -> Unit,
    onSubscribeMonthly: () -> Unit,
    onRestorePurchases: () -> Unit,
    onBackToMap: () -> Unit,
    onRefreshProducts: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val playStoreInstalled =
        rememberPlayStoreInstalled(context.packageManager)

    LaunchedEffect(Unit) {
        onRefreshProducts()
    }

    val priceLoading = stringResource(R.string.route_premium_price_loading)
    val lifetimePrice = inAppProduct?.lifetimeFormattedPrice()
    val monthlyPrice = subscriptionProduct?.subscriptionFormattedPrice()
    val lifetimeReady = billingReady && inAppProduct != null && lifetimePrice != null
    val monthlyReady =
        billingReady &&
            subscriptionProduct != null &&
            monthlyPrice != null &&
            !subscriptionProduct.subscriptionOfferDetails.isNullOrEmpty()
    val pricesPending = !lifetimeReady || !monthlyReady
    var waitTick by remember { mutableLongStateOf(0L) }
    LaunchedEffect(pricesPending, productQueryFinished) {
        if (!pricesPending) return@LaunchedEffect
        waitTick = System.currentTimeMillis()
        kotlinx.coroutines.delay(12_000)
        waitTick = System.currentTimeMillis()
    }

    Box(modifier.fillMaxSize()) {
        Column(
            Modifier
                .widthIn(max = UiBreakpoints.PAYWALL_MAX_WIDTH_DP.dp)
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.route_premium_title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.route_premium_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            if (showTrialOffer) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                ) {
                    OutlinedButton(
                        onClick = onStartTrial,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.route_premium_trial_cta))
                    }
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = stringResource(R.string.route_premium_cd_lock),
                        modifier =
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .size(18.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Text(
                    text =
                        if (lifetimeReady && monthlyReady) {
                            stringResource(
                                R.string.route_premium_trial_terms,
                                lifetimePrice!!,
                                monthlyPrice!!,
                            )
                        } else {
                            stringResource(R.string.route_premium_trial_terms_no_prices)
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Start,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, start = 12.dp, end = 12.dp),
                )
                Spacer(Modifier.height(12.dp))
            }
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
            ) {
                Button(
                    onClick = onBuyLifetime,
                    enabled = lifetimeReady,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(
                            R.string.route_premium_buy_once,
                            lifetimePrice ?: priceLoading,
                        ),
                    )
                }
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = stringResource(R.string.route_premium_cd_lock),
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(18.dp),
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
            Spacer(Modifier.height(10.dp))
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
            ) {
                OutlinedButton(
                    onClick = onSubscribeMonthly,
                    enabled = monthlyReady,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(
                            R.string.route_premium_subscribe_monthly,
                            monthlyPrice ?: priceLoading,
                        ),
                    )
                }
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = stringResource(R.string.route_premium_cd_lock),
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(18.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                stringResource(
                    R.string.route_premium_sub_terms,
                    monthlyPrice ?: priceLoading,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Start,
                modifier = Modifier.padding(top = 8.dp, start = 12.dp, end = 12.dp),
            )

            if (pricesPending) {
                Spacer(Modifier.height(16.dp))
                RoutePremiumBillingStatus(
                    billingReady = billingReady,
                    productQueryFinished = productQueryFinished,
                    productsUnavailable = productsUnavailable,
                    playStoreInstalled = playStoreInstalled,
                    billingDiagnostic = billingDiagnostic,
                    appVersion = BuildConfig.VERSION_NAME,
                    waitTick = waitTick,
                )
            }

            Spacer(Modifier.height(12.dp))
            if (playStoreInstalled) {
                TextButton(
                    onClick = { openPlaySubscriptions(context, context.packageName) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.route_premium_manage_subscription))
                }
            }
            OutlinedButton(onClick = onRestorePurchases) {
                Text(stringResource(R.string.route_premium_restore))
            }
            Spacer(Modifier.height(20.dp))
            OutlinedButton(onClick = onBackToMap) {
                Text(stringResource(R.string.route_premium_back_map))
            }
        }
    }
}

private fun openPlaySubscriptions(
    context: Context,
    packageName: String,
) {
    val uri =
        Uri.parse(
            "https://play.google.com/store/account/subscriptions?package=$packageName",
        )
    val intent = Intent(Intent.ACTION_VIEW, uri)
    runCatching { context.startActivity(intent) }
}

@Composable
private fun RoutePremiumBillingStatus(
    billingReady: Boolean,
    productQueryFinished: Boolean,
    productsUnavailable: Boolean,
    playStoreInstalled: Boolean,
    billingDiagnostic: String,
    appVersion: String,
    waitTick: Long,
) {
    val technicalDiagnostic =
        billingDiagnostic.isNotBlank() &&
            (billingDiagnostic.startsWith("v") || billingDiagnostic.contains("lifetime="))
    val headline =
        when {
            technicalDiagnostic ->
                stringResource(R.string.route_premium_prices_unavailable)
            !playStoreInstalled ->
                stringResource(R.string.route_premium_no_play_store)
            !billingReady && !productQueryFinished ->
                stringResource(R.string.route_premium_billing_connecting)
            !productQueryFinished ->
                stringResource(R.string.route_premium_billing_querying)
            productsUnavailable ->
                stringResource(R.string.route_premium_prices_unavailable)
            else ->
                stringResource(R.string.route_premium_prices_unavailable)
        }
    val detail =
        when {
            technicalDiagnostic -> billingDiagnostic
            !playStoreInstalled -> stringResource(R.string.route_premium_no_play_store_detail)
            !productQueryFinished && waitTick > 0L ->
                stringResource(R.string.route_premium_billing_still_waiting, appVersion)
            !productQueryFinished ->
                stringResource(R.string.route_premium_billing_querying_detail, appVersion)
            billingDiagnostic.isNotBlank() -> billingDiagnostic
            else -> stringResource(R.string.route_premium_billing_fallback_detail, appVersion)
        }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            headline,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
        Text(
            detail,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun rememberPlayStoreInstalled(packageManager: PackageManager): Boolean {
    val installed =
        androidx.compose.runtime.remember(packageManager) {
            runCatching {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo("com.android.vending", 0)
                true
            }.getOrDefault(false)
        }
    return installed
}
