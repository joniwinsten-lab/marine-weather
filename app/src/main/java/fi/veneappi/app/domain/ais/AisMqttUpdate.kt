package fi.veneappi.app.domain.ais

/** Parsed MQTT payload ready to merge into the in-memory fleet. */
sealed interface AisMqttUpdate {
    val mmsi: Int
}

data class AisMqttLocationUpdate(
    override val mmsi: Int,
    val latitude: Double,
    val longitude: Double,
    val sogKn: Double?,
    val cogDeg: Double?,
    val headingDeg: Int?,
    val navStatusCode: Int?,
    val lastSeenEpochMs: Long,
) : AisMqttUpdate

data class AisMqttMetadataUpdate(
    override val mmsi: Int,
    val name: String?,
    val callSign: String?,
    val destination: String?,
) : AisMqttUpdate

fun AisVesselDisplay.applyMqttUpdate(update: AisMqttUpdate): AisVesselDisplay =
    when (update) {
        is AisMqttLocationUpdate ->
            copy(
                latitude = update.latitude,
                longitude = update.longitude,
                sogKn = update.sogKn,
                cogDeg = update.cogDeg,
                headingDeg = update.headingDeg,
                navStatusCode = update.navStatusCode,
                lastSeenEpochMs = update.lastSeenEpochMs,
            )
        is AisMqttMetadataUpdate ->
            copy(
                name = update.name ?: name,
                callSign = update.callSign ?: callSign,
                destination = update.destination ?: destination,
            )
    }
