package fi.veneappi.app.ui

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import fi.veneappi.app.AppContainer
import fi.veneappi.app.billing.PremiumAccess
import fi.veneappi.app.data.harbors.OverpassHarborClient
import fi.veneappi.app.data.routing.OsrmClient
import fi.veneappi.app.domain.Harbor
import fi.veneappi.app.data.routing.VaylaFairwayRouter
import fi.veneappi.app.domain.ForecastSampler
import fi.veneappi.app.domain.GeoMath
import fi.veneappi.app.domain.RouteSourceWeatherSlots
import fi.veneappi.app.domain.SourceId
import fi.veneappi.app.domain.UnifiedForecast
import fi.veneappi.app.domain.UnifiedTimePoint
import fi.veneappi.app.domain.WindUnit
import fi.veneappi.app.data.net.NetworkConnectivityMonitor
import fi.veneappi.app.data.net.WeatherRepository
import fi.veneappi.app.data.offline.OfflineAreaPackDownloader
import fi.veneappi.app.data.prefs.UserPreferencesRepository
import fi.veneappi.app.domain.ForecastFreshness
import fi.veneappi.app.domain.ForecastStaleLevel
import fi.veneappi.app.review.PlayInAppReviewCoordinator
import fi.veneappi.app.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

enum class RoutePickMode {
    None,
    Start,
    End,
}

data class VeneappiUiState(
    /** Oletus: Suomenlinna (Helsinki), päivittyy GPS:stä / emulaattorin sijainnista kun lupa annettu. */
    val latitude: Double = 60.1453,
    val longitude: Double = 24.9884,
    val forecasts: Map<SourceId, Result<UnifiedForecast>> = emptyMap(),
    val loadingWeather: Boolean = false,
    val routeStart: Pair<Double, Double>? = null,
    val routeEnd: Pair<Double, Double>? = null,
    val routeGeometry: List<Pair<Double, Double>> = emptyList(),
    val boatSpeedKn: Double = 6.0,
    val routePickMode: RoutePickMode = RoutePickMode.None,
    val routeError: String? = null,
    /** True when both endpoints are set but Väylä navigointilinjat -reittiä ei saatu; näytetään isoympyrä tai OSRM-demo. */
    val routeFairwayUnavailable: Boolean = false,
    /** True while fairway geometry is being resolved after both endpoints are set. */
    val routeComputingFairway: Boolean = false,
    /** Kasvaa jokaisella "oma sijainti" -napilla, jotta kartta keskitetään vaikka koordinaatit eivät muuttuisi. */
    val mapRecenterSignal: Long = 0L,
    val routeWeatherBySource: Map<SourceId, Result<RouteSourceWeatherSlots>> = emptyMap(),
    val routeWeatherLegNm: Double? = null,
    val routeWeatherEtaHours: Double? = null,
    val loadingRouteWeather: Boolean = false,
)

data class HarborsUiState(
    val items: List<Harbor> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
)

class MainViewModel(
    private val weatherRepository: WeatherRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val overpassHarborClient: OverpassHarborClient,
    private val premiumAccess: PremiumAccess,
    private val networkConnectivityMonitor: NetworkConnectivityMonitor,
    private val offlineAreaPackDownloader: OfflineAreaPackDownloader,
    private val playInAppReviewCoordinator: PlayInAppReviewCoordinator,
) : ViewModel() {
    private val _ui = MutableStateFlow(VeneappiUiState())
    val ui: StateFlow<VeneappiUiState> = _ui.asStateFlow()

    private val _harborsUi = MutableStateFlow(HarborsUiState())
    val harborsUi: StateFlow<HarborsUiState> = _harborsUi.asStateFlow()

    private val _weatherLoadMeta = MutableStateFlow(WeatherConnectivityStatus())
    private val _offlinePackUi = MutableStateFlow(OfflinePackUiState())
    val offlinePackUi: StateFlow<OfflinePackUiState> = _offlinePackUi.asStateFlow()

    val weatherConnectivityStatus: StateFlow<WeatherConnectivityStatus> =
        combine(
            networkConnectivityMonitor.isOnline,
            _ui,
            _weatherLoadMeta,
        ) { online, uiState, meta ->
            val forecasts = uiState.forecasts
            val oldest =
                ForecastFreshness.oldestFetchedUtc(forecasts) ?: meta.oldestFetchedUtc
            val stale =
                oldest?.let { ForecastFreshness.staleLevel(it) }
                    ?: ForecastStaleLevel.Fresh
            WeatherConnectivityStatus(
                isOnline = online,
                anyFromCache = meta.anyFromCache,
                allSourcesFailed =
                    forecasts.isNotEmpty() && forecasts.values.all { it.isFailure },
                staleLevel = stale,
                oldestFetchedUtc = oldest,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = WeatherConnectivityStatus(),
        )

    private var routeWeatherJob: Job? = null

    val windUnit: StateFlow<WindUnit> =
        userPreferencesRepository.windUnit.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = WindUnit.MetersPerSecond,
        )

    val weatherSource: StateFlow<SourceId> =
        userPreferencesRepository.weatherSource.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = SourceId.MET_NORWAY,
        )

    init {
        viewModelScope.launch {
            premiumAccess.isPremium.collect { premium ->
                if (!premium) {
                    clearRoute()
                } else {
                    scheduleRouteWeatherRefresh()
                }
            }
        }
        refreshWeather()
    }

    fun refreshWeather() {
        viewModelScope.launch {
            val lat = _ui.value.latitude
            val lon = _ui.value.longitude
            _ui.value = _ui.value.copy(loadingWeather = true)
            val report = weatherRepository.loadAllWithReport(lat, lon)
            val results = report.forecasts()
            val oldest = ForecastFreshness.oldestFetchedUtc(results)
            _weatherLoadMeta.value =
                WeatherConnectivityStatus(
                    isOnline = networkConnectivityMonitor.isOnline.value,
                    anyFromCache = report.anyServedFromCache,
                    allSourcesFailed = report.allSourcesFailed,
                    staleLevel =
                        oldest?.let { ForecastFreshness.staleLevel(it) }
                            ?: ForecastStaleLevel.Fresh,
                    oldestFetchedUtc = oldest,
                )
            _ui.value =
                _ui.value.copy(
                    forecasts = results,
                    loadingWeather = false,
                )
            if (!report.allSourcesFailed) {
                playInAppReviewCoordinator.onPositiveEngagement()
            }
        }
    }

    fun downloadOfflinePackForRoute(marineTitlesByCountry: Map<String, String>) {
        val geom = _ui.value.routeGeometry
        if (geom.size < 2 || _offlinePackUi.value.downloading) return
        viewModelScope.launch {
            _offlinePackUi.value = OfflinePackUiState(downloading = true)
            try {
                val result =
                    offlineAreaPackDownloader.downloadRoutePack(
                        routeGeometry = geom,
                        marineTitlesByCountry = marineTitlesByCountry,
                    ) { progress ->
                        _offlinePackUi.value =
                            OfflinePackUiState(
                                downloading = true,
                                stepKey = progress.stepKey,
                                current = progress.current,
                                total = progress.total,
                            )
                    }
                _offlinePackUi.value =
                    OfflinePackUiState(
                        lastSuccessWeatherSamples = result.weatherSamples,
                        lastSuccessRouteVertices = result.routeVertices,
                    )
            } catch (_: Exception) {
                _offlinePackUi.value = OfflinePackUiState(lastFailed = true)
            }
        }
    }

    fun setMapLocation(
        lat: Double,
        lon: Double,
    ) {
        _ui.value = _ui.value.copy(latitude = lat, longitude = lon)
        refreshWeather()
    }

    /** Moves map center to a fresh device location (GPS). [onSuccess] runs on the main thread after recenter. */
    fun recenterToDeviceLocation(
        context: Context,
        onSuccess: (() -> Unit)? = null,
    ) {
        val app = context.applicationContext
        if (!hasLocationPermission(context)) {
            Toast.makeText(app, app.getString(R.string.map_no_gps_fix), Toast.LENGTH_SHORT).show()
            return
        }
        viewModelScope.launch {
            val loc = readCurrentLatLon(context)
            if (loc == null) {
                Toast.makeText(app, app.getString(R.string.map_no_gps_fix), Toast.LENGTH_SHORT).show()
                return@launch
            }
            val prev = _ui.value
            _ui.value =
                prev.copy(
                    latitude = loc.first,
                    longitude = loc.second,
                    mapRecenterSignal = prev.mapRecenterSignal + 1L,
                )
            refreshWeather()
            onSuccess?.invoke()
        }
    }

    fun setWindUnit(unit: WindUnit) {
        viewModelScope.launch {
            userPreferencesRepository.setWindUnit(unit)
        }
    }

    fun setWeatherSource(source: SourceId) {
        viewModelScope.launch {
            userPreferencesRepository.setWeatherSource(source)
        }
    }

    fun setRoutePickMode(mode: RoutePickMode) {
        _ui.value = _ui.value.copy(routePickMode = mode, routeError = null)
    }

    fun onMapTapForRoute(lat: Double, lon: Double) {
        if (!premiumAccess.isPremium.value) return
        when (_ui.value.routePickMode) {
            RoutePickMode.Start -> {
                _ui.value =
                    _ui.value.copy(
                        routeStart = lat to lon,
                        routePickMode = RoutePickMode.End,
                        routeError = null,
                    )
                recomputeRouteGeometry()
            }
            RoutePickMode.End -> {
                _ui.value =
                    _ui.value.copy(
                        routeEnd = lat to lon,
                        routePickMode = RoutePickMode.None,
                        routeError = null,
                    )
                recomputeRouteGeometry()
            }
            RoutePickMode.None -> Unit
        }
    }

    /**
     * Reitti-välilehti: ensimmäinen pitkä painallus = alku, toinen = loppu.
     * Jos molemmat jo asetettu, seuraava pitkä painallus aloittaa uuden reitin (uusi alku).
     */
    fun onMapLongPressForRoute(
        lat: Double,
        lon: Double,
    ) {
        if (!premiumAccess.isPremium.value) return
        val s = _ui.value.routeStart
        val e = _ui.value.routeEnd
        when {
            s == null -> {
                _ui.value =
                    _ui.value.copy(
                        routeStart = lat to lon,
                        routeEnd = null,
                        routeGeometry = emptyList(),
                        routePickMode = RoutePickMode.None,
                        routeError = null,
                    )
                recomputeRouteGeometry()
            }
            e == null -> {
                _ui.value =
                    _ui.value.copy(
                        routeEnd = lat to lon,
                        routePickMode = RoutePickMode.None,
                        routeError = null,
                    )
                recomputeRouteGeometry()
            }
            else -> {
                _ui.value =
                    _ui.value.copy(
                        routeStart = lat to lon,
                        routeEnd = null,
                        routeGeometry = emptyList(),
                        routePickMode = RoutePickMode.None,
                        routeError = null,
                    )
                recomputeRouteGeometry()
            }
        }
    }

    fun refreshHarbors() {
        val lat = _ui.value.latitude
        val lon = _ui.value.longitude
        viewModelScope.launch {
            _harborsUi.value = _harborsUi.value.copy(loading = true, error = null)
            val result = overpassHarborClient.fetchInBbox(lat, lon)
            _harborsUi.value =
                result.fold(
                    onSuccess = { list ->
                        val sorted =
                            list.sortedBy { h ->
                                GeoMath.haversineMeters(lat, lon, h.latitude, h.longitude)
                            }
                        HarborsUiState(items = sorted, loading = false, error = null)
                    },
                    onFailure = { e ->
                        HarborsUiState(
                            items = _harborsUi.value.items,
                            loading = false,
                            error = e.message ?: "Overpass",
                        )
                    },
                )
        }
    }

    fun clearRoute() {
        routeWeatherJob?.cancel()
        _ui.value =
            _ui.value.copy(
                routeStart = null,
                routeEnd = null,
                routeGeometry = emptyList(),
                routePickMode = RoutePickMode.None,
                routeError = null,
                routeFairwayUnavailable = false,
                routeComputingFairway = false,
                routeWeatherBySource = emptyMap(),
                routeWeatherLegNm = null,
                routeWeatherEtaHours = null,
                loadingRouteWeather = false,
            )
    }

    fun setBoatSpeedKn(value: Double) {
        _ui.value = _ui.value.copy(boatSpeedKn = value.coerceIn(0.5, 40.0))
        scheduleRouteWeatherRefresh()
    }

    private fun scheduleRouteWeatherRefresh() {
        routeWeatherJob?.cancel()
        routeWeatherJob =
            viewModelScope.launch {
                delay(400)
                if (!premiumAccess.isPremium.value) {
                    val cur = _ui.value
                    _ui.value =
                        cur.copy(
                            routeWeatherBySource = emptyMap(),
                            routeWeatherLegNm = null,
                            routeWeatherEtaHours = null,
                            loadingRouteWeather = false,
                        )
                    return@launch
                }
                val snap = _ui.value
                if (snap.routeStart == null || snap.routeEnd == null) {
                    _ui.value =
                        snap.copy(
                            routeWeatherBySource = emptyMap(),
                            routeWeatherLegNm = null,
                            routeWeatherEtaHours = null,
                            loadingRouteWeather = false,
                        )
                    return@launch
                }
                val geom =
                    if (snap.routeGeometry.size >= 2) {
                        snap.routeGeometry
                    } else {
                        listOf(snap.routeStart!!, snap.routeEnd!!)
                    }
                val totalM = GeoMath.polylineLengthMeters(geom)
                val totalNm = GeoMath.metersToNauticalMiles(totalM)
                val speedKn = snap.boatSpeedKn.coerceIn(0.5, 40.0)
                val etaHours =
                    if (totalNm > 1e-6) {
                        totalNm / speedKn
                    } else {
                        0.0
                    }
                val depart = System.currentTimeMillis()
                val etaMillis = (etaHours * 3_600_000.0).toLong().coerceAtLeast(60_000L)
                val fracs = listOf(0.0, 1.0 / 3.0, 2.0 / 3.0, 1.0)
                val locs = fracs.map { GeoMath.pointAlongPolyline(geom, it) }
                val targets = fracs.map { f -> depart + (etaMillis.toDouble() * f).toLong() }
                _ui.value =
                    _ui.value.copy(
                        loadingRouteWeather = true,
                        routeWeatherLegNm = totalNm,
                        routeWeatherEtaHours = etaHours,
                    )
                try {
                    val chunks =
                        supervisorScope {
                            (0..3).map { i ->
                                async {
                                    val loc = locs[i]
                                    i to weatherRepository.loadAll(loc.first, loc.second)
                                }
                            }.awaitAll()
                        }
                    val ids = listOf(SourceId.MET_NORWAY, SourceId.SMHI, SourceId.FMI)
                    val bySource =
                        ids.associateWith { src ->
                            val slotArr = arrayOfNulls<UnifiedTimePoint>(4)
                            var meta: String? = null
                            var fetchedAt = 0L
                            var firstError: Throwable? = null
                            for ((i, forecastsAtLeg) in chunks) {
                                val r = forecastsAtLeg[src] ?: continue
                                r.fold(
                                    onSuccess = { fc ->
                                        val picked =
                                            ForecastSampler
                                                .sampleAtTargetMillis(fc.points, listOf(targets[i]))
                                                .firstOrNull()
                                        slotArr[i] = picked
                                        if (i == 0) {
                                            meta = fc.modelInfo
                                            fetchedAt = fc.fetchedAtUtc
                                        }
                                    },
                                    onFailure = { e ->
                                        if (firstError == null) firstError = e
                                    },
                                )
                            }
                            val list = slotArr.toList()
                            if (list.all { it == null } && firstError != null) {
                                Result.failure(firstError!!)
                            } else {
                                Result.success(
                                    RouteSourceWeatherSlots(
                                        slots = list,
                                        fetchedAtUtc = fetchedAt,
                                        modelInfo = meta,
                                    ),
                                )
                            }
                        }
                    _ui.value =
                        _ui.value.copy(
                            routeWeatherBySource = bySource,
                            loadingRouteWeather = false,
                        )
                } catch (_: Exception) {
                    _ui.value =
                        _ui.value.copy(
                            loadingRouteWeather = false,
                            routeWeatherBySource = emptyMap(),
                        )
                }
            }
    }

    private fun recomputeRouteGeometry() {
        if (!premiumAccess.isPremium.value) return
        val start = _ui.value.routeStart
        val end = _ui.value.routeEnd
        if (start == null || end == null) {
            _ui.value =
                _ui.value.copy(
                    routeGeometry = emptyList(),
                    routeFairwayUnavailable = false,
                    routeComputingFairway = false,
                )
            scheduleRouteWeatherRefresh()
            return
        }
        val gc = GeoMath.greatCirclePoints(start.first, start.second, end.first, end.second)
        _ui.value =
            _ui.value.copy(
                routeGeometry = gc,
                routeFairwayUnavailable = false,
                routeComputingFairway = true,
            )
        scheduleRouteWeatherRefresh()
        viewModelScope.launch {
            val launchStart = start
            val launchEnd = end
            try {
                val fairway =
                    runCatching {
                        VaylaFairwayRouter.routeAlongNavLines(
                            launchStart.first,
                            launchStart.second,
                            launchEnd.first,
                            launchEnd.second,
                        )
                    }.getOrNull()
                if (_ui.value.routeStart != launchStart || _ui.value.routeEnd != launchEnd) return@launch
                val fairwayOk =
                    fairway?.takeIf { pts ->
                        pts.size >= 2 && pts.all { (la, lo) -> la.isFinite() && lo.isFinite() }
                    }
                if (fairwayOk != null) {
                    _ui.value =
                        _ui.value.copy(
                            routeGeometry = fairwayOk,
                            routeError = null,
                            routeFairwayUnavailable = false,
                        )
                    scheduleRouteWeatherRefresh()
                    return@launch
                }
                val osrm =
                    OsrmClient.fetchDrivingGeometry(
                        launchStart.first,
                        launchStart.second,
                        launchEnd.first,
                        launchEnd.second,
                    )
                if (_ui.value.routeStart != launchStart || _ui.value.routeEnd != launchEnd) return@launch
                if (!osrm.isNullOrEmpty()) {
                    _ui.value =
                        _ui.value.copy(
                            routeGeometry = osrm,
                            routeError = null,
                            routeFairwayUnavailable = true,
                        )
                } else {
                    _ui.value = _ui.value.copy(routeFairwayUnavailable = true)
                }
                scheduleRouteWeatherRefresh()
            } finally {
                if (_ui.value.routeStart == launchStart && _ui.value.routeEnd == launchEnd) {
                    _ui.value = _ui.value.copy(routeComputingFairway = false)
                }
            }
        }
    }

    class Factory(
        private val container: AppContainer,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(MainViewModel::class.java))
            return MainViewModel(
                weatherRepository = container.weatherRepository,
                userPreferencesRepository = container.userPreferencesRepository,
                overpassHarborClient = container.overpassHarborClient,
                premiumAccess = container.premiumAccess,
                networkConnectivityMonitor = container.networkConnectivityMonitor,
                offlineAreaPackDownloader = container.offlineAreaPackDownloader,
                playInAppReviewCoordinator = container.playInAppReviewCoordinator,
            ) as T
        }
    }
}
