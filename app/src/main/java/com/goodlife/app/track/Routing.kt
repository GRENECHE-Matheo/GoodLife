package com.goodlife.app.track

import com.goodlife.app.i18n.t

import com.goodlife.app.data.OutingType
import com.goodlife.app.data.TrackPoint
import com.goodlife.app.net.USER_AGENT
import com.goodlife.app.net.networkError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.PriorityQueue
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Un itinéraire calculé : les points et sa longueur (m). */
data class PlannedRoute(val points: List<TrackPoint>, val lengthM: Double, val label: String)

/**
 * Calcul d'itinéraires dans l'app, à partir des rues et chemins d'OpenStreetMap (téléchargés via Overpass
 * pour la zone autour de toi, puis gardés en mémoire) : chemin le plus court vers une destination, et
 * boucles d'une distance voulue qui évitent de repasser par les mêmes rues.
 */
object Routing {
    private const val ENDPOINT = "https://overpass-api.de/api/interpreter"

    /** Réseau de chemins : nœuds (lat, lng) et voisins avec la distance. */
    /** [w] = coût (distance pondérée par le type de voie), [d] = vraie distance en mètres. */
    private class Graph(val lat: DoubleArray, val lng: DoubleArray, val adj: Array<IntArray>, val w: Array<DoubleArray>) {
        /** Nœuds du plus grand réseau connecté : on n'y accroche que départs et arrivées (évite les bouts isolés). */
        val main: BooleanArray = run {
            val comp = IntArray(lat.size) { -1 }
            var bestComp = -1; var bestSize = 0; var c = 0
            val stack = ArrayDeque<Int>()
            for (s in lat.indices) {
                if (comp[s] != -1 || adj[s].isEmpty()) continue
                var size = 0
                comp[s] = c; stack.addLast(s)
                while (stack.isNotEmpty()) {
                    val u = stack.removeLast(); size++
                    for (v in adj[u]) if (comp[v] == -1) { comp[v] = c; stack.addLast(v) }
                }
                if (size > bestSize) { bestSize = size; bestComp = c }
                c++
            }
            BooleanArray(lat.size) { comp[it] == bestComp && bestComp != -1 }
        }

        fun nearest(la: Double, lo: Double): Int {
            var best = -1; var bestD = Double.MAX_VALUE
            for (i in lat.indices) {
                if (!main[i]) continue
                val d = (lat[i] - la) * (lat[i] - la) + (lng[i] - lo) * (lng[i] - lo) * 0.45
                if (d < bestD) { bestD = d; best = i }
            }
            return best
        }
    }

    private data class CacheKey(val lat: Int, val lng: Int, val radius: Int, val bike: Boolean)
    private val cache = HashMap<CacheKey, Graph>()

    /**
     * Voies autorisées. Jamais d'autoroute, de voie rapide ni de route nationale (motorway, trunk, primary ne sont
     * pas demandés, et « motorroad=yes » est exclu). À vélo : ni trottoirs ni escaliers.
     */
    private fun highways(bike: Boolean): String =
        if (bike) "cycleway|path|track|residential|living_street|unclassified|tertiary|secondary|service|road|pedestrian"
        else "footway|path|pedestrian|track|steps|residential|living_street|unclassified|tertiary|service|cycleway|secondary|road"

    /**
     * Préférence par type de voie (multiplie la distance) : on privilégie chemins, parcs, rues calmes et pistes
     * cyclables, et on évite les grands axes quand il existe mieux.
     */
    private fun weight(highway: String, bike: Boolean): Double = if (bike) when (highway) {
        "cycleway" -> 0.75
        "residential", "living_street" -> 1.0
        "unclassified", "service", "road" -> 1.15
        "tertiary" -> 1.25
        "track", "path" -> 1.3
        "pedestrian" -> 1.6
        "secondary" -> 1.7
        else -> 1.3
    } else when (highway) {
        "footway", "path", "pedestrian", "track", "living_street" -> 0.85
        "residential" -> 1.0
        "cycleway", "service" -> 1.1
        "unclassified", "road" -> 1.2
        "steps" -> 1.4
        "tertiary" -> 1.5
        "secondary" -> 2.0
        else -> 1.2
    }

    /** Télécharge (ou reprend en mémoire) le réseau de chemins dans un rayon autour d'un point. */
    private suspend fun graph(lat: Double, lng: Double, radiusM: Int, bike: Boolean): Graph = withContext(Dispatchers.IO) {
        val key = CacheKey((lat * 100).roundToInt(), (lng * 100).roundToInt(), radiusM, bike)
        cache[key]?.let { return@withContext it }
        val forbidden = if (bike) "[\"bicycle\"!~\"no|dismount\"]" else "[\"foot\"!~\"no\"]"
        val q = """
            [out:json][timeout:40];
            way["highway"~"^(${highways(bike)})$"]["access"!~"private|no"]["motorroad"!="yes"]$forbidden(around:$radiusM,$lat,$lng)->.w;
            .w out body qt;
            node(w.w);
            out skel qt;
        """.trimIndent()
        var body: String? = null
        var lastError: IOException? = null
        for (attempt in 0 until 3) {
            if (attempt > 0) delay(2500L * attempt)
            val conn = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; connectTimeout = 15_000; readTimeout = 60_000; doOutput = true
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            }
            try {
                conn.outputStream.use { it.write(("data=" + URLEncoder.encode(q, "UTF-8")).toByteArray()) }
                val code = conn.responseCode
                if (code == 429 || code in 502..504) {
                    lastError = IOException(t("Le service de chemins OpenStreetMap est surchargé. Réessaie dans une minute.")); continue
                }
                if (code !in 200..299) throw IOException(t("Service OpenStreetMap indisponible (%1\$s).", code))
                body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                break
            } catch (e: IOException) {
                lastError = if (e.message?.contains("OpenStreetMap") == true) e else networkError("OpenStreetMap", e)
            } finally {
                conn.disconnect()
            }
        }
        val json = JSONObject(body ?: throw (lastError ?: IOException(t("Service OpenStreetMap indisponible."))))
        val elements = json.optJSONArray("elements") ?: throw IOException(t("Pas de chemins trouvés ici."))
        // 1. Nœuds
        val index = HashMap<Long, Int>()
        val la = ArrayList<Double>(); val lo = ArrayList<Double>()
        for (i in 0 until elements.length()) {
            val e = elements.optJSONObject(i) ?: continue
            if (e.optString("type") != "node") continue
            index[e.optLong("id")] = la.size
            la.add(e.optDouble("lat")); lo.add(e.optDouble("lon"))
        }
        // 2. Arêtes (chemins dans les deux sens), pondérées selon le type de voie
        val adj = Array(la.size) { ArrayList<Int>(3) }
        val fac = Array(la.size) { ArrayList<Double>(3) }
        for (i in 0 until elements.length()) {
            val e = elements.optJSONObject(i) ?: continue
            if (e.optString("type") != "way") continue
            val nodes = e.optJSONArray("nodes") ?: continue
            val f = weight(e.optJSONObject("tags")?.optString("highway").orEmpty(), bike)
            for (k in 0 until nodes.length() - 1) {
                val a = index[nodes.optLong(k)] ?: continue
                val b = index[nodes.optLong(k + 1)] ?: continue
                if (a != b) { adj[a].add(b); fac[a].add(f); adj[b].add(a); fac[b].add(f) }
            }
        }
        if (la.isEmpty()) throw IOException(t("Pas de chemins trouvés ici."))
        val latA = la.toDoubleArray(); val lngA = lo.toDoubleArray()
        val g = Graph(
            latA, lngA,
            Array(adj.size) { adj[it].toIntArray() },
            Array(adj.size) { i -> DoubleArray(adj[i].size) { k -> Tracker.haversine(latA[i], lngA[i], latA[adj[i][k]], lngA[adj[i][k]]) * fac[i][k] } }
        )
        if (cache.size > 6) cache.clear()
        cache[key] = g
        g
    }

    /** A* : plus court chemin ; [penalty] multiplie le coût des arêtes déjà utilisées (pour varier les boucles). */
    private fun astar(g: Graph, from: Int, to: Int, penalty: Map<Long, Double> = emptyMap()): List<Int>? {
        if (from < 0 || to < 0) return null
        if (from == to) return listOf(from)
        val n = g.lat.size
        val dist = DoubleArray(n) { Double.MAX_VALUE }
        val prev = IntArray(n) { -1 }
        val open = PriorityQueue<Pair<Double, Int>>(compareBy { it.first })
        dist[from] = 0.0
        open.add(Tracker.haversine(g.lat[from], g.lng[from], g.lat[to], g.lng[to]) * 0.75 to from)
        var visited = 0
        while (open.isNotEmpty()) {
            val (_, u) = open.poll()!!
            if (u == to) break
            if (++visited > 400_000) return null
            for (k in g.adj[u].indices) {
                val v = g.adj[u][k]
                val edge = if (u < v) u.toLong() * n + v else v.toLong() * n + u
                val nd = dist[u] + g.w[u][k] * (penalty[edge] ?: 1.0)
                if (nd < dist[v]) {
                    dist[v] = nd; prev[v] = u
                    open.add((nd + Tracker.haversine(g.lat[v], g.lng[v], g.lat[to], g.lng[to]) * 0.75) to v)
                }
            }
        }
        if (prev[to] == -1) return null
        val path = ArrayList<Int>()
        var c = to
        while (c != -1) { path.add(c); c = prev[c] }
        return path.reversed()
    }

    private fun toPoints(g: Graph, path: List<Int>) = path.map { TrackPoint(g.lat[it], g.lng[it], Double.NaN, 0L) }

    /** Itinéraire le plus court entre deux points (par les chemins et rues). */
    suspend fun toDestination(fromLat: Double, fromLng: Double, toLat: Double, toLng: Double, type: OutingType): PlannedRoute {
        val direct = Tracker.haversine(fromLat, fromLng, toLat, toLng)
        if (direct > 25_000) throw IOException(t("Destination trop loin (plus de 25 km à vol d'oiseau)."))
        val radius = (direct * 0.65 + 800).roundToInt().coerceIn(1000, 16_000)
        val g = graph((fromLat + toLat) / 2, (fromLng + toLng) / 2, radius, type == OutingType.BIKE)
        val path = withContext(Dispatchers.Default) { astar(g, g.nearest(fromLat, fromLng), g.nearest(toLat, toLng)) }
            ?: throw IOException(t("Aucun chemin trouvé jusqu'à ce point."))
        val pts = toPoints(g, path)
        return PlannedRoute(pts, Tracker.length(pts), t("Vers la destination"))
    }

    /**
     * Boucles d'environ [targetM] mètres qui partent et reviennent au point de départ, dans plusieurs directions.
     * Chaque boucle passe par deux points intermédiaires, et les rues déjà prises coûtent plus cher pour éviter les allers-retours.
     */
    suspend fun loops(lat: Double, lng: Double, targetM: Double, type: OutingType, count: Int = 3): List<PlannedRoute> {
        val radius = (targetM / 2.6 + 600).roundToInt().coerceIn(800, 12_000)
        val g = graph(lat, lng, radius, type == OutingType.BIKE)
        return withContext(Dispatchers.Default) {
            val start = g.nearest(lat, lng)
            val result = ArrayList<PlannedRoute>()
            val names = listOf(t("Boucle nord"), t("Boucle nord-est"), t("Boucle est"), t("Boucle sud-est"), t("Boucle sud"), t("Boucle sud-ouest"), t("Boucle ouest"), t("Boucle nord-ouest"))
            val baseAngles = (0 until 8).map { it * 45.0 }.shuffled().take(count + 3)
            for (angle in baseAngles) {
                if (result.size >= count) break
                var scale = 1.0
                repeat(3) { // ajuste la taille pour s'approcher de la distance voulue
                    val r = targetM / 3.4 * scale
                    val a1 = Math.toRadians(angle - 30); val a2 = Math.toRadians(angle + 30)
                    fun offset(a: Double) = Pair(
                        lat + r * cos(a) / 111_320.0,
                        lng + r * sin(a) / (111_320.0 * cos(Math.toRadians(lat)))
                    )
                    val (la1, lo1) = offset(a1); val (la2, lo2) = offset(a2)
                    val w1 = g.nearest(la1, lo1); val w2 = g.nearest(la2, lo2)
                    val penalty = HashMap<Long, Double>()
                    val n = g.lat.size
                    fun leg(from: Int, to: Int): List<Int>? = astar(g, from, to, penalty)?.also { p ->
                        p.zipWithNext().forEach { (u, v) -> penalty[if (u < v) u.toLong() * n + v else v.toLong() * n + u] = 6.0 }
                    }
                    val p1 = leg(start, w1) ?: return@repeat
                    val p2 = leg(w1, w2) ?: return@repeat
                    val p3 = leg(w2, start) ?: return@repeat
                    val path = p1 + p2.drop(1) + p3.drop(1)
                    val pts = toPoints(g, path)
                    val len = Tracker.length(pts)
                    val ratio = len / targetM
                    if (ratio in 0.8..1.25) {
                        val label = names[((angle / 45.0).roundToInt()) % 8]
                        if (result.none { kotlin.math.abs(it.lengthM - len) < 50 && it.label == label }) result.add(PlannedRoute(pts, len, label))
                        return@repeat
                    }
                    scale /= ratio.coerceIn(0.5, 2.0)
                }
            }
            if (result.isEmpty()) throw IOException(t("Pas assez de chemins ici pour une boucle de cette distance. Essaie une autre distance."))
            result.sortedBy { kotlin.math.abs(it.lengthM - targetM) }
        }
    }

    /** Position sur l'itinéraire : distance restante et écart au tracé (m). */
    fun progress(route: List<TrackPoint>, lat: Double, lng: Double): Pair<Double, Double> {
        if (route.size < 2) return 0.0 to 0.0
        var bestI = 0; var bestD = Double.MAX_VALUE
        for (i in route.indices) {
            val d = Tracker.haversine(route[i].lat, route[i].lng, lat, lng)
            if (d < bestD) { bestD = d; bestI = i }
        }
        return Tracker.length(route.subList(bestI, route.size)) to bestD
    }
}
