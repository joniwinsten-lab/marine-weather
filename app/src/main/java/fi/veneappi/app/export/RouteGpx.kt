package fi.veneappi.app.export

import java.time.Instant
import java.time.format.DateTimeFormatter

object RouteGpx {
    private val isoTime: DateTimeFormatter = DateTimeFormatter.ISO_INSTANT

    fun build(
        routeName: String,
        points: List<Pair<Double, Double>>,
        createdAt: Instant = Instant.now(),
    ): String {
        require(points.size >= 2) { "GPX route needs at least two points" }
        val timeStr = isoTime.format(createdAt)
        val safeName = xmlEscape(routeName.ifBlank { "Route" })
        return buildString {
            appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
            appendLine(
                """<gpx version="1.1" creator="Marine Weather" xmlns="http://www.topografix.com/GPX/1/1" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd">""",
            )
            appendLine("  <metadata>")
            appendLine("    <name>$safeName</name>")
            appendLine("    <time>$timeStr</time>")
            appendLine("  </metadata>")
            appendLine("  <rte>")
            appendLine("    <name>$safeName</name>")
            appendLine("    <type>Route planning (not for primary navigation)</type>")
            for ((lat, lon) in points) {
                appendLine("    <rtept lat=\"$lat\" lon=\"$lon\">")
                appendLine("      <time>$timeStr</time>")
                appendLine("    </rtept>")
            }
            appendLine("  </rte>")
            appendLine("</gpx>")
        }
    }

    private fun xmlEscape(s: String): String =
        buildString(s.length + 8) {
            for (c in s) {
                when (c) {
                    '<' -> append("&lt;")
                    '>' -> append("&gt;")
                    '&' -> append("&amp;")
                    '"' -> append("&quot;")
                    '\'' -> append("&apos;")
                    else -> append(c)
                }
            }
        }
}
