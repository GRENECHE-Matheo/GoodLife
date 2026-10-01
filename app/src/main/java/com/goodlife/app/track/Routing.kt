package com.goodlife.app.track

import com.goodlife.app.i18n.t

import com.goodlife.app.data.OutingType
import com.goodlife.app.data.TrackPoint
import com.goodlife.app.net.USER_AGENT
import com.goodlife.app.net.networkError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Un itinéraire calculé : les points et sa longueur (m). */
data class PlannedRoute(val points: List<TrackPoint>, val lengthM: Double, val label: String)

/**
 * Calcul d'itinéraires dans l'app, à partir des rues et chemins d'OpenStreetMap (lus dans les tuiles de carte
 * OpenFreeMap, ou demandés au serveur Overpass en secours) : chemin le plus court vers une destination, et
 * boucles d'une distance voulue qui évitent de repasser par les mêmes rues.
 *
 * Algorithme : A* (Dijkstra guidé vers l'arrivée par la distance à vol d'oiseau), qui donne exactement le même
 * chemin que Dijkstra en explorant beaucoup moins de rues.
 * Pour aller vite :
 * - les rues viennent des tuiles de la carte (CDN rapide, gardées en cache sur le téléphone) ;
 * - en secours (Overpass), la zone est demandée en rectangle et seuls les points et le type de voie sont téléchargés ;
 * - la réponse est lue au fil de l'eau, sans tout charger en mémoire ;
 * - le réseau est rangé dans des tableaux compacts ;
 * - les boucles sont cherchées en parallèle.
 */
object Routing {
    private const val ENDPOINT = "https://overpass-api.de/api/interpreter"

    /**
     * Réseau de chemins rangé de façon compacte (format « CSR ») : les voisins du nœud i sont to[off[i] until off[i+1]],
     * avec [w] = coût (distance pondérée par le type de voie). [x]/[y] = position en mètres (projection locale), pour
     * l'estimation rapide de la distance restante.
     */
    private class Graph(
        val lat: DoubleArray, val lng: DoubleArray, val x: DoubleArray, val y: DoubleArray,
        val off: IntArray, val to: IntArray, val w: DoubleArray,
        val south: Double, val west: Double, val north: Double, val east: Double, val bike: Boolean
    ) {
        val n get() = lat.size

        /** Nœuds du plus grand réseau connecté : on n'y accroche que départs et arrivées (évite les bouts isolés). */
        val main: BooleanArray = run {
            val comp = IntArray(n) { -1 }
            var bestComp = -1; var bestSize = 0; var c = 0
            val stack = IntArray(n.coerceAtLeast(1))
            for (s in 0 until n) {
                if (comp[s] != -1 || off[s] == off[s + 1]) continue
                var size = 0; var top = 0
                comp[s] = c; stack[top++] = s
                while (top > 0) {
                    val u = stack[--top]; size++
                    for (k in off[u] until off[u + 1]) { val v = to[k]; if (comp[v] == -1) { comp[v] = c; stack[top++] = v } }
                }
                if (size > bestSize) { bestSize = size; bestComp = c }
                c++
            }
            BooleanArray(n) { comp[it] == bestComp && bestComp != -1 }
        }

        fun contains(s: Double, w: Double, nn: Double, e: Double) = s >= south && w >= west && nn <= north && e <= east

        fun nearest(la: Double, lo: Double): Int {
            var best = -1; var bestD = Double.MAX_VALUE
            for (i in 0 until n) {
                if (!main[i]) continue
                val d = (lat[i] - la) * (lat[i] - la) + (lng[i] - lo) * (lng[i] - lo) * 0.45
                if (d < bestD) { bestD = d; best = i }
            }
            return best
        }
    }

    /** Derniers réseaux téléchargés : une nouvelle demande dans une zone déjà couverte ne retélécharge rien. */
    private val cache = ArrayList<Graph>()

    /**
     * Voies autorisées. Jamais d'autoroute, de voie rapide ni de route nationale (motorway, trunk, primary ne sont
     * pas demandés, et « motorroad=yes » est exclu). À vélo : ni trottoirs ni escaliers.
     */
    private fun highways(bike: Boolean): List<String> =
        (if (bike) "cycleway|path|track|residential|living_street|unclassified|tertiary|secondary|service|road|pedestrian"
        else "footway|path|pedestrian|track|steps|residential|living_street|unclassified|tertiary|service|cycleway|secondary|road").split("|")

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

    /** Plus petit coefficient possible : l'estimation de A* ne doit jamais dépasser le vrai coût. */
    private fun minWeight(bike: Boolean) = if (bike) 0.75 else 0.85

    /**
     * Requête Overpass : un groupe de voies par coefficient, précédé d'un petit marqueur « w » qui porte ce coefficient.
     * On reçoit d'abord tous les points (coordonnées seules), puis les voies (liste de points seulement, sans leurs
     * autres étiquettes) : 5 à 10 fois moins de données à préparer pour le serveur.
     */
    private fun query(s: Double, w: Double, n: Double, e: Double, bike: Boolean): String {
        val forbidden = if (bike) "[\"bicycle\"!~\"no|dismount\"]" else "[\"foot\"!~\"no\"]"
        val groups = highways(bike).groupBy { weight(it, bike) }.entries.toList()
        val bbox = String.format(Locale.US, "%.5f,%.5f,%.5f,%.5f", s, w, n, e)
        val sb = StringBuilder("[out:json][timeout:40][bbox:$bbox];\n")
        groups.forEachIndexed { i, (_, list) ->
            sb.append("way[\"highway\"~\"^(${list.joinToString("|")})$\"][\"access\"!~\"private|no\"][\"motorroad\"!=\"yes\"]$forbidden->.c$i;\n")
        }
        sb.append("(").append(groups.indices.joinToString("") { ".c$it;" }).append(")->.all;\nnode(w.all);\nout skel qt;\n")
        groups.forEachIndexed { i, (f, _) ->
            sb.append(String.format(Locale.US, "make w f=\"%.2f\";\nout;\n.c%d out skel qt;\n", f, i))
        }
        return sb.toString()
    }

    /**
     * Réseau de chemins dans un carré de côté 2 × [radiusM] autour d'un point (déjà en mémoire, sinon tuiles
     * OpenFreeMap, et Overpass en secours). [corridor] = trajet départ–arrivée : seules les tuiles le long du trajet.
     */
    private suspend fun graph(lat: Double, lng: Double, radiusM: Int, bike: Boolean, corridor: DoubleArray? = null, warmOnly: Boolean = false): Graph =
        // Une seule préparation à la fois : une demande arrivée pendant la préparation anticipée la retrouve en mémoire
        lock.withLock { graphLocked(lat, lng, radiusM, bike, corridor, warmOnly) }

    private val lock = Mutex()

    private suspend fun graphLocked(lat: Double, lng: Double, radiusM: Int, bike: Boolean, corridor: DoubleArray?, warmOnly: Boolean): Graph {
        // Un carré un peu plus petit que le cercle d'avant couvre la même zone utile, avec à peine plus de données
        val half = radiusM * 0.9
        val dLat = half / 111_320.0
        val dLng = half / (111_320.0 * cos(Math.toRadians(lat)))
        val s = lat - dLat; val n = lat + dLat; val w = lng - dLng; val e = lng + dLng
        synchronized(cache) { cache.firstOrNull { it.bike == bike && it.contains(s, w, n, e) } }?.let { return it }

        val tiles = if (corridor != null) RoadTiles.tilesForCorridor(corridor[0], corridor[1], corridor[2], corridor[3], half * 0.6)
                    else RoadTiles.tilesForBox(s, w, n, e)
        val fromTiles = if (tiles.size <= RoadTiles.MAX_TILES) {
            runCatching { RoadTiles.network(tiles, bike) }.getOrNull()?.let { raw ->
                withContext(Dispatchers.Default) {
                    // Un trajet ne couvre pas tout le carré : ce réseau ne resservira pas pour une autre demande
                    if (corridor != null) toGraph(raw, lat, lng, Double.NaN, Double.NaN, Double.NaN, Double.NaN, bike)
                    else toGraph(raw, lat, lng, s, w, n, e, bike)
                }
            }?.takeIf { g -> g.main.count { it } > 50 }
        } else null
        if (fromTiles == null && warmOnly) throw IOException("warm")
        val g = fromTiles ?: overpass(lat, lng, s, w, n, e, bike)
        synchronized(cache) {
            if (cache.size >= 4) cache.removeAt(0)
            cache.add(g)
        }
        return g
    }

    /** Secours : le même réseau demandé au serveur public Overpass d'OpenStreetMap. */
    private suspend fun overpass(lat: Double, lng: Double, s: Double, w: Double, n: Double, e: Double, bike: Boolean): Graph = withContext(Dispatchers.IO) {
        val q = query(s, w, n, e, bike)
        var g: Graph? = null
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
                g = conn.inputStream.use { parse(it) }.let { toGraph(it, lat, lng, s, w, n, e, bike) }
                break
            } catch (ex: IOException) {
                lastError = if (ex.message?.contains("OpenStreetMap") == true || ex.message == t("Pas de chemins trouvés ici.")) ex
                            else networkError("OpenStreetMap", ex)
                if (ex.message == t("Pas de chemins trouvés ici.")) break
            } finally {
                conn.disconnect()
            }
        }
        g ?: throw (lastError ?: IOException(t("Service OpenStreetMap indisponible.")))
    }

    /** Petite liste d'entiers sans objets (pour les arêtes). */
    private class Ints(cap: Int = 1024) {
        var a = IntArray(cap); var size = 0
        fun add(v: Int) { if (size == a.size) a = a.copyOf(size * 2); a[size++] = v }
    }

    /** Lecture de la réponse au fil de l'eau (JsonReader) : rien n'est gardé à part les points et les arêtes. */
    private fun parse(input: InputStream): RawNetwork {
        val index = HashMap<Long, Int>(1 shl 16)
        var la = DoubleArray(1 shl 14); var lo = DoubleArray(1 shl 14); var count = 0
        val from = Ints(1 shl 15); val dest = Ints(1 shl 15)
        var fac = DoubleArray(1 shl 15)
        var factor = 1.2
        var remark: String? = null
        val way = ArrayList<Long>(64)

        android.util.JsonReader(InputStreamReader(input.buffered(64 * 1024), Charsets.UTF_8)).use { r ->
            r.beginObject()
            while (r.hasNext()) {
                when (r.nextName()) {
                    "remark" -> remark = r.nextString()
                    "elements" -> {
                        r.beginArray()
                        while (r.hasNext()) {
                            var type = ""; var id = 0L; var plat = Double.NaN; var plon = Double.NaN; var f: Double? = null
                            way.clear()
                            r.beginObject()
                            while (r.hasNext()) {
                                when (r.nextName()) {
                                    "type" -> type = r.nextString()
                                    "id" -> id = r.nextLong()
                                    "lat" -> plat = r.nextDouble()
                                    "lon" -> plon = r.nextDouble()
                                    "nodes" -> { r.beginArray(); while (r.hasNext()) way.add(r.nextLong()); r.endArray() }
                                    "tags" -> {
                                        r.beginObject()
                                        while (r.hasNext()) { if (r.nextName() == "f") f = r.nextString().toDoubleOrNull() else r.skipValue() }
                                        r.endObject()
                                    }
                                    else -> r.skipValue()
                                }
                            }
                            r.endObject()
                            when (type) {
                                "node" -> if (!plat.isNaN() && !plon.isNaN()) {
                                    if (count == la.size) { la = la.copyOf(count * 2); lo = lo.copyOf(count * 2) }
                                    index[id] = count; la[count] = plat; lo[count] = plon; count++
                                }
                                "w" -> factor = f ?: factor
                                "way" -> for (k in 0 until way.size - 1) {
                                    val a = index[way[k]] ?: continue
                                    val b = index[way[k + 1]] ?: continue
                                    if (a == b) continue
                                    if (from.size + 1 > fac.size) fac = fac.copyOf(fac.size * 2)
                                    fac[from.size] = factor; from.add(a); dest.add(b)
                                }
                            }
                        }
                        r.endArray()
                    }
                    else -> r.skipValue()
                }
            }
            r.endObject()
        }
        // Le serveur signale parfois une requête interrompue (trop chargé) avec une réponse 200 et un « remark »
        if (count == 0 || from.size == 0) {
            if (remark?.contains("error", ignoreCase = true) == true)
                throw IOException(t("Le service de chemins OpenStreetMap est surchargé. Réessaie dans une minute."))
            throw IOException(t("Pas de chemins trouvés ici."))
        }

        return RawNetwork(la.copyOf(count), lo.copyOf(count), from.a, dest.a, fac, from.size)
    }

    /** Rangement CSR (les deux sens de chaque arête) ; coût = vraie distance × coefficient de la voie. */
    private fun toGraph(raw: RawNetwork, lat0: Double, lng0: Double, s: Double, w: Double, n: Double, e: Double, bike: Boolean): Graph {
        val count = raw.lat.size
        val lat = raw.lat; val lng = raw.lng
        val kx = 111_320.0 * cos(Math.toRadians(lat0))
        val x = DoubleArray(count) { (lng[it] - lng0) * kx }
        val y = DoubleArray(count) { (lat[it] - lat0) * 111_320.0 }
        val off = IntArray(count + 1)
        for (i in 0 until raw.edges) { off[raw.a[i] + 1]++; off[raw.b[i] + 1]++ }
        for (i in 0 until count) off[i + 1] += off[i]
        val pos = off.copyOf(count)
        val to = IntArray(raw.edges * 2); val wt = DoubleArray(raw.edges * 2)
        for (i in 0 until raw.edges) {
            val a = raw.a[i]; val b = raw.b[i]
            val c = Tracker.haversine(lat[a], lng[a], lat[b], lng[b]) * raw.f[i]
            var k = pos[a]++; to[k] = b; wt[k] = c
            k = pos[b]++; to[k] = a; wt[k] = c
        }
        return Graph(lat, lng, x, y, off, to, wt, s, w, n, e, bike)
    }

    /**
     * Recherche A* réutilisable (tableaux alloués une fois par fil de calcul). Les rues déjà prises par la même boucle
     * ([used]) coûtent 6 fois plus cher, pour éviter les allers-retours.
     */
    private class Search(val g: Graph) {
        private val dist = DoubleArray(g.n) { Double.MAX_VALUE }
        private val prev = IntArray(g.n) { -1 }
        private val closed = BooleanArray(g.n)
        private val touched = Ints(4096)
        private var hk = DoubleArray(4096); private var hv = IntArray(4096); private var hs = 0
        private val hFactor = minWeight(g.bike) * 0.99
        val used = BooleanArray(g.n)

        private fun push(k: Double, v: Int) {
            if (hs == hk.size) { hk = hk.copyOf(hs * 2); hv = hv.copyOf(hs * 2) }
            var i = hs++
            while (i > 0) {
                val p = (i - 1) ushr 1
                if (hk[p] <= k) break
                hk[i] = hk[p]; hv[i] = hv[p]; i = p
            }
            hk[i] = k; hv[i] = v
        }

        private fun pop(): Int {
            val top = hv[0]
            val k = hk[--hs]; val v = hv[hs]
            var i = 0
            while (true) {
                var c = 2 * i + 1
                if (c >= hs) break
                if (c + 1 < hs && hk[c + 1] < hk[c]) c++
                if (hk[c] >= k) break
                hk[i] = hk[c]; hv[i] = hv[c]; i = c
            }
            if (hs > 0) { hk[i] = k; hv[i] = v }
            return top
        }

        private fun reset() {
            for (i in 0 until touched.size) { val v = touched.a[i]; dist[v] = Double.MAX_VALUE; prev[v] = -1; closed[v] = false }
            touched.size = 0; hs = 0
        }

        fun path(from: Int, target: Int, penalize: Boolean): List<Int>? {
            if (from < 0 || target < 0) return null
            if (from == target) return listOf(from)
            reset()
            val tx = g.x[target]; val ty = g.y[target]
            fun h(v: Int): Double { val dx = g.x[v] - tx; val dy = g.y[v] - ty; return sqrt(dx * dx + dy * dy) * hFactor }
            dist[from] = 0.0; touched.add(from)
            push(h(from), from)
            var found = false
            while (hs > 0) {
                val u = pop()
                if (closed[u]) continue
                if (u == target) { found = true; break }
                closed[u] = true
                val du = dist[u]
                for (k in g.off[u] until g.off[u + 1]) {
                    val v = g.to[k]
                    if (closed[v]) continue
                    val nd = du + g.w[k] * (if (penalize && used[u] && used[v]) 6.0 else 1.0)
                    if (nd < dist[v]) {
                        if (dist[v] == Double.MAX_VALUE) touched.add(v)
                        dist[v] = nd; prev[v] = u
                        push(nd + h(v), v)
                    }
                }
            }
            if (!found) return null
            val path = ArrayList<Int>()
            var c = target
            while (c != -1) { path.add(c); c = prev[c] }
            path.reverse()
            return path
        }
    }

    private fun toPoints(g: Graph, path: List<Int>) = path.map { TrackPoint(g.lat[it], g.lng[it], Double.NaN, 0L) }

    /** Itinéraire le plus court entre deux points (par les chemins et rues). */
    suspend fun toDestination(fromLat: Double, fromLng: Double, toLat: Double, toLng: Double, type: OutingType): PlannedRoute {
        val direct = Tracker.haversine(fromLat, fromLng, toLat, toLng)
        if (direct > 25_000) throw IOException(t("Destination trop loin (plus de 25 km à vol d'oiseau)."))
        val radius = (direct * 0.65 + 800).roundToInt().coerceIn(1000, 16_000)
        val g = graph((fromLat + toLat) / 2, (fromLng + toLng) / 2, radius, type == OutingType.BIKE, doubleArrayOf(fromLat, fromLng, toLat, toLng))
        val path = withContext(Dispatchers.Default) { Search(g).path(g.nearest(fromLat, fromLng), g.nearest(toLat, toLng), penalize = false) }
            ?: throw IOException(t("Aucun chemin trouvé jusqu'à ce point."))
        val pts = toPoints(g, path)
        return PlannedRoute(pts, Tracker.length(pts), t("Vers la destination"))
    }

    /**
     * Boucles d'environ [targetM] mètres qui partent et reviennent au point de départ, dans plusieurs directions.
     * Chaque boucle passe par deux points intermédiaires, et les rues déjà prises coûtent plus cher pour éviter les
     * allers-retours. Les directions sont essayées en même temps, sur tous les cœurs du téléphone.
     */
    private fun loopRadius(targetM: Double) = (targetM / 2.6 + 600).roundToInt().coerceIn(800, 12_000)

    /**
     * Préparation anticipée, dès que le panneau des boucles est ouvert : les rues de la zone sont téléchargées et
     * rangées pendant qu'on choisit la distance, pour que « Proposer » réponde tout de suite. Seulement depuis les
     * tuiles de la carte (jamais le serveur Overpass), et sans rien afficher en cas d'échec.
     */
    suspend fun warmUp(lat: Double, lng: Double, targetM: Double, type: OutingType) {
        runCatching { graph(lat, lng, loopRadius(targetM), type == OutingType.BIKE, warmOnly = true) }
    }

    suspend fun loops(lat: Double, lng: Double, targetM: Double, type: OutingType, count: Int = 3): List<PlannedRoute> {
        val g = graph(lat, lng, loopRadius(targetM), type == OutingType.BIKE)
        return withContext(Dispatchers.Default) {
            val start = g.nearest(lat, lng)
            val names = listOf(t("Boucle nord"), t("Boucle nord-est"), t("Boucle est"), t("Boucle sud-est"), t("Boucle sud"), t("Boucle sud-ouest"), t("Boucle ouest"), t("Boucle nord-ouest"))
            val baseAngles = (0 until 8).map { it * 45.0 }.shuffled().take(count + 3)
            val found = coroutineScope {
                baseAngles.map { angle ->
                    async {
                        val search = Search(g)
                        var scale = 1.0
                        var route: PlannedRoute? = null
                        for (step in 0 until 3) { // ajuste la taille pour s'approcher de la distance voulue
                            val r = targetM / 3.4 * scale
                            val a1 = Math.toRadians(angle - 30); val a2 = Math.toRadians(angle + 30)
                            fun offset(a: Double) = Pair(
                                lat + r * cos(a) / 111_320.0,
                                lng + r * sin(a) / (111_320.0 * cos(Math.toRadians(lat)))
                            )
                            val (la1, lo1) = offset(a1); val (la2, lo2) = offset(a2)
                            val w1 = g.nearest(la1, lo1); val w2 = g.nearest(la2, lo2)
                            search.used.fill(false)
                            fun leg(from: Int, to: Int): List<Int>? = search.path(from, to, penalize = true)?.also { p -> p.forEach { search.used[it] = true } }
                            val p1 = leg(start, w1) ?: break
                            val p2 = leg(w1, w2) ?: break
                            val p3 = leg(w2, start) ?: break
                            val pts = toPoints(g, p1 + p2.drop(1) + p3.drop(1))
                            val len = Tracker.length(pts)
                            val ratio = len / targetM
                            if (ratio in 0.8..1.25) {
                                route = PlannedRoute(pts, len, names[((angle / 45.0).roundToInt()) % 8]); break
                            }
                            scale /= ratio.coerceIn(0.5, 2.0)
                        }
                        route
                    }
                }.awaitAll()
            }
            // Même choix qu'avant : les premières directions qui réussissent (ordre tiré au hasard), puis les plus proches de la distance voulue
            val result = found.filterNotNull().take(count)
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
