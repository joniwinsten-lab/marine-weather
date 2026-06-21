package fi.veneappi.app.domain.ais

/** Digitraffic AIS MQTT over WebSockets (Fintraffic open data). */
object AisMqttConfig {
    const val BROKER_URL = "wss://meri.digitraffic.fi:443/mqtt"
    const val USERNAME = "digitraffic"
    const val PASSWORD = "digitrafficPassword"

    /** Verified against web prototype (`docs/website/track/track.js`). */
    const val LOCATION_TOPIC_SUFFIX = "location"
    const val METADATA_TOPIC_SUFFIX = "metadata"

    const val RECONNECT_PERIOD_MS = 5_000L
    const val CONNECT_TIMEOUT_MS = 15_000L

    /** Batch map publishes after MQTT bursts (similar to web rAF throttle). */
    const val MAP_PUBLISH_THROTTLE_MS = 300L

    private val TOPIC_MMSI_REGEX = Regex("^vessels-v2/(\\d+)/")

    fun locationTopic(mmsi: Int): String = "vessels-v2/$mmsi/$LOCATION_TOPIC_SUFFIX"

    fun metadataTopic(mmsi: Int): String = "vessels-v2/$mmsi/$METADATA_TOPIC_SUFFIX"

    fun mmsiFromTopic(topic: String): Int? {
        val match = TOPIC_MMSI_REGEX.find(topic) ?: return null
        return match.groupValues[1].toIntOrNull()
    }

    fun isLocationTopic(topic: String): Boolean = topic.contains("/$LOCATION_TOPIC_SUFFIX")

    fun isMetadataTopic(topic: String): Boolean = topic.endsWith("/$METADATA_TOPIC_SUFFIX")
}
