package fi.veneappi.app.data.radar

import fi.veneappi.app.BuildConfig
import fi.veneappi.app.data.FmiInstantFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

/**
 * SMHI open radar PNG (EPSG:3006 QCOMP). Listing is public; file download may return 401 without agreement.
 */
class SmhiRadarRepository(
    okHttpClient: OkHttpClient? = null,
) {
    private val client =
        okHttpClient
            ?: OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    val request =
                        chain.request().newBuilder()
                            .header("User-Agent", BuildConfig.WEATHER_USER_AGENT)
                            .build()
                    chain.proceed(request)
                }
                .build()

    suspend fun loadLatestOverlay(): ActiveRadarOverlay? =
        withContext(Dispatchers.IO) {
            val fileName = fetchLatestFileNameFromIndex() ?: return@withContext null
            val url = "$BASE_URL/$fileName"
            if (!probeImageOk(url)) return@withContext null
            val time = parseTimeFromFileName(fileName)
            ActiveRadarOverlay(
                sourceId = RadarSourceId.SMHI,
                kind = RadarDisplayKind.GEO_IMAGE,
                sourceLabel = "SMHI",
                timeLabel = time?.let { FmiInstantFormat.toDisplayHHmm(it) } ?: fileName,
                geoImageUrl = url,
                geoBounds = SMHI_BOUNDS,
            )
        }

    suspend fun loadStormTimelineFrames(now: Instant): List<RadarAnimationFrame> =
        withContext(Dispatchers.IO) {
            buildStormTimelineFrames(now)
        }

    private fun buildStormTimelineFrames(now: Instant): List<RadarAnimationFrame> {
        val names = fetchRecentFileNamesFromIndex(48)
        val parsed =
            names.mapNotNull { fileName ->
                val t = parseTimeFromFileName(fileName) ?: return@mapNotNull null
                fileName to t
            }.sortedBy { it.second }
        if (parsed.isEmpty()) return emptyList()
        return StormRadarTimeline.offsetsMinutes.map { offset ->
            val target = now.plus(offset.toLong(), ChronoUnit.MINUTES)
            val match =
                if (offset <= 0) {
                    parsed.filter { (_, t) -> !t.isAfter(target) }.maxByOrNull { (_, t) -> t }
                } else {
                    parsed.filter { (_, t) -> !t.isBefore(target) }.minByOrNull { (_, t) -> t }
                        ?: parsed.maxByOrNull { (_, t) -> t }
                }
            if (match != null) {
                val (fileName, t) = match
                val url = "$BASE_URL/$fileName"
                RadarAnimationFrame(
                    sourceId = RadarSourceId.SMHI,
                    kind = RadarDisplayKind.GEO_IMAGE,
                    timeIso = t.toString(),
                    timeLabel = FmiInstantFormat.toDisplayHHmm(t),
                    offsetMinutesFromNow = offset,
                    geoImageUrl = if (probeImageOk(url)) url else null,
                    geoBounds = SMHI_BOUNDS,
                )
            } else {
                val iso = FmiInstantFormat.toParam(target)
                RadarAnimationFrame(
                    sourceId = RadarSourceId.SMHI,
                    kind = RadarDisplayKind.GEO_IMAGE,
                    timeIso = iso,
                    timeLabel = FmiInstantFormat.toDisplayHHmm(target),
                    offsetMinutesFromNow = offset,
                    geoImageUrl = null,
                    geoBounds = SMHI_BOUNDS,
                )
            }
        }
    }

    suspend fun loadAnimationFrames(frameCount: Int = 12): List<RadarAnimationFrame> =
        withContext(Dispatchers.IO) {
            val names = fetchRecentFileNamesFromIndex(frameCount)
            names.mapNotNull { fileName ->
                val url = "$BASE_URL/$fileName"
                if (!probeImageOk(url)) return@mapNotNull null
                val t = parseTimeFromFileName(fileName)
                RadarAnimationFrame(
                    sourceId = RadarSourceId.SMHI,
                    kind = RadarDisplayKind.GEO_IMAGE,
                    timeIso = t?.toString() ?: "",
                    timeLabel = t?.let { FmiInstantFormat.toDisplayHHmm(it) } ?: fileName,
                    offsetMinutesFromNow = 0,
                    geoImageUrl = url,
                    geoBounds = SMHI_BOUNDS,
                )
            }
        }

    private fun fetchLatestFileNameFromIndex(): String? =
        fetchRecentFileNamesFromIndex(1).lastOrNull()

    private fun fetchRecentFileNamesFromIndex(limit: Int): List<String> {
        val request = Request.Builder().url("$BASE_URL/").build()
        val html =
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                response.body?.string().orEmpty()
            }
        val regex = Regex("""RADAR_SWE_EPSG3006_QCOMP_(\d{12})\.png""")
        return regex
            .findAll(html)
            .map { it.value }
            .distinct()
            .sorted()
            .toList()
            .takeLast(limit)
    }

    private fun probeImageOk(url: String): Boolean {
        val request =
            Request.Builder()
                .url(url)
                .header("Range", "bytes=0-0")
                .build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                response.isSuccessful || response.code == 206
            }
        }.getOrDefault(false)
    }

    private fun parseTimeFromFileName(fileName: String): Instant? {
        val m = Regex("""RADAR_SWE_EPSG3006_QCOMP_(\d{12})\.png""").find(fileName) ?: return null
        val raw = m.groupValues[1]
        return runCatching {
            val ldt =
                LocalDateTime.parse(
                    raw,
                    DateTimeFormatter.ofPattern("yyyyMMddHHmm"),
                )
            ldt.toInstant(ZoneOffset.UTC)
        }.getOrNull()
    }

    companion object {
        private const val BASE_URL = "https://data-download.smhi.se/data/meteorology/radar"
        const val ATTRIBUTION =
            "Radar © SMHI (CC BY 4.0). Not for operational navigation."

        /** SWEREF99 TM extent approximated in WGS84 for Sweden composite. */
        val SMHI_BOUNDS =
            RadarGeoBounds(
                northLat = 69.5,
                westLon = 10.0,
                southLat = 54.5,
                eastLon = 25.0,
            )

    }
}
