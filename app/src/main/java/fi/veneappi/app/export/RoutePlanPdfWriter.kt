package fi.veneappi.app.export

import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import fi.veneappi.app.domain.UnifiedTimePoint
import fi.veneappi.app.domain.WindUnit
import fi.veneappi.app.domain.msToKnots
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlin.math.roundToInt

data class RoutePlanPdfSourceSection(
    val title: String,
    val modelLine: String?,
    val errorLine: String?,
    val slotLines: List<String>?,
)

data class RoutePlanPdfInput(
    val docTitle: String,
    val generatedLine: String,
    val coordinatesHeading: String,
    val startLine: String,
    val endLine: String,
    val legSummaryLine: String,
    val departureLine: String? = null,
    val boatSpeedLine: String,
    val weatherHeading: String,
    val disclaimer: String,
    val footerAttribution: String,
    val sections: List<RoutePlanPdfSourceSection>,
)

object RoutePlanPdfWriter {
    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 48f
    private const val LINE = 13.5f
    private const val SECTION_GAP = 8f

    fun writeToFile(output: File, input: RoutePlanPdfInput): Result<Unit> =
        runCatching {
            output.parentFile?.mkdirs()
            val doc = PdfDocument()
            var pageNum = 1
            var page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNum).create())
            var canvas = page.canvas
            var y = MARGIN + 24f

            val titlePaint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = 18f
                    typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                }
            val headingPaint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = 13f
                    typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                }
            val bodyPaint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = 11f
                    typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                }
            val smallPaint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    textSize = 9.5f
                    typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC)
                    color = 0xFF444444.toInt()
                }

            fun pageBottom() = PAGE_H - MARGIN

            fun newPageIfNeeded(extra: Float) {
                if (y + extra <= pageBottom()) return
                doc.finishPage(page)
                pageNum++
                page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNum).create())
                canvas = page.canvas
                y = MARGIN + 18f
            }

            fun drawWrapped(text: String, paint: Paint) {
                val maxW = PAGE_W - 2 * MARGIN
                var remaining = text
                while (remaining.isNotEmpty()) {
                    newPageIfNeeded(LINE * 3)
                    var count = paint.breakText(remaining, true, maxW, null)
                    if (count <= 0) count = remaining.length.coerceAtMost(1)
                    val line = remaining.take(count)
                    remaining = remaining.drop(count).trimStart()
                    canvas.drawText(line, MARGIN, y, paint)
                    y += LINE
                }
            }

            fun drawLine(text: String, paint: Paint = bodyPaint) {
                newPageIfNeeded(LINE)
                canvas.drawText(text, MARGIN, y, paint)
                y += LINE
            }

            canvas.drawText(input.docTitle, MARGIN, y, titlePaint)
            y += LINE * 1.8f
            drawLine(input.generatedLine, smallPaint)
            y += SECTION_GAP

            canvas.drawText(input.coordinatesHeading, MARGIN, y, headingPaint)
            y += LINE * 1.3f
            drawWrapped(input.startLine, bodyPaint)
            drawWrapped(input.endLine, bodyPaint)
            y += SECTION_GAP * 0.5f
            drawWrapped(input.legSummaryLine, bodyPaint)
            input.departureLine?.let { drawWrapped(it, bodyPaint) }
            drawWrapped(input.boatSpeedLine, bodyPaint)
            y += SECTION_GAP

            canvas.drawText(input.weatherHeading, MARGIN, y, headingPaint)
            y += LINE * 1.3f

            for (sec in input.sections) {
                newPageIfNeeded(LINE * 6)
                canvas.drawText(sec.title, MARGIN, y, headingPaint)
                y += LINE * 1.1f
                sec.modelLine?.let { drawWrapped(it, smallPaint) }
                if (sec.errorLine != null) {
                    drawWrapped(sec.errorLine, bodyPaint)
                } else {
                    sec.slotLines?.forEach { drawWrapped(it, bodyPaint) }
                }
                y += SECTION_GAP
            }

            y += SECTION_GAP
            drawWrapped(input.disclaimer, smallPaint)
            y += SECTION_GAP * 0.5f
            drawWrapped(input.footerAttribution, smallPaint)

            doc.finishPage(page)
            FileOutputStream(output).use { out -> doc.writeTo(out) }
            doc.close()
        }
}

fun formatRouteSlotForPdf(
    slotLabel: String,
    point: UnifiedTimePoint?,
    windUnit: WindUnit,
    locale: Locale,
): String {
    if (point == null || point.windSpeedMs == null) {
        return "  • $slotLabel: —"
    }
    val speedStr =
        when (windUnit) {
            WindUnit.MetersPerSecond ->
                String.format(locale, "%.1f m/s", point.windSpeedMs)
            WindUnit.Knots ->
                String.format(locale, "%.0f kn", point.windSpeedMs.msToKnots() ?: 0.0)
        }
    val dirStr =
        point.windFromDeg?.let { d ->
            val card = cardinal16(d)
            String.format(locale, ", %d° (%s)", d.roundToInt(), card)
        } ?: ""
    val gustStr =
        point.windGustMs?.let { g ->
            val gTxt =
                when (windUnit) {
                    WindUnit.MetersPerSecond -> String.format(locale, "%.1f m/s", g)
                    WindUnit.Knots -> String.format(locale, "%.0f kn", g.msToKnots() ?: g)
                }
            String.format(locale, ", max %s", gTxt)
        } ?: ""
    val tempStr =
        point.airTempC?.let { t ->
            String.format(locale, ", air %.1f °C", t)
        } ?: ""
    return "  • $slotLabel: $speedStr$dirStr$gustStr$tempStr"
}

private fun cardinal16(deg: Double): String {
    val dirs =
        arrayOf(
            "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
            "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW",
        )
    val x = ((deg % 360.0) + 360.0) % 360.0
    val idx = ((x + 11.25) / 22.5).toInt() % 16
    return dirs[idx]
}
