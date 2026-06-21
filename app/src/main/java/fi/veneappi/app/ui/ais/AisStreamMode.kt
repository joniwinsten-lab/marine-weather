package fi.veneappi.app.ui.ais

enum class AisStreamMode {
    Off,
    Connecting,
    /** MQTT connected; live position updates. */
    Live,
    RestOnly,
    /** Network or parse failure (e.g. emulator SSL). */
    Error,
}
