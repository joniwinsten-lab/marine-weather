package fi.veneappi.app.data.marine

/**
 * Detects whether loaded source text contains marine warnings or softer notices.
 * Uses only data already fetched for the card — details stay on the provider site.
 */
object MarineForecastAlertClassifier {
    private val noWarningsLine =
        Regex(
            """(?i)^\s*(no\s+warnings?|ingen\s+varningar?|inga\s+varningar?|ei\s+varoituksia?|varoituksia\s+ei|uten\s+varsler?)\s*\.?\s*$""",
        )

    private val warningPatterns =
        listOf(
            Regex("""(?i)\b(wind\s+warning|ice\s+warning|polarlow\s+warning|polarlow)\b"""),
            Regex("""(?i)\b(warning|varoitus|varning|varsel)\b"""),
            Regex("""(?i)\b(near\s+)?gale\s+\d"""),
            Regex("""(?i)\b(storm|hurricane)\s+\d"""),
            Regex("""(?i)\b(strong\s+wind|kova\s+tuuli|hård\s+vind)\s+(warning|varoitus|varning)"""),
        )

    private val noticePatterns =
        listOf(
            Regex("""(?i)\b(risk\s+of|possible|locally|attention|caution|observe)\b"""),
            Regex("""(?i)\b(huomio|huomautus|anmärkning|merknad|bemerkning)\b"""),
            Regex("""(?i)\b(fog|sumu|tåke|tåka|uku|visibility|näkyvyys|sikt)\b"""),
            Regex("""(?i)\b(poor\s+visibility|reduced\s+visibility|näkyvyys\s+heikko)"""),
            Regex("""(?i)\b(moderate|rough|very\s+rough|kova\s+aallokko)\b"""),
            Regex("""(?i)\b(showers?|sleet|thunder|ukkonen|åska)\b"""),
        )

    fun classify(
        rawText: String,
        metWindWarning: String? = null,
        metIceWarning: String? = null,
        metPolarlowWarning: String? = null,
    ): MarineForecastAlertLevel {
        if (hasStructuredMetWarnings(metWindWarning, metIceWarning, metPolarlowWarning)) {
            return MarineForecastAlertLevel.Warning
        }

        val text = rawText.trim()
        if (text.isBlank() || text.startsWith("At the map centre:")) {
            return MarineForecastAlertLevel.None
        }

        if (containsWarning(text)) {
            return MarineForecastAlertLevel.Warning
        }
        if (containsNotice(text)) {
            return MarineForecastAlertLevel.Notice
        }
        return MarineForecastAlertLevel.None
    }

    private fun hasStructuredMetWarnings(
        wind: String?,
        ice: String?,
        polarlow: String?,
    ): Boolean =
        listOf(wind, ice, polarlow).any { it?.isNotBlank() == true }

    private fun containsWarning(text: String): Boolean {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        for (line in lines) {
            if (noWarningsLine.matches(line)) continue
            if (warningPatterns.any { it.containsMatchIn(line) }) {
                if (!isBenignWarningMention(line)) return true
            }
        }
        return false
    }

    private fun containsNotice(text: String): Boolean {
        if (noWarningsOnlyReport(text)) return false
        return noticePatterns.any { it.containsMatchIn(text) }
    }

    /** e.g. "No warnings." as the only alert-related content. */
    private fun noWarningsOnlyReport(text: String): Boolean {
        val alertLines =
            text.lines().map { it.trim() }.filter { line ->
                line.isNotEmpty() &&
                    (
                        noWarningsLine.matches(line) ||
                            warningPatterns.any { it.containsMatchIn(line) } ||
                            noticePatterns.any { it.containsMatchIn(line) }
                    )
            }
        return alertLines.isNotEmpty() && alertLines.all { noWarningsLine.matches(it) }
    }

    private fun isBenignWarningMention(line: String): Boolean =
        noWarningsLine.matches(line) ||
            Regex("""(?i)without\s+warning""").containsMatchIn(line)
}
