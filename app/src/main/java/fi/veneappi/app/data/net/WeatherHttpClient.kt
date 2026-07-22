package fi.veneappi.app.data.net

import fi.veneappi.app.BuildConfig
import fi.veneappi.app.data.fmi.FmiMultipointParser
import fi.veneappi.app.data.met.MetFeature
import fi.veneappi.app.data.met.MetMapper
import fi.veneappi.app.data.smhi.SmhiMapper
import fi.veneappi.app.data.smhi.SmhiPointResponse
import fi.veneappi.app.domain.UnifiedForecast
import fi.veneappi.app.domain.WeatherSources
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class WeatherHttpClient(
    okHttpClient: OkHttpClient?,
    private val json: Json,
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

    suspend fun fetchMetNorway(lat: Double, lon: Double): UnifiedForecast =
        withContext(Dispatchers.IO) {
            val latR = roundCoord(lat, 4)
            val lonR = roundCoord(lon, 4)
            val url =
                "https://api.met.no/weatherapi/locationforecast/2.0/compact"
                    .toHttpUrl()
                    .newBuilder()
                    .addQueryParameter("lat", latR.toString())
                    .addQueryParameter("lon", lonR.toString())
                    .build()
            val body = executeGet(url.toString())
            val feature = json.decodeFromString(MetFeature.serializer(), body)
            MetMapper.toUnified(feature, System.currentTimeMillis())
        }

    suspend fun fetchSmhi(lat: Double, lon: Double): UnifiedForecast =
        withContext(Dispatchers.IO) {
            val lonP = roundCoord(lon, 3)
            val latP = roundCoord(lat, 3)
            val url =
                "https://opendata-download-metfcst.smhi.se/api/category/snow1g/version/1/geotype/point/lon/$lonP/lat/$latP/data.json"
            val body = executeGet(url)
            val dto = json.decodeFromString(SmhiPointResponse.serializer(), body)
            SmhiMapper.toUnified(dto, System.currentTimeMillis())
        }

    suspend fun fetchFmi(lat: Double, lon: Double): UnifiedForecast =
        withContext(Dispatchers.IO) {
            val latP = roundCoord(lat, 5)
            val lonP = roundCoord(lon, 5)
            val url =
                "https://opendata.fmi.fi/wfs"
                    .toHttpUrl()
                    .newBuilder()
                    .addQueryParameter("request", "getFeature")
                    .addQueryParameter(
                        "storedquery_id",
                        "fmi::forecast::harmonie::surface::point::multipointcoverage",
                    )
                    .addQueryParameter("latlon", "$latP,$lonP")
                    .addQueryParameter(
                        "parameters",
                        "WeatherSymbol,temperature,WindSpeedMS,WindDirection,WindGust,PrecipitationAmount",
                    )
                    .build()
            val xml = executeGet(url.toString())
            val points = FmiMultipointParser.parse(xml)
            UnifiedForecast(
                source = WeatherSources.Fmi,
                fetchedAtUtc = System.currentTimeMillis(),
                modelInfo = "HARMONIE surface point",
                points = points,
            )
        }

    private fun executeGet(url: String): String {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("HTTP ${response.code} for $url")
            }
            return response.body?.string().orEmpty()
        }
    }

    private fun roundCoord(value: Double, decimals: Int): Double {
        val factor = Math.pow(10.0, decimals.toDouble())
        return kotlin.math.round(value * factor) / factor
    }
}
