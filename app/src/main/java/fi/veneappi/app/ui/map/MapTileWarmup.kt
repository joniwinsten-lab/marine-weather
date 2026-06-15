package fi.veneappi.app.ui.map

import fi.veneappi.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

/**
 * Primes shared HTTP disk cache ([MapHttp]) for the compare-map style and nearby tiles
 * while the splash is visible. MapLibre uses the same OkHttp client via [HttpRequestUtil].
 */
class MapTileWarmup(
    okHttpClient: OkHttpClient? = null,
) {
    private companion object {
        const val MAX_TILES_PER_ZOOM = 120
    }
    private val client =
        okHttpClient
            ?: OkHttpClient.Builder()
                .connectTimeout(12, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(12, java.util.concurrent.TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    val request =
                        chain.request().newBuilder()
                            .header("User-Agent", BuildConfig.WEATHER_USER_AGENT)
                            .build()
                    chain.proceed(request)
                }
                .build()

    suspend fun warm(
        lat: Double,
        lon: Double,
        zoom: Int = MapConfig.DEFAULT_COMPARE_ZOOM.toInt(),
    ) {
        if (!lat.isFinite() || !lon.isFinite()) return
        withContext(Dispatchers.IO) {
            runCatching { fetch(MapConfig.STYLE_URL) }
            runCatching { fetch("https://tiles.openfreemap.org/planet") }
            warmTileGrid(lat, lon, zoom)
        }
    }

    /** Prefetch tiles covering a route corridor (HTTP disk cache for MapLibre). */
    suspend fun warmRouteCorridor(
        routePoints: List<Pair<Double, Double>>,
        minZoom: Int = 8,
        maxZoom: Int = 13,
    ) {
        if (routePoints.size < 2) return
        val bounds =
            fi.veneappi.app.domain.GeoMath.boundsAroundPolyline(routePoints, paddingDeg = 0.15)
                ?: return
        withContext(Dispatchers.IO) {
            runCatching { fetch(MapConfig.STYLE_URL) }
            for (z in minZoom..maxZoom) {
                val (xMin, yMin) = latLonToTileXY(bounds.maxLat, bounds.minLon, z)
                val (xMax, yMax) = latLonToTileXY(bounds.minLat, bounds.maxLon, z)
                val xLo = minOf(xMin, xMax).coerceAtLeast(0)
                val xHi = maxOf(xMin, xMax)
                val yLo = minOf(yMin, yMax).coerceAtLeast(0)
                val yHi = maxOf(yMin, yMax)
                var tileBudget = MAX_TILES_PER_ZOOM
                for (x in xLo..xHi) {
                    if (tileBudget <= 0) break
                    for (y in yLo..yHi) {
                        if (tileBudget-- <= 0) break
                        val tileUrl =
                            MapConfig.VECTOR_TILES_TEMPLATE
                                .replace("{z}", z.toString())
                                .replace("{x}", x.toString())
                                .replace("{y}", y.toString())
                        runCatching { fetch(tileUrl) }
                        if (z <= 6) {
                            val rasterUrl =
                                MapConfig.NE2_RASTER_TEMPLATE
                                    .replace("{z}", z.toString())
                                    .replace("{x}", x.toString())
                                    .replace("{y}", y.toString())
                            runCatching { fetch(rasterUrl) }
                        }
                    }
                }
            }
        }
    }

    private fun warmTileGrid(
        lat: Double,
        lon: Double,
        zoom: Int,
    ) {
        for (z in (zoom - 1)..(zoom + 1)) {
            if (z < 0) continue
            val (tx, ty) = latLonToTileXY(lat, lon, z)
            for (dx in -1..1) {
                for (dy in -1..1) {
                    val x = tx + dx
                    val y = ty + dy
                    if (x < 0 || y < 0) continue
                    val tileUrl =
                        MapConfig.VECTOR_TILES_TEMPLATE
                            .replace("{z}", z.toString())
                            .replace("{x}", x.toString())
                            .replace("{y}", y.toString())
                    runCatching { fetch(tileUrl) }
                    if (z <= 6) {
                        val rasterUrl =
                            MapConfig.NE2_RASTER_TEMPLATE
                                .replace("{z}", z.toString())
                                .replace("{x}", x.toString())
                                .replace("{y}", y.toString())
                        runCatching { fetch(rasterUrl) }
                    }
                }
            }
        }
    }

    private fun fetch(url: String) {
        client.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
            response.body?.close()
        }
    }

    internal fun latLonToTileXY(
        lat: Double,
        lon: Double,
        zoom: Int,
    ): Pair<Int, Int> {
        val n = 1 shl zoom.coerceIn(0, 22)
        val x = floor((lon + 180.0) / 360.0 * n).toInt().coerceIn(0, n - 1)
        val latRad = Math.toRadians(lat)
        val y =
            floor(
                (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / Math.PI) / 2.0 * n,
            ).toInt().coerceIn(0, n - 1)
        return x to y
    }
}
