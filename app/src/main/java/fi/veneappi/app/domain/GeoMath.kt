package fi.veneappi.app.domain

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object GeoMath {
    fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(p1) * cos(p2) * sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    fun greatCirclePoints(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double,
        segments: Int = 48,
    ): List<Pair<Double, Double>> {
        if (segments < 1) return listOf(lat1 to lon1, lat2 to lon2)
        val results = ArrayList<Pair<Double, Double>>(segments + 1)
        val lat1r = Math.toRadians(lat1)
        val lon1r = Math.toRadians(lon1)
        val lat2r = Math.toRadians(lat2)
        val lon2r = Math.toRadians(lon2)
        val dLat = lat2r - lat1r
        val dLon = lon2r - lon1r
        val hav =
            sin(dLat / 2) * sin(dLat / 2) +
                cos(lat1r) * cos(lat2r) * sin(dLon / 2) * sin(dLon / 2)
        val d = 2 * atan2(sqrt(hav.coerceIn(0.0, 1.0)), sqrt((1 - hav).coerceIn(0.0, 1.0)))
        if (d < 1e-6) {
            return listOf(lat1 to lon1, lat2 to lon2)
        }
        val sinD = sin(d)
        for (i in 0..segments) {
            val f = i.toDouble() / segments
            val a = sin((1 - f) * d) / sinD
            val b = sin(f * d) / sinD
            val x = a * cos(lat1r) * cos(lon1r) + b * cos(lat2r) * cos(lon2r)
            val y = a * cos(lat1r) * sin(lon1r) + b * cos(lat2r) * sin(lon2r)
            val z = a * sin(lat1r) + b * sin(lat2r)
            val lat = Math.toDegrees(atan2(z, sqrt(x * x + y * y)))
            val lon = Math.toDegrees(atan2(y, x))
            results.add(lat to lon)
        }
        return results
    }

    fun metersToNauticalMiles(m: Double): Double = m / 1852.0

    /** Cumulative great-circle length of a polyline in meters. */
    fun polylineLengthMeters(pts: List<Pair<Double, Double>>): Double {
        if (pts.size < 2) return 0.0
        var sum = 0.0
        for (i in 0 until pts.lastIndex) {
            val a = pts[i]
            val b = pts[i + 1]
            sum += haversineMeters(a.first, a.second, b.first, b.second)
        }
        return sum
    }

    /**
     * Point along [pts] at [fraction] of total path length (0 = first vertex, 1 = last).
     */
    fun pointAlongPolyline(
        pts: List<Pair<Double, Double>>,
        fraction: Double,
    ): Pair<Double, Double> {
        if (pts.isEmpty()) return 0.0 to 0.0
        val f = fraction.coerceIn(0.0, 1.0)
        if (pts.size == 1) return pts[0]
        if (f <= 0.0) return pts.first()
        if (f >= 1.0) return pts.last()
        val total = polylineLengthMeters(pts)
        if (total < 1e-3) return pts.last()
        val target = f * total
        var acc = 0.0
        for (i in 0 until pts.lastIndex) {
            val a = pts[i]
            val b = pts[i + 1]
            val seg = haversineMeters(a.first, a.second, b.first, b.second)
            if (acc + seg >= target) {
                val t = if (seg > 1e-6) ((target - acc) / seg).coerceIn(0.0, 1.0) else 0.0
                val lat = a.first + t * (b.first - a.first)
                val lon = a.second + t * (b.second - a.second)
                return lat to lon
            }
            acc += seg
        }
        return pts.last()
    }
}
