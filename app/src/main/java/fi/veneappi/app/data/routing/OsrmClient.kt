package fi.veneappi.app.data.routing

import fi.veneappi.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object OsrmClient {
    private val client =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()

    private val json =
        Json {
            ignoreUnknownKeys = true
        }

    suspend fun fetchDrivingGeometry(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double,
    ): List<Pair<Double, Double>>? {
        if (!BuildConfig.ENABLE_OSRM_DEMO) return null
        return withContext(Dispatchers.IO) {
            val url =
                "https://router.project-osrm.org/route/v1/driving/$lon1,$lat1;$lon2,$lat2"
                    .toHttpUrl()
                    .newBuilder()
                    .addQueryParameter("overview", "full")
                    .addQueryParameter("geometries", "geojson")
                    .build()
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string().orEmpty()
                val parsed = runCatching { json.decodeFromString(OsrmResponse.serializer(), body) }.getOrNull()
                val coords = parsed?.routes?.firstOrNull()?.geometry?.coordinates.orEmpty()
                coords.map { it[1] to it[0] }
            }
        }
    }
}

@Serializable
private data class OsrmResponse(
    val routes: List<OsrmRoute> = emptyList(),
)

@Serializable
private data class OsrmRoute(
    val geometry: OsrmGeometry,
)

@Serializable
private data class OsrmGeometry(
    val coordinates: List<List<Double>> = emptyList(),
)
