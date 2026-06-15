package fi.veneappi.app.ui.ais

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import fi.veneappi.app.data.ais.DigitrafficAisRepository
import fi.veneappi.app.domain.ais.AisConfig
import fi.veneappi.app.domain.ais.AisVesselDisplay
import fi.veneappi.app.domain.ais.AisViewportFilter
import fi.veneappi.app.domain.ais.MapViewport
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Premium AIS overlay: REST poll (~60 s) + debounced reload on viewport change. */
class AisMapViewModel(
    private val repository: DigitrafficAisRepository,
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
    private var fleetByMmsi: Map<Int, AisVesselDisplay> = emptyMap()
    private var lastViewport: MapViewport? = null
    private var appIsActive = true
    private var pollCycle = 0
    private val refreshMutex = Mutex()

    fun setSceneActive(active: Boolean) {
        if (appIsActive == active) return
        appIsActive = active
        if (!_isEnabled.value) return
        if (active) {
            _streamMode.value = AisStreamMode.Connecting
            bootstrap()
        } else {
            pollJob?.cancel()
            pollJob = null
            viewportJob?.cancel()
            viewportJob = null
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
        val regionChanged =
            lastViewport?.let { !viewportApproximatelyEquals(it, viewport) } ?: true
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
        viewModelScope.launch { refreshFromNetwork(fullFetch = true) }
        startPollingIfNeeded()
    }

    private fun tearDown() {
        _streamMode.value = AisStreamMode.Off
        _lastError.value = null
        pollJob?.cancel()
        pollJob = null
        viewportJob?.cancel()
        viewportJob = null
        fleetByMmsi = emptyMap()
        pollCycle = 0
        _vessels.value = emptyList()
    }

    private fun scheduleViewportRefresh() {
        viewportJob?.cancel()
        viewportJob =
            viewModelScope.launch {
                delay(AisConfig.VIEWPORT_REFRESH_DEBOUNCE_MS)
                refreshFromNetwork(fullFetch = pollCycle == 0)
            }
    }

    private fun startPollingIfNeeded() {
        pollJob?.cancel()
        pollJob =
            viewModelScope.launch {
                while (isActive) {
                    delay(AisConfig.REST_POLL_INTERVAL_SECONDS * 1000L)
                    if (!_isEnabled.value || !appIsActive) continue
                    val fullFetch =
                        pollCycle == 0 ||
                            pollCycle % AisConfig.METADATA_REFRESH_EVERY_N_POLLS == 0
                    refreshFromNetwork(fullFetch = fullFetch)
                }
            }
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
                }
            }.onFailure { e ->
                if (_isEnabled.value) {
                    _lastError.value = e.message ?: e.javaClass.simpleName
                    _streamMode.value = AisStreamMode.Error
                    Log.w(TAG, "fetch failed: ${_lastError.value}", e)
                }
            }
            _isLoading.value = false
        }
    }

    /** Merge new positions; keep names/metadata from previous poll when positions-only. */
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

    private fun publishMap() {
        val viewport = lastViewport
        if (viewport == null) {
            _vessels.value = emptyList()
            if (_isEnabled.value) {
                _streamMode.value = AisStreamMode.Connecting
            }
            return
        }
        _vessels.value = fleetByMmsi.values.toList()
        if (_isEnabled.value) {
            _streamMode.value =
                if (_vessels.value.isEmpty()) {
                    AisStreamMode.Connecting
                } else {
                    AisStreamMode.RestOnly
                }
        }
        _mapRenderGeneration.value = _mapRenderGeneration.value + 1
    }

    private fun viewportApproximatelyEquals(
        a: MapViewport,
        b: MapViewport,
    ): Boolean =
        kotlin.math.abs(a.southLatitude - b.southLatitude) < 0.015 &&
            kotlin.math.abs(a.southLongitude - b.southLongitude) < 0.015 &&
            kotlin.math.abs(a.northLatitude - b.northLatitude) < 0.015 &&
            kotlin.math.abs(a.northLongitude - b.northLongitude) < 0.015

    private fun log(message: String) {
        Log.i(TAG, message)
    }

    override fun onCleared() {
        tearDown()
        super.onCleared()
    }

    companion object {
        private const val TAG = "AisMapViewModel"

        fun factory(repository: DigitrafficAisRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    AisMapViewModel(repository) as T
            }
    }
}
