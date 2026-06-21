package fi.veneappi.app.ui.ais

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import fi.veneappi.app.data.ais.AisMqttConnectionState
import fi.veneappi.app.data.ais.AisMqttCoordinator
import fi.veneappi.app.data.ais.AisMqttMessageParser
import fi.veneappi.app.data.ais.AisWatchlistRepository
import fi.veneappi.app.data.ais.DigitrafficAisRepository
import fi.veneappi.app.data.ais.WatchlistFullException
import fi.veneappi.app.domain.ais.AddedSource
import fi.veneappi.app.domain.ais.AisBrowseItem
import fi.veneappi.app.domain.ais.AisBrowseSource
import fi.veneappi.app.domain.ais.AisConfig
import fi.veneappi.app.domain.ais.AisMqttConfig
import fi.veneappi.app.domain.ais.AisTrackConfig
import fi.veneappi.app.domain.ais.AisTrackListFilter
import fi.veneappi.app.domain.ais.AisTrackPanel
import fi.veneappi.app.domain.ais.AisVesselDisplay
import fi.veneappi.app.domain.ais.AisVesselMotion
import fi.veneappi.app.domain.ais.AisWatchlistEntry
import fi.veneappi.app.domain.ais.MapViewport
import fi.veneappi.app.domain.ais.applyMqttUpdate
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class AisWatchlistRow(
    val entry: AisWatchlistEntry,
    val vessel: AisVesselDisplay?,
)

class AisTrackViewModel(
    private val repository: DigitrafficAisRepository,
    private val watchlistRepository: AisWatchlistRepository,
    private val mqttCoordinator: AisMqttCoordinator,
    private val mqttParser: AisMqttMessageParser = AisMqttMessageParser(),
) : ViewModel() {
    val watchlistEntries: StateFlow<List<AisWatchlistEntry>> =
        watchlistRepository.entries.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    private val _panel = MutableStateFlow(AisTrackPanel.WATCHLIST)
    val panel: StateFlow<AisTrackPanel> = _panel.asStateFlow()

    private val _listFilter = MutableStateFlow(AisTrackListFilter.ALL)
    val listFilter: StateFlow<AisTrackListFilter> = _listFilter.asStateFlow()

    private val _watchlistSearch = MutableStateFlow("")
    val watchlistSearch: StateFlow<String> = _watchlistSearch.asStateFlow()

    private val _browseSearch = MutableStateFlow("")
    val browseSearch: StateFlow<String> = _browseSearch.asStateFlow()

    private val _watchlistRows = MutableStateFlow<List<AisWatchlistRow>>(emptyList())
    val watchlistRows: StateFlow<List<AisWatchlistRow>> = _watchlistRows.asStateFlow()

    private val _browseItems = MutableStateFlow<List<AisBrowseItem>>(emptyList())
    val browseItems: StateFlow<List<AisBrowseItem>> = _browseItems.asStateFlow()

    private val _vessels = MutableStateFlow<List<AisVesselDisplay>>(emptyList())
    val vessels: StateFlow<List<AisVesselDisplay>> = _vessels.asStateFlow()

    private val _streamMode = MutableStateFlow(AisStreamMode.Off)
    val streamMode: StateFlow<AisStreamMode> = _streamMode.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _browseLoading = MutableStateFlow(false)
    val browseLoading: StateFlow<Boolean> = _browseLoading.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    private val _mapRenderGeneration = MutableStateFlow(0)
    val mapRenderGeneration: StateFlow<Int> = _mapRenderGeneration.asStateFlow()

    private val _mapRecenterSignal = MutableStateFlow(0L)
    val mapRecenterSignal: StateFlow<Long> = _mapRecenterSignal.asStateFlow()

    private val _mapRecenterTarget = MutableStateFlow<Pair<Double, Double>?>(null)
    val mapRecenterTarget: StateFlow<Pair<Double, Double>?> = _mapRecenterTarget.asStateFlow()

    private val _mapRecenterZoom = MutableStateFlow<Double?>(null)
    val mapRecenterZoom: StateFlow<Double?> = _mapRecenterZoom.asStateFlow()

    private var fleetByMmsi: Map<Int, AisVesselDisplay> = emptyMap()
    private var lastViewport: MapViewport? = null
    private var sceneActive = false
    private var premiumActive = false
    private var pollJob: Job? = null
    private var browseJob: Job? = null
    private var publishThrottleJob: Job? = null
    private var mqttMessagesJob: Job? = null
    private var mqttConnectionJob: Job? = null
    private val refreshMutex = Mutex()
    private val fleetMutex = Mutex()

    init {
        mqttMessagesJob =
            viewModelScope.launch {
                mqttCoordinator.messages.collect { message ->
                    handleMqttMessage(message.topic, message.payload)
                }
            }
        mqttConnectionJob =
            viewModelScope.launch {
                mqttCoordinator.connectionState.collect {
                    updateStreamMode()
                }
            }
        viewModelScope.launch {
            watchlistEntries.collect {
                if (sceneActive && premiumActive) {
                    refreshWatchlistFleet()
                } else {
                    publishUi()
                }
            }
        }
    }

    fun setSceneActive(active: Boolean) {
        sceneActive = active
        if (!premiumActive) return
        if (active) {
            _streamMode.value = AisStreamMode.Connecting
            bootstrap()
        } else {
            stopPolling()
            browseJob?.cancel()
            mqttCoordinator.setWatchlistConsumer(active = false, mmsis = emptySet())
            _streamMode.value = AisStreamMode.Off
        }
    }

    fun setPremiumActive(active: Boolean) {
        premiumActive = active
        if (!active) {
            tearDown()
        } else if (sceneActive) {
            bootstrap()
        }
    }

    fun setPanel(panel: AisTrackPanel) {
        _panel.value = panel
        if (panel == AisTrackPanel.BROWSE && sceneActive && premiumActive) {
            scheduleBrowseRefresh()
        } else {
            publishUi()
        }
    }

    fun setListFilter(filter: AisTrackListFilter) {
        _listFilter.value = filter
        publishUi()
    }

    fun setWatchlistSearch(query: String) {
        _watchlistSearch.value = query
        publishUi()
    }

    fun setBrowseSearch(query: String) {
        _browseSearch.value = query
        viewModelScope.launch { refreshBrowseMerged() }
    }

    fun updateViewport(viewport: MapViewport) {
        lastViewport = viewport
        if (_panel.value == AisTrackPanel.BROWSE && sceneActive && premiumActive) {
            scheduleBrowseRefresh()
        }
    }

    fun ensureFallbackViewport(
        latitude: Double,
        longitude: Double,
        zoom: Double = 8.0,
    ) {
        if (lastViewport != null) return
        lastViewport = MapViewport.aroundCenter(latitude, longitude, zoom)
        if (_panel.value == AisTrackPanel.BROWSE) {
            scheduleBrowseRefresh()
        }
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }

    fun addManualVessel(
        mmsi: Int,
        nickname: String?,
    ) {
        viewModelScope.launch {
            val meta = repository.lookupMetadata(mmsi)
            watchlistRepository
                .addEntry(
                    mmsi = mmsi,
                    nickname = nickname,
                    name = meta?.name,
                    callSign = meta?.callSign,
                    source = AddedSource.MANUAL,
                ).onSuccess {
                    refreshWatchlistFleet()
                    focusWatchlistVessel(mmsi)
                }.onFailure { e ->
                    _userMessage.value = e.message
                }
        }
    }

    fun addBrowseItem(item: AisBrowseItem) {
        viewModelScope.launch {
            watchlistRepository
                .addEntry(
                    mmsi = item.mmsi,
                    name = item.name,
                    callSign = item.callSign,
                    source =
                        when (item.source) {
                            AisBrowseSource.NEARBY -> AddedSource.SEARCH
                            AisBrowseSource.GLOBAL -> AddedSource.SEARCH
                        },
                ).onSuccess {
                    _panel.value = AisTrackPanel.WATCHLIST
                    refreshWatchlistFleet()
                    focusWatchlistVessel(item.mmsi)
                }.onFailure { e ->
                    _userMessage.value =
                        when (e) {
                            is WatchlistFullException ->
                                "Max ${AisTrackConfig.MAX_WATCHLIST_VESSELS} vessels"
                            else -> e.message
                        }
                }
        }
    }

    fun removeFromWatchlist(mmsi: Int) {
        viewModelScope.launch {
            watchlistRepository.removeEntry(mmsi)
            fleetMutex.withLock {
                fleetByMmsi = fleetByMmsi - mmsi
            }
            syncMqttSubscriptions()
            publishUi()
        }
    }

    fun refreshBrowseNow() {
        scheduleBrowseRefresh(force = true)
    }

    fun tickLiveMapRender() {
        if (!sceneActive || !premiumActive) return
        if (!AisVesselMotion.needsLiveMapTick(_vessels.value)) return
        _mapRenderGeneration.value = _mapRenderGeneration.value + 1
    }

    fun focusWatchlistVessel(mmsi: Int) {
        viewModelScope.launch {
            var vessel = fleetMutex.withLock { fleetByMmsi[mmsi] }
            if (vessel == null && premiumActive) {
                refreshWatchlistFleet()
                vessel = fleetMutex.withLock { fleetByMmsi[mmsi] }
            }
            val resolved = vessel ?: return@launch
            if (!resolved.latitude.isFinite() || !resolved.longitude.isFinite()) return@launch
            lastViewport =
                MapViewport.aroundCenter(
                    resolved.latitude,
                    resolved.longitude,
                    AisTrackConfig.FOCUS_VESSEL_ZOOM,
                )
            requestMapRecenter(
                latitude = resolved.latitude,
                longitude = resolved.longitude,
                zoom = AisTrackConfig.FOCUS_VESSEL_ZOOM,
            )
            publishUi()
        }
    }

    private fun requestMapRecenter(
        latitude: Double,
        longitude: Double,
        zoom: Double,
    ) {
        _mapRecenterTarget.value = latitude to longitude
        _mapRecenterZoom.value = zoom
        _mapRecenterSignal.value = System.currentTimeMillis()
    }

    private fun bootstrap() {
        viewModelScope.launch { refreshWatchlistFleet() }
        startPollingIfNeeded()
        if (_panel.value == AisTrackPanel.BROWSE) {
            scheduleBrowseRefresh(force = true)
        }
    }

    private fun tearDown() {
        stopPolling()
        browseJob?.cancel()
        publishThrottleJob?.cancel()
        mqttCoordinator.setWatchlistConsumer(active = false, mmsis = emptySet())
        fleetByMmsi = emptyMap()
        _vessels.value = emptyList()
        _watchlistRows.value = emptyList()
        _streamMode.value = AisStreamMode.Off
    }

    private fun startPollingIfNeeded() {
        pollJob?.cancel()
        pollJob =
            viewModelScope.launch {
                while (isActive) {
                    delay(
                        if (mqttCoordinator.isConnected) {
                            AisConfig.REST_METADATA_POLL_INTERVAL_SECONDS_WHEN_MQTT_LIVE * 1000L
                        } else {
                            AisConfig.REST_POLL_INTERVAL_SECONDS * 1000L
                        },
                    )
                    if (!sceneActive || !premiumActive) continue
                    refreshWatchlistFleet()
                }
            }
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    private suspend fun refreshWatchlistFleet() {
        if (!premiumActive) return
        val mmsis = watchlistEntries.value.map { it.mmsi }.toSet()
        refreshMutex.withLock {
            if (!premiumActive) return
            _isLoading.value = mmsis.isNotEmpty()
            runCatching {
                repository.fetchVesselsForMmsis(mmsis)
            }.onSuccess { fleet ->
                fleetMutex.withLock {
                    fleetByMmsi = fleet.associateBy { it.mmsi }
                }
                _lastError.value = null
                syncMqttSubscriptions()
                publishUi()
            }.onFailure { e ->
                _lastError.value = e.message
                updateStreamMode()
                Log.w(TAG, "watchlist refresh failed", e)
            }
            _isLoading.value = false
        }
    }

    private fun scheduleBrowseRefresh(force: Boolean = false) {
        browseJob?.cancel()
        browseJob =
            viewModelScope.launch {
                if (!force) delay(AisTrackConfig.BROWSE_DEBOUNCE_MS)
                if (!sceneActive || !premiumActive || _panel.value != AisTrackPanel.BROWSE) return@launch
                val viewport = lastViewport ?: return@launch
                _browseLoading.value = true
                runCatching {
                    val radius = viewport.queryRadiusKm()
                    val nearby =
                        repository.fetchNearbyBrowseItems(
                            latitude = viewport.centerLatitude,
                            longitude = viewport.centerLongitude,
                            radiusKm = radius,
                        )
                    _browseItems.value = nearby
                    refreshBrowseMerged()
                }.onFailure { e ->
                    Log.w(TAG, "browse nearby failed", e)
                    _browseItems.value = emptyList()
                    refreshBrowseMerged()
                }
                _browseLoading.value = false
            }
    }

    private suspend fun refreshBrowseMerged() {
        val q = _browseSearch.value.trim()
        val nearbyFiltered =
            filterBrowseItems(_browseItems.value, q)
        val merged = LinkedHashMap<Int, AisBrowseItem>()
        for (item in nearbyFiltered) merged[item.mmsi] = item
        if (q.length >= AisTrackConfig.GLOBAL_SEARCH_MIN_CHARS) {
            for (item in repository.searchVesselsMetadata(q)) {
                merged.putIfAbsent(item.mmsi, item)
            }
        }
        _browseItems.value = merged.values.sortedBy { it.displayLabel.lowercase() }
    }

    private fun filterBrowseItems(
        items: List<AisBrowseItem>,
        query: String,
    ): List<AisBrowseItem> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return items
        return items.filter { item ->
            listOfNotNull(item.mmsi.toString(), item.name, item.callSign)
                .joinToString(" ")
                .lowercase()
                .contains(q)
        }
    }

    private suspend fun handleMqttMessage(
        topic: String,
        payload: ByteArray,
    ) {
        if (!sceneActive || !premiumActive) return
        val update = mqttParser.parseMessage(topic, payload) ?: return
        var changed = false
        fleetMutex.withLock {
            val prev = fleetByMmsi[update.mmsi] ?: return
            val merged = prev.applyMqttUpdate(update)
            if (merged != prev) {
                fleetByMmsi = fleetByMmsi + (update.mmsi to merged)
                changed = true
            }
        }
        if (changed) {
            scheduleThrottledPublish()
        }
    }

    private fun scheduleThrottledPublish() {
        if (publishThrottleJob?.isActive == true) return
        publishThrottleJob =
            viewModelScope.launch {
                delay(AisMqttConfig.MAP_PUBLISH_THROTTLE_MS)
                publishUi()
            }
    }

    private fun syncMqttSubscriptions() {
        if (!sceneActive || !premiumActive) {
            mqttCoordinator.setWatchlistConsumer(active = false, mmsis = emptySet())
            return
        }
        mqttCoordinator.setWatchlistConsumer(
            active = true,
            mmsis = watchlistEntries.value.map { it.mmsi }.toSet(),
        )
    }

    private fun publishUi() {
        val entries = watchlistEntries.value
        val rows =
            entries.map { entry ->
                AisWatchlistRow(entry = entry, vessel = fleetByMmsi[entry.mmsi])
            }
        _watchlistRows.value = filterWatchlistRows(rows)
        _vessels.value =
            if (_panel.value == AisTrackPanel.WATCHLIST) {
                _watchlistRows.value.mapNotNull { it.vessel }
                    .filter { it.latitude.isFinite() && it.longitude.isFinite() }
            } else {
                _browseItems.value.mapNotNull { item ->
                    val lat = item.latitude
                    val lon = item.longitude
                    if (lat == null || lon == null || !lat.isFinite() || !lon.isFinite()) return@mapNotNull null
                    AisVesselDisplay(
                        mmsi = item.mmsi,
                        latitude = lat,
                        longitude = lon,
                        name = item.name,
                        callSign = item.callSign,
                        destination = null,
                        imo = null,
                        draughtTenthsM = null,
                        shipTypeCode = null,
                        etaRaw = null,
                        navStatusCode = null,
                        sogKn = item.sogKn,
                        cogDeg = null,
                        headingDeg = null,
                        lastSeenEpochMs = item.lastSeenEpochMs,
                    )
                }
            }
        _mapRenderGeneration.value = _mapRenderGeneration.value + 1
        updateStreamMode()
    }

    private fun filterWatchlistRows(rows: List<AisWatchlistRow>): List<AisWatchlistRow> {
        val q = _watchlistSearch.value.trim().lowercase()
        val filteredBySearch =
            if (q.isEmpty()) {
                rows
            } else {
                rows.filter { row ->
                    listOfNotNull(
                        row.entry.mmsi.toString(),
                        row.entry.nickname,
                        row.entry.name,
                        row.entry.callSign,
                    ).joinToString(" ").lowercase().contains(q)
                }
            }
        return when (_listFilter.value) {
            AisTrackListFilter.ALL -> filteredBySearch
            AisTrackListFilter.ACTIVE ->
                filteredBySearch.filter { row ->
                    row.vessel?.let { AisVesselMotion.isActive(it) } == true
                }
            AisTrackListFilter.STALE ->
                filteredBySearch.filter { row ->
                    row.vessel?.let { !AisVesselMotion.isActive(it) } != false
                }
        }
    }

    private fun updateStreamMode() {
        if (!sceneActive || !premiumActive) {
            _streamMode.value = AisStreamMode.Off
            return
        }
        val hasFleet = fleetByMmsi.isNotEmpty()
        _streamMode.value =
            when {
                hasFleet && _isLoading.value -> AisStreamMode.Connecting
                !hasFleet && _lastError.value != null -> AisStreamMode.Error
                mqttCoordinator.isConnected && hasFleet -> AisStreamMode.Live
                hasFleet -> AisStreamMode.RestOnly
                else -> AisStreamMode.Connecting
            }
    }

    override fun onCleared() {
        tearDown()
        mqttMessagesJob?.cancel()
        mqttConnectionJob?.cancel()
        super.onCleared()
    }

    companion object {
        private const val TAG = "AisTrackViewModel"

        fun factory(
            repository: DigitrafficAisRepository,
            watchlistRepository: AisWatchlistRepository,
            mqttCoordinator: AisMqttCoordinator,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    AisTrackViewModel(repository, watchlistRepository, mqttCoordinator) as T
            }
    }
}
