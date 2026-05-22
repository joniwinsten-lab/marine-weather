package fi.veneappi.app.ui

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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.android.billingclient.api.ProductDetails
import fi.veneappi.app.R

@Composable
fun RoutePremiumPaywall(
    billingReady: Boolean,
    inAppProduct: ProductDetails?,
    subscriptionProduct: ProductDetails?,
    showTrialOffer: Boolean,
    onStartTrial: () -> Unit,
    onBuyLifetime: () -> Unit,
    onSubscribeMonthly: () -> Unit,
    onRestorePurchases: () -> Unit,
    onBackToMap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lifetimePrice = inAppProduct?.oneTimePurchaseOfferDetails?.formattedPrice
    val monthlyPrice =
        subscriptionProduct
            ?.subscriptionOfferDetails
            ?.firstOrNull()
            ?.pricingPhases
            ?.pricingPhaseList
            ?.firstOrNull()
            ?.formattedPrice
    val lifetimeReady = billingReady && inAppProduct != null && lifetimePrice != null
    val monthlyReady =
        billingReady &&
            subscriptionProduct != null &&
            monthlyPrice != null &&
            subscriptionProduct.subscriptionOfferDetails?.isNotEmpty() == true

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
            Spacer(Modifier.height(10.dp))
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
                        lifetimePrice ?: stringResource(R.string.route_premium_price_loading),
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
                        monthlyPrice ?: stringResource(R.string.route_premium_price_loading),
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
            stringResource(R.string.route_premium_sub_recurring_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp, start = 12.dp, end = 12.dp),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onRestorePurchases) {
            Text(stringResource(R.string.route_premium_restore))
        }
        Spacer(Modifier.height(20.dp))
        OutlinedButton(onClick = onBackToMap) {
            Text(stringResource(R.string.route_premium_back_map))
        }
        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(R.string.route_premium_trial_play_note),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        }
    }
}
