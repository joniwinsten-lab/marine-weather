package fi.veneappi.app.domain.ais

import java.util.Locale

object AisFormatting {
    fun formatSpeedKn(kn: Double?): String {
        if (kn == null || !kn.isFinite()) return "—"
        return String.format(Locale.US, "%.1f kn", kn)
    }

    fun formatBearing(deg: Double?): String {
        if (deg == null || !deg.isFinite() || deg < 0 || deg >= 360) return "—"
        return String.format(Locale.US, "%.0f°", deg)
    }

    fun formatHeading(deg: Int?): String {
        if (deg == null || deg !in 0..359) return "—"
        return String.format(Locale.US, "%d°", deg)
    }

    fun formatDraughtMeters(tenths: Int?): String? {
        if (tenths == null || tenths <= 0) return null
        return String.format(Locale.US, "%.1f m", tenths / 10.0)
    }

    fun formatDestination(raw: String?): String? {
        val trimmed = raw?.trim().orEmpty()
        return trimmed.ifEmpty { null }
    }

    /** AIS ETA field: MMDDHHMM (month/day/hour/minute, UTC). */
    fun formatEta(eta: Int?): String? {
        if (eta == null || eta <= 0) return null
        val s = String.format(Locale.US, "%06d", eta % 1_000_000)
        if (s.length != 6) return null
        val month = s.substring(0, 2).toIntOrNull() ?: return null
        val day = s.substring(2, 4).toIntOrNull() ?: return null
        val hour = s.substring(4, 6).toIntOrNull() ?: return null
        if (month !in 1..12 || day !in 1..31 || hour !in 0..23) return null
        return String.format(Locale.US, "%02d-%02d %02d:00 UTC", month, day, hour)
    }
}
