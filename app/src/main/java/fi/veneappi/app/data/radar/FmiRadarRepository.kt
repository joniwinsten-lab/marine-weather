package fi.veneappi.app.data.radar

import fi.veneappi.app.BuildConfig
import fi.veneappi.app.data.FmiInstantFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Instant
import java.util.concurrent.TimeUnit

class FmiRadarRepository(
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

    private val timeMutex = Mutex()
    private var cachedAtMs: Long = 0L

    suspend fun latestRadarTimeUtc(): String? =
        withContext(Dispatchers.IO) {
            latestRadarInstant()?.let { FmiInstantFormat.toDisplayYmdHm(it) }
        }

    suspend fun latestRadarInstant(): Instant? =
        withContext(Dispatchers.IO) {
            timeMutex.withLock {
                val now = System.currentTimeMillis()
                if (cachedInstant != null && now - cachedAtMs < CAPABILITIES_CACHE_MS) {
                    return@withContext cachedInstant
                }
                val parsed = fetchLatestInstantFromCapabilities()
                cachedInstant = parsed
                cachedAtMs = now
                parsed
            }
        }

    suspend fun timeDimensionRaw(): String? =
        withContext(Dispatchers.IO) {
            fetchTimeDimension()
        }

    private fun fetchLatestInstantFromCapabilities(): Instant? {
        val dimension = fetchTimeDimension() ?: return null
        return FmiRadarTimeSeries.parseDimensionEnd(dimension)
    }

    private fun fetchTimeDimension(): String? {
        val request = Request.Builder().url(FmiRadarConfig.CAPABILITIES_URL).build()
        val xml =
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                response.body?.string().orEmpty()
            }
        return Regex(
            """<Dimension name="time"[^>]*>([^<]+)</Dimension>""",
        ).find(xml)?.groupValues?.get(1)
    }

    companion object {
        private const val CAPABILITIES_CACHE_MS = 5 * 60 * 1000L
    }

    private var cachedInstant: Instant? = null
}
