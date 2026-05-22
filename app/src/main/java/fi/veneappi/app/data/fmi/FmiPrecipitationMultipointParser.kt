package fi.veneappi.app.data.fmi

import java.time.Instant

/** One timestep of precipitation at a single point from FMI WFS multipoint coverage. */
data class FmiPrecipitationStep(
    val epochMs: Long,
    /** Model precipitation amount for the timestep (typically mm per step). */
    val amountMm: Double,
)

object FmiPrecipitationMultipointParser {
    private val positionsBlock =
        Regex(
            "<gmlcov:positions>\\s*([\\s\\S]*?)\\s*</gmlcov:positions>",
            RegexOption.IGNORE_CASE,
        )
    private val tupleBlock =
        Regex(
            "<gml:doubleOrNilReasonTupleList>\\s*([\\s\\S]*?)\\s*</gml:doubleOrNilReasonTupleList>",
            RegexOption.IGNORE_CASE,
        )

    fun parse(xml: String): List<FmiPrecipitationStep> {
        if (xml.contains("ExceptionReport", ignoreCase = true)) {
            error("FMI error response")
        }
        val positionsXml =
            positionsBlock.find(xml)?.groupValues?.get(1)?.trim()
                ?: error("positions block missing")
        val tuplesXml =
            tupleBlock.find(xml)?.groupValues?.get(1)?.trim()
                ?: error("tuple block missing")
        val epochs = parsePositionEpochs(positionsXml)
        val amounts = parseAmounts(tuplesXml)
        require(epochs.size == amounts.size) {
            "FMI precip positions ${epochs.size} != tuples ${amounts.size}"
        }
        return epochs.zip(amounts) { epochMs, amount ->
            FmiPrecipitationStep(epochMs = epochMs, amountMm = amount)
        }
    }

    private fun parsePositionEpochs(block: String): List<Long> =
        block
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { line ->
                val parts = line.split(Regex("\\s+")).filter { it.isNotEmpty() }
                if (parts.size < 3) return@mapNotNull null
                Instant.ofEpochSecond(parts[2].toLong()).toEpochMilli()
            }

    private fun parseAmounts(block: String): List<Double> =
        block
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { line ->
                when {
                    line.equals("NaN", ignoreCase = true) -> 0.0
                    else -> line.toDoubleOrNull() ?: 0.0
                }
            }
}
