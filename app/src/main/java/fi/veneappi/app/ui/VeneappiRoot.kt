package fi.veneappi.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Thunderstorm
import androidx.compose.material.icons.outlined.Waves
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import fi.veneappi.app.R
import fi.veneappi.app.MainActivity
import fi.veneappi.app.VeneappiApplication
import fi.veneappi.app.domain.GeoMath
import fi.veneappi.app.domain.RouteSourceWeatherSlots
import fi.veneappi.app.domain.SourceId
import fi.veneappi.app.domain.UnifiedForecast
import fi.veneappi.app.domain.WeatherSources
import fi.veneappi.app.domain.WindUnit
import fi.veneappi.app.export.RouteGpx
import fi.veneappi.app.export.RoutePlanPdfInput
import fi.veneappi.app.export.RoutePlanPdfSourceSection
import fi.veneappi.app.export.RoutePlanPdfWriter
import fi.veneappi.app.export.exportFileUri
import fi.veneappi.app.export.formatRouteSlotForPdf
import fi.veneappi.app.export.routeExportCacheDir
import fi.veneappi.app.export.shareStream
import fi.veneappi.app.ui.ais.AisMapViewModel
import fi.veneappi.app.ui.ais.MapWithAisChrome
import fi.veneappi.app.ui.map.MapPane
import fi.veneappi.app.ui.theme.VeneappiTheme
import fi.veneappi.app.ui.weather.SourceWindForecastCard
import androidx.core.content.ContextCompat
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private enum class MainDest {
    COMPARE,
    ROUTE,
    EXTENDED_WIND,
    MARINE_TEXT,
    STORM_RADAR,
}

/** NavigationRailItem label slots can get unbounded max width; cap width so the rail does not steal the whole Row. */
private val NavigationRailItemLabelMaxWidth = 88.dp

@Composable
fun VeneappiApp() {
    VeneappiTheme {
        val context = LocalContext.current
        val activity = context as MainActivity
        val app = context.applicationContext as VeneappiApplication
    val vm: MainViewModel = viewModel(factory = MainViewModel.Factory(app.appContainer))
    val stormVm: StormMapViewModel = viewModel(factory = StormMapViewModel.Factory(app.appContainer))
    var showSplashText by remember { mutableStateOf(true) }

        val applyLastKnown: () -> Unit = {
            readLastKnownLatLon(activity)?.let { (lat, lon) -> vm.setMapLocation(lat, lon) }
        }
        val permissionLauncher =
            rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions(),
            ) { granted ->
                val ok =
                    granted[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                        granted[Manifest.permission.ACCESS_COARSE_LOCATION] == true
                if (ok) applyLastKnown()
            }

        LaunchedEffect(vm) {
            val fineGranted =
                ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
            val coarseGranted =
                ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
            if (fineGranted || coarseGranted) {
                applyLastKnown()
            } else {
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    ),
                )
            }
        }

        Box(Modifier.fillMaxSize()) {
            VeneappiRoot(vm = vm, stormVm = stormVm)
            if (showSplashText) {
                SplashBranded(
                    onComposeReady = { activity.dismissAndroidSplashScreen() },
                    onFinished = { showSplashText = false },
                )
            }
        }
    }
}

@Composable
fun VeneappiRoot(
    vm: MainViewModel,
    stormVm: StormMapViewModel,
) {
    val ui by vm.ui.collectAsState(initial = VeneappiUiState())
    val weatherConnectivity by vm.weatherConnectivityStatus.collectAsState()
    val offlinePackUi by vm.offlinePackUi.collectAsState()
    val stormUi by stormVm.stormUi.collectAsState()
    val windUnit by vm.windUnit.collectAsState(initial = WindUnit.MetersPerSecond)
    val context = LocalContext.current
    val activity = context as ComponentActivity
    val app = context.applicationContext as VeneappiApplication
    val isRoutePremium by app.appContainer.premiumAccess.isPremium.collectAsState()
    val billingReady by app.appContainer.billingManager.billingReady.collectAsState(initial = false)
    val routePremiumProductsUnavailable by
        app.appContainer.billingManager.routePremiumProductsUnavailable.collectAsState(initial = false)
    val routePremiumBillingDiagnostic by
        app.appContainer.billingManager.routePremiumProductQueryDiagnostic.collectAsState(initial = "")
    val routePremiumProductQueryFinished by
        app.appContainer.billingManager.routePremiumProductQueryFinished.collectAsState(initial = false)
    val routePremiumInApp by app.appContainer.billingManager.routePremiumInAppProduct.collectAsState()
    val routePremiumSub by app.appContainer.billingManager.routePremiumSubscriptionProduct.collectAsState()
    val routeTrialWasStarted by
        app.appContainer.userPreferencesRepository.routeTrialWasStarted.collectAsState(initial = false)
    val scope = rememberCoroutineScope()
    val aisViewModel: AisMapViewModel =
        viewModel(factory = AisMapViewModel.factory(app.appContainer.digitrafficAisRepository))
    var destination by remember { mutableStateOf(MainDest.COMPARE) }
    var showAttribution by remember { mutableStateOf(false) }
    var traficomPlanningChart by remember { mutableStateOf(true) }
    val configuration = LocalConfiguration.current
    val useNavigationRail =
        configuration.screenWidthDp >= UiBreakpoints.NAVIGATION_RAIL_MIN_WIDTH_DP &&
            configuration.screenHeightDp >= UiBreakpoints.NAVIGATION_RAIL_MIN_HEIGHT_DP
    val railCompactLabels =
        configuration.screenHeightDp < UiBreakpoints.NAVIGATION_RAIL_COMPACT_MAX_HEIGHT_DP

    LaunchedEffect(ui.latitude, ui.longitude) {
        app.appContainer.applicationScope.launch {
            runCatching {
                app.appContainer.mapTileWarmup.warm(ui.latitude, ui.longitude)
            }
        }
        app.appContainer.stormRadarPrefetcher.schedule(
            scope = app.appContainer.applicationScope,
            lat = ui.latitude,
            lon = ui.longitude,
        )
    }

    LaunchedEffect(destination, isRoutePremium) {
        if (destination == MainDest.EXTENDED_WIND && isRoutePremium) {
            vm.refreshWeather()
        }
        if (destination == MainDest.STORM_RADAR) {
            stormVm.refreshRadar(ui.latitude, ui.longitude)
        }
    }
    if (showAttribution) {
        AttributionDialog(onDismiss = { showAttribution = false })
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        ) {
            OfflineStatusBanner(status = weatherConnectivity)
            Row(
                modifier = Modifier.fillMaxSize(),
            ) {
            if (useNavigationRail) {
                ScrollableDestinationRail(
                    destination = destination,
                    isRoutePremium = isRoutePremium,
                    compactLabels = railCompactLabels,
                    onCompare = {
                        destination = MainDest.COMPARE
                        vm.setRoutePickMode(RoutePickMode.None)
                    },
                    onRoute = { destination = MainDest.ROUTE },
                    onWind = { destination = MainDest.EXTENDED_WIND },
                    onMarine = { destination = MainDest.MARINE_TEXT },
                    onStorm = { destination = MainDest.STORM_RADAR },
                )
            }

            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(),
            ) {
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .fillMaxHeight(),
                ) {
                    when (destination) {
                        MainDest.COMPARE -> {
                            ComparePane(
                                ui = ui,
                                windUnit = windUnit,
                                onRefresh = vm::refreshWeather,
                                onLongPressMap = vm::setMapLocation,
                                onMyLocation = { vm.recenterToDeviceLocation(context) },
                                onMapTapRoute =
                                    if (isRoutePremium) {
                                        { lat, lon -> vm.onMapTapForRoute(lat, lon) }
                                    } else {
                                        { _, _ -> }
                                    },
                                routePickMode =
                                    if (isRoutePremium) {
                                        ui.routePickMode
                                    } else {
                                        RoutePickMode.None
                                    },
                                onWindUnit = vm::setWindUnit,
                                traficomPlanningChart = traficomPlanningChart,
                                onTraficomPlanningChartChange = { traficomPlanningChart = it },
                                aisViewModel = aisViewModel,
                                isPremium = isRoutePremium,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        MainDest.ROUTE ->
                            if (isRoutePremium) {
                                RoutePane(
                                    ui = ui,
                                    windUnit = windUnit,
                                    onWindUnit = vm::setWindUnit,
                                    onClear = vm::clearRoute,
                                    onSpeedChange = vm::setBoatSpeedKn,
                                    onLongPressRoute = vm::onMapLongPressForRoute,
                                    onMyLocation = { vm.recenterToDeviceLocation(context) },
                                    traficomPlanningChart = traficomPlanningChart,
                                    onTraficomPlanningChartChange = { traficomPlanningChart = it },
                                    onRefreshWeather = vm::refreshWeather,
                                    aisViewModel = aisViewModel,
                                    isPremium = true,
                                    offlinePackUi = offlinePackUi,
                                    onDownloadOfflinePack = {
                                        vm.downloadOfflinePackForRoute(
                                            mapOf(
                                                "NO" to context.getString(R.string.marine_text_country_no),
                                                "SE" to context.getString(R.string.marine_text_country_se),
                                                "FI" to context.getString(R.string.marine_text_country_fi),
                                                "EE" to context.getString(R.string.marine_text_country_ee),
                                            ),
                                        )
                                    },
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else {
                                RoutePremiumPaywall(
                                    billingReady = billingReady,
                                    productsUnavailable = routePremiumProductsUnavailable,
                                    productQueryFinished = routePremiumProductQueryFinished,
                                    billingDiagnostic = routePremiumBillingDiagnostic,
                                    inAppProduct = routePremiumInApp,
                                    subscriptionProduct = routePremiumSub,
                                    showTrialOffer = !routeTrialWasStarted,
                                    onStartTrial = {
                                        scope.launch {
                                            val ok =
                                                app.appContainer.userPreferencesRepository.startRouteTrialIfEligible()
                                            if (!ok) {
                                                Toast.makeText(
                                                    context,
                                                    context.getString(R.string.route_premium_trial_already_used),
                                                    Toast.LENGTH_SHORT,
                                                ).show()
                                            }
                                        }
                                    },
                                    onBuyLifetime = {
                                        val started =
                                            app.appContainer.billingManager.launchRoutePremiumInAppPurchase(activity)
                                        if (!started) {
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.route_premium_purchase_unavailable),
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        }
                                    },
                                    onSubscribeMonthly = {
                                        val started =
                                            app.appContainer.billingManager.launchRoutePremiumSubscriptionPurchase(
                                                activity,
                                            )
                                        if (!started) {
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.route_premium_purchase_unavailable),
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        }
                                    },
                                    onRestorePurchases = {
                                        app.appContainer.billingManager.syncPurchasesAndAcknowledge()
                                        app.appContainer.billingManager.refreshRouteProductDetails()
                                    },
                                    onBackToMap = {
                                        destination = MainDest.COMPARE
                                        vm.setRoutePickMode(RoutePickMode.None)
                                    },
                                    onRefreshProducts = {
                                        app.appContainer.billingManager.refreshRouteProductDetails()
                                    },
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        MainDest.EXTENDED_WIND ->
                            if (isRoutePremium) {
                                ExtendedWindOutlookPane(
                                    ui = ui,
                                    windUnit = windUnit,
                                    onWindUnit = vm::setWindUnit,
                                    onRefresh = vm::refreshWeather,
                                    onLongPressMap = vm::setMapLocation,
                                    onMyLocation = { vm.recenterToDeviceLocation(context) },
                                    traficomPlanningChart = traficomPlanningChart,
                                    onTraficomPlanningChartChange = { traficomPlanningChart = it },
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else {
                                RoutePremiumPaywall(
                                    billingReady = billingReady,
                                    productsUnavailable = routePremiumProductsUnavailable,
                                    productQueryFinished = routePremiumProductQueryFinished,
                                    billingDiagnostic = routePremiumBillingDiagnostic,
                                    inAppProduct = routePremiumInApp,
                                    subscriptionProduct = routePremiumSub,
                                    showTrialOffer = !routeTrialWasStarted,
                                    onStartTrial = {
                                        scope.launch {
                                            val ok =
                                                app.appContainer.userPreferencesRepository.startRouteTrialIfEligible()
                                            if (!ok) {
                                                Toast.makeText(
                                                    context,
                                                    context.getString(R.string.route_premium_trial_already_used),
                                                    Toast.LENGTH_SHORT,
                                                ).show()
                                            }
                                        }
                                    },
                                    onBuyLifetime = {
                                        val started =
                                            app.appContainer.billingManager.launchRoutePremiumInAppPurchase(activity)
                                        if (!started) {
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.route_premium_purchase_unavailable),
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        }
                                    },
                                    onSubscribeMonthly = {
                                        val started =
                                            app.appContainer.billingManager.launchRoutePremiumSubscriptionPurchase(
                                                activity,
                                            )
                                        if (!started) {
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.route_premium_purchase_unavailable),
                                                Toast.LENGTH_SHORT,
                                            ).show()
                                        }
                                    },
                                    onRestorePurchases = {
                                        app.appContainer.billingManager.syncPurchasesAndAcknowledge()
                                        app.appContainer.billingManager.refreshRouteProductDetails()
                                    },
                                    onBackToMap = {
                                        destination = MainDest.COMPARE
                                        vm.setRoutePickMode(RoutePickMode.None)
                                    },
                                    onRefreshProducts = {
                                        app.appContainer.billingManager.refreshRouteProductDetails()
                                    },
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        MainDest.MARINE_TEXT ->
                            MarineTextOverviewPane(
                                ui = ui,
                                repository = app.appContainer.marineTextRepository,
                                modifier = Modifier.fillMaxSize(),
                            )
                        MainDest.STORM_RADAR ->
                            StormRadarPane(
                                mapUi = ui,
                                stormUi = stormUi,
                                onLongPressMap = vm::setMapLocation,
                                onMyLocation = { vm.recenterToDeviceLocation(context) },
                                onRadarEnabled = stormVm::setRadarEnabled,
                                onLightningEnabled = stormVm::setLightningEnabled,
                                onRefreshRadar = stormVm::refreshRadar,
                                onRefreshLightning = stormVm::refreshLightning,
                                onToggleAnimation = stormVm::toggleRadarAnimation,
                                onStepRadarFrame = stormVm::stepRadarFrame,
                                onSetRadarFrameIndex = stormVm::setRadarFrameIndex,
                                modifier = Modifier.fillMaxSize(),
                            )
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    tonalElevation = 1.dp,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.92f),
                ) {
                    TextButton(
                        onClick = { showAttribution = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            stringResource(R.string.attribution_open),
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                if (!useNavigationRail) {
                    NavigationBar {
                        NavigationBarItem(
                            selected = destination == MainDest.COMPARE,
                            onClick = {
                                destination = MainDest.COMPARE
                                vm.setRoutePickMode(RoutePickMode.None)
                            },
                            icon = { Icon(Icons.Default.Map, contentDescription = null) },
                            label = {
                                Text(
                                    stringResource(R.string.nav_compare),
                                    maxLines = 2,
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                        )
                        NavigationBarItem(
                            selected = destination == MainDest.ROUTE,
                            onClick = { destination = MainDest.ROUTE },
                            icon = {
                                NavPremiumBadgeIcon(
                                    baseImageVector = Icons.Default.Navigation,
                                    baseContentDescription = stringResource(R.string.nav_route),
                                    isUnlocked = isRoutePremium,
                                )
                            },
                            label = { NavPremiumStackLabel(stringResource(R.string.nav_route)) },
                        )
                        NavigationBarItem(
                            selected = destination == MainDest.EXTENDED_WIND,
                            onClick = { destination = MainDest.EXTENDED_WIND },
                            icon = {
                                NavPremiumBadgeIcon(
                                    baseImageVector = Icons.Outlined.CalendarMonth,
                                    baseContentDescription = stringResource(R.string.route_tab_extended_wind),
                                    isUnlocked = isRoutePremium,
                                )
                            },
                            label = { NavPremiumStackLabel(stringResource(R.string.route_tab_extended_wind)) },
                        )
                        NavigationBarItem(
                            selected = destination == MainDest.MARINE_TEXT,
                            onClick = { destination = MainDest.MARINE_TEXT },
                            icon = {
                                Icon(
                                    Icons.Outlined.Waves,
                                    contentDescription = stringResource(R.string.marine_nav_cd),
                                )
                            },
                            label = {
                                Text(
                                    stringResource(R.string.nav_marine_four_seas),
                                    maxLines = 2,
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                        )
                        NavigationBarItem(
                            selected = destination == MainDest.STORM_RADAR,
                            onClick = { destination = MainDest.STORM_RADAR },
                            icon = {
                                Icon(
                                    Icons.Outlined.Thunderstorm,
                                    contentDescription = stringResource(R.string.storm_nav_cd),
                                )
                            },
                            label = {
                                Text(
                                    stringResource(R.string.nav_storm_radar),
                                    maxLines = 2,
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            },
                        )
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun NavPremiumBadgeIcon(
    baseImageVector: ImageVector,
    baseContentDescription: String?,
    isUnlocked: Boolean,
) {
    Box {
        Icon(baseImageVector, contentDescription = baseContentDescription)
        Icon(
            Icons.Outlined.Lock,
            contentDescription = stringResource(R.string.route_premium_cd_lock),
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(1.dp)
                    .size(12.dp),
            tint =
                if (isUnlocked) {
                    MaterialTheme.colorScheme.outline
                } else {
                    MaterialTheme.colorScheme.primary
                },
        )
    }
}

@Composable
private fun NavPremiumStackLabel(
    title: String,
    forRail: Boolean = false,
    showLabel: Boolean = true,
) {
    if (!showLabel) return
    val columnModifier =
        if (forRail) {
            Modifier.widthIn(max = NavigationRailItemLabelMaxWidth)
        } else {
            Modifier.fillMaxWidth()
        }
    Column(
        modifier = columnModifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall.copy(lineHeight = 13.sp),
            textAlign = TextAlign.Center,
            maxLines = 4,
            softWrap = true,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(R.string.nav_premium_mark),
            style =
                MaterialTheme.typography.labelSmall.copy(
                    fontSize = 10.sp,
                    lineHeight = 11.sp,
                ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun DestinationRailItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    label: @Composable () -> Unit,
    showLabel: Boolean = true,
) {
    NavigationRailItem(
        selected = selected,
        onClick = onClick,
        icon = icon,
        label = if (showLabel) label else ({ }),
    )
}

@Composable
private fun ScrollableDestinationRail(
    destination: MainDest,
    isRoutePremium: Boolean,
    compactLabels: Boolean,
    onCompare: () -> Unit,
    onRoute: () -> Unit,
    onWind: () -> Unit,
    onMarine: () -> Unit,
    onStorm: () -> Unit,
) {
    val scrollState = rememberScrollState()
    val showLabels = !compactLabels
    LaunchedEffect(destination) {
        if (destination == MainDest.STORM_RADAR && scrollState.maxValue > 0) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }
    NavigationRail(modifier = Modifier.fillMaxHeight()) {
        Column(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            DestinationRailItem(
                selected = destination == MainDest.COMPARE,
                onClick = onCompare,
                showLabel = showLabels,
                icon = {
                    Icon(
                        Icons.Default.Map,
                        contentDescription = stringResource(R.string.content_map),
                    )
                },
                label = {
                    Text(
                        text = stringResource(R.string.nav_compare),
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        maxLines = 4,
                        softWrap = true,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = NavigationRailItemLabelMaxWidth),
                    )
                },
            )
            DestinationRailItem(
                selected = destination == MainDest.ROUTE,
                onClick = onRoute,
                showLabel = showLabels,
                icon = {
                    NavPremiumBadgeIcon(
                        baseImageVector = Icons.Default.Navigation,
                        baseContentDescription = stringResource(R.string.nav_route),
                        isUnlocked = isRoutePremium,
                    )
                },
                label = {
                    NavPremiumStackLabel(
                        stringResource(R.string.nav_route),
                        forRail = true,
                        showLabel = showLabels,
                    )
                },
            )
            DestinationRailItem(
                selected = destination == MainDest.EXTENDED_WIND,
                onClick = onWind,
                showLabel = showLabels,
                icon = {
                    NavPremiumBadgeIcon(
                        baseImageVector = Icons.Outlined.CalendarMonth,
                        baseContentDescription = stringResource(R.string.route_tab_extended_wind),
                        isUnlocked = isRoutePremium,
                    )
                },
                label = {
                    NavPremiumStackLabel(
                        stringResource(R.string.route_tab_extended_wind),
                        forRail = true,
                        showLabel = showLabels,
                    )
                },
            )
            DestinationRailItem(
                selected = destination == MainDest.MARINE_TEXT,
                onClick = onMarine,
                showLabel = showLabels,
                icon = {
                    Icon(
                        Icons.Outlined.Waves,
                        contentDescription = stringResource(R.string.marine_nav_cd),
                    )
                },
                label = {
                    Text(
                        text = stringResource(R.string.nav_marine_four_seas),
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        maxLines = 4,
                        softWrap = true,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = NavigationRailItemLabelMaxWidth),
                    )
                },
            )
            DestinationRailItem(
                selected = destination == MainDest.STORM_RADAR,
                onClick = onStorm,
                showLabel = showLabels,
                icon = {
                    Icon(
                        Icons.Outlined.Thunderstorm,
                        contentDescription = stringResource(R.string.storm_nav_cd),
                    )
                },
                label = {
                    Text(
                        text = stringResource(R.string.nav_storm_radar),
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center,
                        maxLines = 4,
                        softWrap = true,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = NavigationRailItemLabelMaxWidth),
                    )
                },
            )
        }
    }
}

@Composable
private fun ComparePane(
    ui: VeneappiUiState,
    windUnit: WindUnit,
    onRefresh: () -> Unit,
    onLongPressMap: (Double, Double) -> Unit,
    onMyLocation: () -> Unit,
    onMapTapRoute: (Double, Double) -> Unit,
    routePickMode: RoutePickMode,
    onWindUnit: (WindUnit) -> Unit,
    traficomPlanningChart: Boolean,
    onTraficomPlanningChartChange: (Boolean) -> Unit,
    aisViewModel: AisMapViewModel,
    isPremium: Boolean,
    modifier: Modifier = Modifier,
) {
    val mapLabel = stringResource(R.string.content_map)
    val twoPane =
        LocalConfiguration.current.screenWidthDp >= UiBreakpoints.TWO_PANE_MIN_WIDTH_DP
    if (twoPane) {
        Row(modifier = modifier.fillMaxSize()) {
            Column(
                modifier =
                    Modifier
                        .weight(0.56f)
                        .fillMaxHeight(),
            ) {
                FilterChip(
                    selected = traficomPlanningChart,
                    onClick = { onTraficomPlanningChartChange(!traficomPlanningChart) },
                    label = { Text(stringResource(R.string.map_traficom_overlay)) },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
                MapWithAisChrome(
                    latitude = ui.latitude,
                    longitude = ui.longitude,
                    routeGeometry = ui.routeGeometry,
                    routeStart = ui.routeStart,
                    routeEnd = ui.routeEnd,
                    harbors = emptyList(),
                    onLongPress = onLongPressMap,
                    onMapClick =
                        if (routePickMode != RoutePickMode.None) {
                            onMapTapRoute
                        } else {
                            null
                        },
                    traficomPlanningRasterEnabled = traficomPlanningChart,
                    onMyLocation = onMyLocation,
                    mapRecenterSignal = ui.mapRecenterSignal,
                    aisViewModel = aisViewModel,
                    isPremium = isPremium,
                    mapModifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .semantics { contentDescription = mapLabel },
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .fillMaxHeight(),
                )
            }
            WeatherPane(
                ui = ui,
                windUnit = windUnit,
                onRefresh = onRefresh,
                onWindUnitChange = onWindUnit,
                modifier =
                    Modifier
                        .weight(0.44f)
                        .fillMaxHeight(),
            )
        }
    } else {
        Column(modifier = modifier.fillMaxSize()) {
            FilterChip(
                selected = traficomPlanningChart,
                onClick = { onTraficomPlanningChartChange(!traficomPlanningChart) },
                label = { Text(stringResource(R.string.map_traficom_overlay)) },
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
            MapWithAisChrome(
                latitude = ui.latitude,
                longitude = ui.longitude,
                routeGeometry = ui.routeGeometry,
                routeStart = ui.routeStart,
                routeEnd = ui.routeEnd,
                harbors = emptyList(),
                onLongPress = onLongPressMap,
                onMapClick =
                    if (routePickMode != RoutePickMode.None) {
                        onMapTapRoute
                    } else {
                        null
                    },
                traficomPlanningRasterEnabled = traficomPlanningChart,
                onMyLocation = onMyLocation,
                mapRecenterSignal = ui.mapRecenterSignal,
                aisViewModel = aisViewModel,
                isPremium = isPremium,
                mapModifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(0.52f)
                        .semantics { contentDescription = mapLabel },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(0.52f),
            )
            WeatherPane(
                ui = ui,
                windUnit = windUnit,
                onRefresh = onRefresh,
                onWindUnitChange = onWindUnit,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(0.48f),
            )
        }
    }
}

@Composable
private fun WeatherPane(
    ui: VeneappiUiState,
    windUnit: WindUnit,
    onRefresh: () -> Unit,
    onWindUnitChange: (WindUnit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val zone = ZoneId.systemDefault()
    val formatter =
        remember(zone) {
            DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT).withZone(zone)
        }
    val slotLabels =
        listOf(
            stringResource(R.string.weather_slot_now),
            stringResource(R.string.weather_slot_p3h),
            stringResource(R.string.weather_slot_p6h),
            stringResource(R.string.weather_slot_p12h),
        )
    val refreshDesc = stringResource(R.string.weather_refresh)

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val fitThreeSources =
            maxWidth >= UiBreakpoints.WEATHER_PANE_DENSE_MIN_WIDTH_DP.dp &&
                maxHeight >= UiBreakpoints.WEATHER_PANE_DENSE_MIN_HEIGHT_DP.dp
        if (fitThreeSources) {
            Column(
                modifier =
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
            ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.weather_sources_title),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.map_hint_long_press),
                        style = MaterialTheme.typography.labelSmall,
                        lineHeight = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (ui.loadingWeather) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                }
                FilterChip(
                    selected = windUnit == WindUnit.MetersPerSecond,
                    onClick = { onWindUnitChange(WindUnit.MetersPerSecond) },
                    label = { Text(stringResource(R.string.units_ms), style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.height(28.dp),
                )
                FilterChip(
                    selected = windUnit == WindUnit.Knots,
                    onClick = { onWindUnitChange(WindUnit.Knots) },
                    label = { Text(stringResource(R.string.units_kn), style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.height(28.dp),
                )
                IconButton(
                    onClick = onRefresh,
                    enabled = !ui.loadingWeather,
                    modifier = Modifier.semantics { contentDescription = refreshDesc },
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                }
            }
            Column(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    SourceWindSection(
                        title = stringResource(R.string.source_met_norway),
                        result = ui.forecasts[SourceId.MET_NORWAY],
                        windUnit = windUnit,
                        slotLabels = slotLabels,
                        formatter = formatter,
                        dense = true,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    SourceWindSection(
                        title = stringResource(R.string.source_smhi),
                        result = ui.forecasts[SourceId.SMHI],
                        windUnit = windUnit,
                        slotLabels = slotLabels,
                        formatter = formatter,
                        dense = true,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    SourceWindSection(
                        title = stringResource(R.string.source_fmi),
                        result = ui.forecasts[SourceId.FMI],
                        windUnit = windUnit,
                        slotLabels = slotLabels,
                        formatter = formatter,
                        dense = true,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    } else {
        Column(
            modifier =
                modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.weather_sources_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = stringResource(R.string.map_hint_long_press),
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.units_wind), style = MaterialTheme.typography.labelLarge)
                FilterChip(
                    selected = windUnit == WindUnit.MetersPerSecond,
                    onClick = { onWindUnitChange(WindUnit.MetersPerSecond) },
                    label = { Text(stringResource(R.string.units_ms)) },
                )
                FilterChip(
                    selected = windUnit == WindUnit.Knots,
                    onClick = { onWindUnitChange(WindUnit.Knots) },
                    label = { Text(stringResource(R.string.units_kn)) },
                )
            }
            Button(onClick = onRefresh, enabled = !ui.loadingWeather) {
                Text(stringResource(R.string.weather_refresh))
            }
            if (ui.loadingWeather) {
                CircularProgressIndicator()
            }
            SourceWindSection(
                title = stringResource(R.string.source_met_norway),
                result = ui.forecasts[SourceId.MET_NORWAY],
                windUnit = windUnit,
                slotLabels = slotLabels,
                formatter = formatter,
                dense = false,
            )
            SourceWindSection(
                title = stringResource(R.string.source_smhi),
                result = ui.forecasts[SourceId.SMHI],
                windUnit = windUnit,
                slotLabels = slotLabels,
                formatter = formatter,
                dense = false,
            )
            SourceWindSection(
                title = stringResource(R.string.source_fmi),
                result = ui.forecasts[SourceId.FMI],
                windUnit = windUnit,
                slotLabels = slotLabels,
                formatter = formatter,
                dense = false,
            )
        }
    }
    }
}

@Composable
private fun SourceWindSection(
    title: String,
    result: Result<UnifiedForecast>?,
    windUnit: WindUnit,
    slotLabels: List<String>,
    formatter: DateTimeFormatter,
    dense: Boolean = false,
    compactInlineWindDirection: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val cardOuter =
        if (dense) {
            modifier.fillMaxSize()
        } else {
            modifier.fillMaxWidth()
        }
    when {
        result == null -> {
            Card(
                modifier = cardOuter,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceDim),
                shape = RoundedCornerShape(if (dense) 8.dp else 12.dp),
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(if (dense) 8.dp else 14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.metric_na))
                }
            }
        }
        result.isFailure -> {
            Card(
                modifier = cardOuter,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceDim),
                shape = RoundedCornerShape(if (dense) 8.dp else 12.dp),
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(if (dense) 8.dp else 14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = stringResource(R.string.weather_error, result.exceptionOrNull()?.message ?: "error"),
                        color = MaterialTheme.colorScheme.error,
                        maxLines = if (dense) 3 else Int.MAX_VALUE,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        else -> {
            val fc = result.getOrNull()!!
            val meta =
                buildString {
                    append(
                        stringResource(
                            R.string.weather_fetched_at,
                            formatter.format(Instant.ofEpochMilli(fc.fetchedAtUtc)),
                        ),
                    )
                    fc.modelInfo?.let {
                        append(" · ")
                        append(it)
                    }
                }
            SourceWindForecastCard(
                modifier = cardOuter,
                title = title,
                forecast = fc,
                windUnit = windUnit,
                slotLabels = slotLabels,
                metaLine = meta,
                dense = dense,
                compactInlineWindDirection = compactInlineWindDirection,
            )
        }
    }
}

private fun shellForecastForRoute(
    id: SourceId,
    strip: RouteSourceWeatherSlots,
): UnifiedForecast =
    UnifiedForecast(
        source = WeatherSources.source(id),
        fetchedAtUtc = strip.fetchedAtUtc,
        modelInfo = strip.modelInfo,
        points = emptyList(),
    )

@Composable
private fun RouteStripForSource(
    sourceId: SourceId,
    title: String,
    result: Result<RouteSourceWeatherSlots>?,
    slotLabels: List<String>,
    windUnit: WindUnit,
    modifier: Modifier = Modifier,
) {
    when {
        result == null -> {
            Card(
                modifier = modifier,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceDim),
                shape = RoundedCornerShape(8.dp),
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.metric_na))
                }
            }
        }
        result.isFailure -> {
            Card(
                modifier = modifier,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceDim),
                shape = RoundedCornerShape(8.dp),
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = stringResource(R.string.weather_error, result.exceptionOrNull()?.message ?: "error"),
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        else -> {
            val strip = result.getOrNull()!!
            val shell = shellForecastForRoute(sourceId, strip)
            SourceWindForecastCard(
                modifier = modifier,
                title = title,
                forecast = shell,
                windUnit = windUnit,
                slotLabels = slotLabels,
                metaLine = null,
                dense = true,
                compactInlineWindDirection = true,
                slotPoints = strip.slots,
            )
        }
    }
}

private fun formatBoatSpeedDraftText(kn: Double): String =
    String.format(Locale.US, "%.1f", kn)

private fun boatSpeedTextFieldValue(
    kn: Double,
    selectAll: Boolean,
): TextFieldValue {
    val text = formatBoatSpeedDraftText(kn)
    val selection =
        if (selectAll && text.isNotEmpty()) {
            TextRange(0, text.length)
        } else {
            TextRange(text.length)
        }
    return TextFieldValue(text, selection)
}

private fun routeLegNmForExport(ui: VeneappiUiState): Double =
    ui.routeWeatherLegNm
        ?: run {
            val s = ui.routeStart
            val e = ui.routeEnd
            if (s == null || e == null) {
                0.0
            } else {
                val g = if (ui.routeGeometry.size >= 2) ui.routeGeometry else listOf(s, e)
                GeoMath.metersToNauticalMiles(GeoMath.polylineLengthMeters(g))
            }
        }

private fun routeEtaHoursForExport(ui: VeneappiUiState, legNm: Double): Double =
    ui.routeWeatherEtaHours ?: if (legNm > 0) legNm / ui.boatSpeedKn.coerceIn(0.5, 40.0) else 0.0

private fun buildRoutePlanPdfInput(
    context: android.content.Context,
    locale: Locale,
    ui: VeneappiUiState,
    windUnit: WindUnit,
    slotLabels: List<String>,
    legNm: Double,
    etaHours: Double,
): RoutePlanPdfInput {
    val res = context.resources
    val zone = ZoneId.systemDefault()
    val genTime =
        ZonedDateTime.now(zone).format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM))
    val s = ui.routeStart!!
    val e = ui.routeEnd!!
    val sourceOrder = listOf(SourceId.MET_NORWAY, SourceId.SMHI, SourceId.FMI)
    val sections =
        sourceOrder.map { id ->
            val title =
                when (id) {
                    SourceId.MET_NORWAY -> res.getString(R.string.source_met_norway)
                    SourceId.SMHI -> res.getString(R.string.source_smhi)
                    SourceId.FMI -> res.getString(R.string.source_fmi)
                }
            val r = ui.routeWeatherBySource[id]
            when {
                r == null ->
                    RoutePlanPdfSourceSection(
                        title,
                        null,
                        res.getString(R.string.metric_na),
                        null,
                    )
                r.isFailure -> {
                    val msg = r.exceptionOrNull()?.message ?: ""
                    RoutePlanPdfSourceSection(
                        title,
                        null,
                        res.getString(R.string.weather_error, msg),
                        null,
                    )
                }
                else -> {
                    val strip = r.getOrNull()!!
                    val modelLine = strip.modelInfo?.let { mi -> res.getString(R.string.weather_model, mi) }
                    val slotLines =
                        slotLabels.indices.map { i ->
                            formatRouteSlotForPdf(
                                slotLabels[i],
                                strip.slots.getOrNull(i),
                                windUnit,
                                locale,
                            )
                        }
                    RoutePlanPdfSourceSection(title, modelLine, null, slotLines)
                }
            }
        }
    return RoutePlanPdfInput(
        docTitle = res.getString(R.string.route_pdf_doc_title),
        generatedLine = res.getString(R.string.route_pdf_generated, genTime),
        coordinatesHeading = res.getString(R.string.route_pdf_coords),
        startLine = res.getString(R.string.route_pdf_start_fmt, s.first, s.second),
        endLine = res.getString(R.string.route_pdf_end_fmt, e.first, e.second),
        legSummaryLine = res.getString(R.string.route_weather_leg, legNm, etaHours),
        boatSpeedLine =
            res.getString(R.string.route_boat_speed_title) +
                ": " +
                res.getString(R.string.metric_wind_kn, ui.boatSpeedKn),
        weatherHeading = res.getString(R.string.route_pdf_weather_heading),
        disclaimer = res.getString(R.string.disclaimer_nav_body),
        footerAttribution = res.getString(R.string.route_pdf_sources_footer),
        sections = sections,
    )
}

@Composable
private fun RouteExportActionsRow(
    enabled: Boolean,
    onExportGpx: () -> Unit,
    onExportPdf: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(
            onClick = onExportGpx,
            enabled = enabled,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
        ) {
            Text(
                stringResource(R.string.route_export_gpx),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        OutlinedButton(
            onClick = onExportPdf,
            enabled = enabled,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
        ) {
            Text(
                stringResource(R.string.route_export_pdf),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun RouteWeatherRightPane(
    ui: VeneappiUiState,
    windUnit: WindUnit,
    onWindUnit: (WindUnit) -> Unit,
    slotLabels: List<String>,
    speedDraft: TextFieldValue,
    onSpeedDraftChange: (TextFieldValue) -> Unit,
    speedTextFieldModifier: Modifier,
    onSliderSpeed: (Double) -> Unit,
    hasRouteToClear: Boolean,
    onClear: () -> Unit,
    focusManager: androidx.compose.ui.focus.FocusManager,
    legNm: Double,
    etaHours: Double,
    exportEnabled: Boolean,
    onExportGpx: () -> Unit,
    onExportPdf: () -> Unit,
    offlinePackUi: OfflinePackUiState,
    onDownloadOfflinePack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .imePadding()
            .padding(horizontal = 2.dp, vertical = 1.dp),
    ) {
        RouteSpeedCompactBar(
            ui = ui,
            speedDraft = speedDraft,
            onSpeedDraftChange = onSpeedDraftChange,
            textFieldModifier = speedTextFieldModifier,
            onSliderChange = onSliderSpeed,
            hasRouteToClear = hasRouteToClear,
            onClear = onClear,
            focusManager = focusManager,
        )
        RouteExportActionsRow(
            enabled = exportEnabled,
            onExportGpx = onExportGpx,
            onExportPdf = onExportPdf,
            modifier = Modifier.padding(top = 4.dp),
        )
        OfflineRoutePackCard(
            enabled = exportEnabled && ui.routeGeometry.size >= 2,
            packState = offlinePackUi,
            onDownload = onDownloadOfflinePack,
            modifier = Modifier.padding(top = 4.dp),
        )
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.route_weather_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(R.string.route_weather_leg, legNm, etaHours),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (ui.loadingRouteWeather) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                )
            }
            FilterChip(
                selected = windUnit == WindUnit.MetersPerSecond,
                onClick = { onWindUnit(WindUnit.MetersPerSecond) },
                label = { Text(stringResource(R.string.units_ms), style = MaterialTheme.typography.labelSmall) },
                modifier = Modifier.height(26.dp),
            )
            FilterChip(
                selected = windUnit == WindUnit.Knots,
                onClick = { onWindUnit(WindUnit.Knots) },
                label = { Text(stringResource(R.string.units_kn), style = MaterialTheme.typography.labelSmall) },
                modifier = Modifier.height(26.dp),
            )
        }
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            val cards =
                listOf(
                    Triple(SourceId.MET_NORWAY, stringResource(R.string.source_met_norway), ui.routeWeatherBySource[SourceId.MET_NORWAY]),
                    Triple(SourceId.SMHI, stringResource(R.string.source_smhi), ui.routeWeatherBySource[SourceId.SMHI]),
                    Triple(SourceId.FMI, stringResource(R.string.source_fmi), ui.routeWeatherBySource[SourceId.FMI]),
                )
            for ((id, title, result) in cards) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    RouteStripForSource(
                        sourceId = id,
                        title = title,
                        result = result,
                        slotLabels = slotLabels,
                        windUnit = windUnit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun RouteSpeedCompactBar(
    ui: VeneappiUiState,
    speedDraft: TextFieldValue,
    onSpeedDraftChange: (TextFieldValue) -> Unit,
    textFieldModifier: Modifier,
    onSliderChange: (Double) -> Unit,
    hasRouteToClear: Boolean,
    onClear: () -> Unit,
    focusManager: androidx.compose.ui.focus.FocusManager,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.94f),
        tonalElevation = 1.dp,
    ) {
        Column(
            Modifier.padding(horizontal = 3.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                stringResource(R.string.route_boat_speed_title),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val start = ui.routeStart
                val end = ui.routeEnd
                Column(modifier = Modifier.weight(1f)) {
                    if (start != null && end != null) {
                        val meters = GeoMath.haversineMeters(start.first, start.second, end.first, end.second)
                        val nm =
                            if (ui.routeGeometry.size >= 2) {
                                GeoMath.metersToNauticalMiles(GeoMath.polylineLengthMeters(ui.routeGeometry))
                            } else {
                                GeoMath.metersToNauticalMiles(meters)
                            }
                        val eta = if (ui.boatSpeedKn > 0) nm / ui.boatSpeedKn else Double.NaN
                        Text(
                            stringResource(R.string.route_distance_nm, nm),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (!eta.isNaN()) {
                            Text(
                                stringResource(R.string.route_eta_hours, eta),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    } else {
                        Text(
                            stringResource(R.string.route_pick_status_short),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.metric_wind_kn, ui.boatSpeedKn),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                OutlinedTextField(
                    value = speedDraft,
                    onValueChange = onSpeedDraftChange,
                    modifier = textFieldModifier.widthIn(max = 76.dp),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.labelMedium,
                    placeholder = {
                        Text(stringResource(R.string.units_kn), style = MaterialTheme.typography.labelSmall)
                    },
                    keyboardOptions =
                        KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Done,
                        ),
                    keyboardActions =
                        KeyboardActions(
                            onDone = { focusManager.clearFocus() },
                        ),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Slider(
                    value = ui.boatSpeedKn.toFloat().coerceIn(0.5f, 40f),
                    onValueChange = { onSliderChange(it.toDouble()) },
                    valueRange = 0.5f..40f,
                    modifier =
                        Modifier
                            .weight(1f)
                            .height(20.dp),
                )
                TextButton(
                    onClick = {
                        focusManager.clearFocus()
                        onClear()
                    },
                    enabled = hasRouteToClear,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                ) {
                    Text(
                        stringResource(R.string.route_clear_route),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun RouteMapTopOverlay(
    onOpenDisclaimer: () -> Unit,
    fairwayUnavailable: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                stringResource(R.string.route_title),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = onOpenDisclaimer,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = stringResource(R.string.disclaimer_nav_title),
                )
            }
        }
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            shadowElevation = 8.dp,
            tonalElevation = 3.dp,
        ) {
            Text(
                stringResource(R.string.route_hint_long_press),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                style =
                    MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 6,
            )
        }
        if (fairwayUnavailable) {
            Surface(
                modifier =
                    Modifier
                        .padding(top = 6.dp)
                        .fillMaxWidth(),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.94f),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(
                    stringResource(R.string.route_fairway_unavailable),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

@Composable
private fun RoutePane(
    ui: VeneappiUiState,
    windUnit: WindUnit,
    onWindUnit: (WindUnit) -> Unit,
    onClear: () -> Unit,
    onSpeedChange: (Double) -> Unit,
    onLongPressRoute: (Double, Double) -> Unit,
    onMyLocation: () -> Unit,
    traficomPlanningChart: Boolean,
    onTraficomPlanningChartChange: (Boolean) -> Unit,
    onRefreshWeather: () -> Unit,
    aisViewModel: AisMapViewModel,
    isPremium: Boolean,
    offlinePackUi: OfflinePackUiState,
    onDownloadOfflinePack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showRouteDisclaimer by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    val exportLegNm = routeLegNmForExport(ui)
    val exportEtaH = routeEtaHoursForExport(ui, exportLegNm)
    val exportReady = ui.routeStart != null && ui.routeEnd != null
    val hasRouteToClear =
        ui.routeStart != null || ui.routeEnd != null || ui.routeGeometry.isNotEmpty()
    var speedDraft by remember { mutableStateOf(boatSpeedTextFieldValue(ui.boatSpeedKn, false)) }
    var speedFieldFocused by remember { mutableStateOf(false) }
    LaunchedEffect(ui.boatSpeedKn, speedFieldFocused) {
        if (!speedFieldFocused) {
            speedDraft = boatSpeedTextFieldValue(ui.boatSpeedKn, false)
        }
    }
    val nmSlotPoints =
        remember(ui.routeStart, ui.routeEnd, ui.routeGeometry, ui.routeWeatherLegNm) {
            val s = ui.routeStart
            val e = ui.routeEnd
            if (s == null || e == null) {
                listOf(0.0, 0.0, 0.0, 0.0)
            } else {
                val leg =
                    ui.routeWeatherLegNm
                        ?: run {
                            val g =
                                if (ui.routeGeometry.size >= 2) ui.routeGeometry else listOf(s, e)
                            GeoMath.metersToNauticalMiles(GeoMath.polylineLengthMeters(g))
                        }
                listOf(0.0, leg / 3.0, leg * 2.0 / 3.0, leg)
            }
        }
    val slotFracs = listOf(0.0, 1.0 / 3.0, 2.0 / 3.0, 1.0)
    val speedKnSlots = ui.boatSpeedKn.coerceIn(0.5, 40.0)
    val etaMinutesTotal =
        remember(nmSlotPoints, speedKnSlots) {
            val leg = nmSlotPoints.getOrNull(3) ?: 0.0
            if (leg <= 0) 0 else (leg / speedKnSlots * 60.0).roundToInt()
        }
    val routeSlotLabels =
        nmSlotPoints.zip(slotFracs).map { (nm, fr) ->
            val elapsedMin = (etaMinutesTotal * fr).roundToInt().coerceAtLeast(0)
            val distStr = stringResource(R.string.route_weather_slot_nm, nm)
            val timeStr =
                if (elapsedMin < 60) {
                    stringResource(R.string.route_duration_min, elapsedMin)
                } else {
                    val h = elapsedMin / 60
                    val m = elapsedMin % 60
                    stringResource(R.string.route_duration_hm, h, m)
                }
            stringResource(R.string.route_slot_label, distStr, timeStr)
        }
    val showRouteWeatherStrip = ui.routeStart != null && ui.routeEnd != null
    val wide = LocalConfiguration.current.screenWidthDp >= UiBreakpoints.TWO_PANE_MIN_WIDTH_DP
    val mapLabel = stringResource(R.string.content_map)
    val speedTfMod =
        Modifier.onFocusChanged { state ->
            if (state.isFocused) {
                speedFieldFocused = true
                speedDraft = boatSpeedTextFieldValue(ui.boatSpeedKn, selectAll = true)
            } else {
                if (speedFieldFocused) {
                    val parsed =
                        speedDraft.text
                            .trim()
                            .replace(',', '.')
                            .toDoubleOrNull()
                    if (parsed != null) {
                        onSpeedChange(parsed)
                    } else {
                        speedDraft = boatSpeedTextFieldValue(ui.boatSpeedKn, false)
                    }
                }
                speedFieldFocused = false
            }
        }
    val onSpeedDraftChange: (TextFieldValue) -> Unit = { new ->
        val filtered = new.text.filter { it.isDigit() || it == '.' || it == ',' }
        speedDraft =
            if (filtered == new.text) {
                new
            } else {
                TextFieldValue(filtered, TextRange(filtered.length))
            }
    }
    val onSliderSpeed: (Double) -> Unit = { kn ->
        onSpeedChange(kn)
        if (speedFieldFocused) {
            speedDraft = boatSpeedTextFieldValue(kn, selectAll = false)
        }
    }
    val onExportGpx: () -> Unit = {
        val start = ui.routeStart
        val end = ui.routeEnd
        if (start != null && end != null) {
            runCatching {
                val pts =
                    if (ui.routeGeometry.size >= 2) {
                        ui.routeGeometry
                    } else {
                        listOf(start, end)
                    }
                val stamp =
                    ZonedDateTime.now(ZoneId.systemDefault()).format(
                        DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"),
                    )
                val gpx = RouteGpx.build(context.getString(R.string.app_name), pts)
                val file = File(context.routeExportCacheDir(), "marine_weather_route_$stamp.gpx")
                file.writeText(gpx)
                context.shareStream(
                    context.exportFileUri(file),
                    "application/gpx+xml",
                    context.getString(R.string.route_export_chooser_gpx),
                )
            }.onFailure {
                Toast.makeText(
                    context,
                    context.getString(R.string.route_export_failed),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }
    val onExportPdf: () -> Unit = {
        val start = ui.routeStart
        val end = ui.routeEnd
        if (start != null && end != null) {
            runCatching {
                val stamp =
                    ZonedDateTime.now(ZoneId.systemDefault()).format(
                        DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"),
                    )
                val file = File(context.routeExportCacheDir(), "marine_weather_route_plan_$stamp.pdf")
                val input =
                    buildRoutePlanPdfInput(
                        context,
                        locale,
                        ui,
                        windUnit,
                        routeSlotLabels,
                        exportLegNm,
                        exportEtaH,
                    )
                RoutePlanPdfWriter.writeToFile(file, input).getOrThrow()
                context.shareStream(
                    context.exportFileUri(file),
                    "application/pdf",
                    context.getString(R.string.route_export_chooser_pdf),
                )
            }.onFailure {
                Toast.makeText(
                    context,
                    context.getString(R.string.route_export_failed),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    if (showRouteDisclaimer) {
        AlertDialog(
            onDismissRequest = { showRouteDisclaimer = false },
            confirmButton = {
                TextButton(onClick = { showRouteDisclaimer = false }) {
                    Text("OK")
                }
            },
            title = { Text(stringResource(R.string.disclaimer_nav_title)) },
            text = { Text(stringResource(R.string.disclaimer_nav_body)) },
        )
    }

    if (showRouteWeatherStrip) {
        if (wide) {
            Row(modifier.fillMaxSize()) {
                                Column(
                                    modifier =
                                        Modifier
                                            .weight(0.58f)
                                            .fillMaxHeight(),
                                ) {
                                    FilterChip(
                                        selected = traficomPlanningChart,
                                        onClick = { onTraficomPlanningChartChange(!traficomPlanningChart) },
                                        label = { Text(stringResource(R.string.map_traficom_overlay)) },
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    )
                                    Box(
                                        modifier =
                                            Modifier
                                                .weight(1f)
                                                .fillMaxWidth(),
                                    ) {
                                        MapWithAisChrome(
                                            latitude = ui.latitude,
                                            longitude = ui.longitude,
                                            routeGeometry = ui.routeGeometry,
                                            routeStart = ui.routeStart,
                                            routeEnd = ui.routeEnd,
                                            harbors = emptyList(),
                                            onLongPress = onLongPressRoute,
                                            onMapClick = null,
                                            traficomPlanningRasterEnabled = traficomPlanningChart,
                                            onMyLocation = onMyLocation,
                                            mapRecenterSignal = ui.mapRecenterSignal,
                                            aisViewModel = aisViewModel,
                                            isPremium = isPremium,
                                            mapModifier =
                                                Modifier
                                                    .fillMaxSize()
                                                    .semantics { contentDescription = mapLabel },
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                        RouteMapTopOverlay(
                                            onOpenDisclaimer = { showRouteDisclaimer = true },
                                            fairwayUnavailable = ui.routeFairwayUnavailable,
                                            modifier = Modifier.align(Alignment.TopStart),
                                        )
                                    }
                                }
                                RouteWeatherRightPane(
                                    ui = ui,
                                    windUnit = windUnit,
                                    onWindUnit = onWindUnit,
                                    slotLabels = routeSlotLabels,
                                    speedDraft = speedDraft,
                                    onSpeedDraftChange = onSpeedDraftChange,
                                    speedTextFieldModifier = speedTfMod,
                                    onSliderSpeed = onSliderSpeed,
                                    hasRouteToClear = hasRouteToClear,
                                    onClear = onClear,
                                    focusManager = focusManager,
                                    legNm = exportLegNm,
                                    etaHours = exportEtaH,
                                    exportEnabled = exportReady,
                                    onExportGpx = onExportGpx,
                                    onExportPdf = onExportPdf,
                                    offlinePackUi = offlinePackUi,
                                    onDownloadOfflinePack = onDownloadOfflinePack,
                                    modifier =
                                        Modifier
                                            .weight(0.42f)
                                            .fillMaxHeight(),
                                )
            }
        } else {
                            Column(Modifier.fillMaxSize()) {
                                Column(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .weight(0.55f),
                                ) {
                                    FilterChip(
                                        selected = traficomPlanningChart,
                                        onClick = { onTraficomPlanningChartChange(!traficomPlanningChart) },
                                        label = { Text(stringResource(R.string.map_traficom_overlay)) },
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    )
                                    Box(
                                        modifier =
                                            Modifier
                                                .weight(1f)
                                                .fillMaxWidth(),
                                    ) {
                                        MapWithAisChrome(
                                            latitude = ui.latitude,
                                            longitude = ui.longitude,
                                            routeGeometry = ui.routeGeometry,
                                            routeStart = ui.routeStart,
                                            routeEnd = ui.routeEnd,
                                            harbors = emptyList(),
                                            onLongPress = onLongPressRoute,
                                            onMapClick = null,
                                            traficomPlanningRasterEnabled = traficomPlanningChart,
                                            onMyLocation = onMyLocation,
                                            mapRecenterSignal = ui.mapRecenterSignal,
                                            aisViewModel = aisViewModel,
                                            isPremium = isPremium,
                                            mapModifier =
                                                Modifier
                                                    .fillMaxSize()
                                                    .semantics { contentDescription = mapLabel },
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                        RouteMapTopOverlay(
                                            onOpenDisclaimer = { showRouteDisclaimer = true },
                                            fairwayUnavailable = ui.routeFairwayUnavailable,
                                            modifier = Modifier.align(Alignment.TopStart),
                                        )
                                    }
                                }
                                RouteWeatherRightPane(
                                    ui = ui,
                                    windUnit = windUnit,
                                    onWindUnit = onWindUnit,
                                    slotLabels = routeSlotLabels,
                                    speedDraft = speedDraft,
                                    onSpeedDraftChange = onSpeedDraftChange,
                                    speedTextFieldModifier = speedTfMod,
                                    onSliderSpeed = onSliderSpeed,
                                    hasRouteToClear = hasRouteToClear,
                                    onClear = onClear,
                                    focusManager = focusManager,
                                    legNm = exportLegNm,
                                    etaHours = exportEtaH,
                                    exportEnabled = exportReady,
                                    onExportGpx = onExportGpx,
                                    onExportPdf = onExportPdf,
                                    offlinePackUi = offlinePackUi,
                                    onDownloadOfflinePack = onDownloadOfflinePack,
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .weight(0.45f),
                                )
            }
        }
    } else {
        Column(modifier.fillMaxSize()) {
                            FilterChip(
                                selected = traficomPlanningChart,
                                onClick = { onTraficomPlanningChartChange(!traficomPlanningChart) },
                                label = { Text(stringResource(R.string.map_traficom_overlay)) },
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                            Box(
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .fillMaxWidth(),
                            ) {
                                MapWithAisChrome(
                                    latitude = ui.latitude,
                                    longitude = ui.longitude,
                                    routeGeometry = ui.routeGeometry,
                                    routeStart = ui.routeStart,
                                    routeEnd = ui.routeEnd,
                                    harbors = emptyList(),
                                    onLongPress = onLongPressRoute,
                                    onMapClick = null,
                                    traficomPlanningRasterEnabled = traficomPlanningChart,
                                    onMyLocation = onMyLocation,
                                    mapRecenterSignal = ui.mapRecenterSignal,
                                    aisViewModel = aisViewModel,
                                    isPremium = isPremium,
                                    mapModifier =
                                        Modifier
                                            .fillMaxSize()
                                            .semantics { contentDescription = mapLabel },
                                    modifier = Modifier.fillMaxSize(),
                                )
                                RouteMapTopOverlay(
                                    onOpenDisclaimer = { showRouteDisclaimer = true },
                                    fairwayUnavailable = ui.routeFairwayUnavailable,
                                    modifier = Modifier.align(Alignment.TopStart),
                                )
                            }
        }
    }
}

@Composable
private fun AttributionDialog(onDismiss: () -> Unit) {
    val uri = LocalUriHandler.current
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("OK") }
        },
        title = { Text(stringResource(R.string.attribution_open)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AttributionLink(
                    name = stringResource(R.string.source_met_norway),
                    url = WeatherSources.MetNorway.licenseUrl,
                    onOpen = { uri.openUri(it) },
                )
                AttributionLink(
                    name = stringResource(R.string.source_smhi),
                    url = WeatherSources.Smhi.licenseUrl,
                    onOpen = { uri.openUri(it) },
                )
                AttributionLink(
                    name = stringResource(R.string.source_fmi),
                    url = WeatherSources.Fmi.licenseUrl,
                    onOpen = { uri.openUri(it) },
                )
                Text(
                    stringResource(R.string.attribution_traficom_charts),
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = { uri.openUri("https://creativecommons.org/licenses/by/4.0/deed.fi") }) {
                    Text(stringResource(R.string.attribution_traficom_cc_link))
                }
                Text(
                    stringResource(R.string.attribution_fmi_radar_lightning),
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = { uri.openUri(WeatherSources.Fmi.licenseUrl) }) {
                    Text(stringResource(R.string.attribution_fmi_cc_link))
                }
                Text(
                    stringResource(R.string.attribution_met_radar),
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = { uri.openUri(WeatherSources.MetNorway.licenseUrl) }) {
                    Text(stringResource(R.string.attribution_met_norway_link))
                }
                Text(
                    stringResource(R.string.attribution_smhi_radar_lightning),
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = { uri.openUri(WeatherSources.Smhi.licenseUrl) }) {
                    Text(stringResource(R.string.attribution_smhi_link))
                }
                Text(
                    stringResource(R.string.attribution_digitraffic_ais),
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = { uri.openUri("https://meri.digitraffic.fi/") }) {
                    Text(stringResource(R.string.attribution_digitraffic_ais_link))
                }
            }
        },
    )
}

@Composable
private fun AttributionLink(
    name: String,
    url: String,
    onOpen: (String) -> Unit,
) {
    TextButton(onClick = { onOpen(url) }) { Text("$name — $url") }
}
