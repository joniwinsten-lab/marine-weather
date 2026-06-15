package fi.veneappi.app.domain

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.ln
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

    /**
     * Web Mercator zoom (512 px world width at z=0) so that [centimeterInPixels] on screen
     * spans [nmPerCentimeter] nautical miles at [latitude].
     */
    fun zoomForNmPerCentimeter(
        latitude: Double,
        nmPerCentimeter: Double,
        centimeterInPixels: Float,
    ): Double {
        if (centimeterInPixels <= 0f || nmPerCentimeter <= 0.0 || !nmPerCentimeter.isFinite()) {
            return 12.5
        }
        val metersPerCm = nmPerCentimeter * 1852.0
        val metersPerPixel = metersPerCm / centimeterInPixels
        if (metersPerPixel <= 0.0 || !metersPerPixel.isFinite()) return 12.5
        val latRad = Math.toRadians(latitude.coerceIn(-85.0, 85.0))
        val worldCircumferenceM = 40_075_016.686
        val worldPxAtZoom0 = 512.0
        val scale =
            cos(latRad) * worldCircumferenceM / (worldPxAtZoom0 * metersPerPixel)
        if (scale <= 0.0 || !scale.isFinite()) return 12.5
        return (ln(scale) / ln(2.0)).coerceIn(3.0, 18.0)
    }

    /** Great-circle destination from start, bearing (° true), distance (nm). */
    fun destinationPoint(
        lat: Double,
        lon: Double,
        bearingDeg: Double,
        distanceNm: Double,
    ): Pair<Double, Double> {
        if (distanceNm <= 0 || !distanceNm.isFinite() || !bearingDeg.isFinite()) {
            return lat to lon
        }
        val earthRadiusM = 6_371_000.0
        val distanceM = distanceNm * 1852.0
        val bearing = Math.toRadians(bearingDeg)
        val lat1 = Math.toRadians(lat)
        val lon1 = Math.toRadians(lon)
        val lat2 =
            kotlin.math.asin(
                kotlin.math.sin(lat1) * kotlin.math.cos(distanceM / earthRadiusM) +
                    kotlin.math.cos(lat1) * kotlin.math.sin(distanceM / earthRadiusM) * kotlin.math.cos(bearing),
            )
        val lon2 =
            lon1 +
                kotlin.math.atan2(
                    kotlin.math.sin(bearing) * kotlin.math.sin(distanceM / earthRadiusM) * kotlin.math.cos(lat1),
                    kotlin.math.cos(distanceM / earthRadiusM) - kotlin.math.sin(lat1) * kotlin.math.sin(lat2),
                )
        return Math.toDegrees(lat2) to Math.toDegrees(lon2)
    }

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

    data class LatLonBounds(
        val minLat: Double,
        val minLon: Double,
        val maxLat: Double,
        val maxLon: Double,
    )

    /** Axis-aligned bounds around polyline vertices with padding in degrees. */
    fun boundsAroundPolyline(
        pts: List<Pair<Double, Double>>,
        paddingDeg: Double = 0.12,
    ): LatLonBounds? {
        if (pts.isEmpty()) return null
        var minLat = pts[0].first
        var maxLat = minLat
        var minLon = pts[0].second
        var maxLon = minLon
        for ((lat, lon) in pts.drop(1)) {
            minLat = minOf(minLat, lat)
            maxLat = maxOf(maxLat, lat)
            minLon = minOf(minLon, lon)
            maxLon = maxOf(maxLon, lon)
        }
        return LatLonBounds(
            minLat = minLat - paddingDeg,
            minLon = minLon - paddingDeg,
            maxLat = maxLat + paddingDeg,
            maxLon = maxLon + paddingDeg,
        )
    }

    /**
     * Sample points along route for offline prefetch — at most [maxPoints], roughly every [spacingNm].
     */
    fun samplePointsAlongRoute(
        pts: List<Pair<Double, Double>>,
        maxPoints: Int = 24,
        spacingNm: Double = 25.0,
    ): List<Pair<Double, Double>> {
        if (pts.isEmpty()) return emptyList()
        if (pts.size == 1) return pts
        val totalM = polylineLengthMeters(pts)
        val totalNm = metersToNauticalMiles(totalM)
        if (totalNm < 1e-3) return listOf(pts.first(), pts.last()).distinct()
        val spacingM = spacingNm * 1852.0
        val countBySpacing = (totalM / spacingM).toInt().coerceAtLeast(1) + 1
        val count = minOf(maxPoints, countBySpacing).coerceAtLeast(2)
        return (0 until count).map { i ->
            val frac = i.toDouble() / (count - 1).coerceAtLeast(1)
            pointAlongPolyline(pts, frac)
        }
    }
}
