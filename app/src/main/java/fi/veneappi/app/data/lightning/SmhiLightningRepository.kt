package fi.veneappi.app.data.lightning

import fi.veneappi.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

class SmhiLightningRepository(
    okHttpClient: OkHttpClient? = null,
    private val lookbackMinutes: Int = 120,
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

    suspend fun fetchRecentStrikes(): Result<List<LightningStrike>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val now = ZonedDateTime.now(ZoneOffset.UTC)
                val url =
                    "https://opendata-download-lightning.smhi.se/api/version/latest/" +
                        "year/${now.year}/month/${now.monthValue}/day/${now.dayOfMonth}/data.csv"
                val request = Request.Builder().url(url).build()
                val csv =
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            error("HTTP ${response.code}")
                        }
                        response.body?.string().orEmpty()
                    }
                val lookbackMs =
                    Instant.now().minusSeconds(lookbackMinutes * 60L).toEpochMilli()
                SmhiLightningParser.parse(csv, lookbackMs)
            }
        }

    companion object {
        const val ATTRIBUTION =
            "Lightning © SMHI (CC BY 4.0). Not for operational navigation."
    }
}
