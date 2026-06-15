package fi.veneappi.app.data.radar

import fi.veneappi.app.data.lightning.CompositeLightningRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Background-loads storm radar timeline + lightning so the Radar tab opens with warm cache.
 */
class StormRadarPrefetcher(
    private val radarRepository: CompositeRadarRepository,
    private val lightningRepository: CompositeLightningRepository,
) {
    private val mutex = Mutex()
    private var cache: StormRadarPrefetch? = null
    private var scheduledJob: Job? = null
    @Volatile
    private var inflightKey: String? = null

    fun schedule(
        scope: CoroutineScope,
        lat: Double,
        lon: Double,
    ) {
        if (!lat.isFinite() || !lon.isFinite()) return
        val key = StormRadarPrefetch.locationKey(lat, lon)
        scheduledJob?.cancel()
        scheduledJob =
            scope.launch {
                runCatching {
                    delay(PREFETCH_DEBOUNCE_MS)
                    val snapshot = peek(lat, lon)
                    if (snapshot != null && !snapshot.isExpired()) return@runCatching
                    if (inflightKey == key) return@runCatching
                    fetchAndCache(lat, lon)
                }
            }
    }

    suspend fun peek(
        lat: Double,
        lon: Double,
    ): StormRadarPrefetch? =
        mutex.withLock {
            val entry = cache ?: return@withLock null
            if (entry.locationKey != StormRadarPrefetch.locationKey(lat, lon)) return@withLock null
            entry
        }

    suspend fun fetchAndCache(
        lat: Double,
        lon: Double,
    ): StormRadarPrefetch {
        val key = StormRadarPrefetch.locationKey(lat, lon)
        if (inflightKey == key) {
            mutex.withLock { cache?.takeIf { it.locationKey == key } }?.let { return it }
        }
        inflightKey = key
        try {
            val bundle = loadBundle(lat, lon)
            mutex.withLock { cache = bundle }
            return bundle
        } finally {
            if (inflightKey == key) {
                inflightKey = null
            }
        }
    }

    private suspend fun loadBundle(
        lat: Double,
        lon: Double,
    ): StormRadarPrefetch =
        withContext(Dispatchers.IO) {
            val latestOverlay =
                runCatching {
                    radarRepository.loadActiveOverlay(lat, lon)
                }.getOrNull()
            val sourceLabel = latestOverlay?.sourceLabel ?: "FMI"
            val frames =
                runCatching {
                    radarRepository.loadAnimation(lat, lon)
                }.getOrElse { emptyList() }
            val lightningResult = lightningRepository.fetchMergedStrikes()
            val now = System.currentTimeMillis()
            StormRadarPrefetch(
                locationKey = StormRadarPrefetch.locationKey(lat, lon),
                fetchedAtMs = now,
                frames = frames,
                latestOverlay = latestOverlay,
                sourceLabel = sourceLabel,
                lightningStrikes = lightningResult.getOrElse { emptyList() },
                lightningFetchedAtMs = now,
                lightningError = lightningResult.exceptionOrNull()?.message,
            )
        }

    companion object {
        private const val PREFETCH_DEBOUNCE_MS = 1_500L
    }
}
