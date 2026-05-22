package fi.veneappi.app.data.harbors

import fi.veneappi.app.BuildConfig
import fi.veneappi.app.domain.Harbor
import fi.veneappi.app.domain.HarborKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.LinkedHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

/**
 * Fetches publicly mapped boating-related places from OpenStreetMap via Overpass API.
 * [OpenStreetMap contributors, ODbL](https://www.openstreetmap.org/copyright)
 */
class OverpassHarborClient(
    private val jsonParser: Json =
        Json {
            ignoreUnknownKeys = true
        },
) {
    private val client =
        OkHttpClient.Builder()
            .connectTimeout(25, TimeUnit.SECONDS)
            .readTimeout(55, TimeUnit.SECONDS)
            .build()

    suspend fun fetchInBbox(
        centerLat: Double,
        centerLon: Double,
        halfLatDeg: Double = 0.72,
        halfLonDeg: Double = 1.05,
    ): Result<List<Harbor>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val south = (centerLat - halfLatDeg).coerceIn(58.5, 70.2)
                val north = (centerLat + halfLatDeg).coerceIn(58.5, 70.2)
                val west = (centerLon - halfLonDeg).coerceIn(18.0, 31.8)
                val east = (centerLon + halfLonDeg).coerceIn(18.0, 31.8)
                val q = buildQuery(south, west, north, east)
                val body =
                    "data=${java.net.URLEncoder.encode(q, Charsets.UTF_8.name())}"
                        .toRequestBody("application/x-www-form-urlencoded; charset=utf-8".toMediaType())
                val request =
                    Request.Builder()
                        .url("https://overpass-api.de/api/interpreter")
                        .header("User-Agent", BuildConfig.WEATHER_USER_AGENT)
                        .post(body)
                        .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        error("HTTP ${response.code}")
                    }
                    val text = response.body?.string().orEmpty()
                    parseElements(text)
                }
            }
        }

    private fun buildQuery(
        south: Double,
        west: Double,
        north: Double,
        east: Double,
    ): String {
        val b = "$south,$west,$north,$east"
        return """
            [out:json][timeout:55];
            (
              nwr["leisure"="marina"]($b);
              nwr["landuse"="marina"]($b);
              nwr["tourism"="guest_harbour"]($b);
              nwr["landuse"="harbour"]($b);
              nwr["seamark:type"="harbour"]($b);
              node["waterway"="fuel"]($b);
              way["waterway"="fuel"]($b);
              node["amenity"="fuel"]["fuel:marine"="yes"]($b);
            );
            out center tags 500;
        """.trimIndent()
    }

    private fun parseElements(jsonText: String): List<Harbor> {
        val root =
            runCatching { jsonParser.parseToJsonElement(jsonText).jsonObject }
                .getOrElse { return emptyList() }
        val elements = root["elements"]?.jsonArray ?: return emptyList()
        val byKey = LinkedHashMap<String, Harbor>()
        for (el in elements) {
            val o = el.jsonObject
            val type = o["type"]?.jsonPrimitive?.contentOrNull ?: continue
            val id = o["id"]?.jsonPrimitive?.longOrNull ?: continue
            val lat =
                o["lat"]?.jsonPrimitive?.doubleOrNull
                    ?: o["center"]?.jsonObject?.get("lat")?.jsonPrimitive?.doubleOrNull
            val lon =
                o["lon"]?.jsonPrimitive?.doubleOrNull
                    ?: o["center"]?.jsonObject?.get("lon")?.jsonPrimitive?.doubleOrNull
            if (lat == null || lon == null) continue
            if (!lat.isFinite() || !lon.isFinite()) continue
            val tags = o["tags"]?.jsonObject ?: JsonObject(emptyMap())
            val harbor = elementToHarbor(type, id, lat, lon, tags) ?: continue
            byKey[harbor.osmKey] = harbor
        }
        return byKey.values.toList()
    }

    private fun JsonObject.tagString(vararg keys: String): String? {
        for (k in keys) {
            val el = this[k] ?: continue
            val s =
                when (el) {
                    is JsonPrimitive -> el.contentOrNull
                    else -> null
                }
            if (!s.isNullOrBlank()) return s.trim()
        }
        return null
    }

    private fun elementToHarbor(
        type: String,
        id: Long,
        lat: Double,
        lon: Double,
        tags: JsonObject,
    ): Harbor? {
        fun tag(vararg keys: String): String? = tags.tagString(*keys)
        val leisure = tag("leisure")
        val landuse = tag("landuse")
        val tourism = tag("tourism")
        val seamarkType = tag("seamark:type")
        val waterway = tag("waterway")
        val amenity = tag("amenity")
        val fuelMarine = tag("fuel:marine")

        val kind =
            when {
                waterway == "fuel" -> HarborKind.BoatFuel
                amenity == "fuel" && fuelMarine == "yes" -> HarborKind.BoatFuel
                tourism == "guest_harbour" -> HarborKind.GuestHarbor
                leisure == "marina" || landuse == "marina" -> HarborKind.Marina
                landuse == "harbour" -> HarborKind.HarbourArea
                seamarkType == "harbour" -> HarborKind.SeaMarkHarbour
                else -> HarborKind.Other
            }

        val name =
            tag("name", "name:fi", "name:sv", "seamark:name", "ref:name")
                ?: tag("operator")
                ?: defaultNameForKind(kind)

        val phone = tag("phone", "contact:phone")
        val website = tag("website", "contact:website", "url")
        val description = tag("description", "description:fi", "note", "note:fi")
        val operator = tag("operator")
        val capacity = tag("capacity", "mooring:capacity")
        val mooring = tag("mooring", "berth")
        val fee = tag("fee")

        return Harbor(
            osmKey = "$type/$id",
            name = name,
            latitude = lat,
            longitude = lon,
            kind = kind,
            phone = phone,
            website = website,
            description = description,
            operator = operator,
            capacity = capacity,
            mooring = mooring,
            fee = fee,
        )
    }

    private fun defaultNameForKind(kind: HarborKind): String =
        when (kind) {
            HarborKind.Marina -> "Marina"
            HarborKind.GuestHarbor -> "Guest harbour"
            HarborKind.HarbourArea -> "Harbour"
            HarborKind.SeaMarkHarbour -> "Harbour"
            HarborKind.BoatFuel -> "Boat fuel"
            HarborKind.Other -> "Harbour"
        }
}
