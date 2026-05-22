package fi.veneappi.app.data.fmi

import fi.veneappi.app.domain.UnifiedTimePoint
import java.time.Instant
import java.util.regex.Pattern

object FmiMultipointParser {
    private val positionsBlock =
        Pattern.compile(
            "<gmlcov:positions>\\s*([\\s\\S]*?)\\s*</gmlcov:positions>",
            Pattern.CASE_INSENSITIVE,
        )
    private val tupleBlock =
        Pattern.compile(
            "<gml:doubleOrNilReasonTupleList>\\s*([\\s\\S]*?)\\s*</gml:doubleOrNilReasonTupleList>",
            Pattern.CASE_INSENSITIVE,
        )

    fun parse(xml: String): List<UnifiedTimePoint> {
        if (xml.contains("ExceptionReport", ignoreCase = true)) {
            error("FMI error response")
        }
        val posMatcher = positionsBlock.matcher(xml)
        val tupleMatcher = tupleBlock.matcher(xml)
        require(posMatcher.find()) { "positions block missing" }
        require(tupleMatcher.find()) { "tuple block missing" }
        val positionsXml = posMatcher.group(1) ?: error("positions empty")
        val tuplesXml = tupleMatcher.group(1) ?: error("tuples empty")
        val times = parsePositions(positionsXml)
        val tuples = parseTuples(tuplesXml)
        require(times.size == tuples.size) { "FMI positions ${times.size} != tuples ${tuples.size}" }
        return times.zip(tuples) { instant, t ->
            UnifiedTimePoint(
                instantUtc = instant,
                airTempC = t.airTempC,
                windSpeedMs = t.windSpeedMs,
                windFromDeg = t.windFromDeg,
                windGustMs = t.windGustMs,
                precipitationMmPerH = null,
                thunderProbPercent = null,
            )
        }
    }

    private data class FmiTuple(
        val airTempC: Double,
        val windSpeedMs: Double,
        val windFromDeg: Double,
        val windGustMs: Double?,
    )

    private fun parsePositions(block: String): List<Long> {
        val lines =
            block
                .trim()
                .lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        val out = ArrayList<Long>(lines.size)
        for (line in lines) {
            val parts = line.split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (parts.size < 3) continue
            val epoch = parts[2].toLong()
            out.add(Instant.ofEpochSecond(epoch).toEpochMilli())
        }
        return out
    }

    private fun parseTuples(block: String): List<FmiTuple> {
        val lines =
            block
                .trim()
                .lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        val out = ArrayList<FmiTuple>(lines.size)
        for (line in lines) {
            val parts = line.split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (parts.size < 3) continue
            val gust =
                parts.getOrNull(3)?.toDoubleOrNull()?.takeUnless { it.isNaN() }
            out.add(
                FmiTuple(
                    airTempC = parts[0].toDouble(),
                    windSpeedMs = parts[1].toDouble(),
                    windFromDeg = parts[2].toDouble(),
                    windGustMs = gust,
                ),
            )
        }
        return out
    }
}
