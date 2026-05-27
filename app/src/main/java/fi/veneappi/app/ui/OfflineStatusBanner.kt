package fi.veneappi.app.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import fi.veneappi.app.R
import fi.veneappi.app.domain.ForecastStaleLevel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun OfflineStatusBanner(
    status: WeatherConnectivityStatus,
    modifier: Modifier = Modifier,
) {
    val message = bannerMessage(status) ?: return
    val containerColor =
        when {
            !status.isOnline || status.allSourcesFailed ->
                MaterialTheme.colorScheme.errorContainer
            status.staleLevel == ForecastStaleLevel.Hard ->
                MaterialTheme.colorScheme.errorContainer
            status.staleLevel == ForecastStaleLevel.Soft || status.anyFromCache ->
                MaterialTheme.colorScheme.tertiaryContainer
            else -> return
        }
    val contentColor =
        when {
            !status.isOnline || status.allSourcesFailed ->
                MaterialTheme.colorScheme.onErrorContainer
            status.staleLevel == ForecastStaleLevel.Hard ->
                MaterialTheme.colorScheme.onErrorContainer
            else -> MaterialTheme.colorScheme.onTertiaryContainer
        }
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = containerColor,
        tonalElevation = 0.dp,
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = contentColor,
        )
    }
}

@Composable
private fun bannerMessage(status: WeatherConnectivityStatus): String? {
    if (status.allSourcesFailed && !status.isOnline) {
        return stringResource(R.string.offline_banner_no_data)
    }
    if (status.allSourcesFailed) {
        return stringResource(R.string.offline_banner_load_failed)
    }
    val fetchedLabel =
        status.oldestFetchedUtc?.let { ms ->
            val fmt =
                DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
                    .withZone(ZoneId.systemDefault())
            fmt.format(Instant.ofEpochMilli(ms))
        }
    if (!status.isOnline) {
        return if (fetchedLabel != null) {
            stringResource(R.string.offline_banner_offline_cached, fetchedLabel)
        } else {
            stringResource(R.string.offline_banner_offline_no_cache)
        }
    }
    if (status.anyFromCache && fetchedLabel != null) {
        return stringResource(R.string.offline_banner_cache_fallback, fetchedLabel)
    }
    return when (status.staleLevel) {
        ForecastStaleLevel.Hard ->
            if (fetchedLabel != null) {
                stringResource(R.string.offline_banner_stale_hard, fetchedLabel)
            } else {
                stringResource(R.string.offline_banner_stale_hard_short)
            }
        ForecastStaleLevel.Soft ->
            if (fetchedLabel != null) {
                stringResource(R.string.offline_banner_stale_soft, fetchedLabel)
            } else {
                stringResource(R.string.offline_banner_stale_soft_short)
            }
        ForecastStaleLevel.Fresh -> null
    }
}
