package fi.veneappi.app.data.lightning

import java.time.Instant

/**
 * Parses FMI WFS 2.0 simple-feature lightning responses (gml:pos and BsWfs parameters).
 */
object FmiLightningParser {
    private val memberBlock = Regex("""<wfs:member>([\s\S]*?)</wfs:member>""", RegexOption.IGNORE_CASE)
    private val gmlPos = Regex("""<gml:pos[^>]*>\s*([-\d.]+)\s+([-\d.]+)\s*</gml:pos>""", RegexOption.IGNORE_CASE)
    private val timeTag = Regex("""<BsWfs:Time[^>]*>([^<]+)</BsWfs:Time>""", RegexOption.IGNORE_CASE)
    private val lonParam =
        Regex(
            """<BsWfs:Parameter[^>]*name="longitude"[^>]*values="([-\d.]+)"""",
            RegexOption.IGNORE_CASE,
        )
    private val latParam =
        Regex(
            """<BsWfs:Parameter[^>]*name="latitude"[^>]*values="([-\d.]+)"""",
            RegexOption.IGNORE_CASE,
        )

    fun parse(xml: String): List<LightningStrike> {
        val out = mutableListOf<LightningStrike>()
        for (block in memberBlock.findAll(xml)) {
            val inner = block.groupValues[1]
            val epoch =
                timeTag.find(inner)?.groupValues?.get(1)?.let { parseTime(it) }
                    ?: System.currentTimeMillis()
            val pos = gmlPos.find(inner)
            if (pos != null) {
                val a = pos.groupValues[1].toDoubleOrNull()
                val b = pos.groupValues[2].toDoubleOrNull()
                if (a != null && b != null) {
                    addStrike(out, a, b, epoch)
                    continue
                }
            }
            val lon = lonParam.find(inner)?.groupValues?.get(1)?.toDoubleOrNull()
            val lat = latParam.find(inner)?.groupValues?.get(1)?.toDoubleOrNull()
            if (lon != null && lat != null) {
                addStrike(out, lon, lat, epoch)
            }
        }
        return out
    }

    private fun addStrike(
        out: MutableList<LightningStrike>,
        first: Double,
        second: Double,
        epoch: Long,
    ) {
        val (lat, lon) = inferLatLon(first, second)
        if (lat in 50.0..72.0 && lon in 10.0..40.0) {
            out.add(
                LightningStrike(
                    latitude = lat,
                    longitude = lon,
                    observedAtEpochMs = epoch,
                    source = LightningSourceId.FMI,
                ),
            )
        }
    }

    /** FMI may return "lon lat" or "lat lon" — pick the pair that falls in the Baltic region. */
    private fun inferLatLon(
        a: Double,
        b: Double,
    ): Pair<Double, Double> {
        val asLonLat = a in 10.0..40.0 && b in 50.0..72.0
        val asLatLon = a in 50.0..72.0 && b in 10.0..40.0
        return when {
            asLonLat -> b to a
            asLatLon -> a to b
            b > a -> a to b // guess lat, lon
            else -> b to a
        }
    }

    private fun parseTime(raw: String): Long =
        runCatching { Instant.parse(raw.trim()).toEpochMilli() }.getOrDefault(System.currentTimeMillis())
}
