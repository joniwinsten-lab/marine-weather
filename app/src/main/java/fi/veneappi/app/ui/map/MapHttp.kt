package fi.veneappi.app.ui.map

import android.app.Application
import fi.veneappi.app.BuildConfig
import java.io.File
import java.util.concurrent.TimeUnit
import okhttp3.Cache
import okhttp3.OkHttpClient
import org.maplibre.android.module.http.HttpRequestUtil

/** Shared HTTP client + disk cache for [MapTileWarmup] and MapLibre tile requests. */
object MapHttp {
    private const val CACHE_BYTES = 50L * 1024L * 1024L

    fun install(application: Application): OkHttpClient {
        val cacheDir = File(application.cacheDir, "map-http")
        val client =
            OkHttpClient.Builder()
                .cache(Cache(cacheDir, CACHE_BYTES))
                .connectTimeout(12, TimeUnit.SECONDS)
                .readTimeout(12, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    val request =
                        chain.request().newBuilder()
                            .header("User-Agent", BuildConfig.WEATHER_USER_AGENT)
                            .build()
                    chain.proceed(request)
                }
                .build()
        HttpRequestUtil.setOkHttpClient(client)
        return client
    }
}
