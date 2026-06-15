package fi.veneappi.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import fi.veneappi.app.AppContainer
import fi.veneappi.app.data.lightning.CompositeLightningRepository
import fi.veneappi.app.data.lightning.LightningStrike
import fi.veneappi.app.data.radar.ActiveRadarOverlay
import fi.veneappi.app.data.radar.RadarAnimationFrame
import fi.veneappi.app.data.radar.StormRadarPrefetch
import fi.veneappi.app.data.radar.StormRadarPrefetcher
import fi.veneappi.app.data.radar.StormRadarTimeline
import fi.veneappi.app.data.radar.filterLightningForRadarFrame
import fi.veneappi.app.data.radar.nowFrameIndex
import fi.veneappi.app.data.radar.toActiveOverlay
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class StormMapUiState(
    val radarEnabled: Boolean = true,
    val lightningEnabled: Boolean = true,
    val radarOverlay: ActiveRadarOverlay? = null,
    val radarAnimationFrames: List<RadarAnimationFrame> = emptyList(),
    val radarAnimationPlaying: Boolean = false,
    val radarAnimationIndex: Int = StormRadarTimeline.nowFrameIndex,
    val radarSourceLabel: String = "FMI",
    /** Full fetch (e.g. 2 h) for HUD counts. */
    val allLightningStrikes: List<LightningStrike> = emptyList(),
    /** Filtered by selected radar time — shown on map. */
    val visibleLightningStrikes: List<LightningStrike> = emptyList(),
    val lightningFetchedAtMs: Long? = null,
    val loadingRadar: Boolean = false,
    val loadingLightning: Boolean = false,
    val lightningError: String? = null,
)

class StormMapViewModel(
    private val lightningRepository: CompositeLightningRepository,
    private val stormRadarPrefetcher: StormRadarPrefetcher,
) : ViewModel() {
    private val _stormUi = MutableStateFlow(StormMapUiState())
    val stormUi: StateFlow<StormMapUiState> = _stormUi.asStateFlow()

    private var lightningJob: Job? = null
    private var animationJob: Job? = null

    fun setRadarEnabled(enabled: Boolean) {
        _stormUi.value = _stormUi.value.copy(radarEnabled = enabled)
        if (enabled) {
            refreshRadar(lat = null, lon = null)
        } else {
            stopAnimation()
            _stormUi.value =
                _stormUi.value.copy(
                    radarOverlay = null,
                    radarAnimationFrames = emptyList(),
                    visibleLightningStrikes = emptyList(),
                )
        }
    }

    fun setLightningEnabled(enabled: Boolean) {
        _stormUi.value = _stormUi.value.copy(lightningEnabled = enabled)
        if (enabled) {
            refreshLightning()
        } else {
            lightningJob?.cancel()
            _stormUi.value =
                _stormUi.value.copy(
                    allLightningStrikes = emptyList(),
                    visibleLightningStrikes = emptyList(),
                    lightningError = null,
                    loadingLightning = false,
                )
        }
    }

    fun refreshRadar(
        lat: Double?,
        lon: Double?,
    ) {
        if (!_stormUi.value.radarEnabled) return
        if (lat == null || lon == null) return
        viewModelScope.launch {
            val wasPlaying = _stormUi.value.radarAnimationPlaying
            val hadTimeline = _stormUi.value.radarAnimationFrames.isNotEmpty()
            val previousIndex = _stormUi.value.radarAnimationIndex
            stopAnimation()
            val cached = stormRadarPrefetcher.peek(lat, lon)
            if (cached != null) {
                applyPrefetch(cached, hadTimeline, previousIndex)
            }
            if (cached != null && !cached.isExpired() && cached.hasWarmContent()) {
                if (wasPlaying && cached.frames.size >= 2) {
                    startAnimation()
                }
                return@launch
            }
            if (cached == null) {
                _stormUi.value =
                    _stormUi.value.copy(
                        loadingRadar = true,
                        loadingLightning = _stormUi.value.lightningEnabled,
                    )
            }
            val bundle = stormRadarPrefetcher.fetchAndCache(lat, lon)
            applyPrefetch(bundle, hadTimeline, previousIndex)
            if (wasPlaying && bundle.frames.size >= 2) {
                startAnimation()
            }
        }
    }

    private fun applyPrefetch(
        bundle: StormRadarPrefetch,
        hadTimeline: Boolean,
        previousIndex: Int,
    ) {
        _stormUi.value =
            _stormUi.value.copy(
                loadingRadar = false,
                loadingLightning = false,
                radarSourceLabel = bundle.sourceLabel,
                radarAnimationFrames = bundle.frames,
                allLightningStrikes = bundle.lightningStrikes,
                lightningFetchedAtMs = bundle.lightningFetchedAtMs,
                lightningError = bundle.lightningError,
            )
        val frames = bundle.frames
        if (frames.isEmpty()) {
            _stormUi.value =
                _stormUi.value.copy(
                    radarOverlay = bundle.latestOverlay,
                    radarAnimationIndex = 0,
                    visibleLightningStrikes =
                        if (_stormUi.value.lightningEnabled) {
                            bundle.lightningStrikes
                        } else {
                            emptyList()
                        },
                )
        } else {
            val nowIdx = nowFrameIndex(frames)
            val startIdx =
                if (hadTimeline && previousIndex in frames.indices) {
                    previousIndex
                } else {
                    nowIdx
                }
            applyFrameIndex(startIdx)
        }
    }

    fun refreshLightning() {
        if (!_stormUi.value.lightningEnabled) return
        lightningJob?.cancel()
        lightningJob =
            viewModelScope.launch {
                _stormUi.value =
                    _stormUi.value.copy(
                        loadingLightning = true,
                        lightningError = null,
                    )
                val result = lightningRepository.fetchMergedStrikes()
                val all = result.getOrElse { emptyList() }
                _stormUi.value =
                    _stormUi.value.copy(
                        loadingLightning = false,
                        allLightningStrikes = all,
                        lightningFetchedAtMs = System.currentTimeMillis(),
                        lightningError = result.exceptionOrNull()?.message,
                    )
                reapplyLightningForCurrentFrame()
            }
    }

    fun toggleRadarAnimation() {
        val ui = _stormUi.value
        if (ui.radarAnimationFrames.size < 2) return
        if (ui.radarAnimationPlaying) {
            stopAnimation()
        } else {
            startAnimation()
        }
    }

    fun stepRadarFrame(delta: Int) {
        val ui = _stormUi.value
        if (ui.radarAnimationFrames.isEmpty()) return
        stopAnimation()
        val newIndex =
            (ui.radarAnimationIndex + delta).coerceIn(0, ui.radarAnimationFrames.lastIndex)
        applyFrameIndex(newIndex)
    }

    fun setRadarFrameIndex(index: Int) {
        val ui = _stormUi.value
        if (ui.radarAnimationFrames.isEmpty()) return
        stopAnimation()
        applyFrameIndex(index)
    }

    private fun startAnimation() {
        stopAnimation()
        _stormUi.value = _stormUi.value.copy(radarAnimationPlaying = true)
        animationJob =
            viewModelScope.launch {
                val frames = _stormUi.value.radarAnimationFrames
                if (frames.isEmpty()) return@launch
                var index = _stormUi.value.radarAnimationIndex.coerceIn(0, frames.lastIndex)
                while (isActive && _stormUi.value.radarAnimationPlaying) {
                    applyFrameIndex(index)
                    index = (index + 1) % frames.size
                    delay(ANIMATION_FRAME_MS)
                }
            }
    }

    private fun stopAnimation() {
        animationJob?.cancel()
        animationJob = null
        _stormUi.value = _stormUi.value.copy(radarAnimationPlaying = false)
    }

    private fun applyFrameIndex(index: Int) {
        val ui = _stormUi.value
        val frames = ui.radarAnimationFrames
        if (frames.isEmpty()) {
            _stormUi.value = ui.copy(radarAnimationIndex = 0)
            return
        }
        val i = index.coerceIn(0, frames.lastIndex)
        val frame = frames[i]
        val overlay = frame.toActiveOverlay(ui.radarSourceLabel)
        val visible =
            if (ui.lightningEnabled) {
                filterLightningForRadarFrame(ui.allLightningStrikes, frame)
            } else {
                emptyList()
            }
        _stormUi.value =
            ui.copy(
                radarAnimationIndex = i,
                radarOverlay = overlay,
                visibleLightningStrikes = visible,
            )
    }

    private fun reapplyLightningForCurrentFrame() {
        val ui = _stormUi.value
        if (!ui.lightningEnabled || ui.radarAnimationFrames.isEmpty()) {
            _stormUi.value =
                ui.copy(
                    visibleLightningStrikes =
                        if (ui.lightningEnabled) ui.allLightningStrikes else emptyList(),
                )
            return
        }
        val frame = ui.radarAnimationFrames[ui.radarAnimationIndex.coerceIn(0, ui.radarAnimationFrames.lastIndex)]
        _stormUi.value =
            ui.copy(
                visibleLightningStrikes = filterLightningForRadarFrame(ui.allLightningStrikes, frame),
            )
    }

    class Factory(
        private val container: AppContainer,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return StormMapViewModel(
                lightningRepository = container.compositeLightningRepository,
                stormRadarPrefetcher = container.stormRadarPrefetcher,
            ) as T
        }
    }

    companion object {
        private const val ANIMATION_FRAME_MS = 800L
    }
}
