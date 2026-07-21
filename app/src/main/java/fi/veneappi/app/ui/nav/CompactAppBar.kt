package fi.veneappi.app.ui.nav

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.DirectionsBoat
import androidx.compose.material.icons.outlined.Thunderstorm
import androidx.compose.material.icons.outlined.Waves
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fi.veneappi.app.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompactAppBar(
    destination: MainDest,
    onOpenMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TopAppBar(
        modifier = modifier,
        navigationIcon = {
            IconButton(onClick = onOpenMenu) {
                Icon(
                    Icons.Default.Menu,
                    contentDescription = stringResource(R.string.nav_menu),
                )
            }
        },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 4.dp),
            ) {
                Icon(
                    mainDestIcon(destination),
                    contentDescription = null,
                    modifier = Modifier.padding(end = 10.dp),
                )
                Text(
                    mainDestTitle(destination),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
    )
}

@Composable
fun mainDestTitle(destination: MainDest): String =
    when (destination) {
        MainDest.COMPARE -> stringResource(R.string.tab_compare)
        MainDest.WEATHER -> stringResource(R.string.tab_weather)
        MainDest.ROUTE -> stringResource(R.string.tab_route)
        MainDest.TRACK -> stringResource(R.string.tab_track)
        MainDest.EXTENDED_WIND -> stringResource(R.string.tab_extended_wind)
        MainDest.MARINE_TEXT -> stringResource(R.string.tab_marine_text)
        MainDest.STORM_RADAR -> stringResource(R.string.tab_storm_radar)
    }

fun mainDestIcon(destination: MainDest): ImageVector =
    when (destination) {
        MainDest.COMPARE -> Icons.Default.Map
        MainDest.WEATHER -> Icons.Outlined.Cloud
        MainDest.ROUTE -> Icons.Default.Navigation
        MainDest.TRACK -> Icons.Outlined.DirectionsBoat
        MainDest.EXTENDED_WIND -> Icons.Outlined.CalendarMonth
        MainDest.MARINE_TEXT -> Icons.Outlined.Waves
        MainDest.STORM_RADAR -> Icons.Outlined.Thunderstorm
    }

fun mainDestRequiresPremium(destination: MainDest): Boolean =
    when (destination) {
        MainDest.ROUTE,
        MainDest.TRACK,
        MainDest.EXTENDED_WIND,
        -> true
        else -> false
    }

val mainDestOrder: List<MainDest> =
    listOf(
        MainDest.COMPARE,
        MainDest.WEATHER,
        MainDest.ROUTE,
        MainDest.TRACK,
        MainDest.EXTENDED_WIND,
        MainDest.MARINE_TEXT,
        MainDest.STORM_RADAR,
    )
