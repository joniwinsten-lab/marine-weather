package fi.veneappi.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fi.veneappi.app.R
import kotlinx.coroutines.delay

/** Fade-in for icon + title. */
private const val ContentFadeMs = 2000

/** Time fully visible before main UI. */
private const val ContentHoldAfterFadeMs = 2800

/**
 * Maritime gradient splash with app icon and title.
 * System splash shows gradient only; icon appears here after a clean handoff.
 */
@Composable
fun SplashBranded(
    onFinished: () -> Unit,
    onComposeReady: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentAlpha = remember { Animatable(0f) }
    val iconPainter = painterResource(R.drawable.ic_launcher_foreground_layer)

    LaunchedEffect(Unit) {
        // Paint gradient first; then drop system splash; then fade icon+title in.
        withFrameNanos { }
        onComposeReady()
        withFrameNanos { }
        contentAlpha.animateTo(
            targetValue = 1f,
            animationSpec =
                tween(
                    durationMillis = ContentFadeMs,
                    easing = FastOutSlowInEasing,
                ),
        )
        delay(ContentHoldAfterFadeMs.toLong())
        onFinished()
    }

    val top = colorResource(R.color.splash_background)
    val mid = colorResource(R.color.splash_gradient_mid)
    val bottom = colorResource(R.color.splash_gradient_bottom)
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(top, mid, bottom),
                    ),
                )
                .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        if (contentAlpha.value > 0f) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier =
                    Modifier.graphicsLayer {
                        alpha = contentAlpha.value
                    },
            ) {
                Image(
                    painter = iconPainter,
                    contentDescription = stringResource(R.string.app_name),
                    modifier = Modifier.size(112.dp),
                    contentScale = ContentScale.Fit,
                )
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = stringResource(R.string.app_name),
                    style =
                        MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFE8F4FC),
                        ),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
