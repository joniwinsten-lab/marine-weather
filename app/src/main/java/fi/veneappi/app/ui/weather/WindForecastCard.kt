package fi.veneappi.app.ui.weather

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fi.veneappi.app.R
import fi.veneappi.app.domain.ForecastSampler
import fi.veneappi.app.domain.UnifiedForecast
import fi.veneappi.app.domain.UnifiedTimePoint
import fi.veneappi.app.domain.WindUnit
import fi.veneappi.app.domain.msToKnots
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun SourceWindForecastCard(
    modifier: Modifier = Modifier,
    title: String,
    forecast: UnifiedForecast,
    windUnit: WindUnit,
    slotLabels: List<String>,
    metaLine: String?,
    dense: Boolean = false,
    /** Route strips: wind arrow + degrees on same row as speed. Map & weather: keep false so direction sits on its own lines. */
    compactInlineWindDirection: Boolean = false,
    slotPoints: List<UnifiedTimePoint?>? = null,
) {
    val slots: List<UnifiedTimePoint?> =
        if (slotPoints != null) {
            remember(slotPoints) { slotPoints }
        } else {
            remember(forecast.points) {
                ForecastSampler.sampleAtOffsets(forecast.points)
            }
        }
    val corner = if (dense) 6.dp else 16.dp
    val vPad = if (dense) 3.dp else 12.dp
    val hPad = if (dense) 5.dp else 14.dp
    val cardModifier =
        if (dense) {
            modifier.fillMaxWidth().fillMaxHeight()
        } else {
            modifier.fillMaxWidth()
        }
    Card(
        modifier = cardModifier,
        shape = RoundedCornerShape(corner),
        elevation = CardDefaults.cardElevation(defaultElevation = if (dense) 0.dp else 2.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
    ) {
        Column(
            modifier =
                Modifier
                    .then(if (dense) Modifier.fillMaxSize() else Modifier.fillMaxWidth())
                    .padding(horizontal = hPad, vertical = vPad),
            verticalArrangement = Arrangement.spacedBy(if (dense) 3.dp else 10.dp),
        ) {
            Text(
                text = title,
                style = if (dense) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            metaLine?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (dense) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier =
                    if (dense) {
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    } else {
                        Modifier.fillMaxWidth()
                    },
                horizontalArrangement = Arrangement.spacedBy(if (dense) 1.dp else 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for (i in slotLabels.indices) {
                    val label = slotLabels[i]
                    val point = slots.getOrNull(i)
                    WindTimeSlot(
                        label = label,
                        point = point,
                        windUnit = windUnit,
                        dense = dense,
                        compactInlineWindDirection = compactInlineWindDirection,
                        modifier =
                            Modifier
                                .weight(1f)
                                .then(if (dense) Modifier.fillMaxHeight() else Modifier)
                                .padding(horizontal = if (dense) 1.dp else 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun WindTimeSlot(
    label: String,
    point: UnifiedTimePoint?,
    windUnit: WindUnit,
    dense: Boolean,
    compactInlineWindDirection: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (dense) 1.dp else 4.dp),
    ) {
        Text(
            text = label,
            style = if (dense) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
            maxLines = if (dense) 2 else 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (point == null || point.windSpeedMs == null) {
            Text(
                text = "—",
                style = if (dense) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }
        val speed =
            when (windUnit) {
                WindUnit.MetersPerSecond -> "%.1f".format(point.windSpeedMs)
                WindUnit.Knots -> "%.0f".format(point.windSpeedMs.msToKnots() ?: 0.0)
            }
        val unit =
            when (windUnit) {
                WindUnit.MetersPerSecond -> "m/s"
                WindUnit.Knots -> "kn"
            }
        val from = point.windFromDeg
        if (!dense) {
            Text(
                text = speed,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = unit,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            if (compactInlineWindDirection && from != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "$speed $unit",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                    Spacer(Modifier.width(4.dp))
                    WindDirectionGlyph(
                        fromDegrees = from,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "${from.toInt()}°",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                }
            } else {
                Text(
                    text = "$speed $unit",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
        point.windGustMs?.let { gust ->
            val gustNum =
                when (windUnit) {
                    WindUnit.MetersPerSecond -> "%.1f".format(gust)
                    WindUnit.Knots -> "%.0f".format(gust.msToKnots() ?: gust)
                }
            val gustUnit =
                when (windUnit) {
                    WindUnit.MetersPerSecond -> "m/s"
                    WindUnit.Knots -> "kn"
                }
            val gustLabel = "$gustNum $gustUnit"
            Text(
                text = stringResource(R.string.weather_gust_max, gustLabel),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
        when {
            dense && compactInlineWindDirection -> Unit
            from != null && dense -> {
                WindDirectionGlyph(
                    fromDegrees = from,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    text = cardinalFromDegrees(from),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "${from.toInt()}°",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                )
            }
            from != null -> {
                WindDirectionGlyph(
                    fromDegrees = from,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(44.dp),
                )
                Text(
                    text = cardinalFromDegrees(from),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "${from.toInt()}°",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Draws wind **from** direction: line from rim toward center (where the boat / point is).
 */
@Composable
private fun WindDirectionGlyph(
    fromDegrees: Double,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val inset = if (size.minDimension < 28.dp.toPx()) 3.dp.toPx() else 6.dp.toPx()
        val r = size.minDimension / 2f - inset
        val rad = Math.toRadians(fromDegrees - 90.0)
        val tip =
            Offset(
                c.x + (cos(rad) * r).toFloat(),
                c.y + (sin(rad) * r).toFloat(),
            )
        drawCircle(
            color = tint.copy(alpha = 0.12f),
            radius = r + 2.dp.toPx(),
            center = c,
        )
        drawLine(
            color = tint,
            start = tip,
            end = c,
            strokeWidth = if (size.minDimension < 28.dp.toPx()) 2.dp.toPx() else 4.dp.toPx(),
            cap = StrokeCap.Round,
        )
        val head = if (size.minDimension < 28.dp.toPx()) 7.dp.toPx() else 11.dp.toPx()
        val ang = atan2((c.y - tip.y).toDouble(), (c.x - tip.x).toDouble())
        val a1 = ang + PI * 3.0 / 4.0
        val a2 = ang - PI * 3.0 / 4.0
        val e1 =
            Offset(
                c.x + (cos(a1) * head * 0.55f).toFloat(),
                c.y + (sin(a1) * head * 0.55f).toFloat(),
            )
        val e2 =
            Offset(
                c.x + (cos(a2) * head * 0.55f).toFloat(),
                c.y + (sin(a2) * head * 0.55f).toFloat(),
            )
        drawLine(color = tint, start = c, end = e1, strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
        drawLine(color = tint, start = c, end = e2, strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
    }
}

private fun cardinalFromDegrees(deg: Double): String {
    val dirs =
        listOf(
            "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
            "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW",
        )
    val x = ((deg % 360.0) + 360.0) % 360.0
    val idx = ((x + 11.25) / 22.5).toInt() % 16
    return dirs[idx]
}
