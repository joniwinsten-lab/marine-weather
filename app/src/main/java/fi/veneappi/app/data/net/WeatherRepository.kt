package fi.veneappi.app.data.net

import fi.veneappi.app.data.room.ForecastCacheDao
import fi.veneappi.app.data.room.ForecastCacheEntity
import fi.veneappi.app.domain.SourceId
import fi.veneappi.app.domain.UnifiedForecast
import fi.veneappi.app.domain.UnifiedTimePoint
import fi.veneappi.app.domain.WeatherSources
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class WeatherRepository(
    private val http: WeatherHttpClient,
    private val cacheDao: ForecastCacheDao,
    private val json: Json,
) {
    suspend fun loadAll(lat: Double, lon: Double): Map<SourceId, Result<UnifiedForecast>> {
        val keyBase = "${roundKey(lat)}_${roundKey(lon)}"
        val metCached = readCache(SourceId.MET_NORWAY, keyBase)
        val smhiCached = readCache(SourceId.SMHI, keyBase)
        val fmiCached = readCache(SourceId.FMI, keyBase)

        val met =
            runCatching { http.fetchMetNorway(lat, lon) }
                .onSuccess { cacheDao.upsert(cacheRow(SourceId.MET_NORWAY, keyBase, it)) }
                .recoverCatching { ex -> metCached ?: throw ex }

        val smhi =
            runCatching { http.fetchSmhi(lat, lon) }
                .onSuccess { cacheDao.upsert(cacheRow(SourceId.SMHI, keyBase, it)) }
                .recoverCatching { ex -> smhiCached ?: throw ex }

        val fmi =
            runCatching { http.fetchFmi(lat, lon) }
                .onSuccess { cacheDao.upsert(cacheRow(SourceId.FMI, keyBase, it)) }
                .recoverCatching { ex -> fmiCached ?: throw ex }

        return mapOf(
            SourceId.MET_NORWAY to met,
            SourceId.SMHI to smhi,
            SourceId.FMI to fmi,
        )
    }

    private suspend fun readCache(
        id: SourceId,
        keyBase: String,
    ): UnifiedForecast? {
        val row = cacheDao.get("${id.name}_$keyBase") ?: return null
        return runCatching { decode(row.json) }.getOrNull()
    }

    private fun roundKey(v: Double): String = "%.3f".format(v)

    private fun cacheRow(
        id: SourceId,
        keyBase: String,
        forecast: UnifiedForecast,
    ): ForecastCacheEntity =
        ForecastCacheEntity(
            cacheKey = "${id.name}_$keyBase",
            json = encode(forecast),
            updatedAt = forecast.fetchedAtUtc,
        )

    private fun encode(f: UnifiedForecast): String =
        json.encodeToString(
            CachedForecast.serializer(),
            CachedForecast.fromDomain(f),
        )

    private fun decode(jsonText: String): UnifiedForecast =
        json.decodeFromString(CachedForecast.serializer(), jsonText).toDomain()
}

@Serializable
private data class CachedForecast(
    val sourceId: String,
    val fetchedAtUtc: Long,
    val modelInfo: String?,
    val points: List<CachedPoint>,
) {
    fun toDomain(): UnifiedForecast {
        val source =
            when (sourceId) {
                SourceId.MET_NORWAY.name -> WeatherSources.MetNorway
                SourceId.SMHI.name -> WeatherSources.Smhi
                SourceId.FMI.name -> WeatherSources.Fmi
                else -> WeatherSources.MetNorway
            }
        return UnifiedForecast(
            source = source,
            fetchedAtUtc = fetchedAtUtc,
            modelInfo = modelInfo,
            points =
                points.map {
                    UnifiedTimePoint(
                        instantUtc = it.instantUtc,
                        airTempC = it.airTempC,
                        windSpeedMs = it.windSpeedMs,
                        windFromDeg = it.windFromDeg,
                        windGustMs = it.windGustMs,
                        precipitationMmPerH = it.precipitationMmPerH,
                        thunderProbPercent = it.thunderProbPercent,
                    )
                },
        )
    }

    companion object {
        fun fromDomain(f: UnifiedForecast): CachedForecast =
            CachedForecast(
                sourceId = f.source.id.name,
                fetchedAtUtc = f.fetchedAtUtc,
                modelInfo = f.modelInfo,
                points =
                    f.points.map {
                        CachedPoint(
                            instantUtc = it.instantUtc,
                            airTempC = it.airTempC,
                            windSpeedMs = it.windSpeedMs,
                            windFromDeg = it.windFromDeg,
                            windGustMs = it.windGustMs,
                            precipitationMmPerH = it.precipitationMmPerH,
                            thunderProbPercent = it.thunderProbPercent,
                        )
                    },
            )
    }
}

@Serializable
private data class CachedPoint(
    val instantUtc: Long,
    val airTempC: Double?,
    val windSpeedMs: Double?,
    val windFromDeg: Double?,
    val windGustMs: Double?,
    val precipitationMmPerH: Double?,
    val thunderProbPercent: Double?,
)
