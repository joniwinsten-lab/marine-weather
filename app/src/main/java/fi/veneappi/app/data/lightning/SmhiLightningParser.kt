package fi.veneappi.app.data.lightning

import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * SMHI open lightning CSV (semicolon-separated).
 * @see <a href="https://opendata-download-lightning.smhi.se/">SMHI lightning API</a>
 */
object SmhiLightningParser {
    fun parse(
        csv: String,
        lookbackEpochMs: Long,
    ): List<LightningStrike> {
        val lines = csv.lines().filter { it.isNotBlank() }
        if (lines.size < 2) return emptyList()
        val header = lines.first().split(';')
        val latIdx = header.indexOf("lat")
        val lonIdx = header.indexOf("lon")
        val yearIdx = header.indexOf("year")
        val monthIdx = header.indexOf("month")
        val dayIdx = header.indexOf("day")
        val hourIdx = header.indexOf("hours")
        val minIdx = header.indexOf("minutes")
        val secIdx = header.indexOf("seconds")
        if (latIdx < 0 || lonIdx < 0) return emptyList()

        val out = mutableListOf<LightningStrike>()
        for (line in lines.drop(1)) {
            val cols = line.split(';')
            if (cols.size <= maxOf(latIdx, lonIdx)) continue
            val lat = cols[latIdx].toDoubleOrNull() ?: continue
            val lon = cols[lonIdx].toDoubleOrNull() ?: continue
            val epoch =
                parseEpoch(cols, yearIdx, monthIdx, dayIdx, hourIdx, minIdx, secIdx)
                    ?: continue
            if (epoch < lookbackEpochMs) continue
            if (lat !in 50.0..72.0 || lon !in 8.0..40.0) continue
            out.add(
                LightningStrike(
                    latitude = lat,
                    longitude = lon,
                    observedAtEpochMs = epoch,
                    source = LightningSourceId.SMHI,
                ),
            )
        }
        return out
    }

    private fun parseEpoch(
        cols: List<String>,
        yearIdx: Int,
        monthIdx: Int,
        dayIdx: Int,
        hourIdx: Int,
        minIdx: Int,
        secIdx: Int,
    ): Long? {
        if (yearIdx < 0 || monthIdx < 0 || dayIdx < 0) return null
        val y = cols.getOrNull(yearIdx)?.toIntOrNull() ?: return null
        val mo = cols.getOrNull(monthIdx)?.toIntOrNull() ?: return null
        val d = cols.getOrNull(dayIdx)?.toIntOrNull() ?: return null
        val h = cols.getOrNull(hourIdx)?.toIntOrNull() ?: 0
        val mi = cols.getOrNull(minIdx)?.toIntOrNull() ?: 0
        val s = cols.getOrNull(secIdx)?.toIntOrNull() ?: 0
        return runCatching {
            ZonedDateTime.of(y, mo, d, h, mi, s, 0, ZoneOffset.UTC).toInstant().toEpochMilli()
        }.getOrNull()
    }
}
