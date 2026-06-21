package fi.veneappi.app.ui.ais

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import fi.veneappi.app.data.ais.AisMqttConnectionState
import fi.veneappi.app.data.ais.AisMqttCoordinator
import fi.veneappi.app.data.ais.AisMqttMessageParser
import fi.veneappi.app.data.ais.DigitrafficAisRepository
import fi.veneappi.app.domain.ais.AisConfig
import fi.veneappi.app.domain.ais.AisMqttConfig
import fi.veneappi.app.domain.ais.AisVesselDisplay
import fi.veneappi.app.domain.ais.AisVesselMotion
import fi.veneappi.app.domain.ais.AisViewportFilter
import fi.veneappi.app.domain.ais.MapViewport
import fi.veneappi.app.domain.ais.applyMqttUpdate
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Premium AIS overlay: REST bootstrap + shared Digitraffic MQTT (viewport MMSI subscriptions). */
class AisMapViewModel(
    private val repository: DigitrafficAisRepository,
    private val mqttCoordinator: AisMqttCoordinator,
    private val mqttParser: AisMqttMessageParser = AisMqttMessageParser(),
) : ViewModel() {
    private val _isEnabled = MutableStateFlow(false)
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    private val _vessels = MutableStateFlow<List<AisVesselDisplay>>(emptyList())
    val vessels: StateFlow<List<AisVesselDisplay>> = _vessels.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _streamMode = MutableStateFlow(AisStreamMode.Off)
    val streamMode: StateFlow<AisStreamMode> = _streamMode.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val _mapRenderGeneration = MutableStateFlow(0)
    val mapRenderGeneration: StateFlow<Int> = _mapRenderGeneration.asStateFlow()

    private var pollJob: Job? = null
    private var viewportJob: Job? = null
    private var mqttMessagesJob: Job? = null
    private var mqttConnectionJob: Job? = null
    private var publishThrottleJob: Job? = null
    private var fleetByMmsi: Map<Int, AisVesselDisplay> = emptyMap()
    private var lastViewport: MapViewport? = null
    private var appIsActive = true
    private var pollCycle = 0
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
                mqttCoordinator.connectionState.collect { state ->
                    when (state) {
                        AisMqttConnectionState.Connected -> {
                            syncMqttSubscriptions()
                            updateStreamMode()
                        }
                        AisMqttConnectionState.Disconnected,
                        AisMqttConnectionState.Error,
                        -> updateStreamMode()
                        AisMqttConnectionState.Connecting -> Unit
                    }
                }
            }
    }

    fun setSceneActive(active: Boolean) {
        if (appIsActive == active) return
        appIsActive = active
        if (!_isEnabled.value) return
        if (active) {
            _streamMode.value = AisStreamMode.Connecting
            bootstrap()
        } else {
            stopPolling()
            viewportJob?.cancel()
            viewportJob = null
            mqttCoordinator.setViewportConsumer(active = false, mmsis = emptySet())
            _streamMode.value = AisStreamMode.Off
        }
    }

    fun setEnabled(
        enabled: Boolean,
        premium: Boolean,
    ) {
        if (!premium) {
            tearDown()
            _isEnabled.value = false
            return
        }
        if (_isEnabled.value == enabled) return
        _isEnabled.value = enabled
        if (enabled) {
            _streamMode.value = AisStreamMode.Connecting
            _lastError.value = null
            pollCycle = 0
            bootstrap()
        } else {
            tearDown()
        }
    }

    fun toggle(premium: Boolean) {
        setEnabled(!_isEnabled.value, premium)
    }

    fun updateViewport(viewport: MapViewport) {
        val prev = lastViewport
        val regionChanged = prev?.let { viewport.regionChangedSignificantly(it) } ?: true
        lastViewport = viewport
        if (!_isEnabled.value) return
        if (!regionChanged && fleetByMmsi.isNotEmpty()) return
        scheduleViewportRefresh()
    }

    fun ensureFallbackViewport(
        latitude: Double,
        longitude: Double,
        zoom: Double = 12.5,
    ) {
        if (lastViewport != null) return
        lastViewport = MapViewport.aroundCenter(latitude, longitude, zoom)
        if (_isEnabled.value) {
            viewModelScope.launch { refreshFromNetwork(fullFetch = true) }
        }
    }

    fun refreshNow() {
        if (!_isEnabled.value) return
        viewModelScope.launch { refreshFromNetwork(fullFetch = pollCycle == 0) }
    }

    private fun bootstrap() {
        viewModelScope.launch {
            refreshFromNetwork(fullFetch = true)
        }
        startPollingIfNeeded()
    }

    private fun tearDown() {
        _streamMode.value = AisStreamMode.Off
        _lastError.value = null
        stopPolling()
        viewportJob?.cancel()
        viewportJob = null
        publishThrottleJob?.cancel()
        publishThrottleJob = null
        mqttCoordinator.setViewportConsumer(active = false, mmsis = emptySet())
        fleetByMmsi = emptyMap()
        pollCycle = 0
        _vessels.value = emptyList()
    }

    private fun scheduleViewportRefresh() {
        viewportJob?.cancel()
        viewportJob =
            viewModelScope.launch {
                delay(AisConfig.VIEWPORT_REFRESH_DEBOUNCE_MS)
                refreshForViewportChange()
            }
    }

    private suspend fun refreshForViewportChange() {
        if (!_isEnabled.value) return
        val viewport = lastViewport ?: return

        refreshMutex.withLock {
            if (!_isEnabled.value) return
            _isLoading.value = true
            val started = System.currentTimeMillis()
            runCatching {
                AisViewportFilter.filter(
                    repository.fetchVesselsInViewport(viewport),
                    viewport,
                )
            }.onSuccess { fleet ->
                if (_isEnabled.value) {
                    _lastError.value = null
                    fleetByMmsi = fleet.associateBy { it.mmsi }
                    publishMap()
                    pollCycle++
                    val elapsed = System.currentTimeMillis() - started
                    log("viewport loaded ${fleet.size} vessels in ${elapsed}ms (r=${viewport.queryRadiusKm()}km)")
                    syncMqttSubscriptions()
                }
            }.onFailure { e ->
                if (_isEnabled.value) {
                    _lastError.value = e.message ?: e.javaClass.simpleName
                    if (fleetByMmsi.isEmpty()) {
                        _streamMode.value = AisStreamMode.Error
                    } else {
                        updateStreamMode()
                    }
                    Log.w(TAG, "viewport fetch failed: ${_lastError.value}", e)
                }
            }
            _isLoading.value = false
        }
    }

    private fun startPollingIfNeeded() {
        pollJob?.cancel()
        pollJob =
            viewModelScope.launch {
                while (isActive) {
                    delay(pollIntervalMs())
                    if (!_isEnabled.value || !appIsActive) continue
                    if (mqttCoordinator.isConnected) {
                        refreshFromNetwork(fullFetch = true)
                    } else {
                        val fullFetch =
                            pollCycle == 0 ||
                                pollCycle % AisConfig.METADATA_REFRESH_EVERY_N_POLLS == 0
                        refreshFromNetwork(fullFetch = fullFetch)
                    }
                }
            }
    }

    private fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    private fun pollIntervalMs(): Long =
        if (mqttCoordinator.isConnected) {
            AisConfig.REST_METADATA_POLL_INTERVAL_SECONDS_WHEN_MQTT_LIVE * 1000L
        } else {
            AisConfig.REST_POLL_INTERVAL_SECONDS * 1000L
        }

    private suspend fun refreshFromNetwork(fullFetch: Boolean) {
        if (!_isEnabled.value) return
        val viewport = lastViewport ?: return

        refreshMutex.withLock {
            if (!_isEnabled.value) return
            if (!_isLoading.value) _isLoading.value = true
            val started = System.currentTimeMillis()
            runCatching {
                val updates =
                    if (fullFetch) {
                        repository.fetchAllVessels()
                    } else {
                        repository.fetchLocationUpdates()
                    }
                val filtered = AisViewportFilter.filter(updates, viewport)
                mergeFleet(filtered, preserveMetadata = !fullFetch)
            }.onSuccess { fleet ->
                if (_isEnabled.value) {
                    _lastError.value = null
                    fleetByMmsi = fleet.associateBy { it.mmsi }
                    publishMap()
                    pollCycle++
                    val elapsed = System.currentTimeMillis() - started
                    log("loaded ${fleet.size} vessels (${if (fullFetch) "full" else "positions"}) in ${elapsed}ms")
                    syncMqttSubscriptions()
                }
            }.onFailure { e ->
                if (_isEnabled.value) {
                    _lastError.value = e.message ?: e.javaClass.simpleName
                    if (fleetByMmsi.isEmpty()) {
                        _streamMode.value = AisStreamMode.Error
                    } else {
                        updateStreamMode()
                    }
                    Log.w(TAG, "fetch failed: ${_lastError.value}", e)
                }
            }
            _isLoading.value = false
        }
    }

    private fun mergeFleet(
        incoming: List<AisVesselDisplay>,
        preserveMetadata: Boolean,
    ): List<AisVesselDisplay> {
        if (!preserveMetadata) return incoming
        return incoming.map { vessel ->
            val prev = fleetByMmsi[vessel.mmsi] ?: return@map vessel
            vessel.copy(
                name = prev.name,
                callSign = prev.callSign,
                destination = prev.destination,
                imo = prev.imo,
                draughtTenthsM = prev.draughtTenthsM,
                shipTypeCode = prev.shipTypeCode,
                etaRaw = prev.etaRaw,
            )
        }
    }

    private suspend fun handleMqttMessage(
        topic: String,
        payload: ByteArray,
    ) {
        if (!_isEnabled.value) return
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
                publishMap()
            }
    }

    private fun syncMqttSubscriptions() {
        if (!_isEnabled.value || !appIsActive) {
            mqttCoordinator.setViewportConsumer(active = false, mmsis = emptySet())
            return
        }
        mqttCoordinator.setViewportConsumer(active = true, mmsis = fleetByMmsi.keys)
    }

    private fun publishMap() {
        _vessels.value = fleetByMmsi.values.toList()
        _mapRenderGeneration.value = _mapRenderGeneration.value + 1
        updateStreamMode()
    }

    fun tickLiveMapRender() {
        if (!_isEnabled.value || !appIsActive) return
        if (!AisVesselMotion.needsLiveMapTick(_vessels.value)) return
        _mapRenderGeneration.value = _mapRenderGeneration.value + 1
    }

    private fun updateStreamMode() {
        if (!_isEnabled.value) {
            _streamMode.value = AisStreamMode.Off
            return
        }
        _streamMode.value =
            when {
                lastViewport == null || (fleetByMmsi.isEmpty() && _isLoading.value) ->
                    AisStreamMode.Connecting
                fleetByMmsi.isEmpty() && _lastError.value != null ->
                    AisStreamMode.Error
                mqttCoordinator.isConnected && fleetByMmsi.isNotEmpty() ->
                    AisStreamMode.Live
                fleetByMmsi.isNotEmpty() ->
                    AisStreamMode.RestOnly
                else ->
                    AisStreamMode.Connecting
            }
    }

    private fun log(message: String) {
        Log.i(TAG, message)
    }

    override fun onCleared() {
        tearDown()
        mqttMessagesJob?.cancel()
        mqttConnectionJob?.cancel()
        super.onCleared()
    }

    companion object {
        private const val TAG = "AisMapViewModel"

        fun factory(
            repository: DigitrafficAisRepository,
            mqttCoordinator: AisMqttCoordinator,
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    AisMapViewModel(repository, mqttCoordinator) as T
            }
    }
}
