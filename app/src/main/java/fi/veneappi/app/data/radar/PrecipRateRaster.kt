package fi.veneappi.app.data.radar

import android.graphics.Bitmap
import android.graphics.Color
import java.io.ByteArrayOutputStream
import kotlin.math.floor
import kotlin.math.min

/** Builds a precipitation raster (mm/h) for map overlay with smooth interpolation. */
object PrecipRateRaster {
    const val GRID_CELLS = 9

    /** Below this rate (mm/h) pixels are fully transparent. */
    const val MIN_DISPLAY_MM_PER_H = 0.45

    /** Frame needs at least this peak rate to generate an overlay. */
    const val MIN_FRAME_MAX_MM_PER_H = 0.65

    /** At least this fraction of grid cells must exceed [MIN_DISPLAY_MM_PER_H]. */
    const val MIN_WET_CELL_FRACTION = 0.012

    private const val DEFAULT_GRID_SMOOTH_PASSES = 1
    private const val DEFAULT_OUTER_FADE_MARGIN = 0.12
    private const val PNG_QUALITY = 82

    fun renderGrid(
        ratesMmPerH: Array<DoubleArray>,
        widthPx: Int = 512,
        heightPx: Int = 512,
        gridSmoothPasses: Int = DEFAULT_GRID_SMOOTH_PASSES,
        outerFadeMarginFraction: Double = DEFAULT_OUTER_FADE_MARGIN,
    ): ByteArray {
        val grid = prepareForRender(ratesMmPerH, gridSmoothPasses)
        val rows = grid.size
        val cols = grid[0].size
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(widthPx * heightPx)
        val maxRow = (rows - 1).coerceAtLeast(0)
        val maxCol = (cols - 1).coerceAtLeast(0)
        var i = 0
        for (y in 0 until heightPx) {
            val gy = if (heightPx <= 1) 0.0 else y.toDouble() * maxRow / (heightPx - 1)
            val outerY = outerEdgeAlphaFactor(y, heightPx, outerFadeMarginFraction)
            for (x in 0 until widthPx) {
                val gx = if (widthPx <= 1) 0.0 else x.toDouble() * maxCol / (widthPx - 1)
                val outerX = outerEdgeAlphaFactor(x, widthPx, outerFadeMarginFraction)
                val rate = bilinearSample(grid, gy, gx)
                val edgeSoftness = edgeSoftnessFactor(grid, gy, gx, rate)
                pixels[i++] = colorForRate(rate, edgeSoftness, min(outerX, outerY))
            }
        }
        bitmap.setPixels(pixels, 0, widthPx, 0, 0, widthPx, heightPx)
        return ByteArrayOutputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, out)
            out.toByteArray()
        }
    }

    fun maxRateMmPerH(ratesMmPerH: Array<DoubleArray>): Double =
        ratesMmPerH.maxOfOrNull { row -> row.maxOrNull() ?: 0.0 } ?: 0.0

    fun hasSignificantPrecip(ratesMmPerH: Array<DoubleArray>): Boolean {
        val prepared = clampNoiseFloor(ratesMmPerH, MIN_DISPLAY_MM_PER_H)
        if (maxRateMmPerH(prepared) < MIN_FRAME_MAX_MM_PER_H) return false
        var wet = 0
        var total = 0
        for (row in prepared) {
            for (value in row) {
                total++
                if (value >= MIN_DISPLAY_MM_PER_H) wet++
            }
        }
        if (total == 0) return false
        return wet.toDouble() / total.toDouble() >= MIN_WET_CELL_FRACTION
    }

    fun prepareForRender(
        ratesMmPerH: Array<DoubleArray>,
        gridSmoothPasses: Int = DEFAULT_GRID_SMOOTH_PASSES,
    ): Array<DoubleArray> {
        val clamped = clampNoiseFloor(ratesMmPerH, MIN_DISPLAY_MM_PER_H)
        if (gridSmoothPasses <= 0 || maxRateMmPerH(clamped) < MIN_DISPLAY_MM_PER_H) {
            return clamped
        }
        return smoothGrid(clamped, gridSmoothPasses)
    }

    fun clampNoiseFloor(
        grid: Array<DoubleArray>,
        floorMmPerH: Double,
    ): Array<DoubleArray> =
        Array(grid.size) { row ->
            DoubleArray(grid[row].size) { col ->
                val v = grid[row][col]
                if (v < floorMmPerH) 0.0 else v
            }
        }

    /** Fades raster alpha near PNG edges so the forecast bbox is not a hard rectangle on the map. */
    internal fun outerEdgeAlphaFactor(
        coord: Int,
        size: Int,
        marginFraction: Double,
    ): Double {
        if (size <= 1 || marginFraction <= 0.0) return 1.0
        val margin = (size * marginFraction).coerceAtLeast(1.0)
        val dist = min(coord.toDouble(), (size - 1 - coord).toDouble())
        return smoothstep(dist / margin)
    }

    internal fun smoothGrid(
        grid: Array<DoubleArray>,
        passes: Int,
    ): Array<DoubleArray> {
        var current = grid
        repeat(passes) {
            current = boxBlur3x3(current)
        }
        return current
    }

    private fun boxBlur3x3(grid: Array<DoubleArray>): Array<DoubleArray> {
        val rows = grid.size
        val cols = grid[0].size
        return Array(rows) { r ->
            DoubleArray(cols) { c ->
                var sum = 0.0
                var count = 0
                for (dr in -1..1) {
                    for (dc in -1..1) {
                        val rr = (r + dr).coerceIn(0, rows - 1)
                        val cc = (c + dc).coerceIn(0, cols - 1)
                        sum += grid[rr][cc]
                        count++
                    }
                }
                sum / count
            }
        }
    }

    private fun smoothstep(t: Double): Double {
        val x = t.coerceIn(0.0, 1.0)
        return x * x * (3.0 - 2.0 * x)
    }

    private fun bilinearSample(
        grid: Array<DoubleArray>,
        rowF: Double,
        colF: Double,
    ): Double {
        val rows = grid.size
        val cols = grid[0].size
        if (rows == 0 || cols == 0) return 0.0
        val r = rowF.coerceIn(0.0, (rows - 1).toDouble())
        val c = colF.coerceIn(0.0, (cols - 1).toDouble())
        val r0 = floor(r).toInt()
        val c0 = floor(c).toInt()
        val r1 = min(r0 + 1, rows - 1)
        val c1 = min(c0 + 1, cols - 1)
        val dr = r - r0
        val dc = c - c0
        val v00 = grid[r0][c0]
        val v01 = grid[r0][c1]
        val v10 = grid[r1][c0]
        val v11 = grid[r1][c1]
        val top = v00 * (1 - dc) + v01 * dc
        val bottom = v10 * (1 - dc) + v11 * dc
        return top * (1 - dr) + bottom * dr
    }

    private fun edgeSoftnessFactor(
        grid: Array<DoubleArray>,
        rowF: Double,
        colF: Double,
        centerRate: Double,
    ): Double {
        if (centerRate < MIN_DISPLAY_MM_PER_H) return 1.0
        val rows = grid.size
        val cols = grid[0].size
        if (rows < 2 || cols < 2) return 1.0
        val r = rowF.coerceIn(0.0, (rows - 1).toDouble())
        val c = colF.coerceIn(0.0, (cols - 1).toDouble())
        val r0 = floor(r).toInt()
        val c0 = floor(c).toInt()
        val r1 = min(r0 + 1, rows - 1)
        val c1 = min(c0 + 1, cols - 1)
        val samples =
            listOf(
                grid[r0][c0],
                grid[r0][c1],
                grid[r1][c0],
                grid[r1][c1],
            )
        val wet = samples.count { it >= MIN_DISPLAY_MM_PER_H }
        return when (wet) {
            4 -> 1.0
            3 -> 0.9
            2 -> 0.7
            1 -> 0.45
            else -> 0.2
        }
    }

    fun colorForRate(
        mmPerH: Double,
        edgeSoftness: Double = 1.0,
        outerFade: Double = 1.0,
    ): Int {
        if (mmPerH < MIN_DISPLAY_MM_PER_H) return Color.TRANSPARENT
        val t = min(1.0, mmPerH / 8.0)
        val (r, g, b) = paletteRgb(t)
        val alpha =
            ((100 + t * 140) * edgeSoftness.coerceIn(0.15, 1.0) * outerFade.coerceIn(0.0, 1.0))
                .toInt()
                .coerceIn(0, 255)
        return Color.argb(alpha, r, g, b)
    }

    private fun paletteRgb(t: Double): Triple<Int, Int, Int> {
        val stops =
            arrayOf(
                0.00 to intArrayOf(80, 200, 120),
                0.25 to intArrayOf(120, 210, 90),
                0.50 to intArrayOf(240, 220, 60),
                0.75 to intArrayOf(255, 140, 40),
                1.00 to intArrayOf(220, 50, 50),
            )
        if (t <= stops.first().first) {
            val c = stops.first().second
            return Triple(c[0], c[1], c[2])
        }
        for (i in 0 until stops.size - 1) {
            val (t0, c0) = stops[i]
            val (t1, c1) = stops[i + 1]
            if (t <= t1) {
                val w = if (t1 <= t0) 1.0 else (t - t0) / (t1 - t0)
                return Triple(
                    lerp(c0[0], c1[0], w),
                    lerp(c0[1], c1[1], w),
                    lerp(c0[2], c1[2], w),
                )
            }
        }
        val c = stops.last().second
        return Triple(c[0], c[1], c[2])
    }

    private fun lerp(
        a: Int,
        b: Int,
        t: Double,
    ): Int = (a + (b - a) * t).toInt().coerceIn(0, 255)
}
