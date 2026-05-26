package fi.veneappi.app.data.ais

import fi.veneappi.app.domain.ais.AisConfig
import fi.veneappi.app.domain.ais.AisVesselDisplay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Fintraffic Digitraffic AIS REST (`/locations` + `/vessels`). */
class DigitrafficAisRepository(
    private val http: DigitrafficHttpClient = DigitrafficHttpClient(),
    private val json: Json =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        },
) {
    suspend fun fetchAllVessels(): List<AisVesselDisplay> =
        withContext(Dispatchers.IO) {
            val locations = fetchLocations()
            val metadataByMmsi =
                runCatching { fetchVesselMetadata() }.getOrElse { emptyMap() }
            locations.map { loc ->
                val meta = metadataByMmsi[loc.mmsi]
                AisVesselDisplay(
                    mmsi = loc.mmsi,
                    latitude = loc.latitude,
                    longitude = loc.longitude,
                    name = meta?.name,
                    callSign = meta?.callSign,
                    destination = meta?.destination,
                    imo = meta?.imo,
                    draughtTenthsM = meta?.draught,
                    shipTypeCode = meta?.shipType,
                    etaRaw = meta?.eta,
                    navStatusCode = loc.navStatusCode,
                    sogKn = loc.sogKn,
                    cogDeg = loc.cogDeg,
                    headingDeg = loc.headingDeg,
                )
            }
        }

    private fun fetchLocations(): List<AisLocationRecord> {
        val url = "${AisConfig.DIGITRAFFIC_BASE_URL}/locations"
        val data = http.getBytes(url)
        val collection = json.decodeFromString<AisLocationFeatureCollection>(data.decodeToString())
        return collection.features.mapNotNull { feature ->
            val coords = feature.geometry.coordinates
            if (coords.size < 2) return@mapNotNull null
            val lon = coords[0]
            val lat = coords[1]
            if (!lat.isFinite() || !lon.isFinite()) return@mapNotNull null
            AisLocationRecord(
                mmsi = feature.mmsi,
                latitude = lat,
                longitude = lon,
                sogKn = feature.properties.sog,
                cogDeg = sanitizeCog(feature.properties.cog),
                headingDeg = sanitizeHeading(feature.properties.heading),
                navStatusCode = feature.properties.navStat,
            )
        }
    }

    private fun fetchVesselMetadata(): Map<Int, AisVesselMetaRecord> {
        val url = "${AisConfig.DIGITRAFFIC_BASE_URL}/vessels"
        val data = http.getBytes(url)
        val records = json.decodeFromString<List<AisVesselMetaRecord>>(data.decodeToString())
        return records.associateBy { it.mmsi }
    }

    private fun sanitizeCog(cog: Double?): Double? {
        if (cog == null || !cog.isFinite() || cog < 0 || cog >= 360) return null
        return cog
    }

    private fun sanitizeHeading(heading: Int?): Int? {
        if (heading == null || heading !in 0..359) return null
        return heading
    }
}

@Serializable
private data class AisLocationFeatureCollection(
    val features: List<AisLocationFeature> = emptyList(),
)

@Serializable
private data class AisLocationFeature(
    val mmsi: Int,
    val geometry: AisPointGeometry,
    val properties: AisLocationProperties,
)

@Serializable
private data class AisPointGeometry(
    val coordinates: List<Double> = emptyList(),
)

@Serializable
private data class AisLocationProperties(
    val sog: Double? = null,
    val cog: Double? = null,
    val heading: Int? = null,
    @SerialName("navStat") val navStat: Int? = null,
)

private data class AisLocationRecord(
    val mmsi: Int,
    val latitude: Double,
    val longitude: Double,
    val sogKn: Double?,
    val cogDeg: Double?,
    val headingDeg: Int?,
    val navStatusCode: Int?,
)

@Serializable
private data class AisVesselMetaRecord(
    val mmsi: Int,
    val name: String? = null,
    val callSign: String? = null,
    val destination: String? = null,
    val imo: Int? = null,
    val draught: Int? = null,
    val shipType: Int? = null,
    val eta: Int? = null,
)
