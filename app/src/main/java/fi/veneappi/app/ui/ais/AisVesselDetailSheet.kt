package fi.veneappi.app.ui.ais

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fi.veneappi.app.R
import fi.veneappi.app.domain.ais.AisConfig
import fi.veneappi.app.domain.ais.AisFormatting
import fi.veneappi.app.domain.ais.AisVesselDisplay
import fi.veneappi.app.domain.ais.AisVesselMotion

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AisVesselDetailSheet(
    vessel: AisVesselDisplay,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(vessel.displayLabel, style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.ais_detail_title),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            DetailSection(stringResource(R.string.ais_detail_motion)) {
                DetailRow(stringResource(R.string.ais_detail_speed), AisFormatting.formatSpeedKn(vessel.sogKn))
                DetailRow(stringResource(R.string.ais_detail_cog), AisFormatting.formatBearing(vessel.cogDeg))
                DetailRow(stringResource(R.string.ais_detail_heading), AisFormatting.formatHeading(vessel.headingDeg))
                DetailRow(stringResource(R.string.ais_detail_last_seen), lastSeenLabel(vessel.lastSeenEpochMs))
                if (!AisVesselMotion.isActive(vessel)) {
                    Text(
                        stringResource(R.string.ais_detail_stale_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (vessel.showsCourseVector) {
                    Text(
                        stringResource(
                            R.string.ais_detail_vector_hint,
                            AisConfig.COURSE_VECTOR_MINUTES.toInt(),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            DetailSection(stringResource(R.string.ais_detail_identity)) {
                DetailRow("MMSI", vessel.mmsi.toString())
                vessel.imo?.let { DetailRow("IMO", it.toString()) }
                vessel.callSign?.let { DetailRow(stringResource(R.string.ais_detail_callsign), it) }
                aisShipTypeLabel(vessel.shipTypeCode)?.let {
                    DetailRow(stringResource(R.string.ais_detail_type), it)
                }
                aisNavStatusLabel(vessel.navStatusCode)?.let {
                    DetailRow(stringResource(R.string.ais_detail_status), it)
                }
            }
            val destination = AisFormatting.formatDestination(vessel.destination)
            val draught = AisFormatting.formatDraughtMeters(vessel.draughtTenthsM)
            val eta = AisFormatting.formatEta(vessel.etaRaw)
            if (destination != null || draught != null || eta != null) {
                DetailSection(stringResource(R.string.ais_detail_voyage)) {
                    destination?.let {
                        DetailRow(stringResource(R.string.ais_detail_destination), it)
                    }
                    draught?.let { DetailRow(stringResource(R.string.ais_detail_draught), it) }
                    eta?.let { DetailRow(stringResource(R.string.ais_detail_eta), it) }
                }
            }
        }
    }
}

@Composable
private fun DetailSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        content()
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
) {
    Text(
        "$label: $value",
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun lastSeenLabel(lastSeenEpochMs: Long?): String {
    if (lastSeenEpochMs == null) return stringResource(R.string.ais_last_seen_none)
    val delta = System.currentTimeMillis() - lastSeenEpochMs
    return when {
        delta < 60_000L -> stringResource(R.string.ais_last_seen_just_now)
        delta < 3_600_000L ->
            stringResource(R.string.ais_last_seen_min, (delta / 60_000L).toInt())
        delta < 86_400_000L ->
            stringResource(R.string.ais_last_seen_hours, (delta / 3_600_000L).toInt())
        else ->
            stringResource(R.string.ais_last_seen_days, (delta / 86_400_000L).toInt())
    }
}
