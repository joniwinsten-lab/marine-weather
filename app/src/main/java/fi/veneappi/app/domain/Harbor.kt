package fi.veneappi.app.domain

enum class HarborKind {
    Marina,
    GuestHarbor,
    HarbourArea,
    SeaMarkHarbour,
    BoatFuel,
    Other,
}

/**
 * Normalized harbor / marina / fuel point from OpenStreetMap (Overpass).
 * Not verified; always confirm with official charts and local notices.
 */
data class Harbor(
    val osmKey: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val kind: HarborKind,
    val phone: String?,
    val website: String?,
    val description: String?,
    val operator: String?,
    val capacity: String?,
    val mooring: String?,
    val fee: String?,
)
