package fi.veneappi.app.ui.map

import android.annotation.SuppressLint
import android.graphics.PointF
import android.util.Log
import android.util.TypedValue
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import fi.veneappi.app.R
import fi.veneappi.app.data.lightning.LightningSourceId
import fi.veneappi.app.data.lightning.LightningStrike
import fi.veneappi.app.data.radar.ActiveRadarOverlay
import fi.veneappi.app.data.radar.FmiRadarConfig
import fi.veneappi.app.data.radar.RadarDisplayKind
import fi.veneappi.app.domain.GeoMath
import fi.veneappi.app.domain.Harbor
import kotlinx.coroutines.delay
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.RasterLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.sources.RasterSource
import org.maplibre.android.style.sources.TileSet
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

private val STYLE_URL = MapConfig.STYLE_URL
private const val ROUTE_SOURCE_ID = "veneappi_route_source"
private const val ROUTE_LAYER_ID = "veneappi_route_layer"
private const val ROUTE_SLOTS_SOURCE_ID = "veneappi_route_weather_slots"
private const val ROUTE_SLOTS_LAYER_ID = "veneappi_route_weather_slots_layer"
private const val ROUTE_START_SOURCE_ID = "veneappi_route_start_pt"
private const val ROUTE_START_LAYER_ID = "veneappi_route_start_layer"
private const val ROUTE_END_SOURCE_ID = "veneappi_route_end_pt"
private const val ROUTE_END_LAYER_ID = "veneappi_route_end_layer"
private const val PIN_SOURCE_ID = "veneappi_pin_source"
private const val PIN_LAYER_ID = "veneappi_pin_layer"
private const val TRAFICOM_SOURCE_ID = "veneappi_traficom_nautical"
private const val TRAFICOM_LAYER_ID = "veneappi_traficom_nautical_layer"
/** Storm radar WMS tiles (FMI) — below route/lightning. */
private const val STORM_RADAR_TILE_SOURCE_ID = "veneappi_storm_radar_tile"
private const val STORM_RADAR_TILE_LAYER_ID = "veneappi_storm_radar_tile_layer"
/** Storm radar georeferenced PNG (forecast raster). */
private const val STORM_RADAR_IMAGE_SOURCE_ID = "veneappi_storm_radar_image"
private const val STORM_RADAR_IMAGE_LAYER_ID = "veneappi_storm_radar_image_layer"
/** Legacy A/B ids from older builds — removed on load. */
private const val STORM_RADAR_IMAGE_SOURCE_A_ID = "veneappi_storm_radar_image_a"
private const val STORM_RADAR_IMAGE_LAYER_A_ID = "veneappi_storm_radar_image_layer_a"
private const val STORM_RADAR_IMAGE_SOURCE_B_ID = "veneappi_storm_radar_image_b"
private const val STORM_RADAR_IMAGE_LAYER_B_ID = "veneappi_storm_radar_image_layer_b"
/** Lightning strikes — above radar/route, below location pin. */
private const val LIGHTNING_SOURCE_ID = "veneappi_lightning"
private const val LIGHTNING_FMI_LAYER_ID = "veneappi_lightning_fmi"
private const val LIGHTNING_SMHI_LAYER_ID = "veneappi_lightning_smhi"
private const val STORM_RADAR_OPACITY = 0.75f
private const val STORM_RADAR_FADE_MS = 200f
private const val STORM_STYLE_DEBOUNCE_MS = 50L

private var stormGeoLastImageUrl: String? = null
private const val HARBOR_SOURCE_ID = "veneappi_harbor_pts"
private const val HARBOR_LAYER_ID = "veneappi_harbor_layer"

private const val DEFAULT_ZOOM = 12.5
/** Storm radar: wide regional view (≈5 zoom-out steps from compare map). */
internal const val STORM_MAP_ZOOM = 5.5
private const val STORM_MAP_MIN_ZOOM = 4.5
private const val MIN_KEEP_ZOOM = 8.5
private const val TAG = "MapPane"
private const val SCALE_DEBOUNCE_MS = 200L

/** Traficom open WMTS — Merikarttasarja B (Gulf of Finland incl. Helsinki). */
private const val TRAFICOM_TILE_URL =
    "https://julkinen.traficom.fi/rasteripalvelu/wmts/rest/Traficom:Merikarttasarja%20B/default/WGS84_Pseudo-Mercator/WGS84_Pseudo-Mercator:{z}/{y}/{x}?format=image/png"

@SuppressLint("ClickableViewAccessibility")
@Composable
fun MapPane(
    latitude: Double,
    longitude: Double,
    routeGeometry: List<Pair<Double, Double>>,
    routeStart: Pair<Double, Double>? = null,
    routeEnd: Pair<Double, Double>? = null,
    harbors: List<Harbor> = emptyList(),
    onLongPress: (lat: Double, lon: Double) -> Unit,
    onMapClick: ((lat: Double, lon: Double) -> Unit)?,
    traficomPlanningRasterEnabled: Boolean = false,
    stormRadarOverlay: ActiveRadarOverlay? = null,
    lightningStrikes: List<LightningStrike> = emptyList(),
    /** Storm tab: one style pass, GLSurfaceView, no lightning layers on map. */
    isStormMap: Boolean = false,
    initialZoom: Double? = null,
    showZoomButtons: Boolean = true,
    onMyLocation: (() -> Unit)? = null,
    mapRecenterSignal: Long = 0L,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView =
        remember(isStormMap) {
            MapView(
                context,
                MapLibreMapOptions.createFromAttributes(context, null)
                    // TextureView + custom raster overlays can crash on some devices (e.g. Honor).
                    .textureMode(!isStormMap)
                    .setPrefetchZoomDelta(6),
            )
        }
    var mapRef by remember { mutableStateOf<MapLibreMap?>(null) }
    var styleReady by remember { mutableStateOf(false) }
    var initialCameraFramed by remember { mutableStateOf(false) }
    var metersPerScreenCm by remember { mutableStateOf<Double?>(null) }
    var prevTraficomRaster by remember { mutableStateOf<Boolean?>(null) }
    var lastRecenterSignalHandled by remember { mutableStateOf(0L) }

    val latestLongPress by rememberUpdatedState(onLongPress)
    val latestMapClick by rememberUpdatedState(onMapClick)
    val latestRouteGeometry by rememberUpdatedState(routeGeometry)
    val latestRouteStart by rememberUpdatedState(routeStart)
    val latestRouteEnd by rememberUpdatedState(routeEnd)
    val latestLat by rememberUpdatedState(latitude)
    val latestLon by rememberUpdatedState(longitude)
    val latestTraficom by rememberUpdatedState(traficomPlanningRasterEnabled)
    val latestStormRadar by rememberUpdatedState(stormRadarOverlay)
    val latestLightning by rememberUpdatedState(lightningStrikes)
    val latestHarbors by rememberUpdatedState(harbors)

    val zoomInDesc = stringResource(R.string.map_zoom_in)
    val zoomOutDesc = stringResource(R.string.map_zoom_out)
    val myLocationDesc = stringResource(R.string.map_my_location)

    val scaleRunnable =
        remember(mapView) {
            Runnable {
                val map = mapRef ?: return@Runnable
                val view = mapView
                val w = view.width
                val h = view.height
                if (w < 8 || h < 8) return@Runnable
                val oneCmPx =
                    TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_MM,
                        10f,
                        view.context.resources.displayMetrics,
                    )
                val cx = w / 2f
                val cy = h / 2f
                val x1 = cx - oneCmPx / 2f
                val x2 = cx + oneCmPx / 2f
                val ll1 = map.projection.fromScreenLocation(PointF(x1, cy))
                val ll2 = map.projection.fromScreenLocation(PointF(x2, cy))
                metersPerScreenCm = ll1.distanceTo(ll2)
            }
        }

    val cameraIdleListener =
        remember(mapView, scaleRunnable) {
            MapLibreMap.OnCameraIdleListener {
                mapView.removeCallbacks(scaleRunnable)
                mapView.postDelayed(scaleRunnable, SCALE_DEBOUNCE_MS)
            }
        }

    DisposableEffect(lifecycle, mapView) {
        val observer =
            LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> mapView.onStart()
                    Lifecycle.Event.ON_RESUME -> mapView.onResume()
                    Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                    Lifecycle.Event.ON_STOP -> mapView.onStop()
                    else -> Unit
                }
            }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapRef?.removeOnCameraIdleListener(cameraIdleListener)
            mapView.removeCallbacks(scaleRunnable)
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = {
                mapView.apply {
                    onCreate(null)
                    // Defer GL/style work until after first layout — smoother Compose attach on cold start.
                    post {
                        getMapAsync { map ->
                        mapRef = map
                        map.uiSettings.isAttributionEnabled = true
                        map.uiSettings.isLogoEnabled = true
                        map.uiSettings.isQuickZoomGesturesEnabled = true
                        map.addOnCameraIdleListener(cameraIdleListener)
                        map.setStyle(Style.Builder().fromUri(STYLE_URL)) { style ->
                            applyAllMapOverlays(
                                style = style,
                                routeGeometry = latestRouteGeometry,
                                routeStart = latestRouteStart,
                                routeEnd = latestRouteEnd,
                                latitude = latestLat,
                                longitude = latestLon,
                                traficomPlanningRasterEnabled = latestTraficom,
                                harbors = latestHarbors,
                            )
                            styleReady = true
                        }
                        map.addOnMapLongClickListener { latLng ->
                            latestLongPress(latLng.latitude, latLng.longitude)
                            true
                        }
                        map.addOnMapClickListener { latLng ->
                            val cb = latestMapClick
                            if (cb != null) {
                                cb(latLng.latitude, latLng.longitude)
                                true
                            } else {
                                false
                            }
                        }
                        }
                    }
                }
            },
            update = { },
        )

        if (isStormMap) {
            LaunchedEffect(mapRef, styleReady, stormRadarOverlay, lightningStrikes, latitude, longitude) {
                val map = mapRef ?: return@LaunchedEffect
                if (!styleReady) return@LaunchedEffect
                delay(STORM_STYLE_DEBOUNCE_MS)
                val overlay = stormRadarOverlay
                val strikes = lightningStrikes
                mapView.post {
                    map.getStyle { style ->
                        try {
                            ensureStormRadar(style, overlay)
                            updateLightningLayer(style, strikes)
                            updatePin(style, latitude, longitude)
                        } catch (e: Exception) {
                            Log.e(TAG, "Storm map style update failed", e)
                        }
                    }
                }
            }
        } else {
            LaunchedEffect(mapRef, traficomPlanningRasterEnabled, routeGeometry, routeStart, routeEnd, latitude, longitude) {
                val map = mapRef ?: return@LaunchedEffect
                val traficomToggled =
                    prevTraficomRaster != null && prevTraficomRaster != traficomPlanningRasterEnabled
                prevTraficomRaster = traficomPlanningRasterEnabled
                map.getStyle { style ->
                    ensureTraficomRaster(style, traficomPlanningRasterEnabled)
                    val rasterAnchor = bottomRasterAnchorLayerId(style)
                    updateRoute(
                        style,
                        routeGeometry,
                        insertAboveLayerId = rasterAnchor,
                        forceReorder = traficomToggled,
                    )
                    updateRouteMarkers(style, routeStart, routeEnd)
                    updatePin(style, latitude, longitude)
                }
            }
            LaunchedEffect(mapRef, styleReady, stormRadarOverlay, lightningStrikes) {
                val map = mapRef ?: return@LaunchedEffect
                if (!styleReady) return@LaunchedEffect
                delay(STORM_STYLE_DEBOUNCE_MS)
                map.getStyle { style ->
                    try {
                        ensureStormRadar(style, stormRadarOverlay)
                        updateLightningLayer(style, lightningStrikes)
                    } catch (e: Exception) {
                        Log.e(TAG, "Storm overlay update failed", e)
                    }
                }
            }
            LaunchedEffect(mapRef, latitude, longitude) {
                val map = mapRef ?: return@LaunchedEffect
                map.getStyle { style -> updatePin(style, latitude, longitude) }
            }
            LaunchedEffect(mapRef, harbors) {
                val map = mapRef ?: return@LaunchedEffect
                map.getStyle { style -> updateHarborLayer(style, harbors) }
            }
        }

        val mpcm = metersPerScreenCm
        if (mpcm != null && mpcm > 0.5) {
            val nmPerCm = GeoMath.metersToNauticalMiles(mpcm)
            val oneCmPx =
                TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_MM,
                    10f,
                    context.resources.displayMetrics,
                )
            val barWidthDp = with(density) { oneCmPx.toDp() }
            Row(
                modifier =
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(
                            start = 8.dp,
                            bottom = if (isStormMap) 118.dp else 56.dp,
                        )
                        .background(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                            shape = MaterialTheme.shapes.small,
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Canvas(
                    modifier =
                        Modifier
                            .width(barWidthDp)
                            .height(5.dp),
                ) {
                    drawLine(
                        color = Color(0xFF424242),
                        start = Offset(0f, size.height / 2f),
                        end = Offset(size.width, size.height / 2f),
                        strokeWidth = 3f,
                    )
                    drawLine(
                        color = Color(0xFF424242),
                        start = Offset(0f, 0f),
                        end = Offset(0f, size.height),
                        strokeWidth = 2f,
                    )
                    drawLine(
                        color = Color(0xFF424242),
                        start = Offset(size.width, 0f),
                        end = Offset(size.width, size.height),
                        strokeWidth = 2f,
                    )
                }
                Text(
                    text = stringResource(R.string.map_scale_1cm, nmPerCm),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        if (showZoomButtons || onMyLocation != null) {
            Column(
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 10.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (onMyLocation != null) {
                    FilledTonalIconButton(
                        onClick = onMyLocation,
                        modifier =
                            Modifier
                                .size(44.dp)
                                .semantics { contentDescription = myLocationDesc },
                    ) {
                        Icon(Icons.Default.MyLocation, contentDescription = null)
                    }
                }
                if (showZoomButtons) {
                    FilledTonalIconButton(
                        onClick = {
                            mapRef?.easeCamera(CameraUpdateFactory.zoomIn(), 220)
                        },
                        modifier =
                            Modifier
                                .size(44.dp)
                                .semantics { contentDescription = zoomInDesc },
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                    }
                    FilledTonalIconButton(
                        onClick = {
                            mapRef?.easeCamera(CameraUpdateFactory.zoomOut(), 220)
                        },
                        modifier =
                            Modifier
                                .size(44.dp)
                                .semantics { contentDescription = zoomOutDesc },
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = null)
                    }
                }
            }
        }
    }

    LaunchedEffect(mapRef, routeGeometry, latitude, longitude, mapRecenterSignal) {
        val map = mapRef ?: return@LaunchedEffect
        if (!styleReady && isStormMap) return@LaunchedEffect
        if (mapRecenterSignal > lastRecenterSignalHandled) {
            lastRecenterSignalHandled = mapRecenterSignal
            mapView.post {
                val target = LatLng(latitude, longitude)
                val z = map.cameraPosition.zoom
                val minZoom = if (isStormMap) STORM_MAP_MIN_ZOOM else MIN_KEEP_ZOOM
                val keepZoom =
                    if (z.isFinite() && z >= 2f) {
                        z.toDouble().coerceIn(minZoom, 18.0)
                    } else if (isStormMap) {
                        STORM_MAP_ZOOM
                    } else {
                        DEFAULT_ZOOM
                    }
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(target, keepZoom), 450)
            }
            return@LaunchedEffect
        }
        if (routeGeometry.size >= 2) {
            mapView.post {
                val b = LatLngBounds.Builder()
                for ((lat, lon) in routeGeometry) {
                    if (lat.isFinite() && lon.isFinite()) {
                        b.include(LatLng(lat, lon))
                    }
                }
                val bounds =
                    try {
                        b.build()
                    } catch (_: Exception) {
                        return@post
                    }
                val padPx = (44f * mapView.context.resources.displayMetrics.density).toInt().coerceIn(32, 120)
                try {
                    map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, padPx), 650)
                } catch (_: IllegalArgumentException) {
                    val c = bounds.center
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(c, DEFAULT_ZOOM), 450)
                }
            }
            initialCameraFramed = true
        } else if (!initialCameraFramed) {
            val zoom =
                when {
                    isStormMap -> initialZoom ?: STORM_MAP_ZOOM
                    initialZoom != null -> initialZoom
                    else -> DEFAULT_ZOOM
                }
            map.moveCamera(
                CameraUpdateFactory.newLatLngZoom(LatLng(latitude, longitude), zoom),
            )
            initialCameraFramed = true
        } else if (!isStormMap) {
            val target = LatLng(latitude, longitude)
            val z = map.cameraPosition.zoom
            val keepZoom =
                if (z.isFinite() && z >= 2f) {
                    z.toDouble().coerceIn(MIN_KEEP_ZOOM, 18.0)
                } else {
                    DEFAULT_ZOOM
                }
            map.easeCamera(CameraUpdateFactory.newLatLngZoom(target, keepZoom), 450)
        }
    }
}

/** Full overlay pass after remote style JSON has loaded (initial load only from setStyle callback). */
private fun applyAllMapOverlays(
    style: Style,
    routeGeometry: List<Pair<Double, Double>>,
    routeStart: Pair<Double, Double>?,
    routeEnd: Pair<Double, Double>?,
    latitude: Double,
    longitude: Double,
    traficomPlanningRasterEnabled: Boolean,
    harbors: List<Harbor>,
) {
    ensureTraficomRaster(style, traficomPlanningRasterEnabled)
    val rasterAnchor = bottomRasterAnchorLayerId(style)
    updateRoute(style, routeGeometry, insertAboveLayerId = rasterAnchor)
    updateRouteMarkers(style, routeStart, routeEnd)
    updatePin(style, latitude, longitude)
    updateHarborLayer(style, harbors)
}

/** Topmost raster under vector overlays: FMI radar if present, else Traficom. */
private fun bottomRasterAnchorLayerId(style: Style): String? =
    when {
        style.getLayer(STORM_RADAR_TILE_LAYER_ID) != null -> STORM_RADAR_TILE_LAYER_ID
        style.getLayer(STORM_RADAR_IMAGE_LAYER_ID) != null -> STORM_RADAR_IMAGE_LAYER_ID
        style.getLayer(STORM_RADAR_IMAGE_LAYER_B_ID) != null -> STORM_RADAR_IMAGE_LAYER_B_ID
        style.getLayer(STORM_RADAR_IMAGE_LAYER_A_ID) != null -> STORM_RADAR_IMAGE_LAYER_A_ID
        style.getLayer(TRAFICOM_LAYER_ID) != null -> TRAFICOM_LAYER_ID
        else -> null
    }

/** Only adds/removes the heavy WMTS raster when the toggle actually changes — never on pan/pin updates. */
private fun ensureTraficomRaster(
    style: Style,
    enabled: Boolean,
) {
    val present = style.getLayer(TRAFICOM_LAYER_ID) != null
    when {
        enabled && !present -> addTraficomPlanningLayer(style)
        !enabled && present -> removeIfPresent(style, TRAFICOM_LAYER_ID, TRAFICOM_SOURCE_ID)
        else -> Unit
    }
}

private fun ensureStormRadar(
    style: Style,
    overlay: ActiveRadarOverlay?,
) {
    if (overlay == null) {
        removeIfPresent(style, STORM_RADAR_TILE_LAYER_ID, STORM_RADAR_TILE_SOURCE_ID)
        removeStormGeoRadar(style)
        return
    }
    when (overlay.kind) {
        RadarDisplayKind.WMS_TILES -> {
            val url = overlay.wmsTileUrlTemplate ?: return
            removeStormGeoRadar(style)
            val existingUrl =
                (style.getSource(STORM_RADAR_TILE_SOURCE_ID) as? RasterSource)?.uri
            if (existingUrl == url && style.getLayer(STORM_RADAR_TILE_LAYER_ID) != null) {
                return
            }
            removeIfPresent(style, STORM_RADAR_TILE_LAYER_ID, STORM_RADAR_TILE_SOURCE_ID)
            addStormRadarTileLayer(style, url)
        }
        RadarDisplayKind.GEO_IMAGE -> {
            val imageUrl = overlay.geoImageUrl ?: return
            val bounds = overlay.geoBounds ?: return
            // Forecast raster only — persistence tiles made every future frame look identical.
            removeIfPresent(style, STORM_RADAR_TILE_LAYER_ID, STORM_RADAR_TILE_SOURCE_ID)
            if (imageUrl == stormGeoLastImageUrl && style.getLayer(STORM_RADAR_IMAGE_LAYER_ID) != null) {
                return
            }
            if (StormGeoRadarOverlay.updateGeoImageUri(style, STORM_RADAR_IMAGE_SOURCE_ID, imageUrl) &&
                style.getLayer(STORM_RADAR_IMAGE_LAYER_ID) != null
            ) {
                stormGeoLastImageUrl = imageUrl
                return
            }
            removeStormGeoRadar(style)
            addStormRadarImageLayer(
                style,
                STORM_RADAR_IMAGE_SOURCE_ID,
                STORM_RADAR_IMAGE_LAYER_ID,
                imageUrl,
                bounds,
                aboveLayerId = if (style.getLayer(TRAFICOM_LAYER_ID) != null) TRAFICOM_LAYER_ID else null,
            )
            stormGeoLastImageUrl = imageUrl
        }
    }
}

private fun removeStormGeoRadar(style: Style) {
    stormGeoLastImageUrl = null
    removeIfPresent(style, STORM_RADAR_IMAGE_LAYER_ID, STORM_RADAR_IMAGE_SOURCE_ID)
    removeIfPresent(style, STORM_RADAR_IMAGE_LAYER_A_ID, STORM_RADAR_IMAGE_SOURCE_A_ID)
    removeIfPresent(style, STORM_RADAR_IMAGE_LAYER_B_ID, STORM_RADAR_IMAGE_SOURCE_B_ID)
}

private fun addStormRadarTileLayer(
    style: Style,
    tileUrl: String,
) {
    if (style.getSource(STORM_RADAR_TILE_SOURCE_ID) != null) {
        Log.w(TAG, "Storm tile source already present, skip add")
        return
    }
    val tileSet =
        TileSet("2.2.0", tileUrl).also { ts ->
            ts.setMinZoom(3f)
            ts.setMaxZoom(14f)
            ts.setBounds(*FmiRadarConfig.TILE_BOUNDS)
            ts.attribution = FmiRadarConfig.ATTRIBUTION
        }
    style.addSource(RasterSource(STORM_RADAR_TILE_SOURCE_ID, tileSet, FmiRadarConfig.TILE_SIZE))
    val layer =
        RasterLayer(STORM_RADAR_TILE_LAYER_ID, STORM_RADAR_TILE_SOURCE_ID).withProperties(
            PropertyFactory.rasterOpacity(STORM_RADAR_OPACITY),
            PropertyFactory.rasterFadeDuration(STORM_RADAR_FADE_MS),
        )
    if (style.getLayer(TRAFICOM_LAYER_ID) != null) {
        style.addLayerAbove(layer, TRAFICOM_LAYER_ID)
    } else {
        style.addLayer(layer)
    }
}

private fun addStormRadarImageLayer(
    style: Style,
    sourceId: String,
    layerId: String,
    imageUrl: String,
    bounds: fi.veneappi.app.data.radar.RadarGeoBounds,
    aboveLayerId: String?,
) {
    val above =
        aboveLayerId?.takeIf { style.getLayer(it) != null }
            ?: if (style.getLayer(TRAFICOM_LAYER_ID) != null) TRAFICOM_LAYER_ID else null
    try {
        StormGeoRadarOverlay.addGeoImage(
            style,
            sourceId,
            layerId,
            imageUrl,
            bounds.northLat,
            bounds.westLon,
            bounds.southLat,
            bounds.eastLon,
            STORM_RADAR_OPACITY,
            above,
        )
    } catch (e: Exception) {
        Log.e(TAG, "Failed to add storm geo radar image", e)
    }
}

private fun updateLightningLayer(
    style: Style,
    strikes: List<LightningStrike>,
) {
    if (strikes.isEmpty()) {
        if (style.getLayer(LIGHTNING_SMHI_LAYER_ID) != null) {
            style.removeLayer(LIGHTNING_SMHI_LAYER_ID)
        }
        removeIfPresent(style, LIGHTNING_FMI_LAYER_ID, LIGHTNING_SOURCE_ID)
        return
    }
    val features =
        strikes.map { s ->
            Feature.fromGeometry(Point.fromLngLat(s.longitude, s.latitude)).also { f ->
                f.addStringProperty("source", s.source.name.lowercase())
            }
        }
    val fc = FeatureCollection.fromFeatures(features)
    val existing = style.getSource(LIGHTNING_SOURCE_ID) as? GeoJsonSource
    if (existing != null &&
        style.getLayer(LIGHTNING_FMI_LAYER_ID) != null &&
        style.getLayer(LIGHTNING_SMHI_LAYER_ID) != null
    ) {
        existing.setGeoJson(fc)
        return
    }
    removeIfPresent(style, LIGHTNING_FMI_LAYER_ID, LIGHTNING_SOURCE_ID)
    if (style.getLayer(LIGHTNING_SMHI_LAYER_ID) != null) {
        style.removeLayer(LIGHTNING_SMHI_LAYER_ID)
    }
    if (style.getSource(LIGHTNING_SOURCE_ID) != null) {
        style.removeSource(LIGHTNING_SOURCE_ID)
    }
    style.addSource(GeoJsonSource(LIGHTNING_SOURCE_ID, fc))
    val anchor =
        sequenceOf(
            ROUTE_END_LAYER_ID,
            ROUTE_START_LAYER_ID,
            ROUTE_SLOTS_LAYER_ID,
            ROUTE_LAYER_ID,
            STORM_RADAR_TILE_LAYER_ID,
            STORM_RADAR_IMAGE_LAYER_ID,
            STORM_RADAR_IMAGE_LAYER_B_ID,
            STORM_RADAR_IMAGE_LAYER_A_ID,
            TRAFICOM_LAYER_ID,
        ).firstOrNull { style.getLayer(it) != null }
    val fmiFilter =
        Expression.eq(
            Expression.get("source"),
            Expression.literal(LightningSourceId.FMI.name.lowercase()),
        )
    val smhiFilter =
        Expression.eq(
            Expression.get("source"),
            Expression.literal(LightningSourceId.SMHI.name.lowercase()),
        )
    val fmiLayer =
        CircleLayer(LIGHTNING_FMI_LAYER_ID, LIGHTNING_SOURCE_ID)
            .withFilter(fmiFilter)
            .withProperties(
                PropertyFactory.circleRadius(7f),
                PropertyFactory.circleColor("#FFEB3B"),
                PropertyFactory.circleOpacity(0.92f),
                PropertyFactory.circleStrokeColor("#FFFFFF"),
                PropertyFactory.circleStrokeWidth(2f),
            )
    val smhiLayer =
        CircleLayer(LIGHTNING_SMHI_LAYER_ID, LIGHTNING_SOURCE_ID)
            .withFilter(smhiFilter)
            .withProperties(
                PropertyFactory.circleRadius(7f),
                PropertyFactory.circleColor("#FF9800"),
                PropertyFactory.circleOpacity(0.92f),
                PropertyFactory.circleStrokeColor("#FFFFFF"),
                PropertyFactory.circleStrokeWidth(2f),
            )
    if (anchor != null) {
        style.addLayerAbove(fmiLayer, anchor)
        style.addLayerAbove(smhiLayer, LIGHTNING_FMI_LAYER_ID)
    } else {
        style.addLayer(fmiLayer)
        style.addLayerAbove(smhiLayer, LIGHTNING_FMI_LAYER_ID)
    }
}

private fun addTraficomPlanningLayer(style: Style) {
    val tileSet =
        TileSet("2.2.0", TRAFICOM_TILE_URL).also { ts ->
            ts.setMinZoom(5f)
            ts.setMaxZoom(18f)
            ts.setBounds(*floatArrayOf(17f, 58f, 32f, 71f))
            ts.attribution =
                "Traficom open nautical raster (CC BY 4.0). Not for navigational use."
        }
    style.addSource(RasterSource(TRAFICOM_SOURCE_ID, tileSet, 256))
    val rasterLayer =
        RasterLayer(TRAFICOM_LAYER_ID, TRAFICOM_SOURCE_ID).withProperties(
            PropertyFactory.rasterOpacity(1f),
        )
    style.addLayer(rasterLayer)
}

private fun updateRouteMarkers(
    style: Style,
    start: Pair<Double, Double>?,
    end: Pair<Double, Double>?,
) {
    val hasRouteLine = style.getLayer(ROUTE_LAYER_ID) != null
    val slotPresent = style.getLayer(ROUTE_SLOTS_LAYER_ID) != null
    val aboveForStart =
        when {
            slotPresent -> ROUTE_SLOTS_LAYER_ID
            hasRouteLine -> ROUTE_LAYER_ID
            else -> null
        }
    syncRouteEndpointMarker(
        style = style,
        coordinate = start,
        sourceId = ROUTE_START_SOURCE_ID,
        layerId = ROUTE_START_LAYER_ID,
        colorHex = "#2E7D32",
        addAboveLayerId = aboveForStart,
    )
    val aboveForEnd =
        when {
            style.getLayer(ROUTE_START_LAYER_ID) != null -> ROUTE_START_LAYER_ID
            slotPresent -> ROUTE_SLOTS_LAYER_ID
            hasRouteLine -> ROUTE_LAYER_ID
            else -> null
        }
    syncRouteEndpointMarker(
        style = style,
        coordinate = end,
        sourceId = ROUTE_END_SOURCE_ID,
        layerId = ROUTE_END_LAYER_ID,
        colorHex = "#1565C0",
        addAboveLayerId = aboveForEnd,
    )
}

private fun syncRouteEndpointMarker(
    style: Style,
    coordinate: Pair<Double, Double>?,
    sourceId: String,
    layerId: String,
    colorHex: String,
    addAboveLayerId: String?,
) {
    if (coordinate == null) {
        removeIfPresent(style, layerId, sourceId)
        return
    }
    val feature = Feature.fromGeometry(Point.fromLngLat(coordinate.second, coordinate.first))
    val existing = style.getSource(sourceId) as? GeoJsonSource
    if (existing == null) {
        removeIfPresent(style, layerId, sourceId)
        style.addSource(GeoJsonSource(sourceId, feature))
    } else {
        existing.setGeoJson(feature)
    }
    if (style.getLayer(layerId) != null) {
        style.removeLayer(layerId)
    }
    val layer =
        CircleLayer(layerId, sourceId).withProperties(
            PropertyFactory.circleRadius(10f),
            PropertyFactory.circleColor(colorHex),
            PropertyFactory.circleStrokeColor("#FFFFFF"),
            PropertyFactory.circleStrokeWidth(2f),
        )
    if (addAboveLayerId != null && style.getLayer(addAboveLayerId) != null) {
        style.addLayerAbove(layer, addAboveLayerId)
    } else {
        style.addLayer(layer)
    }
}

private fun updatePin(
    style: Style,
    lat: Double,
    lon: Double,
) {
    val feature = Feature.fromGeometry(Point.fromLngLat(lon, lat))
    val existing = style.getSource(PIN_SOURCE_ID) as? GeoJsonSource
    if (existing == null) {
        removeIfPresent(style, PIN_LAYER_ID, PIN_SOURCE_ID)
        style.addSource(GeoJsonSource(PIN_SOURCE_ID, feature))
    } else {
        existing.setGeoJson(feature)
    }
    if (style.getLayer(PIN_LAYER_ID) != null) {
        style.removeLayer(PIN_LAYER_ID)
    }
    val layer =
        CircleLayer(PIN_LAYER_ID, PIN_SOURCE_ID).withProperties(
            PropertyFactory.circleRadius(7f),
            PropertyFactory.circleColor("#D32F2F"),
            PropertyFactory.circleStrokeColor("#FFFFFF"),
            PropertyFactory.circleStrokeWidth(2f),
        )
    val anchor =
        sequenceOf(
            ROUTE_END_LAYER_ID,
            ROUTE_START_LAYER_ID,
            ROUTE_SLOTS_LAYER_ID,
            ROUTE_LAYER_ID,
            LIGHTNING_SMHI_LAYER_ID,
            LIGHTNING_FMI_LAYER_ID,
            STORM_RADAR_TILE_LAYER_ID,
            STORM_RADAR_IMAGE_LAYER_ID,
            STORM_RADAR_IMAGE_LAYER_B_ID,
            STORM_RADAR_IMAGE_LAYER_A_ID,
            TRAFICOM_LAYER_ID,
        ).firstOrNull { style.getLayer(it) != null }
    if (anchor != null) {
        style.addLayerAbove(layer, anchor)
    } else {
        style.addLayer(layer)
    }
}

private fun updateHarborLayer(
    style: Style,
    harbors: List<Harbor>,
) {
    if (harbors.isEmpty()) {
        removeIfPresent(style, HARBOR_LAYER_ID, HARBOR_SOURCE_ID)
        return
    }
    val features =
        harbors.map { h ->
            Feature.fromGeometry(Point.fromLngLat(h.longitude, h.latitude)).also { f ->
                f.addStringProperty("name", h.name)
                f.addStringProperty("kind", h.kind.name)
            }
        }
    val fc = FeatureCollection.fromFeatures(features)
    val existing = style.getSource(HARBOR_SOURCE_ID) as? GeoJsonSource
    if (existing != null && style.getLayer(HARBOR_LAYER_ID) != null) {
        existing.setGeoJson(fc)
        return
    }
    removeIfPresent(style, HARBOR_LAYER_ID, HARBOR_SOURCE_ID)
    style.addSource(GeoJsonSource(HARBOR_SOURCE_ID, fc))
    val layer =
        CircleLayer(HARBOR_LAYER_ID, HARBOR_SOURCE_ID).withProperties(
            PropertyFactory.circleRadius(6f),
            PropertyFactory.circleColor("#1565C0"),
            PropertyFactory.circleOpacity(0.9f),
            PropertyFactory.circleStrokeColor("#FFFFFF"),
            PropertyFactory.circleStrokeWidth(1.5f),
        )
    if (style.getLayer(PIN_LAYER_ID) != null) {
        style.addLayerBelow(layer, PIN_LAYER_ID)
    } else {
        style.addLayer(layer)
    }
}

private fun updateRoute(
    style: Style,
    routeGeometry: List<Pair<Double, Double>>,
    insertAboveLayerId: String?,
    forceReorder: Boolean = false,
) {
    if (routeGeometry.size < 2) {
        removeIfPresent(style, ROUTE_SLOTS_LAYER_ID, ROUTE_SLOTS_SOURCE_ID)
        removeIfPresent(style, ROUTE_LAYER_ID, ROUTE_SOURCE_ID)
        return
    }
    val finite =
        routeGeometry
            .filter { (la, lo) -> la.isFinite() && lo.isFinite() }
            .fold(ArrayList<Pair<Double, Double>>(routeGeometry.size)) { acc, p ->
                if (acc.isEmpty() || acc.last() != p) acc.add(p)
                acc
            }
    if (finite.size < 2) {
        removeIfPresent(style, ROUTE_SLOTS_LAYER_ID, ROUTE_SLOTS_SOURCE_ID)
        removeIfPresent(style, ROUTE_LAYER_ID, ROUTE_SOURCE_ID)
        return
    }
    val points =
        finite.map { coord ->
            Point.fromLngLat(coord.second, coord.first)
        }
    val line = LineString.fromLngLats(points)
    val feature = Feature.fromGeometry(line)

    val existing = style.getSource(ROUTE_SOURCE_ID) as? GeoJsonSource
    if (existing != null && style.getLayer(ROUTE_LAYER_ID) != null && !forceReorder) {
        existing.setGeoJson(feature)
        updateRouteSlotMarkers(style, finite)
        return
    }
    removeIfPresent(style, ROUTE_SLOTS_LAYER_ID, ROUTE_SLOTS_SOURCE_ID)
    removeIfPresent(style, ROUTE_LAYER_ID, ROUTE_SOURCE_ID)

    val source = GeoJsonSource(ROUTE_SOURCE_ID, feature)
    style.addSource(source)
    val lineLayer =
        LineLayer(ROUTE_LAYER_ID, ROUTE_SOURCE_ID).withProperties(
            PropertyFactory.lineColor("#FF6D00"),
            PropertyFactory.lineWidth(6f),
            PropertyFactory.lineOpacity(1f),
            PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        )
    val anchor = insertAboveLayerId?.takeIf { style.getLayer(it) != null }
    if (anchor != null) {
        style.addLayerAbove(lineLayer, anchor)
    } else {
        style.addLayer(lineLayer)
    }
    updateRouteSlotMarkers(style, finite)
}

/** Small dots at ~⅓ and ~⅔ path length — same positions as route weather forecast slots. */
private fun updateRouteSlotMarkers(
    style: Style,
    routePoints: List<Pair<Double, Double>>,
) {
    if (routePoints.size < 2 || style.getLayer(ROUTE_LAYER_ID) == null) {
        removeIfPresent(style, ROUTE_SLOTS_LAYER_ID, ROUTE_SLOTS_SOURCE_ID)
        return
    }
    val pThird = GeoMath.pointAlongPolyline(routePoints, 1.0 / 3.0)
    val pTwoThird = GeoMath.pointAlongPolyline(routePoints, 2.0 / 3.0)
    val fc =
        FeatureCollection.fromFeatures(
            listOf(
                Feature.fromGeometry(Point.fromLngLat(pThird.second, pThird.first)),
                Feature.fromGeometry(Point.fromLngLat(pTwoThird.second, pTwoThird.first)),
            ),
        )
    val existing = style.getSource(ROUTE_SLOTS_SOURCE_ID) as? GeoJsonSource
    if (existing != null && style.getLayer(ROUTE_SLOTS_LAYER_ID) != null) {
        existing.setGeoJson(fc)
        return
    }
    removeIfPresent(style, ROUTE_SLOTS_LAYER_ID, ROUTE_SLOTS_SOURCE_ID)
    style.addSource(GeoJsonSource(ROUTE_SLOTS_SOURCE_ID, fc))
    val slotLayer =
        CircleLayer(ROUTE_SLOTS_LAYER_ID, ROUTE_SLOTS_SOURCE_ID).withProperties(
            PropertyFactory.circleRadius(5f),
            PropertyFactory.circleColor("#FFFFFF"),
            PropertyFactory.circleOpacity(0.95f),
            PropertyFactory.circleStrokeColor("#FF6D00"),
            PropertyFactory.circleStrokeWidth(2f),
        )
    style.addLayerAbove(slotLayer, ROUTE_LAYER_ID)
}

private fun removeIfPresent(
    style: Style,
    layerId: String,
    sourceId: String,
) {
    if (style.getLayer(layerId) != null) {
        style.removeLayer(layerId)
    }
    if (style.getSource(sourceId) != null) {
        style.removeSource(sourceId)
    }
}
