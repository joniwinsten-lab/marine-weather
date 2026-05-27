package fi.veneappi.app.data.ais

import fi.veneappi.app.domain.ais.AisConfig
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * HTTP client for Fintraffic Digitraffic Marine AIS API (requires `Digitraffic-User` header).
 *
 * Do not set `Accept-Encoding: gzip` manually — OkHttp adds transparent gzip and decompresses
 * the body. A manual header leaves gzip bytes in the body and breaks JSON parsing.
 */
class DigitrafficHttpClient(
    okHttpClient: OkHttpClient? = null,
) {
    private val client: OkHttpClient =
        okHttpClient
            ?: OkHttpClient.Builder()
                .connectTimeout(45, TimeUnit.SECONDS)
                .readTimeout(90, TimeUnit.SECONDS)
                .build()

    fun getBytes(url: String): ByteArray {
        val request =
            Request.Builder()
                .url(url)
                .header("Digitraffic-User", AisConfig.DIGITRAFFIC_USER)
                .header("Accept", "application/json")
                .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Digitraffic HTTP ${response.code}")
            }
            return response.body?.bytes() ?: error("empty body")
        }
    }
}
