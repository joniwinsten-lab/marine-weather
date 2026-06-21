package fi.veneappi.app.domain.ais

import kotlinx.serialization.Serializable

@Serializable
data class AisWatchlistEntry(
    val mmsi: Int,
    val nickname: String? = null,
    val name: String? = null,
    val callSign: String? = null,
    val destination: String? = null,
    val addedAtEpochMs: Long = System.currentTimeMillis(),
    val addedSource: String = AddedSource.MANUAL.name,
) {
    val displayLabel: String
        get() {
            val nick = nickname?.trim().orEmpty()
            if (nick.isNotEmpty()) return nick
            val vesselName = name?.trim().orEmpty()
            if (vesselName.isNotEmpty()) return vesselName
            return "MMSI $mmsi"
        }
}

enum class AddedSource {
    MAP_TAP,
    SEARCH,
    MANUAL,
}

/** Row in the Hae (browse) panel — nearby or global metadata hit. */
data class AisBrowseItem(
    val mmsi: Int,
    val name: String?,
    val callSign: String?,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val sogKn: Double? = null,
    val lastSeenEpochMs: Long? = null,
    val source: AisBrowseSource,
) {
    val displayLabel: String
        get() {
            val trimmed = name?.trim().orEmpty()
            return trimmed.ifEmpty { "MMSI $mmsi" }
        }
}

enum class AisBrowseSource {
    NEARBY,
    GLOBAL,
}

enum class AisTrackListFilter {
    ALL,
    ACTIVE,
    STALE,
}

enum class AisTrackPanel {
    WATCHLIST,
    BROWSE,
}
