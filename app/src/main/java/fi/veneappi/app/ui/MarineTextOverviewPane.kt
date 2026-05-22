package fi.veneappi.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fi.veneappi.app.R
import fi.veneappi.app.data.marine.MarineCountryText
import fi.veneappi.app.data.marine.MarineForecastAlertLevel
import fi.veneappi.app.data.marine.MarineTextOverview
import fi.veneappi.app.data.marine.MarineTextRepository

private val countryGridOrder = listOf("NO", "SE", "FI", "EE")

/**
 * Latest official **text** marine outlooks for four countries around the Baltic,
 * using the map centre from [VeneappiUiState]. Does not modify other tabs’ UI.
 */
@Composable
fun MarineTextOverviewPane(
    ui: VeneappiUiState,
    repository: MarineTextRepository,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    val titleNo = stringResource(R.string.marine_text_country_no)
    val titleSe = stringResource(R.string.marine_text_country_se)
    val titleFi = stringResource(R.string.marine_text_country_fi)
    val titleEe = stringResource(R.string.marine_text_country_ee)
    val titlesMap =
        remember(titleNo, titleSe, titleFi, titleEe) {
            mapOf(
                "NO" to titleNo,
                "SE" to titleSe,
                "FI" to titleFi,
                "EE" to titleEe,
            )
        }

    var loading by remember { mutableStateOf(false) }
    var overview by remember { mutableStateOf<MarineTextOverview?>(null) }
    var loadKey by remember { mutableStateOf(0) }

    LaunchedEffect(ui.latitude, ui.longitude, loadKey) {
        loading = true
        overview =
            runCatching {
                repository.loadOverview(
                    lat = ui.latitude,
                    lon = ui.longitude,
                    titlesByCountry = titlesMap,
                )
            }.getOrElse { e ->
                MarineTextOverview(
                    lastFetchedUtc = System.currentTimeMillis(),
                    metNorwaySeaLastChange = null,
                    countries = emptyList(),
                    errors = listOf(e.message ?: e.toString()),
                )
            }
        loading = false
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.marine_text_screen_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            IconButton(
                onClick = { loadKey++ },
                enabled = !loading,
            ) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.marine_text_refresh_cd),
                )
            }
        }
        Text(
            text = stringResource(R.string.marine_text_intro),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Text(
            text =
                stringResource(
                    R.string.marine_text_map_hint,
                    ui.latitude,
                    ui.longitude,
                ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        if (loading && overview == null) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator()
            }
        }

        overview?.errors?.forEach { err ->
            Text(
                text = err,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }

        val countries = overview?.countries.orEmpty()
        if (countries.isNotEmpty()) {
            val byCode = countries.associateBy { it.countryCode }
            Column(
                modifier =
                    Modifier
                        .weight(1f, fill = true)
                        .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier =
                        Modifier
                            .weight(1f, fill = true)
                            .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    for (code in countryGridOrder.take(2)) {
                        MarineCountryGridCell(
                            card = byCode[code],
                            onOpenServicePage = { card -> uriHandler.openUri(card.servicePageUrl) },
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                        )
                    }
                }
                Row(
                    modifier =
                        Modifier
                            .weight(1f, fill = true)
                            .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    for (code in countryGridOrder.drop(2)) {
                        MarineCountryGridCell(
                            card = byCode[code],
                            onOpenServicePage = { card -> uriHandler.openUri(card.servicePageUrl) },
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MarineCountryGridCell(
    card: MarineCountryText?,
    onOpenServicePage: (MarineCountryText) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (card == null) {
        return
    }
    CountryMarineCard(
        card = card,
        onOpenServicePage = { onOpenServicePage(card) },
        modifier = modifier,
    )
}

@Composable
private fun CountryMarineCard(
    card: MarineCountryText,
    onOpenServicePage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val emptyCellText = stringResource(R.string.marine_text_empty_cell)
    val bodyDisplay = card.body.trim().ifBlank { emptyCellText }
    val hasBody = card.body.isNotBlank()
    val bodyScroll = rememberScrollState()

    Card(
        modifier = modifier.fillMaxSize(),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(12.dp),
        ) {
            Text(
                text = card.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            card.publishedOrValidLabel?.let { meta ->
                Text(
                    text = meta,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (card.alertLevel != MarineForecastAlertLevel.None) {
                MarineAlertBanner(level = card.alertLevel)
            }
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 6.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            Text(
                text = bodyDisplay,
                style = MaterialTheme.typography.bodyMedium,
                color =
                    if (hasBody) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                modifier =
                    Modifier
                        .weight(1f, fill = true)
                        .fillMaxWidth()
                        .verticalScroll(bodyScroll),
            )
            TextButton(
                onClick = onOpenServicePage,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
            ) {
                Text(
                    stringResource(R.string.marine_open_web_forecast),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun MarineAlertBanner(level: MarineForecastAlertLevel) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val (containerColor, contentColor, labelRes) =
        when (level) {
            MarineForecastAlertLevel.Warning ->
                Triple(
                    MaterialTheme.colorScheme.errorContainer,
                    MaterialTheme.colorScheme.onErrorContainer,
                    R.string.marine_alert_warning,
                )
            MarineForecastAlertLevel.Notice ->
                if (isDark) {
                    Triple(Color(0xFF4A3F10), Color(0xFFFFE082), R.string.marine_alert_notice)
                } else {
                    Triple(Color(0xFFFFF3CD), Color(0xFF5D4200), R.string.marine_alert_notice)
                }
            MarineForecastAlertLevel.None -> return
        }
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
    ) {
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
        )
    }
}

private fun Color.luminance(): Float =
    0.299f * red + 0.587f * green + 0.114f * blue
