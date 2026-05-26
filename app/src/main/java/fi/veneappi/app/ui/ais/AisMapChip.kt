package fi.veneappi.app.ui.ais

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fi.veneappi.app.R

@Composable
fun AisMapChip(
    viewModel: AisMapViewModel,
    premium: Boolean,
    onNeedPremium: () -> Unit,
    onEnabled: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isEnabled by viewModel.isEnabled.collectAsState()
    val streamMode by viewModel.streamMode.collectAsState()
    val chipOn = premium && isEnabled
    val accessibility = stringResource(R.string.ais_chip_accessibility)

    Row(
        modifier =
            modifier
                .clip(RoundedCornerShape(20.dp))
                .background(
                    if (chipOn) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                    },
                )
                .clickable {
                    if (premium) {
                        val wasEnabled = isEnabled
                        viewModel.toggle(premium = true)
                        if (!wasEnabled) {
                            onEnabled()
                        }
                    } else {
                        onNeedPremium()
                    }
                }
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .semantics { contentDescription = accessibility },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (!premium) {
            Icon(
                Icons.Outlined.Lock,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        } else when (streamMode) {
            AisStreamMode.RestOnly ->
                androidx.compose.foundation.Canvas(Modifier.size(8.dp)) {
                    drawCircle(color = androidx.compose.ui.graphics.Color(0xFF2E7D32))
                }
            AisStreamMode.Connecting ->
                androidx.compose.foundation.Canvas(Modifier.size(8.dp)) {
                    drawCircle(color = androidx.compose.ui.graphics.Color(0xFFF57C00))
                }
            AisStreamMode.Error ->
                androidx.compose.foundation.Canvas(Modifier.size(8.dp)) {
                    drawCircle(color = androidx.compose.ui.graphics.Color(0xFFC62828))
                }
            AisStreamMode.Off -> Unit
        }
        Text(
            stringResource(R.string.ais_chip),
            style = MaterialTheme.typography.labelLarge,
            color =
                if (chipOn) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
        )
    }
}
