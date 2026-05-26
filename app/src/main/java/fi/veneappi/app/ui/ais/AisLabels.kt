package fi.veneappi.app.ui.ais

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import fi.veneappi.app.R

@Composable
fun aisShipTypeLabel(code: Int?): String? {
    if (code == null) return null
    return when (code) {
        in 20..29 -> stringResource(R.string.ais_ship_type_wing)
        30 -> stringResource(R.string.ais_ship_type_fishing)
        31, 32 -> stringResource(R.string.ais_ship_type_towing)
        33 -> stringResource(R.string.ais_ship_type_dredging)
        34 -> stringResource(R.string.ais_ship_type_diving)
        35 -> stringResource(R.string.ais_ship_type_military)
        36 -> stringResource(R.string.ais_ship_type_sailing)
        37 -> stringResource(R.string.ais_ship_type_pleasure)
        in 40..49 -> stringResource(R.string.ais_ship_type_high_speed)
        50 -> stringResource(R.string.ais_ship_type_pilot)
        51 -> stringResource(R.string.ais_ship_type_sar)
        52 -> stringResource(R.string.ais_ship_type_tug)
        53 -> stringResource(R.string.ais_ship_type_port)
        54 -> stringResource(R.string.ais_ship_type_anti_pollution)
        55 -> stringResource(R.string.ais_ship_type_law)
        58 -> stringResource(R.string.ais_ship_type_medical)
        in 60..69 -> stringResource(R.string.ais_ship_type_passenger)
        in 70..79 -> stringResource(R.string.ais_ship_type_cargo)
        in 80..89 -> stringResource(R.string.ais_ship_type_tanker)
        in 90..99 -> stringResource(R.string.ais_ship_type_other)
        else -> stringResource(R.string.ais_ship_type_code, code)
    }
}

@Composable
fun aisNavStatusLabel(code: Int?): String? {
    if (code == null) return null
    return when (code) {
        0 -> stringResource(R.string.ais_nav_underway)
        1 -> stringResource(R.string.ais_nav_anchor)
        2 -> stringResource(R.string.ais_nav_nuc)
        3 -> stringResource(R.string.ais_nav_restricted)
        4 -> stringResource(R.string.ais_nav_draught)
        5 -> stringResource(R.string.ais_nav_moored)
        6 -> stringResource(R.string.ais_nav_aground)
        7 -> stringResource(R.string.ais_nav_fishing)
        8 -> stringResource(R.string.ais_nav_sailing)
        14 -> stringResource(R.string.ais_nav_ais_sart)
        else -> stringResource(R.string.ais_nav_code, code)
    }
}
