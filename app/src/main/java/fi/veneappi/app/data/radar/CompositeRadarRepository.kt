package fi.veneappi.app.data.radar

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.time.Instant
import fi.veneappi.app.data.FmiInstantFormat

class CompositeRadarRepository(
    private val fmiRadarRepository: FmiRadarRepository,
    private val metNorwayRadarRepository: MetNorwayRadarRepository,
    private val smhiRadarRepository: SmhiRadarRepository,
    private val fmiForecastRadarRepository: FmiForecastRadarRepository,
) {
    suspend fun loadActiveOverlay(
        lat: Double,
        lon: Double,
    ): ActiveRadarOverlay? =
        withContext(Dispatchers.IO) {
            when (RadarRegionSelector.preferredSource(lat, lon)) {
                RadarSourceId.FMI -> loadFmiOverlay()
                RadarSourceId.MET_NORDIC ->
                    metNorwayRadarRepository.loadLatestOverlay()
                        ?: loadFmiOverlay()
                RadarSourceId.SMHI ->
                    smhiRadarRepository.loadLatestOverlay()
                        ?: metNorwayRadarRepository.loadLatestOverlay()
                        ?: loadFmiOverlay()
            }
        }

    suspend fun loadAnimation(
        lat: Double,
        lon: Double,
    ): List<RadarAnimationFrame> =
        withContext(Dispatchers.IO) {
            coroutineScope {
                val now = fmiRadarRepository.latestRadarInstant() ?: Instant.now()
                val forecastOverlays =
                    async {
                        runCatching {
                            fmiForecastRadarRepository.buildForecastOverlays(now, lat, lon)
                        }.getOrElse { emptyMap() }
                    }
                val observationFrames = async { loadFmiStormTimeline(now) }
                enrichStormTimelineWithForecast(
                    observationFrames.await(),
                    forecastOverlays.await(),
                )
            }
        }

    private suspend fun loadFmiOverlay(): ActiveRadarOverlay? {
        val end = fmiRadarRepository.latestRadarInstant() ?: Instant.now()
        return ActiveRadarOverlay(
            sourceId = RadarSourceId.FMI,
            kind = RadarDisplayKind.WMS_TILES,
            sourceLabel = "FMI",
            timeLabel = FmiInstantFormat.toDisplayHHmm(end),
            // Latest frame only — omit TIME= on tiles (some GPUs crash when swapping TIME URLs).
            wmsTileUrlTemplate = FmiRadarConfig.WMS_TILE_URL_TEMPLATE,
            animationFrames = emptyList(),
        )
    }

    private suspend fun loadFmiStormTimeline(now: Instant): List<RadarAnimationFrame> =
        FmiRadarTimeSeries.buildStormTimelineFrames(now)

    private suspend fun loadMetStormTimeline(now: Instant): List<RadarAnimationFrame> =
        metNorwayRadarRepository.loadStormTimelineFrames(now)
}
