package fi.veneappi.app.data.radar

import android.content.Context
import fi.veneappi.app.BuildConfig
import fi.veneappi.app.data.FmiInstantFormat
import fi.veneappi.app.data.fmi.FmiPrecipitationMultipointParser
import fi.veneappi.app.data.fmi.FmiPrecipitationStep
import java.io.File
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
/**
 * FMI precipitation forecast on the storm timeline.
 * Prefers one HARMONIE GRIB download (high resolution); falls back to WFS multipoint grid.
 */
class FmiForecastRadarRepository(
    private val appContext: Context,
    okHttpClient: OkHttpClient? = null,
) {
    @Volatile
    private var gribCache: GribCache? = null

    private val client =
        okHttpClient
            ?: OkHttpClient.Builder()
                .connectTimeout(45, TimeUnit.SECONDS)
                .readTimeout(45, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    val request =
                        chain.request().newBuilder()
                            .header("User-Agent", BuildConfig.WEATHER_USER_AGENT)
                            .build()
                    chain.proceed(request)
                }
                .build()

    suspend fun buildForecastOverlays(
        now: Instant,
        lat: Double,
        lon: Double,
    ): Map<Int, ForecastRasterOverlay> =
        withContext(Dispatchers.IO) {
            val halfSpan = GRID_HALF_SPAN_DEG
            val bounds =
                RadarGeoBounds(
                    northLat = lat + halfSpan,
                    westLon = lon - halfSpan,
                    southLat = lat - halfSpan,
                    eastLon = lon + halfSpan,
                )
            val gribFields =
                runCatching {
                    fetchHarmonieGribFields(now, bounds)
                }.getOrElse { emptyList() }
            if (gribFields.isNotEmpty()) {
                buildOverlaysFromGrib(now, gribFields, bounds)
            } else {
                val grid = fetchPrecipitationGridWfs(lat, lon, now, bounds)
                buildOverlaysFromWfsGrid(now, grid)
            }
        }

    private fun buildOverlaysFromGrib(
        now: Instant,
        fields: List<FmiHarmonieGribParser.PrecipField>,
        bounds: RadarGeoBounds,
    ): Map<Int, ForecastRasterOverlay> {
        val dir = File(appContext.cacheDir, "storm_fmi_forecast").apply { mkdirs() }
        return StormRadarTimeline.offsetsMinutes
            .filter { it > 0 }
            .mapNotNull { offset ->
                val target = now.plus(offset.toLong(), ChronoUnit.MINUTES)
                val rates =
                    PrecipTimelineInterpolator.gribRatesAt(fields, target)
                        ?: return@mapNotNull null
                if (!PrecipRateRaster.hasSignificantPrecip(rates)) return@mapNotNull null
                val png =
                    PrecipRateRaster.renderGrid(
                        rates,
                        widthPx = FORECAST_RASTER_PX,
                        heightPx = FORECAST_RASTER_PX,
                        gridSmoothPasses = 1,
                    )
                val file = File(dir, "fc_grib_${offset}_${now.epochSecond}.png")
                file.writeBytes(png)
                offset to ForecastRasterOverlay(fileUri = file.toURI().toString(), bounds = bounds)
            }.toMap()
    }

    private fun buildOverlaysFromWfsGrid(
        now: Instant,
        grid: PrecipGrid,
    ): Map<Int, ForecastRasterOverlay> {
        val dir = File(appContext.cacheDir, "storm_fmi_forecast").apply { mkdirs() }
        return StormRadarTimeline.offsetsMinutes
            .filter { it > 0 }
            .mapNotNull { offset ->
                val target = now.plus(offset.toLong(), ChronoUnit.MINUTES)
                val rates =
                    PrecipTimelineInterpolator.wfsRatesAt(
                        grid.series,
                        target,
                        grid.stepMinutes,
                    )
                if (!PrecipRateRaster.hasSignificantPrecip(rates)) return@mapNotNull null
                val png =
                    PrecipRateRaster.renderGrid(
                        rates,
                        widthPx = FORECAST_RASTER_PX,
                        heightPx = FORECAST_RASTER_PX,
                        gridSmoothPasses = 1,
                    )
                val file = File(dir, "fc_${offset}_${now.epochSecond}.png")
                file.writeBytes(png)
                offset to
                    ForecastRasterOverlay(
                        fileUri = file.toURI().toString(),
                        bounds = grid.bounds,
                    )
            }.toMap()
    }

    private fun fetchHarmonieGribFields(
        now: Instant,
        bounds: RadarGeoBounds,
    ): List<FmiHarmonieGribParser.PrecipField> {
        val cacheKey = boundsCacheKey(bounds)
        val minuteBucket = now.epochSecond / 60
        gribCache?.let { cached ->
            if (cached.minuteBucket == minuteBucket && cached.boundsKey == cacheKey) {
                return cached.fields
            }
        }
        val start = now
        val end = now.plus(StormRadarTimeline.HORIZON_MINUTES.toLong(), ChronoUnit.MINUTES)
        val origin = now.truncatedTo(ChronoUnit.DAYS)
        val url =
            FmiHarmonieGribParser.buildDownloadUrl(
                west = bounds.westLon,
                south = bounds.southLat,
                east = bounds.eastLon,
                north = bounds.northLat,
                start = start,
                end = end,
                originTime = origin,
            )
        val bytes =
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) error("GRIB HTTP ${response.code}")
                response.body?.bytes() ?: error("empty GRIB body")
            }
        if (bytes.size < 100 || bytes[0].toInt() != 'G'.code) return emptyList()
        val fields = FmiHarmonieGribParser.parseAll(bytes)
        gribCache = GribCache(minuteBucket, cacheKey, fields)
        return fields
    }

    private fun boundsCacheKey(bounds: RadarGeoBounds): String =
        listOf(
            (bounds.westLon * 10).roundToInt(),
            (bounds.southLat * 10).roundToInt(),
            (bounds.eastLon * 10).roundToInt(),
            (bounds.northLat * 10).roundToInt(),
        ).joinToString(",")

    private suspend fun fetchPrecipitationGridWfs(
        lat: Double,
        lon: Double,
        now: Instant,
        bounds: RadarGeoBounds,
    ): PrecipGrid =
        coroutineScope {
            val halfSpan = GRID_HALF_SPAN_DEG
            val step = (halfSpan * 2) / (WFS_GRID_CELLS - 1)
            val lats =
                (0 until WFS_GRID_CELLS).map { i ->
                    lat - halfSpan + i * step
                }
            val lons =
                (0 until WFS_GRID_CELLS).map { i ->
                    lon - halfSpan + i * step
                }
            val start = now.minus(StormRadarTimeline.HORIZON_MINUTES.toLong(), ChronoUnit.MINUTES)
            val end = now.plus(StormRadarTimeline.HORIZON_MINUTES.toLong(), ChronoUnit.MINUTES)
            val series =
                Array(WFS_GRID_CELLS) { row ->
                    Array(WFS_GRID_CELLS) { col ->
                        async {
                            wfsSemaphore.withPermit {
                                fetchPointSeries(lats[row], lons[col], start, end)
                            }
                        }
                    }
                }
            val resolved =
                Array(WFS_GRID_CELLS) { row ->
                    Array(WFS_GRID_CELLS) { col ->
                        series[row][col].await()
                    }
                }
            PrecipGrid(
                bounds = bounds,
                series = resolved,
                stepMinutes = TIMESTEP_MINUTES,
            )
        }

    private fun fetchPointSeries(
        lat: Double,
        lon: Double,
        start: Instant,
        end: Instant,
    ): List<FmiPrecipitationStep> {
        val edited =
            runCatching {
                fetchMultipointSeries(STORED_QUERY_EDITED, lat, lon, start, end)
            }.getOrElse { emptyList() }
        if (edited.isNotEmpty() && edited.any { it.amountMm > 0 }) return edited
        return fetchMultipointSeries(STORED_QUERY_MEPS, lat, lon, start, end)
    }

    private fun fetchMultipointSeries(
        storedQueryId: String,
        lat: Double,
        lon: Double,
        start: Instant,
        end: Instant,
    ): List<FmiPrecipitationStep> {
        val url =
            WFS_BASE
                .toHttpUrl()
                .newBuilder()
                .addQueryParameter("request", "getFeature")
                .addQueryParameter("storedquery_id", storedQueryId)
                .addQueryParameter("latlon", "${roundCoord(lat, 4)},${roundCoord(lon, 4)}")
                .addQueryParameter("parameters", "PrecipitationAmount")
                .addQueryParameter("timestep", TIMESTEP_MINUTES.toString())
                .addQueryParameter("starttime", FmiInstantFormat.toParam(start))
                .addQueryParameter("endtime", FmiInstantFormat.toParam(end))
                .build()
        val xml = executeGet(url.toString())
        return FmiPrecipitationMultipointParser.parse(xml)
    }

    private fun executeGet(url: String): String {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code} for $url")
            return response.body?.string().orEmpty()
        }
    }

    private fun roundCoord(
        value: Double,
        decimals: Int,
    ): Double {
        val factor = Math.pow(10.0, decimals.toDouble())
        return kotlin.math.round(value * factor) / factor
    }

    private data class PrecipGrid(
        val bounds: RadarGeoBounds,
        val series: Array<Array<List<FmiPrecipitationStep>>>,
        val stepMinutes: Int,
    )

    private data class GribCache(
        val minuteBucket: Long,
        val boundsKey: String,
        val fields: List<FmiHarmonieGribParser.PrecipField>,
    )

    companion object {
        private val wfsSemaphore = Semaphore(6)

        private const val GRID_HALF_SPAN_DEG = 4.5
        private const val WFS_GRID_CELLS = 9
        private const val FORECAST_RASTER_PX = 512

        private const val WFS_BASE = "https://opendata.fmi.fi/wfs"
        private const val STORED_QUERY_EDITED = "fmi::forecast::edited::weather::scandinavia::point::multipointcoverage"
        private const val STORED_QUERY_MEPS = "fmi::forecast::meps::surface::point::multipointcoverage"
        private const val TIMESTEP_MINUTES = 30
    }
}
