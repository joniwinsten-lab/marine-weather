package fi.veneappi.app.ui.ais

enum class AisStreamMode {
    Off,
    Connecting,
    RestOnly,
    /** Network or parse failure (e.g. emulator SSL). */
    Error,
}
