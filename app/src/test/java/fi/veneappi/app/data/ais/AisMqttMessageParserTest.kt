package fi.veneappi.app.data.ais

import com.google.common.truth.Truth.assertThat
import fi.veneappi.app.domain.ais.AisMqttLocationUpdate
import fi.veneappi.app.domain.ais.AisMqttMetadataUpdate
import org.junit.Test

class AisMqttMessageParserTest {
    private val parser = AisMqttMessageParser(clockMs = { FIXED_NOW_MS })

    @Test
    fun parseTopicMmsi_fromLocationTopic() {
        assertThat(parser.parseTopicMmsi("vessels-v2/230982000/location")).isEqualTo(230982000)
    }

    @Test
    fun parseTopicMmsi_invalidTopic_returnsNull() {
        assertThat(parser.parseTopicMmsi("other/topic")).isNull()
    }

    @Test
    fun parseMessage_flatLocationJson() {
        val payload =
            """
            {
              "lat": 60.12,
              "lon": 24.88,
              "sog": 12.3,
              "cog": 45.0,
              "heading": 46,
              "navStat": 0,
              "time": 1710000000
            }
            """.trimIndent().toByteArray()

        val update =
            parser.parseMessage("vessels-v2/230982000/location", payload)
                as AisMqttLocationUpdate

        assertThat(update.mmsi).isEqualTo(230982000)
        assertThat(update.latitude).isWithin(0.0001).of(60.12)
        assertThat(update.longitude).isWithin(0.0001).of(24.88)
        assertThat(update.sogKn).isWithin(0.01).of(12.3)
        assertThat(update.cogDeg).isWithin(0.01).of(45.0)
        assertThat(update.headingDeg).isEqualTo(46)
        assertThat(update.navStatusCode).isEqualTo(0)
        assertThat(update.lastSeenEpochMs).isEqualTo(1_710_000_000_000L)
    }

    @Test
    fun parseMessage_geoJsonLocation() {
        val payload =
            """
            {
              "geometry": { "coordinates": [24.88, 60.12] },
              "properties": {
                "sog": 8.0,
                "cog": 180.0,
                "heading": 511,
                "navStat": 5,
                "timestampExternal": 1710000000000
              }
            }
            """.trimIndent().toByteArray()

        val update =
            parser.parseMessage("vessels-v2/123456789/location", payload)
                as AisMqttLocationUpdate

        assertThat(update.latitude).isWithin(0.0001).of(60.12)
        assertThat(update.longitude).isWithin(0.0001).of(24.88)
        assertThat(update.headingDeg).isNull()
        assertThat(update.lastSeenEpochMs).isEqualTo(1_710_000_000_000L)
    }

    @Test
    fun parseMessage_metadataFlat() {
        val payload =
            """
            {
              "name": "FINNMAID",
              "callSign": "OIXX",
              "destination": "HELSINKI"
            }
            """.trimIndent().toByteArray()

        val update =
            parser.parseMessage("vessels-v2/230982000/metadata", payload)
                as AisMqttMetadataUpdate

        assertThat(update.name).isEqualTo("FINNMAID")
        assertThat(update.callSign).isEqualTo("OIXX")
        assertThat(update.destination).isEqualTo("HELSINKI")
    }

    @Test
    fun parseMessage_metadataNestedProperties() {
        val payload =
            """
            {
              "properties": {
                "name": "TEST SHIP",
                "callSign": "ABCD"
              }
            }
            """.trimIndent().toByteArray()

        val update =
            parser.parseMessage("vessels-v2/111222333/metadata", payload)
                as AisMqttMetadataUpdate

        assertThat(update.name).isEqualTo("TEST SHIP")
        assertThat(update.callSign).isEqualTo("ABCD")
        assertThat(update.destination).isNull()
    }

    @Test
    fun parseMessage_invalidJson_returnsNull() {
        assertThat(parser.parseMessage("vessels-v2/1/location", "{not json".toByteArray())).isNull()
    }

    @Test
    fun parseMessage_unknownTopicKind_returnsNull() {
        val payload = """{"lat":1,"lon":2}""".toByteArray()
        assertThat(parser.parseMessage("vessels-v2/1/status", payload)).isNull()
    }

    @Test
    fun normalizeEpochMs_secondsToMillis() {
        assertThat(AisMqttMessageParser.normalizeEpochMs(1_710_000_000L))
            .isEqualTo(1_710_000_000_000L)
    }

    @Test
    fun normalizeEpochMs_alreadyMillis() {
        assertThat(AisMqttMessageParser.normalizeEpochMs(1_710_000_000_000L))
            .isEqualTo(1_710_000_000_000L)
    }

    companion object {
        private const val FIXED_NOW_MS = 1_700_000_000_000L
    }
}
