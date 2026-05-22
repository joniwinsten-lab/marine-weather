package fi.veneappi.app.data.marine

/**
 * Short on-screen outlook (about two sentences), prioritising warnings.
 */
object MarineTextSummarizer {
    private val warningHint =
        Regex(
            """(?i)\b(warning|varoitus|varning|alert|gale|storm|kuling|hazard|ice\s+warning|wind\s+warning|polarlow)\b""",
        )
    private val sentenceSplit = Regex("""(?<=[.!?])\s+""")

    fun summarize(
        raw: String,
        maxSentences: Int = 2,
    ): String {
        val normalized =
            raw
                .trim()
                .replace(Regex("""\s+"""), " ")
        if (normalized.isBlank()) return ""

        val sentences = splitSentences(normalized)
        if (sentences.isEmpty()) {
            return normalized.take(280).let { if (normalized.length > 280) "$it…" else it }
        }

        val warnings = sentences.filter { warningHint.containsMatchIn(it) }
        val rest = sentences.filterNot { warningHint.containsMatchIn(it) }
        val picked = (warnings + rest).take(maxSentences)
        return picked.joinToString(". ") { it.trimEnd('.', '!', '?') } + "."
    }

    private fun splitSentences(text: String): List<String> =
        sentenceSplit
            .split(text)
            .map { it.trim() }
            .filter { it.length >= 10 }
}
