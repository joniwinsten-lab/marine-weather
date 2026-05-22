package fi.veneappi.app.data.radar

import android.content.Context
import fi.veneappi.app.BuildConfig
import fi.veneappi.app.data.FmiInstantFormat
import fi.veneappi.app.data.met.MetFeature
import java.io.File
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class ForecastRasterOverlay(
    val fileUri: String,
    val bounds: RadarGeoBounds,
)

/**
 * Radar-style precipitation forecast from MET Norway Nowcast (≈2 h optical-flow)
 * and Locationforecast for longer steps. Open radar tile APIs have no future times.
 */
class MetNowcastRadarRepository(
    private val appContext: Context,
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

    suspend fun buildForecastOverlays(
        now: Instant,
        lat: Double,
        lon: Double,
    ): Map<Int, ForecastRasterOverlay> =
        withContext(Dispatchers.IO) {
            val grid = fetchNowcastGrid(lat, lon)
            val longRange = fetchLocationForecastHourly(lat, lon)
            val bounds = grid.bounds
            val dir = File(appContext.cacheDir, "storm_nowcast").apply { mkdirs() }
            StormRadarTimeline.offsetsMinutes
                .filter { it > 0 }
                .mapNotNull { offset ->
                    val target = now.plus(offset.toLong(), ChronoUnit.MINUTES)
                    val rates =
                        if (offset <= NOWCAST_MAX_OFFSET_MIN) {
                            grid.ratesAt(target)
                        } else {
                            longRange.ratesAt(target, fallback = grid.ratesAt(now))
                        }
                    val png = PrecipRateRaster.renderGrid(rates)
                    val file = File(dir, "fc_${offset}_${now.epochSecond}.png")
                    file.writeBytes(png)
                    offset to
                        ForecastRasterOverlay(
                            fileUri = file.toURI().toString(),
                            bounds = bounds,
                        )
                }.toMap()
        }

    private suspend fun fetchNowcastGrid(
        lat: Double,
        lon: Double,
    ): NowcastGrid =
        coroutineScope {
            val halfSpan = 0.5
            val step = (halfSpan * 2) / (PrecipRateRaster.GRID_CELLS - 1)
            val lats =
                (0 until PrecipRateRaster.GRID_CELLS).map { i ->
                    lat - halfSpan + i * step
                }
            val lons =
                (0 until PrecipRateRaster.GRID_CELLS).map { i ->
                    lon - halfSpan + i * step
                }
            val series =
                Array(PrecipRateRaster.GRID_CELLS) { row ->
                    Array(PrecipRateRaster.GRID_CELLS) { col ->
                        async { fetchNowcastSeries(lats[row], lons[col]) }
                    }
                }
            val resolved =
                Array(PrecipRateRaster.GRID_CELLS) { row ->
                    Array(PrecipRateRaster.GRID_CELLS) { col ->
                        series[row][col].await()
                    }
                }
            NowcastGrid(
                bounds =
                    RadarGeoBounds(
                        northLat = lats.last(),
                        westLon = lons.first(),
                        southLat = lats.first(),
                        eastLon = lons.last(),
                    ),
                series = resolved,
            )
        }

    private fun fetchNowcastSeries(
        lat: Double,
        lon: Double,
    ): List<NowcastStep> {
        val url =
            "https://api.met.no/weatherapi/nowcast/2.0/complete"
                .toHttpUrl()
                .newBuilder()
                .addQueryParameter("lat", roundCoord(lat, 4).toString())
                .addQueryParameter("lon", roundCoord(lon, 4).toString())
                .build()
        val body = executeGet(url.toString())
        return parseNowcastSeries(body)
    }

    private fun parseNowcastSeries(body: String): List<NowcastStep> {
        val root = json.parseToJsonElement(body).jsonObject
        val timeseries = root["properties"]?.jsonObject?.get("timeseries")?.jsonArray ?: return emptyList()
        return timeseries.mapNotNull { el ->
            val obj = el.jsonObject
            val time = obj["time"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val instant = obj["data"]?.jsonObject?.get("instant")?.jsonObject ?: return@mapNotNull null
            val rate =
                instant["details"]?.jsonObject
                    ?.get("precipitation_rate")
                    ?.jsonPrimitive
                    ?.doubleOrNull
            val t = runCatching { Instant.parse(time) }.getOrNull() ?: return@mapNotNull null
            NowcastStep(t, rate ?: 0.0)
        }
    }

    private suspend fun fetchLocationForecastHourly(
        lat: Double,
        lon: Double,
    ): LongRangeForecast {
        val url =
            "https://api.met.no/weatherapi/locationforecast/2.0/compact"
                .toHttpUrl()
                .newBuilder()
                .addQueryParameter("lat", roundCoord(lat, 4).toString())
                .addQueryParameter("lon", roundCoord(lon, 4).toString())
                .build()
        val body = executeGet(url.toString())
        val feature = json.decodeFromString(MetFeature.serializer(), body)
        val steps =
            feature.properties.timeseries.mapNotNull { ts ->
                val t = runCatching { Instant.parse(ts.time) }.getOrNull() ?: return@mapNotNull null
                val mm = ts.data.next1Hours?.details?.precipitationAmount ?: 0.0
                NowcastStep(t, mm)
            }
        return LongRangeForecast(steps)
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

    private inner class NowcastGrid(
        val bounds: RadarGeoBounds,
        val series: Array<Array<List<NowcastStep>>>,
    ) {
        fun ratesAt(target: Instant): Array<DoubleArray> {
            val cells = PrecipRateRaster.GRID_CELLS
            return Array(cells) { row ->
                DoubleArray(cells) { col ->
                    this@MetNowcastRadarRepository.nearestPrecipRate(series[row][col], target)
                }
            }
        }
    }

    private inner class LongRangeForecast(
        val steps: List<NowcastStep>,
    ) {
        fun ratesAt(
            target: Instant,
            fallback: Array<DoubleArray>,
        ): Array<DoubleArray> {
            val rate = this@MetNowcastRadarRepository.nearestPrecipRate(steps, target)
            return Array(PrecipRateRaster.GRID_CELLS) { row ->
                DoubleArray(PrecipRateRaster.GRID_CELLS) { rate }
            }
        }
    }

    private fun nearestPrecipRate(
        steps: List<NowcastStep>,
        target: Instant,
    ): Double {
        if (steps.isEmpty()) return 0.0
        return steps.minByOrNull { kotlin.math.abs(it.time.toEpochMilli() - target.toEpochMilli()) }
            ?.rateMmPerH
            ?: 0.0
    }

    private data class NowcastStep(
        val time: Instant,
        val rateMmPerH: Double,
    )

    companion object {
        /** MET Nowcast radar extrapolation horizon (minutes). */
        const val NOWCAST_MAX_OFFSET_MIN = 120
    }
}
