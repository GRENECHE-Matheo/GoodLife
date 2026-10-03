package com.goodlife.app.track

import com.goodlife.app.net.capped
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
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Type de trajet choisi par l'utilisateur. Les autoroutes et voies rapides ne sont jamais utilisées, quel que soit le
 * choix ; les grandes routes (nationales) seulement avec [MAIN_ROADS].
 */
enum class RouteStyle {
    BALANCED, SHORTEST, QUIET, NATURE, FLAT, HILLY, MAIN_ROADS;

    val label: String get() = when (this) {
        BALANCED -> t("Équilibré")
        SHORTEST -> t("Plus court")
        QUIET -> t("Petites routes")
        NATURE -> t("Nature")
        FLAT -> t("Éviter les côtes")
        HILLY -> t("Dénivelé")
        MAIN_ROADS -> t("Grandes routes")
    }

    val emoji: String get() = when (this) {
        BALANCED -> "⚖️"; SHORTEST -> "📏"; QUIET -> "🏘️"; NATURE -> "🌳"; FLAT -> "➖"; HILLY -> "⛰️"; MAIN_ROADS -> "🛣️"
    }

    companion object {
        fun of(name: String) = entries.firstOrNull { it.name == name } ?: BALANCED
    }
}

/**
 * Un itinéraire calculé : les points (avec l'altitude si elle est connue), sa longueur (m), son nom, le dénivelé
 * positif / négatif (NaN si inconnu), la destination (pour le recalcul en route) et le type de trajet.
 */
data class PlannedRoute(
    val points: List<TrackPoint>,
    val lengthM: Double,
    val label: String,
    val gainM: Double = Double.NaN,
    val lossM: Double = Double.NaN,
    val destLat: Double = Double.NaN,
    val destLng: Double = Double.NaN,
    val style: RouteStyle = RouteStyle.BALANCED,
    /** Trait dessiné au pinceau (lat, lng) : gardé pour recalculer le trajet avec un autre type ou une autre activité. */
    val stroke: List<Pair<Double, Double>> = emptyList()
) {
    /** On peut recalculer cet itinéraire (destination ou trait dessiné), pas une boucle tirée au hasard. */
    val reroutable: Boolean get() = hasDestination || stroke.size >= 2
    val hasDestination: Boolean get() = !destLat.isNaN() && !destLng.isNaN()
    val hasElevation: Boolean get() = !gainM.isNaN()
}

/** Types de voie (codes stockés pour chaque arête du réseau). */
internal object RoadClass {
    const val FOOTWAY = 0; const val PEDESTRIAN = 1; const val LIVING = 2; const val PATH = 3; const val TRACK = 4
    const val STEPS = 5; const val CYCLEWAY = 6; const val RESIDENTIAL = 7; const val UNCLASSIFIED = 8; const val SERVICE = 9
    const val TERTIARY = 10; const val SECONDARY = 11; const val PRIMARY = 12
    const val COUNT = 13

    /** Valeur « highway » d'OpenStreetMap → code (null = voie jamais utilisée). */
    fun ofOsm(highway: String): Int? = when (highway) {
        "footway", "bridleway" -> FOOTWAY
        "pedestrian" -> PEDESTRIAN
        "living_street" -> LIVING
        "path" -> PATH
        "track" -> TRACK
        "steps" -> STEPS
        "cycleway" -> CYCLEWAY
        "residential" -> RESIDENTIAL
        "unclassified", "road" -> UNCLASSIFIED
        "service" -> SERVICE
        "tertiary" -> TERTIARY
        "secondary" -> SECONDARY
        "primary" -> PRIMARY
        else -> null
    }

    private const val X = Double.POSITIVE_INFINITY

    /**
     * Coefficient de chaque type de voie (multiplie la distance) selon le type de trajet ; X = interdit.
     * Ordre : trottoir/sentier piéton, rue piétonne, zone de rencontre, chemin, piste, escaliers, piste cyclable,
     * rue résidentielle, petite route, voie de service, départementale, route secondaire, nationale.
     */
    fun factors(style: RouteStyle, bike: Boolean): DoubleArray = if (bike) when (style) {
        RouteStyle.SHORTEST -> doubleArrayOf(X, 1.0, 1.0, 1.0, 1.0, X, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, X)
        RouteStyle.QUIET -> doubleArrayOf(X, 2.0, 0.8, 1.6, 1.6, X, 0.8, 0.8, 0.9, 1.0, 2.0, 3.0, X)
        RouteStyle.NATURE -> doubleArrayOf(X, 1.8, 1.2, 0.8, 0.8, X, 0.9, 1.3, 1.3, 1.4, 1.8, 2.4, X)
        RouteStyle.MAIN_ROADS -> doubleArrayOf(X, 1.8, 1.3, 1.8, 1.8, X, 0.9, 1.2, 1.0, 1.3, 0.85, 0.8, 0.8)
        else -> doubleArrayOf(X, 1.6, 1.0, 1.3, 1.3, X, 0.75, 1.0, 1.15, 1.15, 1.25, 1.7, X)
    } else when (style) {
        RouteStyle.SHORTEST -> doubleArrayOf(1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, 1.0, X)
        RouteStyle.QUIET -> doubleArrayOf(0.9, 0.9, 0.8, 1.0, 1.0, 1.4, 1.1, 0.8, 0.9, 1.0, 2.0, 3.0, X)
        RouteStyle.NATURE -> doubleArrayOf(0.8, 1.0, 1.0, 0.6, 0.6, 1.3, 1.2, 1.3, 1.4, 1.4, 2.0, 2.6, X)
        RouteStyle.MAIN_ROADS -> doubleArrayOf(1.2, 1.1, 1.1, 1.4, 1.4, 1.6, 1.2, 1.1, 1.0, 1.2, 0.9, 0.85, 0.95)
        else -> doubleArrayOf(0.85, 0.85, 0.85, 0.85, 0.85, 1.4, 1.1, 1.0, 1.2, 1.1, 1.5, 2.0, X)
    }
}

/**
 * Calcul d'itinéraires dans l'app, à partir des rues et chemins d'OpenStreetMap (lus dans les tuiles de carte
 * OpenFreeMap, ou demandés au serveur Overpass en secours) : chemin vers une destination, boucles d'une distance
 * voulue, et trajet qui suit un trait dessiné sur la carte. Le type de trajet (plus court, petites routes, nature,
 * sans côtes…) change seulement le coût de chaque voie : le réseau téléchargé est le même.
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
     * avec [len] = longueur (m) et [cls] = type de voie de chaque arête. [x]/[y] = position en mètres (projection
     * locale), pour l'estimation rapide de la distance restante. [z] = altitude des nœuds, chargée seulement si besoin.
     */
    private class Graph(
        val lat: DoubleArray, val lng: DoubleArray, val x: DoubleArray, val y: DoubleArray,
        val off: IntArray, val to: IntArray, val len: DoubleArray, val cls: ByteArray,
        val south: Double, val west: Double, val north: Double, val east: Double, val bike: Boolean
    ) {
        val n get() = lat.size
        @Volatile var z: FloatArray? = null
        /** Réseau des longs trajets (zoom 12 : routes et pistes cyclables, sans tous les petits chemins). */
        var long = false
        /** Couloir vers une destination : réutilisé pour les recalculs en route vers la même destination. */
        var destLat = Double.NaN; var destLng = Double.NaN
        /** Couloir le long d'un trait au pinceau : réutilisé pour recalculer ce trait avec un autre type de trajet. */
        var strokeKey = 0

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

        /** Nœud le plus proche, sur le réseau principal et relié à au moins une voie permise pour ce type de trajet. */
        fun nearest(la: Double, lo: Double, fac: DoubleArray): Int {
            var best = -1; var bestD = Double.MAX_VALUE
            for (i in 0 until n) {
                if (!main[i]) continue
                val d = (lat[i] - la) * (lat[i] - la) + (lng[i] - lo) * (lng[i] - lo) * 0.45
                if (d >= bestD) continue
                var ok = false
                for (k in off[i] until off[i + 1]) if (fac[cls[k].toInt()].isFinite()) { ok = true; break }
                if (ok) { bestD = d; best = i }
            }
            return best
        }
    }

    /** Derniers réseaux téléchargés : une nouvelle demande dans une zone déjà couverte ne retélécharge rien. */
    private val cache = ArrayList<Graph>()

    /**
     * Voies téléchargées (le type de trajet choisit ensuite lesquelles utiliser). Jamais d'autoroute ni de voie rapide :
     * motorway et trunk ne sont pas demandés, et « motorroad=yes » est exclu. À vélo : ni trottoirs ni escaliers.
     */
    private fun highways(bike: Boolean): List<String> =
        (if (bike) "cycleway|path|track|residential|living_street|unclassified|tertiary|secondary|primary|service|road|pedestrian"
        else "footway|bridleway|path|pedestrian|track|steps|residential|living_street|unclassified|tertiary|service|cycleway|secondary|primary|road").split("|")

    /**
     * Requête Overpass : un groupe de voies par type, précédé d'un petit marqueur « w » qui porte le code du type.
     * On reçoit d'abord tous les points (coordonnées seules), puis les voies (liste de points seulement, sans leurs
     * autres étiquettes) : 5 à 10 fois moins de données à préparer pour le serveur.
     */
    private fun query(s: Double, w: Double, n: Double, e: Double, bike: Boolean): String {
        val forbidden = if (bike) "[\"bicycle\"!~\"no|dismount\"]" else "[\"foot\"!~\"no\"]"
        val groups = highways(bike).groupBy { RoadClass.ofOsm(it) ?: RoadClass.UNCLASSIFIED }.entries.toList()
        val bbox = String.format(Locale.US, "%.5f,%.5f,%.5f,%.5f", s, w, n, e)
        val sb = StringBuilder("[out:json][timeout:40][bbox:$bbox];\n")
        groups.forEachIndexed { i, (_, list) ->
            sb.append("way[\"highway\"~\"^(${list.joinToString("|")})$\"][\"access\"!~\"private|no\"][\"motorroad\"!=\"yes\"]$forbidden->.c$i;\n")
        }
        sb.append("(").append(groups.indices.joinToString("") { ".c$it;" }).append(")->.all;\nnode(w.all);\nout skel qt;\n")
        groups.forEachIndexed { i, (code, _) ->
            sb.append(String.format(Locale.US, "make w f=\"%d\";\nout;\n.c%d out skel qt;\n", code, i))
        }
        return sb.toString()
    }

    /**
     * Réseau de chemins dans un carré de côté 2 × [radiusM] autour d'un point (déjà en mémoire, sinon tuiles
     * OpenFreeMap, et Overpass en secours). [corridor] = trajet départ–arrivée : seules les tuiles le long du trajet.
     */
    private suspend fun graph(
        lat: Double, lng: Double, radiusM: Int, bike: Boolean, corridor: DoubleArray? = null, warmOnly: Boolean = false,
        long: Boolean = false, stroke: List<Pair<Double, Double>>? = null
    ): Graph =
        // Une seule préparation à la fois : une demande arrivée pendant la préparation anticipée la retrouve en mémoire
        lock.withLock { graphLocked(lat, lng, radiusM, bike, corridor, warmOnly, long, stroke) }

    private val lock = Mutex()

    private suspend fun graphLocked(
        lat: Double, lng: Double, radiusM: Int, bike: Boolean, corridor: DoubleArray?, warmOnly: Boolean, long: Boolean,
        stroke: List<Pair<Double, Double>>?
    ): Graph {
        val strokeKey = stroke?.hashCode() ?: 0
        // Un carré un peu plus petit que le cercle d'avant couvre la même zone utile, avec à peine plus de données
        val half = radiusM * 0.9
        val dLat = half / 111_320.0
        val dLng = half / (111_320.0 * cos(Math.toRadians(lat)))
        val s = lat - dLat; val n = lat + dLat; val w = lng - dLng; val e = lng + dLng
        synchronized(cache) {
            (if (stroke != null) cache.firstOrNull { it.bike == bike && it.long == long && it.strokeKey == strokeKey } else null)
                ?: cache.firstOrNull { it.bike == bike && it.long == long && it.contains(s, w, n, e) }
                ?: if (corridor != null) cache.lastOrNull { it.bike == bike && it.long == long && !it.destLat.isNaN() &&
                       Tracker.haversine(it.destLat, it.destLng, corridor[2], corridor[3]) < 100 } else null
        }?.let { return it }

        val z = if (long) RoadTiles.LONG else RoadTiles.DETAIL
        val tiles = if (stroke != null) {
            // Long trait au pinceau : seulement les tuiles le long du trait (pas tout le rectangle autour)
            RoadTiles.tilesForPolyline(stroke, 4_000.0, z)
        } else if (corridor != null) {
            // Long trajet : un couloir plus étroit autour de la ligne droite (12 km de chaque côté au plus)
            val margin = if (long) (Tracker.haversine(corridor[0], corridor[1], corridor[2], corridor[3]) * 0.18 + 4000).coerceAtMost(14_000.0) else half * 0.6
            RoadTiles.tilesForCorridor(corridor[0], corridor[1], corridor[2], corridor[3], margin, z)
        } else RoadTiles.tilesForBox(s, w, n, e, z)
        val fromTiles = if (tiles.size <= (if (stroke != null) 160 else if (long) 90 else RoadTiles.MAX_TILES)) {
            runCatching { RoadTiles.network(tiles, bike, z) }.getOrNull()?.let { raw ->
                withContext(Dispatchers.Default) {
                    // Un trajet ne couvre pas tout le carré : ce réseau ne resservira pas pour une autre demande
                    if (corridor != null || stroke != null) toGraph(raw, lat, lng, Double.NaN, Double.NaN, Double.NaN, Double.NaN, bike)
                    else toGraph(raw, lat, lng, s, w, n, e, bike)
                }
            }?.takeIf { g -> g.main.count { it } > 50 }?.also { it.long = long; it.strokeKey = strokeKey; if (corridor != null) { it.destLat = corridor[2]; it.destLng = corridor[3] } }
        } else null
        if (fromTiles == null && warmOnly) throw IOException("warm")
        // Long trajet : pas de secours Overpass (trop lourd pour le serveur public)
        if (fromTiles == null && long) throw IOException(t("Impossible de préparer un trajet aussi long pour le moment. Vérifie ta connexion et réessaie."))
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
                g = conn.inputStream.capped(80_000_000).use { parse(it) }.let { toGraph(it, lat, lng, s, w, n, e, bike) }
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
        var code = RoadClass.UNCLASSIFIED.toDouble()
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
                                "w" -> code = f?.takeIf { it >= 0 && it < RoadClass.COUNT } ?: code
                                "way" -> for (k in 0 until way.size - 1) {
                                    val a = index[way[k]] ?: continue
                                    val b = index[way[k + 1]] ?: continue
                                    if (a == b) continue
                                    if (from.size + 1 > fac.size) fac = fac.copyOf(fac.size * 2)
                                    fac[from.size] = code; from.add(a); dest.add(b)
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

    /** Rangement CSR (les deux sens de chaque arête) : longueur réelle et type de voie ([RawNetwork.f] = code du type). */
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
        val to = IntArray(raw.edges * 2); val len = DoubleArray(raw.edges * 2); val cls = ByteArray(raw.edges * 2)
        for (i in 0 until raw.edges) {
            val a = raw.a[i]; val b = raw.b[i]
            val d = Tracker.haversine(lat[a], lng[a], lat[b], lng[b])
            val c = raw.f[i].toInt().coerceIn(0, RoadClass.COUNT - 1).toByte()
            var k = pos[a]++; to[k] = b; len[k] = d; cls[k] = c
            k = pos[b]++; to[k] = a; len[k] = d; cls[k] = c
        }
        return Graph(lat, lng, x, y, off, to, len, cls, s, w, n, e, bike)
    }

    /** Altitude des nœuds du réseau (une fois par réseau), pour « Éviter les côtes » et « Dénivelé ». */
    private suspend fun ensureElevation(g: Graph): FloatArray {
        g.z?.let { return it }
        val z = Elevation.of(g.lat, g.lng) ?: throw IOException(t("Données d'altitude indisponibles pour le moment. Choisis un autre type de trajet ou réessaie."))
        g.z = z
        return z
    }

    /**
     * Recherche A* réutilisable (tableaux alloués une fois par fil de calcul). Coût d'une arête = longueur × coefficient
     * du type de voie ; « Éviter les côtes » ajoute chaque mètre de montée, « Dénivelé » rend les pentes moins chères.
     * Les rues déjà prises par la même boucle ([used]) coûtent 6 fois plus cher, pour éviter les allers-retours.
     */
    private class Search(val g: Graph, val style: RouteStyle) {
        // Long trajet : les nationales restent possibles en dernier recours (coût très élevé), car au zoom 12 certains
        // quartiers ne sont reliés au reste que par elles
        private val fac = RoadClass.factors(style, g.bike).also { f ->
            if (g.long && !f[RoadClass.PRIMARY].isFinite()) f[RoadClass.PRIMARY] = 3.0
        }
        private val z = if (style == RouteStyle.FLAT || style == RouteStyle.HILLY) g.z else null
        private val climb = if (g.bike) 12.0 else 8.0
        private val dist = DoubleArray(g.n) { Double.MAX_VALUE }
        private val prev = IntArray(g.n) { -1 }
        private val closed = BooleanArray(g.n)
        private val touched = Ints(4096)
        private var hk = DoubleArray(4096); private var hv = IntArray(4096); private var hs = 0
        // L'estimation ne doit jamais dépasser le vrai coût (sinon le chemin trouvé ne serait plus le meilleur)
        private val hFactor = (fac.filter { it.isFinite() }.minOrNull() ?: 1.0) * 0.99 / (if (z != null && style == RouteStyle.HILLY) 2.8 else 1.0)
        val used = BooleanArray(g.n)

        fun nearest(la: Double, lo: Double) = g.nearest(la, lo, fac)

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

        private fun cost(u: Int, k: Int, v: Int): Double {
            val f = fac[g.cls[k].toInt()]
            if (!f.isFinite()) return Double.POSITIVE_INFINITY
            val base = g.len[k] * f
            val zz = z ?: return base
            val dz = (zz[v] - zz[u]).toDouble()
            return if (style == RouteStyle.FLAT) base + (if (dz > 0) dz * climb else 0.0)
                   else base / (1.0 + 6.0 * minOf(abs(dz) / g.len[k].coerceAtLeast(1.0), 0.3))
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
                    val c = cost(u, k, v)
                    if (c == Double.POSITIVE_INFINITY) continue
                    val nd = du + c * (if (penalize && used[u] && used[v]) 6.0 else 1.0)
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

    private fun toPoints(g: Graph, path: List<Int>): List<TrackPoint> {
        val z = g.z
        return path.map { TrackPoint(g.lat[it], g.lng[it], z?.get(it)?.toDouble() ?: Double.NaN, 0L) }
    }

    /** Ajoute l'altitude et le dénivelé à un itinéraire (sans rien changer s'ils ne sont pas disponibles). */
    suspend fun withElevation(r: PlannedRoute): PlannedRoute {
        if (r.hasElevation) return r
        val (pts, gain, loss) = runCatching { Elevation.annotate(r.points) }.getOrNull() ?: return r
        return r.copy(points = pts, gainM = gain, lossM = loss)
    }

    private suspend fun searchFor(g: Graph, style: RouteStyle): Search {
        if (style == RouteStyle.FLAT || style == RouteStyle.HILLY) ensureElevation(g)
        return Search(g, style)
    }

    /** Itinéraire vers une destination (par les chemins et rues), selon le type de trajet choisi. */
    suspend fun toDestination(fromLat: Double, fromLng: Double, toLat: Double, toLng: Double, type: OutingType, style: RouteStyle = RouteStyle.BALANCED): PlannedRoute {
        val direct = Tracker.haversine(fromLat, fromLng, toLat, toLng)
        val max = if (type == OutingType.BIKE) 150_000 else 60_000
        if (direct > max) throw IOException(t("Destination trop loin (plus de %1\$s km à vol d'oiseau).", max / 1000))
        // Au-delà de 15 km : réseau des longs trajets (routes et pistes cyclables)
        val long = direct > 15_000
        val radius = (direct * 0.65 + 800).roundToInt().coerceIn(1000, if (long) 110_000 else 16_000)
        var g = graph((fromLat + toLat) / 2, (fromLng + toLng) / 2, radius, type == OutingType.BIKE, doubleArrayOf(fromLat, fromLng, toLat, toLng), long = long)
        // Couloir réutilisé (recalcul en route) mais départ trop loin de lui : on en prépare un nouveau
        val probe = Search(g, RouteStyle.SHORTEST).nearest(fromLat, fromLng)
        if (probe < 0 || Tracker.haversine(g.lat[probe], g.lng[probe], fromLat, fromLng) > 1500) {
            synchronized(cache) { cache.remove(g) }
            g = graph((fromLat + toLat) / 2, (fromLng + toLng) / 2, radius, type == OutingType.BIKE, doubleArrayOf(fromLat, fromLng, toLat, toLng), long = long)
        }
        val search = searchFor(g, style)
        val path = withContext(Dispatchers.Default) { search.path(search.nearest(fromLat, fromLng), search.nearest(toLat, toLng), penalize = false) }
            ?: throw IOException(t("Aucun chemin trouvé jusqu'à ce point."))
        val pts = toPoints(g, path)
        return withElevation(PlannedRoute(pts, Tracker.length(pts), t("Vers la destination"), destLat = toLat, destLng = toLng, style = style))
    }

    /**
     * Trajet qui suit un trait dessiné sur la carte : il passe par des points pris le long du trait (puis la
     * destination, si elle est choisie), en restant sur les chemins et rues les plus proches.
     */
    suspend fun alongStroke(
        fromLat: Double, fromLng: Double, stroke: List<Pair<Double, Double>>, type: OutingType, style: RouteStyle,
        destLat: Double = Double.NaN, destLng: Double = Double.NaN
    ): PlannedRoute {
        val pts = ArrayList<Pair<Double, Double>>()
        pts += fromLat to fromLng
        // Un point de passage tous les 250 m environ le long du trait
        var acc = 0.0
        stroke.forEachIndexed { i, p ->
            if (i == 0) { pts += p; return@forEachIndexed }
            val q = stroke[i - 1]
            acc += Tracker.haversine(q.first, q.second, p.first, p.second)
            if (acc >= 250.0 || i == stroke.lastIndex) { pts += p; acc = 0.0 }
        }
        if (!destLat.isNaN() && Tracker.haversine(pts.last().first, pts.last().second, destLat, destLng) > 80) pts += destLat to destLng
        if (pts.size < 2) throw IOException(t("Trace un trait plus long sur la carte."))
        val s = pts.minOf { it.first }; val n = pts.maxOf { it.first }; val w = pts.minOf { it.second }; val e = pts.maxOf { it.second }
        val cLat = (s + n) / 2; val cLng = (w + e) / 2
        val halfDiag = Tracker.haversine(s, w, n, e) / 2
        var strokeLen = 0.0
        for (i in 1 until pts.size) strokeLen += Tracker.haversine(pts[i - 1].first, pts[i - 1].second, pts[i].first, pts[i].second)
        if (strokeLen > 150_000) throw IOException(t("Trait trop long (plus de 150 km). Dessine un trajet plus court."))
        val long = halfDiag > 8_000
        // Long trait : un couloir le long du trait (points tous les 2 km environ suffisent pour le tracer)
        val corridorPts = if (long) pts.filterIndexed { i, _ -> i % 8 == 0 || i == pts.lastIndex } else null
        val g = graph(cLat, cLng, (halfDiag / 0.9 + 800).roundToInt().coerceIn(1000, 70_000), type == OutingType.BIKE, long = long, stroke = corridorPts)
        val search = searchFor(g, style)
        val nodes = withContext(Dispatchers.Default) {
            val ids = pts.map { (la, lo) -> search.nearest(la, lo) }.filter { it >= 0 }
            val out = ArrayList<Int>()
            for (i in 0 until ids.size - 1) {
                if (ids[i] == ids[i + 1]) continue
                val leg = search.path(ids[i], ids[i + 1], penalize = false) ?: continue
                if (out.isEmpty()) out += leg else out += leg.drop(1)
            }
            out
        }
        // Allers-retours inutiles (un point de passage tombé sur une rue voisine) : A → B → A devient A
        val clean = ArrayList<Int>(nodes.size)
        for (v in nodes) {
            if (clean.size >= 2 && clean[clean.size - 2] == v) clean.removeAt(clean.lastIndex)
            else if (clean.isEmpty() || clean.last() != v) clean.add(v)
        }
        if (clean.size < 2) throw IOException(t("Aucun chemin trouvé le long de ce trait."))
        val route = toPoints(g, clean)
        return withElevation(PlannedRoute(route, Tracker.length(route), t("Trajet dessiné"), destLat = destLat, destLng = destLng, style = style, stroke = stroke))
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

    suspend fun loops(lat: Double, lng: Double, targetM: Double, type: OutingType, style: RouteStyle = RouteStyle.BALANCED, count: Int = 3): List<PlannedRoute> {
        val g = graph(lat, lng, loopRadius(targetM), type == OutingType.BIKE)
        if (style == RouteStyle.FLAT || style == RouteStyle.HILLY) ensureElevation(g)
        val found = withContext(Dispatchers.Default) {
            val start = Search(g, style).nearest(lat, lng)
            val names = listOf(t("Boucle nord"), t("Boucle nord-est"), t("Boucle est"), t("Boucle sud-est"), t("Boucle sud"), t("Boucle sud-ouest"), t("Boucle ouest"), t("Boucle nord-ouest"))
            val baseAngles = (0 until 8).map { it * 45.0 }.shuffled().take(count + 3)
            coroutineScope {
                baseAngles.map { angle ->
                    async {
                        val search = Search(g, style)
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
                            val w1 = search.nearest(la1, lo1); val w2 = search.nearest(la2, lo2)
                            search.used.fill(false)
                            fun leg(from: Int, to: Int): List<Int>? = search.path(from, to, penalize = true)?.also { p -> p.forEach { search.used[it] = true } }
                            val p1 = leg(start, w1) ?: break
                            val p2 = leg(w1, w2) ?: break
                            val p3 = leg(w2, start) ?: break
                            val pts = toPoints(g, p1 + p2.drop(1) + p3.drop(1))
                            val len = Tracker.length(pts)
                            val ratio = len / targetM
                            if (ratio in 0.8..1.25) {
                                route = PlannedRoute(pts, len, names[((angle / 45.0).roundToInt()) % 8], style = style); break
                            }
                            scale /= ratio.coerceIn(0.5, 2.0)
                        }
                        route
                    }
                }.awaitAll()
            }
        }
        // Les premières directions qui réussissent (ordre tiré au hasard), puis les plus proches de la distance voulue
        val result = found.filterNotNull().take(count)
        if (result.isEmpty()) throw IOException(t("Pas assez de chemins ici pour une boucle de cette distance. Essaie une autre distance."))
        return result.map { withElevation(it) }.sortedBy { abs(it.lengthM - targetM) }
    }

    /**
     * Position sur l'itinéraire : distance restante, écart au tracé (m) et index du point le plus proche. On cherche
     * d'abord autour de la dernière position connue ([hint]) pour ne pas « sauter » au début d'une boucle quand on
     * repasse près du départ.
     */
    fun progress(route: List<TrackPoint>, lat: Double, lng: Double, hint: Int = -1): Triple<Double, Double, Int> {
        if (route.size < 2) return Triple(0.0, 0.0, 0)
        fun best(range: IntRange): Pair<Int, Double> {
            var bi = range.first; var bd = Double.MAX_VALUE
            for (i in range) {
                val d = Tracker.haversine(route[i].lat, route[i].lng, lat, lng)
                if (d < bd) { bd = d; bi = i }
            }
            return bi to bd
        }
        var (i, d) = if (hint >= 0) best((hint - 20).coerceAtLeast(0)..(hint + 400).coerceAtMost(route.lastIndex)) else -1 to Double.MAX_VALUE
        if (i < 0 || d > 60.0) { val g = best(route.indices); if (g.second < d) { i = g.first; d = g.second } }
        return Triple(Tracker.length(route.subList(i, route.size)), d, i)
    }
}
