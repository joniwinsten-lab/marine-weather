package fi.veneappi.app.data.routing

import fi.veneappi.app.BuildConfig
import fi.veneappi.app.domain.GeoMath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.PriorityQueue
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Plans a route along Finnish waterway **navigation lines** from Väylävirasto open OGC API
 * (`navigointilinjat_uusi`). Falls back to null when the graph has no path (caller uses great circle).
 */
object VaylaFairwayRouter {
    private const val START = "__START__"
    private const val END = "__END__"
    private const val MAX_PAGES = 18
    private const val PAGE_LIMIT = 400
    private const val MAX_SEGMENTS = 12_000
    private const val SNAP_RADIUS_M = 3500.0
    private const val SNAP_NEAREST = 14
    private const val ENDPOINT_BRIDGE_MIN_M = 1.0
    private const val ENDPOINT_BRIDGE_MAX_M = 150.0

    private val client =
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .build()

    private val jsonParser =
        Json {
            ignoreUnknownKeys = true
        }

    private val itemsBase: HttpUrl =
        "https://avoinapi.vaylapilvi.fi/vaylatiedot/ogc/features/v1/collections/vesivaylatiedot%3Anavigointilinjat_uusi/items"
            .toHttpUrl()

    suspend fun routeAlongNavLines(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double,
    ): List<Pair<Double, Double>>? =
        withContext(Dispatchers.IO) {
            val bbox = paddedBbox(lat1, lon1, lat2, lon2)
            val segments = fetchSegments(bbox) ?: return@withContext null
            if (segments.isEmpty()) return@withContext null
            val graph = buildGraph(segments)
            if (graph.isEmpty()) return@withContext null
            val pathKeys =
                shortestPathKeys(
                    adj = graph,
                    startLat = lat1,
                    startLon = lon1,
                    endLat = lat2,
                    endLon = lon2,
                ) ?: return@withContext null
            keysToPolyline(pathKeys, lat1, lon1, lat2, lon2)
        }

    private fun paddedBbox(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double,
    ): String {
        val minLat = min(lat1, lat2)
        val maxLat = max(lat1, lat2)
        val minLon = min(lon1, lon2)
        val maxLon = max(lon1, lon2)
        val spanLat = max(abs(maxLat - minLat), 0.02)
        val spanLon = max(abs(maxLon - minLon), 0.03)
        val padLat = (spanLat * 0.55).coerceIn(0.04, 0.35)
        val padLon = (spanLon * 0.55).coerceIn(0.06, 0.55)
        val bMinLon = (minLon - padLon).coerceIn(-180.0, 180.0)
        val bMinLat = (minLat - padLat).coerceIn(-85.0, 85.0)
        val bMaxLon = (maxLon + padLon).coerceIn(-180.0, 180.0)
        val bMaxLat = (maxLat + padLat).coerceIn(-85.0, 85.0)
        return "$bMinLon,$bMinLat,$bMaxLon,$bMaxLat"
    }

    private fun fetchSegments(bbox: String): List<List<Pair<Double, Double>>>? {
        val segments = ArrayList<List<Pair<Double, Double>>>(512)
        var url: HttpUrl? =
            itemsBase
                .newBuilder()
                .addQueryParameter("bbox", bbox)
                .addQueryParameter("f", "application/geo+json")
                .addQueryParameter("limit", PAGE_LIMIT.toString())
                .build()
        var pages = 0
        while (url != null && pages < MAX_PAGES && segments.size < MAX_SEGMENTS) {
            val request =
                Request.Builder()
                    .url(url)
                    .header("User-Agent", BuildConfig.WEATHER_USER_AGENT)
                    .header("Accept", "application/geo+json, application/json")
                    .build()
            val body =
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return null
                    response.body?.string().orEmpty()
                }
            val root = runCatching { jsonParser.parseToJsonElement(body).jsonObject }.getOrNull()
                ?: return null
            val features = root["features"]?.jsonArray ?: break
            for (featureEl in features) {
                val feature = featureEl.jsonObject
                val geom = feature["geometry"]?.jsonObject ?: continue
                when (geom["type"]?.jsonPrimitive?.content) {
                    "LineString" -> {
                        val coords = geom["coordinates"] ?: continue
                        val line = lineStringToLatLon(coords) ?: continue
                        if (line.size >= 2) segments.add(line)
                    }
                    "MultiLineString" -> {
                        val coordsRoot = geom["coordinates"]?.jsonArray ?: continue
                        for (part in coordsRoot) {
                            val line = lineStringToLatLon(part) ?: continue
                            if (line.size >= 2) segments.add(line)
                        }
                    }
                }
            }
            pages++
            url = nextPageUrl(root["links"]?.jsonArray)
        }
        return segments
    }

    private fun lineStringToLatLon(coordsElement: JsonElement): List<Pair<Double, Double>>? {
        val arr = coordsElement.jsonArray
        val out = ArrayList<Pair<Double, Double>>(arr.size)
        for (ptEl in arr) {
            val pt = ptEl.jsonArray
            if (pt.size < 2) continue
            val lon = pt[0].jsonDouble() ?: continue
            val lat = pt[1].jsonDouble() ?: continue
            out.add(lat to lon)
        }
        return out.takeIf { it.size >= 2 }
    }

    private fun JsonElement.jsonDouble(): Double? =
        when (this) {
            is JsonPrimitive ->
                doubleOrNull
                    ?: contentOrNull?.toDoubleOrNull()
            else -> null
        }

    private fun nextPageUrl(links: JsonArray?): HttpUrl? {
        if (links == null) return null
        for (linkEl in links) {
            val link = linkEl.jsonObject
            if (link["rel"]?.jsonPrimitive?.content != "next") continue
            val href = link["href"]?.jsonPrimitive?.contentOrNull ?: continue
            return runCatching { href.toHttpUrl() }.getOrNull()
        }
        return null
    }

    private fun nodeKey(lat: Double, lon: Double): String =
        "${formatCoord(lat)},${formatCoord(lon)}"

    private fun formatCoord(v: Double): String = String.format(java.util.Locale.US, "%.5f", v)

    private fun parseKey(key: String): Pair<Double, Double> {
        val idx = key.indexOf(',')
        val lat = key.substring(0, idx).toDouble()
        val lon = key.substring(idx + 1).toDouble()
        return lat to lon
    }

    private fun buildGraph(segments: List<List<Pair<Double, Double>>>): MutableMap<String, MutableList<Pair<String, Double>>> {
        val adj = mutableMapOf<String, MutableList<Pair<String, Double>>>()
        fun addUndirected(a: String, b: String, w: Double) {
            if (a == b || w <= 0) return
            adj.getOrPut(a) { mutableListOf() }.add(b to w)
            adj.getOrPut(b) { mutableListOf() }.add(a to w)
        }
        for (seg in segments) {
            for (i in 0 until seg.lastIndex) {
                val (la1, lo1) = seg[i]
                val (la2, lo2) = seg[i + 1]
                val a = nodeKey(la1, lo1)
                val b = nodeKey(la2, lo2)
                val w = GeoMath.haversineMeters(la1, lo1, la2, lo2)
                addUndirected(a, b, w)
            }
        }
        val endpoints = LinkedHashSet<String>()
        for (seg in segments) {
            if (seg.size < 2) continue
            endpoints.add(nodeKey(seg.first().first, seg.first().second))
            endpoints.add(nodeKey(seg.last().first, seg.last().second))
        }
        val epList = endpoints.toList()
        if (epList.size <= 2200) {
            for (i in epList.indices) {
                val ai = epList[i]
                val (la1, lo1) = parseKey(ai)
                for (j in i + 1 until epList.size) {
                    val bj = epList[j]
                    val (la2, lo2) = parseKey(bj)
                    val d = GeoMath.haversineMeters(la1, lo1, la2, lo2)
                    if (d in ENDPOINT_BRIDGE_MIN_M..ENDPOINT_BRIDGE_MAX_M) {
                        addUndirected(ai, bj, d)
                    }
                }
            }
        }
        return adj
    }

    private fun shortestPathKeys(
        adj: MutableMap<String, MutableList<Pair<String, Double>>>,
        startLat: Double,
        startLon: Double,
        endLat: Double,
        endLon: Double,
    ): List<String>? {
        val graphNodes = adj.keys.toList()
        if (graphNodes.isEmpty()) return null
        val snapStart = nearestNodes(startLat, startLon, graphNodes, SNAP_NEAREST, SNAP_RADIUS_M)
        val snapEnd = nearestNodes(endLat, endLon, graphNodes, SNAP_NEAREST, SNAP_RADIUS_M)
        if (snapStart.isEmpty() || snapEnd.isEmpty()) return null
        val work = HashMap<String, MutableList<Pair<String, Double>>>(adj.size + 4)
        for ((k, v) in adj) {
            work[k] = ArrayList(v)
        }
        fun ensureEdge(from: String, to: String, w: Double) {
            work.getOrPut(from) { mutableListOf() }.add(to to w)
        }
        for ((n, d) in snapStart) {
            ensureEdge(START, n, d)
            ensureEdge(n, START, d)
        }
        for ((n, d) in snapEnd) {
            ensureEdge(END, n, d)
            ensureEdge(n, END, d)
        }
        val dist = mutableMapOf<String, Double>()
        val prev = mutableMapOf<String, String?>()
        val pq = PriorityQueue<Pair<String, Double>>(compareBy { it.second })
        dist[START] = 0.0
        pq.add(START to 0.0)
        while (pq.isNotEmpty()) {
            val (u, du) = pq.poll() ?: break
            if (du > dist.getOrDefault(u, Double.MAX_VALUE)) continue
            if (u == END) break
            for ((v, w) in work[u].orEmpty()) {
                val nd = du + w
                if (nd < dist.getOrDefault(v, Double.MAX_VALUE)) {
                    dist[v] = nd
                    prev[v] = u
                    pq.add(v to nd)
                }
            }
        }
        if (!dist.containsKey(END)) return null
        val path = ArrayList<String>()
        var cur: String? = END
        while (cur != null) {
            path.add(cur)
            if (cur == START) break
            cur = prev[cur]
        }
        if (path.lastOrNull() != START) return null
        path.reverse()
        return path
    }

    private fun nearestNodes(
        lat: Double,
        lon: Double,
        nodes: List<String>,
        k: Int,
        maxM: Double,
    ): List<Pair<String, Double>> =
        nodes
            .map { key ->
                val (la, lo) = parseKey(key)
                key to GeoMath.haversineMeters(lat, lon, la, lo)
            }
            .filter { it.second <= maxM }
            .sortedBy { it.second }
            .take(k)

    private fun keysToPolyline(
        keys: List<String>,
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double,
    ): List<Pair<Double, Double>> {
        val inner = keys.drop(1).dropLast(1)
        val out = ArrayList<Pair<Double, Double>>(inner.size + 2)
        out.add(lat1 to lon1)
        for (k in inner) {
            out.add(parseKey(k))
        }
        out.add(lat2 to lon2)
        return out
    }
}
