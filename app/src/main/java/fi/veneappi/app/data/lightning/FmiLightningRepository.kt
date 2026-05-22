package fi.veneappi.app.data.lightning

import fi.veneappi.app.BuildConfig
import fi.veneappi.app.data.FmiInstantFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

class FmiLightningRepository(
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

    suspend fun fetchRecentStrikes(
        lonMin: Double = 19.0,
        latMin: Double = 59.0,
        lonMax: Double = 32.0,
        latMax: Double = 71.0,
    ): Result<List<LightningStrike>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val end = Instant.now()
                val start = end.minus(lookbackMinutes.toLong(), ChronoUnit.MINUTES)
                val url =
                    "https://opendata.fmi.fi/wfs"
                        .toHttpUrl()
                        .newBuilder()
                        .addQueryParameter("request", "getFeature")
                        .addQueryParameter("storedquery_id", STORED_QUERY)
                        .addQueryParameter("starttime", FmiInstantFormat.toParam(start))
                        .addQueryParameter("endtime", FmiInstantFormat.toParam(end))
                        .addQueryParameter("bbox", "$lonMin,$latMin,$lonMax,$latMax")
                        .build()
                val request = Request.Builder().url(url).build()
                val xml =
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            error("HTTP ${response.code}")
                        }
                        response.body?.string().orEmpty()
                    }
                FmiLightningParser.parse(xml)
            }
        }

    companion object {
        private const val STORED_QUERY = "fmi::observations::lightning::simple"
        const val ATTRIBUTION =
            "Lightning © Finnish Meteorological Institute (CC BY 4.0). Not for operational navigation."
    }
}
