package fi.veneappi.app.data.ais

import fi.veneappi.app.domain.ais.AisMqttConfig
import fi.veneappi.app.domain.ais.AisMqttLocationUpdate
import fi.veneappi.app.domain.ais.AisMqttMetadataUpdate
import fi.veneappi.app.domain.ais.AisMqttUpdate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Parses Digitraffic MQTT topics and JSON payloads (flat + GeoJSON). */
class AisMqttMessageParser(
    private val json: Json =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        },
    private val clockMs: () -> Long = { System.currentTimeMillis() },
) {
    fun parseTopicMmsi(topic: String): Int? = AisMqttConfig.mmsiFromTopic(topic)

    fun parseMessage(
        topic: String,
        payload: ByteArray,
    ): AisMqttUpdate? {
        val mmsi = parseTopicMmsi(topic) ?: return null
        val root =
            runCatching {
                json.parseToJsonElement(payload.decodeToString()).jsonObject
            }.getOrNull() ?: return null

        return when {
            AisMqttConfig.isMetadataTopic(topic) -> parseMetadata(mmsi, root)
            AisMqttConfig.isLocationTopic(topic) -> parseLocation(mmsi, root)
            else -> null
        }
    }

    private fun parseMetadata(
        mmsi: Int,
        root: JsonObject,
    ): AisMqttMetadataUpdate? {
        val props = root["properties"]?.jsonObject ?: root
        val name = props.stringOrNull("name")
        val callSign = props.stringOrNull("callSign")
        val destination = props.stringOrNull("destination")
        if (name == null && callSign == null && destination == null) return null
        return AisMqttMetadataUpdate(
            mmsi = mmsi,
            name = name,
            callSign = callSign,
            destination = destination,
        )
    }

    private fun parseLocation(
        mmsi: Int,
        root: JsonObject,
    ): AisMqttLocationUpdate? {
        val flatLat = root.doubleOrNull("lat")
        val flatLon = root.doubleOrNull("lon")
        if (flatLat != null && flatLon != null) {
            return AisMqttLocationUpdate(
                mmsi = mmsi,
                latitude = flatLat,
                longitude = flatLon,
                sogKn = root.doubleOrNull("sog"),
                cogDeg = sanitizeCog(root.doubleOrNull("cog")),
                headingDeg = sanitizeHeading(root.intOrNull("heading")),
                navStatusCode = root.intOrNull("navStat"),
                lastSeenEpochMs =
                    normalizeEpochMs(root.longOrNull("time"))
                        ?: clockMs(),
            )
        }

        val coords = root["geometry"]?.jsonObject?.get("coordinates")?.jsonArray
        if (coords == null || coords.size < 2) return null
        val lon = coords[0].jsonPrimitive.content.toDouble()
        val lat = coords[1].jsonPrimitive.content.toDouble()
        if (!lat.isFinite() || !lon.isFinite()) return null

        val props = root["properties"]?.jsonObject ?: return null
        val lastSeen =
            normalizeEpochMs(props.longOrNull("timestampExternal"))
                ?: normalizeEpochMs(props.longOrNull("timestamp"))
                ?: clockMs()

        return AisMqttLocationUpdate(
            mmsi = mmsi,
            latitude = lat,
            longitude = lon,
            sogKn = props.doubleOrNull("sog"),
            cogDeg = sanitizeCog(props.doubleOrNull("cog")),
            headingDeg = sanitizeHeading(props.intOrNull("heading")),
            navStatusCode = props.intOrNull("navStat"),
            lastSeenEpochMs = lastSeen,
        )
    }

    private fun sanitizeCog(cog: Double?): Double? {
        if (cog == null || !cog.isFinite() || cog < 0 || cog >= 360) return null
        return cog
    }

    private fun sanitizeHeading(heading: Int?): Int? {
        if (heading == null || heading !in 0..359 || heading == 511) return null
        return heading
    }

    companion object {
        fun normalizeEpochMs(raw: Long?): Long? {
            if (raw == null) return null
            return if (raw < 1_000_000_000_000L) raw * 1000 else raw
        }
    }
}

private fun JsonObject.stringOrNull(key: String): String? {
    val value = this[key]?.jsonPrimitive?.content?.trim().orEmpty()
    return value.ifEmpty { null }
}

private fun JsonObject.doubleOrNull(key: String): Double? =
    this[key]?.jsonPrimitive?.content?.toDoubleOrNull()

private fun JsonObject.intOrNull(key: String): Int? =
    this[key]?.jsonPrimitive?.content?.toIntOrNull()

private fun JsonObject.longOrNull(key: String): Long? =
    this[key]?.jsonPrimitive?.content?.toLongOrNull()
