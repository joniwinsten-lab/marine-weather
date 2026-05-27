package fi.veneappi.app.data.offline

import fi.veneappi.app.data.marine.MarineTextRepository
import fi.veneappi.app.data.net.WeatherRepository
import fi.veneappi.app.data.room.OfflineAreaPackDao
import fi.veneappi.app.data.room.OfflineAreaPackEntity
import fi.veneappi.app.domain.GeoMath
import fi.veneappi.app.ui.map.MapTileWarmup

class OfflineAreaPackDownloader(
    private val weatherRepository: WeatherRepository,
    private val mapTileWarmup: MapTileWarmup,
    private val marineTextRepository: MarineTextRepository,
    private val packDao: OfflineAreaPackDao,
) {
    data class Progress(
        val stepKey: String,
        val current: Int,
        val total: Int,
    )

    data class Result(
        val packId: Long,
        val weatherSamples: Int,
        val routeVertices: Int,
    )

    suspend fun downloadRoutePack(
        routeGeometry: List<Pair<Double, Double>>,
        marineTitlesByCountry: Map<String, String>,
        onProgress: (Progress) -> Unit,
    ): Result {
        require(routeGeometry.size >= 2) { "route needs at least two points" }
        val samples = GeoMath.samplePointsAlongRoute(routeGeometry)
        val bounds =
            GeoMath.boundsAroundPolyline(routeGeometry, paddingDeg = 0.15)
                ?: error("bounds")
        val totalSteps = samples.size + 2
        var step = 0

        fun report(key: String) {
            onProgress(Progress(key, step.coerceAtMost(totalSteps), totalSteps))
        }

        report("tiles")
        mapTileWarmup.warmRouteCorridor(routeGeometry)
        step++

        report("marine")
        val mid = samples[samples.size / 2]
        runCatching {
            marineTextRepository.loadOverview(
                lat = mid.first,
                lon = mid.second,
                titlesByCountry = marineTitlesByCountry,
            )
        }
        step++

        for ((i, pt) in samples.withIndex()) {
            onProgress(Progress("weather", i + 1, samples.size))
            weatherRepository.loadAllWithReport(pt.first, pt.second)
        }
        step = totalSteps

        val label =
            buildString {
                append("%.2f".format(routeGeometry.first().first))
                append(",")
                append("%.2f".format(routeGeometry.first().second))
                append(" → ")
                append("%.2f".format(routeGeometry.last().first))
                append(",")
                append("%.2f".format(routeGeometry.last().second))
            }
        val id =
            packDao.insert(
                OfflineAreaPackEntity(
                    createdAtMs = System.currentTimeMillis(),
                    routePointCount = routeGeometry.size,
                    weatherSampleCount = samples.size,
                    minLat = bounds.minLat,
                    minLon = bounds.minLon,
                    maxLat = bounds.maxLat,
                    maxLon = bounds.maxLon,
                    label = label,
                ),
            )
        onProgress(Progress("done", totalSteps, totalSteps))
        return Result(
            packId = id,
            weatherSamples = samples.size,
            routeVertices = routeGeometry.size,
        )
    }
}
