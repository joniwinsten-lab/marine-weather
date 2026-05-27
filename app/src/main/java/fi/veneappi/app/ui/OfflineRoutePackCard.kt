package fi.veneappi.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fi.veneappi.app.R

data class OfflinePackUiState(
    val downloading: Boolean = false,
    val stepKey: String? = null,
    val current: Int = 0,
    val total: Int = 0,
    val lastSuccessWeatherSamples: Int? = null,
    val lastSuccessRouteVertices: Int? = null,
    val lastFailed: Boolean = false,
)

@Composable
fun OfflineRoutePackCard(
    enabled: Boolean,
    packState: OfflinePackUiState,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (packState.downloading && packState.total > 0) {
            LinearProgressIndicator(
                progress = { packState.current.toFloat() / packState.total.toFloat() },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = offlinePackStepLabel(packState.stepKey, packState.current, packState.total),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        when {
            packState.lastFailed ->
                Text(
                    text = stringResource(R.string.offline_pack_failed),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            packState.lastSuccessWeatherSamples != null && packState.lastSuccessRouteVertices != null ->
                Text(
                    text =
                        stringResource(
                            R.string.offline_pack_done,
                            packState.lastSuccessWeatherSamples,
                            packState.lastSuccessRouteVertices,
                        ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
        }
        OutlinedButton(
            onClick = onDownload,
            enabled = enabled && !packState.downloading,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 6.dp),
        ) {
            Text(
                if (packState.downloading) {
                    stringResource(R.string.offline_pack_downloading)
                } else {
                    stringResource(R.string.offline_pack_download)
                },
            )
        }
    }
}

@Composable
private fun offlinePackStepLabel(
    stepKey: String?,
    current: Int,
    total: Int,
): String =
    when (stepKey) {
        "tiles" -> stringResource(R.string.offline_pack_step_tiles)
        "marine" -> stringResource(R.string.offline_pack_step_marine)
        "weather" -> stringResource(R.string.offline_pack_step_weather, current, total)
        "done" -> stringResource(R.string.offline_pack_step_done)
        else -> stringResource(R.string.offline_pack_step_working)
    }
