package fi.veneappi.app.data.marine

import fi.veneappi.app.BuildConfig
import fi.veneappi.app.data.fmi.FmiMultipointParser
import fi.veneappi.app.data.smhi.SmhiMapper
import fi.veneappi.app.data.smhi.SmhiPointResponse
import fi.veneappi.app.domain.UnifiedTimePoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import kotlin.math.abs
import kotlin.math.roundToInt

data class MarineCountryText(
    val countryCode: String,
    val title: String,
    val publishedOrValidLabel: String?,
    val body: String,
    val alertLevel: MarineForecastAlertLevel = MarineForecastAlertLevel.None,
    val servicePageUrl: String,
)

data class MarineTextOverview(
    val lastFetchedUtc: Long,
    val metNorwaySeaLastChange: String?,
    val countries: List<MarineCountryText>,
    val errors: List<String>,
)

/**
 * Text marine outlooks for four Baltic countries:
 * - NO: MET Norway textforecast sea (North & Norwegian Sea areas).
 * - SE / FI: MET sea where available; otherwise SMHI sea report (Baltic) and/or national point models.
 * - EE: Estonian Environment Agency public forecast XML (marine sections).
 */
class MarineTextRepository(
    private val json: Json,
    okHttpClient: OkHttpClient? = null,
) {
    private val client =
        okHttpClient
            ?: OkHttpClient.Builder()
                .connectTimeout(25, TimeUnit.SECONDS)
                .readTimeout(25, TimeUnit.SECONDS)
                .addInterceptor { chain ->
                    val request =
                        chain.request().newBuilder()
                            .header("User-Agent", BuildConfig.WEATHER_USER_AGENT)
                            .header("Accept", "application/json, text/xml;q=0.9, */*;q=0.8")
                            .build()
                    chain.proceed(request)
                }
                .build()

    suspend fun loadOverview(
        lat: Double,
        lon: Double,
        titlesByCountry: Map<String, String>,
    ): MarineTextOverview =
        withContext(Dispatchers.IO) {
            val errors = mutableListOf<String>()
            val t0 = System.currentTimeMillis()
            val seaDoc =
                runCatching {
                    val body = httpGet("https://api.met.no/weatherapi/textforecast/3.0/sea_en.json")
                    json.decodeFromString(MetSeaCollection.serializer(), body)
                }.getOrElse { e ->
                    errors.add("MET sea_en: ${e.message ?: e.toString()}")
                    null
                }

            // SMHI sea-report JSON requires credentials (401); optional, no user-visible error.
            val smhiSea = loadSmhiSeaReportOrNull()

            val ee =
                runCatching { loadEstoniaMarineEnglish() }.getOrElse { e ->
                    errors.add("EE forecast: ${e.message ?: e.toString()}")
                    null
                }

            val countries = mutableListOf<MarineCountryText>()
            if (seaDoc != null) {
                val features = seaDoc.features.mapNotNull { it.toSeaFeature() }
                for (code in listOf("NO", "SE", "FI")) {
                    val box = bbox(code)
                    val pick = pickFeatureForCountry(features, lat, lon, box)
                    var bodyText = pick?.toBodyText().orEmpty()
                    var label =
                        if (bodyText.isNotBlank()) {
                            metSeaLabel(seaDoc.lastChange, pick)
                        } else {
                            null
                        }

                    if (bodyText.isBlank()) {
                        when (code) {
                            "FI" -> {
                                val smhi = smhiSeaTextForPoint(smhiSea, lat, lon)
                                if (smhi != null) {
                                    bodyText = smhi.body
                                    label = smhi.label
                                } else {
                                    loadFmiPointSummary(lat, lon)?.let { fmi ->
                                        bodyText = fmi.body
                                        label = fmi.label
                                    }
                                }
                            }
                            "SE" -> {
                                val smhi = smhiSeaTextForPoint(smhiSea, lat, lon)
                                if (smhi != null) {
                                    bodyText = smhi.body
                                    label = smhi.label
                                } else {
                                    loadSmhiPointSummary(lat, lon)?.let { smhiPt ->
                                        bodyText = smhiPt.body
                                        label = smhiPt.label
                                    }
                                }
                            }
                            "NO" -> {
                                // Baltic map centre: MET sea areas rarely apply; leave empty.
                            }
                        }
                    }

                    countries.add(
                        buildCountryCard(
                            code = code,
                            title = titlesByCountry[code].orEmpty(),
                            rawBody = bodyText,
                            label = label,
                            metProps = pick?.properties,
                        ),
                    )
                }
            } else {
                for (code in listOf("NO", "SE", "FI")) {
                    var bodyText = ""
                    var label: String? = null
                    when (code) {
                        "FI" -> {
                            val smhi = smhiSeaTextForPoint(smhiSea, lat, lon)
                            if (smhi != null) {
                                bodyText = smhi.body
                                label = smhi.label
                            } else {
                                loadFmiPointSummary(lat, lon)?.let {
                                    bodyText = it.body
                                    label = it.label
                                }
                            }
                        }
                        "SE" -> {
                            val smhi = smhiSeaTextForPoint(smhiSea, lat, lon)
                            if (smhi != null) {
                                bodyText = smhi.body
                                label = smhi.label
                            } else {
                                loadSmhiPointSummary(lat, lon)?.let {
                                    bodyText = it.body
                                    label = it.label
                                }
                            }
                        }
                    }
                    countries.add(
                        buildCountryCard(
                            code = code,
                            title = titlesByCountry[code].orEmpty(),
                            rawBody = bodyText,
                            label = label,
                        ),
                    )
                }
            }

            if (ee != null) {
                countries.add(
                    buildCountryCard(
                        code = "EE",
                        title = titlesByCountry["EE"].orEmpty(),
                        rawBody = ee.body,
                        label = ee.validLabel,
                    ),
                )
            } else {
                countries.add(
                    buildCountryCard(
                        code = "EE",
                        title = titlesByCountry["EE"].orEmpty(),
                        rawBody = "",
                        label = null,
                    ),
                )
            }

            MarineTextOverview(
                lastFetchedUtc = t0,
                metNorwaySeaLastChange = seaDoc?.lastChange,
                countries = countries,
                errors = errors,
            )
        }

    private data class TextBlock(val body: String, val label: String?)

    private fun buildCountryCard(
        code: String,
        title: String,
        rawBody: String,
        label: String?,
        metProps: MetSeaProperties? = null,
    ): MarineCountryText =
        MarineCountryText(
            countryCode = code,
            title = title,
            publishedOrValidLabel = label,
            body = finalizeBody(rawBody),
            alertLevel =
                MarineForecastAlertClassifier.classify(
                    rawText = rawBody,
                    metWindWarning = metProps?.windWarning,
                    metIceWarning = metProps?.iceWarning,
                    metPolarlowWarning = metProps?.polarlowWarning,
                ),
            servicePageUrl = MarineServiceUrls.forCountry(code),
        )

    private fun finalizeBody(raw: String): String {
        val text = raw.trim()
        if (text.isBlank()) return ""
        if (text.startsWith("At the map centre:")) return text
        return MarineTextSummarizer.summarize(text).ifBlank {
            text.take(280).let { if (text.length > 280) "$it…" else it }
        }
    }

    private fun SeaFeature.toBodyText(): String =
        buildString {
            val p = properties
            p.area?.let { appendLine(it).appendLine() }
            p.title?.let { appendLine(it).appendLine() }
            append(p.text.trim())
            appendLine()
            if (p.windWarning?.isNotBlank() == true) {
                appendLine()
                appendLine(p.windWarning.trim())
            }
            if (p.iceWarning?.isNotBlank() == true) {
                appendLine()
                appendLine(p.iceWarning.trim())
            }
            if (p.polarlowWarning?.isNotBlank() == true) {
                appendLine()
                appendLine(p.polarlowWarning.trim())
            }
        }.trim()

    private fun loadSmhiSeaReportOrNull(): SmhiSeaReport? {
        val url = "https://data-download.smhi.se/data/meteorology/texts/sea_report_sweden_sv.json"
        return runCatching {
            val body = httpGet(url)
            json.decodeFromString(SmhiSeaReport.serializer(), body)
        }.getOrNull()
    }

    private fun metSeaLabel(
        lastChange: String?,
        pick: SeaFeature?,
    ): String? =
        listOfNotNull(lastChange, pick?.intervalLabel())
            .joinToString(" · ")
            .ifBlank { null }

    private fun smhiSeaTextForPoint(
        report: SmhiSeaReport?,
        lat: Double,
        lon: Double,
    ): TextBlock? {
        if (report == null) return null
        val districtId = smhiDistrictForPoint(lat, lon) ?: return null
        val districtName = SMHI_DISTRICT_NAMES[districtId].orEmpty()
        val text =
            report.districts
                .firstOrNull { districtId in it.ids }
                ?.text
                ?.trim()
                .orEmpty()
        if (text.isEmpty()) return null
        val overview = report.overview?.trim().orEmpty()
        val body =
            buildString {
                if (overview.isNotEmpty()) {
                    appendLine(overview)
                    appendLine()
                }
                if (districtName.isNotEmpty()) {
                    appendLine(districtName)
                    appendLine()
                }
                append(text)
            }.trim()
        val label =
            listOfNotNull(
                report.updated?.let { "SMHI · $it" },
                "Sea report",
            ).joinToString(" · ")
        return TextBlock(body = body, label = label)
    }

    private fun smhiDistrictForPoint(
        lat: Double,
        lon: Double,
    ): Int? {
        val matches =
            SMHI_DISTRICT_BOXES.filter { (_, box) -> box.contains(lon, lat) }
        if (matches.isNotEmpty()) {
            return matches.minByOrNull { (_, box) -> box.dist2(lon, lat) }?.key
        }
        return SMHI_DISTRICT_BOXES.minByOrNull { (_, box) -> box.dist2(lon, lat) }?.key
    }

    private fun loadFmiPointSummary(
        lat: Double,
        lon: Double,
    ): TextBlock? {
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
                .addQueryParameter("parameters", "temperature,WindSpeedMS,WindDirection,WindGust")
                .build()
        val xml = httpGet(url.toString())
        val points = FmiMultipointParser.parse(xml)
        return formatPointSummary(
            points = points,
            validLabel = "Ilmatieteen laitos",
        )
    }

    private fun loadSmhiPointSummary(
        lat: Double,
        lon: Double,
    ): TextBlock? {
        val lonP = roundCoord(lon, 3)
        val latP = roundCoord(lat, 3)
        val url =
            "https://opendata-download-metfcst.smhi.se/api/category/snow1g/version/1/geotype/point/lon/$lonP/lat/$latP/data.json"
        val body = httpGet(url)
        val dto = json.decodeFromString(SmhiPointResponse.serializer(), body)
        val forecast = SmhiMapper.toUnified(dto, System.currentTimeMillis())
        return formatPointSummary(
            points = forecast.points,
            validLabel = dto.referenceTime?.let { "SMHI · $it" } ?: "SMHI",
        )
    }

    private fun formatPointSummary(
        points: List<UnifiedTimePoint>,
        validLabel: String?,
    ): TextBlock? {
        if (points.isEmpty()) return null
        val now = points.first()
        val later = points.getOrNull(minOf(6, points.lastIndex)) ?: points.last()
        val body =
            buildString {
                append("At the map centre: ")
                append(windPhrase(now))
                append('.')
                if (later.instantUtc != now.instantUtc) {
                    append(' ')
                    append("Later: ")
                    append(windPhrase(later))
                    append('.')
                }
                now.airTempC?.let { t ->
                    append(' ')
                    append("Air about ${t.roundToInt()} °C.")
                }
            }
        return TextBlock(body = body.trim(), label = validLabel)
    }

    private fun windPhrase(pt: UnifiedTimePoint): String {
        val spd = pt.windSpeedMs
        if (spd == null) return "wind variable"
        val dir = pt.windFromDeg?.let { " from ${cardinalFromDegrees(it)}" }.orEmpty()
        val gust = pt.windGustMs?.let { g -> ", gusts to ${formatWind(g)}" }.orEmpty()
        return "${formatWind(spd)}$dir$gust"
    }

    private fun formatWind(ms: Double): String = "${(ms * 10).roundToInt() / 10.0} m/s"

    private fun cardinalFromDegrees(deg: Double): String {
        val dirs =
            listOf(
                "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
                "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW",
            )
        val x = ((deg % 360.0) + 360.0) % 360.0
        val idx = ((x + 11.25) / 22.5).toInt() % 16
        return dirs[idx]
    }

    private fun roundCoord(value: Double, decimals: Int): Double {
        val factor = Math.pow(10.0, decimals.toDouble())
        return kotlin.math.round(value * factor) / factor
    }

    private fun httpGet(url: String): String {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("HTTP ${response.code} for $url")
            }
            val bytes = response.body?.bytes() ?: byteArrayOf()
            if (bytes.size >= 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()) {
                GZIPInputStream(ByteArrayInputStream(bytes)).use { gz ->
                    return gz.bufferedReader(Charsets.UTF_8).readText()
                }
            }
            return bytes.toString(Charsets.UTF_8)
        }
    }

    private data class EeMarine(val validLabel: String?, val body: String)

    private fun loadEstoniaMarineEnglish(): EeMarine {
        val xml = httpGet("https://ilmateenistus.ee/ilma_andmed/xml/forecast.php?lang=eng")
        val forecastM =
            Regex("""<forecast\s+date="([^"]+)">([\s\S]*?)</forecast>""").find(xml)
                ?: return EeMarine(validLabel = null, body = "")
        val date = forecastM.groupValues[1]
        val inner = forecastM.groupValues[2]

        fun extractPeriod(period: String): List<String> {
            val periodM =
                Regex("<$period>([\\s\\S]*?)</$period>").find(inner)
                    ?: return emptyList()
            val block = periodM.groupValues[1]
            val out = mutableListOf<String>()
            Regex("""<sea>([\s\S]*?)</sea>""").findAll(block).forEach { m ->
                val t = m.groupValues[1].trim()
                if (t.isNotEmpty()) {
                    out.add("— $period —\n$t")
                }
            }
            Regex("""<peipsi>([\s\S]*?)</peipsi>""").findAll(block).forEach { m ->
                val t = m.groupValues[1].trim()
                if (t.isNotEmpty()) {
                    out.add("— $period — Lake Peipsi\n$t")
                }
            }
            return out
        }

        val chunks = extractPeriod("night") + extractPeriod("day")
        val firstNightSea =
            Regex("""<night>[\s\S]*?<sea>([\s\S]*?)</sea>""").find(inner)?.groupValues?.get(1)?.trim().orEmpty()
        val validLine =
            firstNightSea
                .lineSequence()
                .map { it.trim() }
                .firstOrNull { it.startsWith("Forecast for Baltic Sea valid") }
        val label =
            listOfNotNull(
                "Forecast date: $date",
                validLine,
            ).joinToString(" · ")
                .ifBlank { null }
        return EeMarine(validLabel = label, body = chunks.joinToString("\n\n").trim())
    }

    private fun bbox(code: String): LonLatBox =
        when (code) {
            "NO" -> LonLatBox(3.0, 32.0, 57.0, 72.0)
            "SE" -> LonLatBox(10.0, 25.5, 54.5, 69.5)
            "FI" -> LonLatBox(19.0, 32.5, 59.0, 70.5)
            else -> LonLatBox(-180.0, 180.0, -90.0, 90.0)
        }

    private fun pickFeatureForCountry(
        features: List<SeaFeature>,
        lat: Double,
        lon: Double,
        box: LonLatBox,
    ): SeaFeature? {
        val inBox =
            features.filter { f ->
                box.contains(f.centroidLon, f.centroidLat) &&
                    f.properties.text.trim().isNotEmpty()
            }
        if (inBox.isEmpty()) return null
        val inside = inBox.filter { pointInPolygon(lon, lat, it.ringLonLat) }
        return inside.minByOrNull { it.areaApprox }
    }

    private data class LonLatBox(
        val lonMin: Double,
        val lonMax: Double,
        val latMin: Double,
        val latMax: Double,
    ) {
        fun contains(lon: Double, lat: Double): Boolean =
            lon in lonMin..lonMax && lat in latMin..latMax

        fun dist2(lon: Double, lat: Double): Double {
            val cLon = (lonMin + lonMax) / 2.0
            val cLat = (latMin + latMax) / 2.0
            val dLon = lon - cLon
            val dLat = lat - cLat
            return dLon * dLon + dLat * dLat
        }
    }

    private data class SeaFeature(
        val centroidLon: Double,
        val centroidLat: Double,
        val ringLonLat: List<Pair<Double, Double>>,
        val areaApprox: Double,
        val properties: MetSeaProperties,
        val whenInterval: List<String>?,
    ) {
        fun intervalLabel(): String? =
            whenInterval?.takeIf { it.size >= 2 }?.let { "${it[0]} … ${it[1]}" }

        fun dist2(lon: Double, lat: Double): Double {
            val dLon = lon - centroidLon
            val dLat = lat - centroidLat
            return dLon * dLon + dLat * dLat
        }
    }

    private fun MetSeaFeature.toSeaFeature(): SeaFeature? {
        val coords = geometry?.coordinates?.firstOrNull() ?: return null
        if (coords.size < 3) return null
        val ring = coords.map { Pair(it[0], it[1]) }
        var sLon = 0.0
        var sLat = 0.0
        val n = ring.size
        for ((lo, la) in ring) {
            sLon += lo
            sLat += la
        }
        val cLon = sLon / n
        val cLat = sLat / n
        val props = properties ?: return null
        val area = polygonArea(ring)
        return SeaFeature(
            centroidLon = cLon,
            centroidLat = cLat,
            ringLonLat = ring,
            areaApprox = area,
            properties = props,
            whenInterval = forecastWhen?.interval,
        )
    }

    private fun polygonArea(ring: List<Pair<Double, Double>>): Double {
        if (ring.size < 3) return 0.0
        var sum = 0.0
        for (i in ring.indices) {
            val p = ring[i]
            val q = ring[(i + 1) % ring.size]
            sum += p.first * q.second - q.first * p.second
        }
        return 0.5 * abs(sum)
    }

    private fun pointInPolygon(
        x: Double,
        y: Double,
        ring: List<Pair<Double, Double>>,
    ): Boolean {
        if (ring.size < 3) return false
        var inside = false
        var j = ring.size - 1
        for (i in ring.indices) {
            val xi = ring[i].first
            val yi = ring[i].second
            val xj = ring[j].first
            val yj = ring[j].second
            val intersect =
                (yi > y) != (yj > y) &&
                    x < (xj - xi) * (y - yi) / ((yj - yi).takeIf { abs(it) > 1e-12 } ?: 1e-12) + xi
            if (intersect) {
                inside = !inside
            }
            j = i
        }
        return inside
    }

    companion object {
        private val SMHI_DISTRICT_NAMES =
            mapOf(
                41 to "Gulf of Bothnia (northern)",
                42 to "Northern Quark",
                43 to "Northern Bothnian Sea",
                44 to "Southern Bothnian Sea",
                45 to "Åland Sea",
                46 to "Archipelago Sea",
                47 to "Gulf of Finland",
                48 to "Northern Baltic Proper",
                49 to "Central Baltic Proper",
                50 to "Gulf of Riga",
                51 to "South-eastern Baltic",
                52 to "Southern Baltic",
                53 to "South-western Baltic",
                54 to "The Belt",
                55 to "The Sound",
                56 to "Kattegat",
                57 to "Skagerrak",
                58 to "Lake Vänern",
                59 to "German Bight",
                60 to "Fisher",
                61 to "South Utsira",
            )

        /** Rough SMHI sea-report district boxes (WGS84). */
        private val SMHI_DISTRICT_BOXES: Map<Int, LonLatBox> =
            mapOf(
                41 to LonLatBox(15.0, 23.0, 63.5, 66.5),
                42 to LonLatBox(19.0, 22.5, 63.0, 66.0),
                43 to LonLatBox(17.0, 21.5, 62.0, 64.5),
                44 to LonLatBox(17.0, 21.5, 60.2, 63.0),
                45 to LonLatBox(19.0, 21.8, 59.5, 61.0),
                46 to LonLatBox(21.0, 23.8, 59.5, 61.5),
                47 to LonLatBox(22.5, 30.5, 59.3, 60.8),
                48 to LonLatBox(18.0, 26.5, 57.5, 59.8),
                49 to LonLatBox(14.0, 20.5, 55.5, 58.5),
                50 to LonLatBox(20.0, 27.5, 56.5, 58.5),
                51 to LonLatBox(18.0, 24.5, 54.5, 57.5),
                52 to LonLatBox(14.0, 18.5, 54.0, 56.5),
                53 to LonLatBox(10.0, 14.5, 54.5, 56.5),
                54 to LonLatBox(10.0, 16.0, 54.0, 56.5),
                55 to LonLatBox(12.0, 13.8, 55.4, 56.2),
                56 to LonLatBox(10.0, 12.8, 56.0, 58.5),
                57 to LonLatBox(8.5, 11.5, 57.5, 59.2),
            )
    }
}

@Serializable
private data class SmhiSeaReport(
    val overview: String? = null,
    val updated: String? = null,
    val districts: List<SmhiSeaDistrict> = emptyList(),
)

@Serializable
private data class SmhiSeaDistrict(
    val ids: List<Int> = emptyList(),
    val text: String = "",
)

@Serializable
private data class MetSeaCollection(
    val type: String? = null,
    val lang: String? = null,
    val lastChange: String? = null,
    val features: List<MetSeaFeature> = emptyList(),
)

@Serializable
private data class MetSeaFeature(
    val type: String? = null,
    val geometry: MetPolygon? = null,
    @SerialName("when") val forecastWhen: MetWhen? = null,
    val properties: MetSeaProperties? = null,
)

@Serializable
private data class MetPolygon(
    val type: String? = null,
    val coordinates: List<List<List<Double>>>? = null,
)

@Serializable
private data class MetWhen(
    val interval: List<String>? = null,
)

@Serializable
private data class MetSeaProperties(
    val area: String? = null,
    val title: String? = null,
    val text: String = "",
    @SerialName("wind_warning") val windWarning: String? = null,
    @SerialName("ice_warning") val iceWarning: String? = null,
    @SerialName("polarlow_warning") val polarlowWarning: String? = null,
)
