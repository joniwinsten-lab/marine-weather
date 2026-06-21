package fi.veneappi.app.data.ais

import fi.veneappi.app.domain.ais.AisBrowseItem
import fi.veneappi.app.domain.ais.AisBrowseSource
import fi.veneappi.app.domain.ais.AisConfig
import fi.veneappi.app.domain.ais.AisTrackConfig
import fi.veneappi.app.domain.ais.AisVesselDisplay
import fi.veneappi.app.domain.ais.MapViewport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val metadataMutex = Mutex()
    private var metadataCache: Map<Int, AisVesselMetaRecord>? = null

    /** Full fetch: locations + vessel metadata (slow — use on first load). */
    suspend fun fetchAllVessels(): List<AisVesselDisplay> =
        withContext(Dispatchers.IO) {
            val fetchedAtMs = System.currentTimeMillis()
            val locations = fetchLocations(fetchedAtMs)
            val metadataByMmsi = ensureMetadataCacheLocked()
            locations.map { it.toDisplay(metadataByMmsi[it.mmsi]) }
        }

    /** Positions only (~7 MB) — for 60 s polling without re-downloading metadata. */
    suspend fun fetchLocationUpdates(): List<AisVesselDisplay> =
        withContext(Dispatchers.IO) {
            val fetchedAtMs = System.currentTimeMillis()
            fetchLocations(fetchedAtMs).map { it.toDisplay(meta = null) }
        }

    /** Fast viewport-scoped fetch (map pan) — uses Digitraffic radius query, not full `/locations`. */
    suspend fun fetchVesselsInViewport(viewport: MapViewport): List<AisVesselDisplay> =
        withContext(Dispatchers.IO) {
            val fetchedAtMs = System.currentTimeMillis()
            val metadataByMmsi = ensureMetadataCacheLocked()
            val radius = viewport.queryRadiusKm()
            val url =
                "${AisConfig.DIGITRAFFIC_BASE_URL}/locations" +
                    "?latitude=${viewport.centerLatitude}&longitude=${viewport.centerLongitude}&radius=$radius"
            val data = http.getBytes(url)
            val collection = json.decodeFromString<AisLocationFeatureCollection>(data.decodeToString())
            collection.features.mapNotNull { feature ->
                val record = parseLocationRecord(feature, fetchedAtMs) ?: return@mapNotNull null
                record.toDisplay(metadataByMmsi[record.mmsi])
            }
        }

    suspend fun fetchVesselsForMmsis(mmsis: Set<Int>): List<AisVesselDisplay> =
        withContext(Dispatchers.IO) {
            if (mmsis.isEmpty()) return@withContext emptyList()
            val fetchedAtMs = System.currentTimeMillis()
            val metadataByMmsi = ensureMetadataCacheLocked()
            val byMmsi =
                fetchLocations(fetchedAtMs)
                    .filter { it.mmsi in mmsis }
                    .associateBy { it.mmsi }
            mmsis.map { mmsi ->
                val location = byMmsi[mmsi]
                if (location != null) {
                    location.toDisplay(metadataByMmsi[mmsi])
                } else {
                    metadataByMmsi[mmsi].toDisplayWithoutLocation(mmsi)
                }
            }
        }

    suspend fun fetchNearbyBrowseItems(
        latitude: Double,
        longitude: Double,
        radiusKm: Int,
    ): List<AisBrowseItem> =
        withContext(Dispatchers.IO) {
            val fetchedAtMs = System.currentTimeMillis()
            val metadataByMmsi = ensureMetadataCacheLocked()
            val url =
                "${AisConfig.DIGITRAFFIC_BASE_URL}/locations" +
                    "?latitude=$latitude&longitude=$longitude&radius=$radiusKm"
            val data = http.getBytes(url)
            val collection = json.decodeFromString<AisLocationFeatureCollection>(data.decodeToString())
            collection.features.mapNotNull { feature ->
                parseBrowseFromFeature(feature, fetchedAtMs, metadataByMmsi, AisBrowseSource.NEARBY)
            }
        }

    suspend fun searchVesselsMetadata(query: String): List<AisBrowseItem> =
        withContext(Dispatchers.IO) {
            val q = query.trim().lowercase()
            if (q.length < AisTrackConfig.GLOBAL_SEARCH_MIN_CHARS) return@withContext emptyList()
            val metadataByMmsi = ensureMetadataCacheLocked()
            val hits = ArrayList<AisBrowseItem>()
            for ((mmsi, meta) in metadataByMmsi) {
                val hay =
                    listOfNotNull(
                        mmsi.toString(),
                        meta.name,
                        meta.callSign,
                    ).joinToString(" ").lowercase()
                if (!hay.contains(q)) continue
                hits +=
                    AisBrowseItem(
                        mmsi = mmsi,
                        name = meta.name,
                        callSign = meta.callSign,
                        source = AisBrowseSource.GLOBAL,
                    )
                if (hits.size >= AisTrackConfig.GLOBAL_SEARCH_MAX) break
            }
            hits.sortedBy { it.displayLabel.lowercase() }
        }

    suspend fun lookupMetadata(mmsi: Int): AisVesselMetaRecord? =
        withContext(Dispatchers.IO) {
            ensureMetadataCacheLocked()[mmsi]
        }

    fun browseRadiusKm(zoom: Double): Int =
        when {
            zoom >= 11 -> 8
            zoom >= 9 -> 15
            zoom >= 7 -> 30
            else -> 50
        }

    private suspend fun ensureMetadataCacheLocked(): Map<Int, AisVesselMetaRecord> =
        metadataMutex.withLock {
            metadataCache ?: fetchVesselMetadata().also { metadataCache = it }
        }

    private fun fetchLocations(fetchedAtMs: Long): List<AisLocationRecord> {
        val url = "${AisConfig.DIGITRAFFIC_BASE_URL}/locations"
        val data = http.getBytes(url)
        val collection = json.decodeFromString<AisLocationFeatureCollection>(data.decodeToString())
        return collection.features.mapNotNull { feature -> parseLocationRecord(feature, fetchedAtMs) }
    }

    private fun fetchVesselMetadata(): Map<Int, AisVesselMetaRecord> {
        val url = "${AisConfig.DIGITRAFFIC_BASE_URL}/vessels"
        val data = http.getBytes(url)
        val records = json.decodeFromString<List<AisVesselMetaRecord>>(data.decodeToString())
        return records.associateBy { it.mmsi }
    }

    private fun parseLocationRecord(
        feature: AisLocationFeature,
        fetchedAtMs: Long,
    ): AisLocationRecord? {
        val coords = feature.geometry.coordinates
        if (coords.size < 2) return null
        val lon = coords[0]
        val lat = coords[1]
        if (!lat.isFinite() || !lon.isFinite()) return null
        return AisLocationRecord(
            mmsi = feature.mmsi,
            latitude = lat,
            longitude = lon,
            sogKn = feature.properties.sog,
            cogDeg = sanitizeCog(feature.properties.cog),
            headingDeg = sanitizeHeading(feature.properties.heading),
            navStatusCode = feature.properties.navStat,
            lastSeenEpochMs = resolveLastSeenEpochMs(feature.properties, fetchedAtMs),
        )
    }

    private fun parseBrowseFromFeature(
        feature: AisLocationFeature,
        fetchedAtMs: Long,
        metadataByMmsi: Map<Int, AisVesselMetaRecord>,
        source: AisBrowseSource,
    ): AisBrowseItem? {
        val record = parseLocationRecord(feature, fetchedAtMs) ?: return null
        val meta = metadataByMmsi[record.mmsi]
        return AisBrowseItem(
            mmsi = record.mmsi,
            name = meta?.name,
            callSign = meta?.callSign,
            latitude = record.latitude,
            longitude = record.longitude,
            sogKn = record.sogKn,
            lastSeenEpochMs = record.lastSeenEpochMs,
            source = source,
        )
    }

    private fun sanitizeCog(cog: Double?): Double? {
        if (cog == null || !cog.isFinite() || cog < 0 || cog >= 360) return null
        return cog
    }

    private fun sanitizeHeading(heading: Int?): Int? {
        if (heading == null || heading !in 0..359) return null
        return heading
    }

    private fun resolveLastSeenEpochMs(
        properties: AisLocationProperties,
        fetchedAtMs: Long,
    ): Long =
        AisMqttMessageParser.normalizeEpochMs(properties.timestampExternal)
            ?: AisMqttMessageParser.normalizeEpochMs(properties.timestamp?.toLong())
            ?: fetchedAtMs
}

private fun AisLocationRecord.toDisplay(meta: AisVesselMetaRecord?): AisVesselDisplay =
    AisVesselDisplay(
        mmsi = mmsi,
        latitude = latitude,
        longitude = longitude,
        name = meta?.name,
        callSign = meta?.callSign,
        destination = meta?.destination,
        imo = meta?.imo,
        draughtTenthsM = meta?.draught,
        shipTypeCode = meta?.shipType,
        etaRaw = meta?.eta,
        navStatusCode = navStatusCode,
        sogKn = sogKn,
        cogDeg = cogDeg,
        headingDeg = headingDeg,
        lastSeenEpochMs = lastSeenEpochMs,
    )

private fun AisVesselMetaRecord?.toDisplayWithoutLocation(mmsi: Int): AisVesselDisplay =
    AisVesselDisplay(
        mmsi = mmsi,
        latitude = Double.NaN,
        longitude = Double.NaN,
        name = this?.name,
        callSign = this?.callSign,
        destination = this?.destination,
        imo = this?.imo,
        draughtTenthsM = this?.draught,
        shipTypeCode = this?.shipType,
        etaRaw = this?.eta,
        navStatusCode = null,
        sogKn = null,
        cogDeg = null,
        headingDeg = null,
        lastSeenEpochMs = null,
    )

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
    val timestampExternal: Long? = null,
    val timestamp: Long? = null,
)

private data class AisLocationRecord(
    val mmsi: Int,
    val latitude: Double,
    val longitude: Double,
    val sogKn: Double?,
    val cogDeg: Double?,
    val headingDeg: Int?,
    val navStatusCode: Int?,
    val lastSeenEpochMs: Long,
)

@Serializable
data class AisVesselMetaRecord(
    val mmsi: Int,
    val name: String? = null,
    val callSign: String? = null,
    val destination: String? = null,
    val imo: Int? = null,
    val draught: Int? = null,
    val shipType: Int? = null,
    val eta: Int? = null,
)
