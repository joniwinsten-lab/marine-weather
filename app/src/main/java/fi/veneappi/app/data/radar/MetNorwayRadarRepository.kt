package fi.veneappi.app.data.radar

import fi.veneappi.app.BuildConfig
import fi.veneappi.app.data.FmiInstantFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

/**
 * MET Norway Radar 2.0 PNG composites (Nordic / Norway areas).
 * @see <a href="https://api.met.no/weatherapi/radar/2.0/documentation">MET Radar API</a>
 */
class MetNorwayRadarRepository(
    okHttpClient: OkHttpClient? = null,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    private val client =
        okHttpClient
            ?: OkHttpClient.Builder()
                .connectTimeout(25, TimeUnit.SECONDS)
                .readTimeout(25, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    val request =
                        chain.request().newBuilder()
                            .header("User-Agent", BuildConfig.WEATHER_USER_AGENT)
                            .build()
                    chain.proceed(request)
                }
                .build()

    suspend fun loadLatestOverlay(area: String = AREA_NORDIC): ActiveRadarOverlay? =
        withContext(Dispatchers.IO) {
            val frames = loadRecentFrames(area, 1)
            frames.lastOrNull()?.toActiveOverlay(area) ?: return@withContext null
        }

    suspend fun loadAnimationFrames(
        area: String = AREA_NORDIC,
        frameCount: Int = 12,
    ): List<RadarAnimationFrame> =
        withContext(Dispatchers.IO) {
            loadRecentFrames(area, frameCount)
        }

    suspend fun loadStormTimelineFrames(
        now: Instant,
        area: String = AREA_NORDIC,
    ): List<RadarAnimationFrame> =
        withContext(Dispatchers.IO) {
            buildStormTimelineFrames(now, area)
        }

    private fun buildStormTimelineFrames(
        now: Instant,
        area: String,
    ): List<RadarAnimationFrame> {
        val available = fetchAvailable() ?: return emptyList()
        val images =
            available
                .filter {
                    it.params.area == area &&
                        it.params.content == "image" &&
                        it.params.type == "reflectivity"
                }
                .sortedBy { it.params.time }
        if (images.isEmpty()) return emptyList()
        val bounds = boundsForArea(area)
        val parsed =
            images.mapNotNull { entry ->
                val t = runCatching { Instant.parse(entry.params.time) }.getOrNull() ?: return@mapNotNull null
                entry to t
            }
        return StormRadarTimeline.offsetsMinutes.map { offset ->
            val target = now.plus(offset.toLong(), ChronoUnit.MINUTES)
            val entry =
                if (offset <= 0) {
                    parsed.filter { (_, t) -> !t.isAfter(target) }.maxByOrNull { (_, t) -> t }
                } else {
                    parsed.filter { (_, t) -> !t.isBefore(target) }.minByOrNull { (_, t) -> t }
                        ?: parsed.maxByOrNull { (_, t) -> t }
                }
            if (entry != null) {
                val (img, t) = entry
                RadarAnimationFrame(
                    sourceId = RadarSourceId.MET_NORDIC,
                    kind = RadarDisplayKind.GEO_IMAGE,
                    timeIso = img.params.time,
                    timeLabel = FmiInstantFormat.toDisplayHHmm(t),
                    offsetMinutesFromNow = offset,
                    geoImageUrl = img.uri,
                    geoBounds = bounds,
                )
            } else {
                val iso = FmiInstantFormat.toParam(target)
                RadarAnimationFrame(
                    sourceId = RadarSourceId.MET_NORDIC,
                    kind = RadarDisplayKind.GEO_IMAGE,
                    timeIso = iso,
                    timeLabel = FmiInstantFormat.toDisplayHHmm(target),
                    offsetMinutesFromNow = offset,
                    geoImageUrl = null,
                    geoBounds = bounds,
                )
            }
        }
    }

    private fun loadRecentFrames(
        area: String,
        frameCount: Int,
    ): List<RadarAnimationFrame> {
        val available = fetchAvailable() ?: return emptyList()
        val images =
            available
                .filter {
                    it.params.area == area &&
                        it.params.content == "image" &&
                        it.params.type == "reflectivity"
                }
                .sortedBy { it.params.time }
        if (images.isEmpty()) return emptyList()
        val picked = images.takeLast(frameCount)
        val bounds = boundsForArea(area)
        return picked.map { entry ->
            val t = Instant.parse(entry.params.time)
            RadarAnimationFrame(
                sourceId = RadarSourceId.MET_NORDIC,
                kind = RadarDisplayKind.GEO_IMAGE,
                timeIso = entry.params.time,
                timeLabel = FmiInstantFormat.toDisplayHHmm(t),
                offsetMinutesFromNow = 0,
                geoImageUrl = entry.uri,
                geoBounds = bounds,
            )
        }
    }

    private fun RadarAnimationFrame.toActiveOverlay(area: String): ActiveRadarOverlay =
        ActiveRadarOverlay(
            sourceId = RadarSourceId.MET_NORDIC,
            kind = RadarDisplayKind.GEO_IMAGE,
            sourceLabel = "MET Norway ($area)",
            timeLabel = timeLabel,
            geoImageUrl = geoImageUrl,
            geoBounds = geoBounds,
        )

    private fun fetchAvailable(): List<MetRadarEntry>? {
        val request =
            Request.Builder()
                .url("https://api.met.no/weatherapi/radar/2.0/available.json")
                .build()
        val body =
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                response.body?.string().orEmpty()
            }
        return runCatching {
            json.decodeFromString<List<MetRadarEntry>>(body)
        }.getOrNull()
    }

    private fun boundsForArea(area: String): RadarGeoBounds =
        when (area) {
            AREA_SOUTHERN_NORWAY -> SOUTHERN_NORWAY_BOUNDS
            else -> NORDIC_BOUNDS
        }

    companion object {
        const val AREA_NORDIC = "nordic"
        const val AREA_SOUTHERN_NORWAY = "southern_norway"
        const val ATTRIBUTION =
            "Radar © MET Norway (NLOD 2.0 / CC BY 4.0). Not for operational navigation."

        /** Approximate WGS84 extent for Nordic composite reflectivity PNG. */
        val NORDIC_BOUNDS =
            RadarGeoBounds(
                northLat = 72.0,
                westLon = 4.0,
                southLat = 54.5,
                eastLon = 35.0,
            )

        val SOUTHERN_NORWAY_BOUNDS =
            RadarGeoBounds(
                northLat = 65.0,
                westLon = 4.0,
                southLat = 57.5,
                eastLon = 14.0,
            )

    }
}

@Serializable
private data class MetRadarEntry(
    val uri: String,
    val params: MetRadarParams,
)

@Serializable
private data class MetRadarParams(
    val area: String,
    val content: String,
    val time: String,
    val type: String,
)
