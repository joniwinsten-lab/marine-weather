package fi.veneappi.app.ui.route

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fi.veneappi.app.R
import fi.veneappi.app.ui.VeneappiUiState

@Composable
fun RouteMapHintBanner(
    ui: VeneappiUiState,
    modifier: Modifier = Modifier,
) {
    val textRes =
        remember(
            ui.routeStart,
            ui.routeEnd,
            ui.routeComputingFairway,
            ui.routeFairwayUnavailable,
            ui.routeGeometry,
        ) {
            when {
                ui.routeComputingFairway -> R.string.route_computing_fairway
                ui.routeStart == null -> R.string.route_hint_set_start
                ui.routeEnd == null -> R.string.route_hint_set_end
                ui.routeFairwayUnavailable -> R.string.route_fairway_fallback
                ui.routeGeometry.size >= 2 -> R.string.route_fairway_ok
                else -> R.string.route_hint_set_end
            }
        }
    Surface(
        modifier = modifier.padding(top = 8.dp, start = 48.dp, end = 48.dp),
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.94f),
        shadowElevation = 4.dp,
        tonalElevation = 2.dp,
    ) {
        Text(
            stringResource(textRes),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            style =
                MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
