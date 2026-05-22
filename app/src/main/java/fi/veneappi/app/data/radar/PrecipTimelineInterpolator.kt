package fi.veneappi.app.data.radar

import fi.veneappi.app.data.fmi.FmiPrecipitationStep
import java.time.Instant

/** Time-interpolates precipitation grids so 30 min scrub steps do not jump hourly. */
internal object PrecipTimelineInterpolator {
    private const val GRIB_STEP_MINUTES = 60

    fun gribRatesAt(
        fields: List<FmiHarmonieGribParser.PrecipField>,
        target: Instant,
    ): Array<DoubleArray>? {
        if (fields.isEmpty()) return null
        val sorted = fields.sortedBy { it.validity }
        val targetMs = target.toEpochMilli()
        val first = sorted.first()
        val last = sorted.last()
        if (targetMs <= first.validity.toEpochMilli()) {
            return mmGridToRatesMmPerH(first.amountMm, GRIB_STEP_MINUTES)
        }
        if (targetMs >= last.validity.toEpochMilli()) {
            return mmGridToRatesMmPerH(last.amountMm, GRIB_STEP_MINUTES)
        }
        val beforeIdx = sorted.indexOfLast { it.validity.toEpochMilli() <= targetMs }
        val a = sorted[beforeIdx]
        val b = sorted[beforeIdx + 1]
        val spanMs = b.validity.toEpochMilli() - a.validity.toEpochMilli()
        if (spanMs <= 0) return mmGridToRatesMmPerH(a.amountMm, GRIB_STEP_MINUTES)
        val weightB = (targetMs - a.validity.toEpochMilli()).toDouble() / spanMs.toDouble()
        val blended = blendMmGrids(a.amountMm, b.amountMm, weightB) ?: a.amountMm
        return mmGridToRatesMmPerH(blended, GRIB_STEP_MINUTES)
    }

    fun wfsRatesAt(
        series: Array<Array<List<FmiPrecipitationStep>>>,
        target: Instant,
        stepMinutes: Int,
    ): Array<DoubleArray> {
        val targetMs = target.toEpochMilli()
        val cells = series.size
        return Array(cells) { row ->
            DoubleArray(cells) { col ->
                val steps = series[row][col]
                val mm = interpolateStepAmountMm(steps, targetMs) ?: 0.0
                val hours = stepMinutes / 60.0
                if (hours <= 0) mm else mm / hours
            }
        }
    }

    private fun interpolateStepAmountMm(
        steps: List<FmiPrecipitationStep>,
        targetMs: Long,
    ): Double? {
        if (steps.isEmpty()) return null
        if (steps.size == 1) return steps[0].amountMm
        val sorted = steps.sortedBy { it.epochMs }
        if (targetMs <= sorted.first().epochMs) return sorted.first().amountMm
        if (targetMs >= sorted.last().epochMs) return sorted.last().amountMm
        val beforeIdx = sorted.indexOfLast { it.epochMs <= targetMs }
        val a = sorted[beforeIdx]
        val b = sorted[beforeIdx + 1]
        val span = b.epochMs - a.epochMs
        if (span <= 0) return a.amountMm
        val weightB = (targetMs - a.epochMs).toDouble() / span.toDouble()
        return a.amountMm * (1 - weightB) + b.amountMm * weightB
    }

    private fun blendMmGrids(
        a: Array<DoubleArray>,
        b: Array<DoubleArray>,
        weightB: Double,
    ): Array<DoubleArray>? {
        if (a.isEmpty() || b.isEmpty() || a.size != b.size || a[0].size != b[0].size) return null
        val w = weightB.coerceIn(0.0, 1.0)
        return Array(a.size) { row ->
            DoubleArray(a[row].size) { col ->
                a[row][col] * (1 - w) + b[row][col] * w
            }
        }
    }

    private fun mmGridToRatesMmPerH(
        mm: Array<DoubleArray>,
        stepMinutes: Int,
    ): Array<DoubleArray> {
        val hours = stepMinutes / 60.0
        return Array(mm.size) { row ->
            DoubleArray(mm[row].size) { col ->
                val amount = mm[row][col]
                if (hours <= 0) amount else amount / hours
            }
        }
    }
}
