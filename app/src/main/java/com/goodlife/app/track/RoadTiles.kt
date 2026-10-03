package com.goodlife.app.track

import com.goodlife.app.net.readCapped
import com.goodlife.app.data.Repo
import com.goodlife.app.net.USER_AGENT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.roundToLong
import kotlin.math.sinh
import kotlin.math.tan

/** Réseau brut : points (lat, lng) et arêtes non orientées a–b avec leur coefficient de voie. */
internal class RawNetwork(val lat: DoubleArray, val lng: DoubleArray, val a: IntArray, val b: IntArray, val f: DoubleArray, val edges: Int)

/**
 * Rues et chemins lus dans les tuiles vectorielles OpenFreeMap (les mêmes que la carte, données OpenStreetMap,
 * schéma OpenMapTiles, zoom 14). Les tuiles viennent d'un CDN : quelques dixièmes de seconde, contre plusieurs
 * secondes (ou un refus quand il est saturé) pour le serveur public Overpass. Elles sont gardées en cache sur le
 * téléphone : une deuxième boucle dans le même quartier ne télécharge plus rien.
 *
 * Les tuiles sont simplifiées : un croisement peut perdre son point commun. On reconstruit donc les croisements
 * (deux voies au même niveau qui se coupent, ou une extrémité posée sur une autre voie). Jamais entre un pont ou un
 * tunnel et la voie qu'il croise.
 */
internal object RoadTiles {
    private const val TILEJSON = "https://tiles.openfreemap.org/planet"
    private const val HOST = "https://tiles.openfreemap.org/"
    /** Au-delà, la zone est trop grande pour les tuiles (trop de données) : on passe par Overpass. */
    const val MAX_TILES = 64
    private const val SNAP = 2.0          // tolérance en pixels de tuile (4096 par tuile ≈ 0,4 m)
    private const val CELL = 64           // grille de recherche des croisements (pixels)
    private const val CACHE_DAYS = 30L

    @Volatile private var template: String? = null
    @Volatile private var templateAt = 0L

    // ---------- Tuiles nécessaires ----------

    private fun tileX(lng: Double, z: Int) = (lng + 180.0) / 360.0 * (1 shl z)
    private fun tileY(lat: Double, z: Int): Double {
        val r = Math.toRadians(lat)
        return (1.0 - ln(tan(r) + 1.0 / cos(r)) / Math.PI) / 2.0 * (1 shl z)
    }

    /**
     * Zoom des tuiles : 14 (toutes les rues et tous les chemins) près de chez soi ; 12 pour les longs trajets (routes,
     * rues et pistes cyclables, 16 fois moins de tuiles à télécharger pour la même distance).
     */
    const val DETAIL = 14
    const val LONG = 12

    /** Tuiles d'un rectangle. */
    fun tilesForBox(s: Double, w: Double, n: Double, e: Double, z: Int = DETAIL): List<Pair<Int, Int>> {
        val x0 = floor(tileX(w, z)).toInt(); val x1 = floor(tileX(e, z)).toInt()
        val y0 = floor(tileY(n, z)).toInt(); val y1 = floor(tileY(s, z)).toInt()
        return (x0..x1).flatMap { x -> (y0..y1).map { y -> x to y } }
    }

    /** Tuiles le long d'un trajet : celles dont le centre est à moins de [marginM] du segment départ–arrivée. */
    fun tilesForCorridor(lat1: Double, lng1: Double, lat2: Double, lng2: Double, marginM: Double, z: Int = DETAIL): List<Pair<Int, Int>> {
        val latC = (lat1 + lat2) / 2
        val kx = 111_320.0 * cos(Math.toRadians(latC))
        val dLat = marginM / 111_320.0; val dLng = marginM / kx
        val box = tilesForBox(minOf(lat1, lat2) - dLat, minOf(lng1, lng2) - dLng, maxOf(lat1, lat2) + dLat, maxOf(lng1, lng2) + dLng, z)
        val tileM = 40_075_016.0 * cos(Math.toRadians(latC)) / (1 shl z)
        val ax = 0.0; val ay = 0.0
        val bx = (lng2 - lng1) * kx; val by = (lat2 - lat1) * 111_320.0
        return box.filter { (x, y) ->
            val cLng = (x + 0.5) / (1 shl z) * 360.0 - 180.0
            val cLat = Math.toDegrees(atan(sinh(Math.PI * (1 - 2 * (y + 0.5) / (1 shl z)))))
            val px = (cLng - lng1) * kx; val py = (cLat - lat1) * 111_320.0
            val len2 = bx * bx + by * by
            val t = if (len2 == 0.0) 0.0 else (((px - ax) * bx + (py - ay) * by) / len2).coerceIn(0.0, 1.0)
            hypot(px - (ax + t * bx), py - (ay + t * by)) <= marginM + tileM * 0.75
        }
    }

    /** Tuiles le long d'une ligne brisée (trait au pinceau) : l'union des couloirs de chaque morceau. */
    fun tilesForPolyline(pts: List<Pair<Double, Double>>, marginM: Double, z: Int = DETAIL): List<Pair<Int, Int>> {
        val out = LinkedHashSet<Pair<Int, Int>>()
        for (i in 0 until pts.size - 1) {
            val (a, b) = pts[i] to pts[i + 1]
            out += tilesForCorridor(a.first, a.second, b.first, b.second, marginM, z)
        }
        if (pts.size == 1) out += tilesForCorridor(pts[0].first, pts[0].second, pts[0].first, pts[0].second, marginM, z)
        return out.toList()
    }

    // ---------- Téléchargement (avec cache) ----------

    private fun get(url: String): ByteArray {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000; readTimeout = 20_000; instanceFollowRedirects = false
            setRequestProperty("User-Agent", USER_AGENT)
        }
        try {
            val code = conn.responseCode
            if (code == 204 || code == 404) return ByteArray(0) // tuile vide (mer, désert…)
            if (code !in 200..299) throw IOException("HTTP $code")
            return conn.inputStream.use { it.readCapped(8 * 1024 * 1024) }
        } finally {
            conn.disconnect()
        }
    }

    /** Adresse des tuiles de la version en cours (elle change chaque semaine), vérifiée : toujours chez OpenFreeMap. */
    private fun template(cacheDir: File): String {
        val now = System.currentTimeMillis()
        template?.let { if (now - templateAt < 6 * 3_600_000L) return it }
        val saved = File(cacheDir, "template.txt")
        // Adresse enregistrée il y a moins de 6 h : pas besoin de redemander au serveur (≈ 1 s de gagnée à l'ouverture)
        if (saved.exists() && now - saved.lastModified() < 6 * 3_600_000L) {
            runCatching { saved.readText().trim() }.getOrNull()
                ?.takeIf { it.startsWith(HOST) && it.endsWith("/{z}/{x}/{y}.pbf") && !it.contains("..") }
                ?.let { template = it; templateAt = saved.lastModified(); return it }
        }
        val fresh = runCatching {
            JSONObject(String(get(TILEJSON), Charsets.UTF_8)).getJSONArray("tiles").getString(0)
        }.getOrNull()?.takeIf { it.startsWith(HOST) && it.endsWith("/{z}/{x}/{y}.pbf") && !it.contains("..") }
        val t = fresh?.also { runCatching { saved.writeText(it) } }
            ?: runCatching { saved.readText().trim() }.getOrNull()?.takeIf { it.startsWith(HOST) && it.endsWith("/{z}/{x}/{y}.pbf") }
            ?: throw IOException("OpenFreeMap")
        template = t; templateAt = now
        return t
    }

    private fun cacheDir(): File? = Repo.appContext?.cacheDir?.let { File(it, "route_tiles") }?.also { it.mkdirs() }

    /** Vide les tuiles de plus de 30 jours, et les plus anciennes au-delà de 30 Mo (Android peut aussi vider ce cache). */
    private fun prune(dir: File) {
        val limit = System.currentTimeMillis() - CACHE_DAYS * 86_400_000L
        val files = dir.listFiles()?.filter { it.name != "template.txt" }.orEmpty()
        files.filter { it.lastModified() < limit }.forEach { it.delete() }
        var total = 0L
        files.filter { it.exists() }.sortedByDescending { it.lastModified() }.forEach { f ->
            total += f.length()
            if (total > 30L * 1024 * 1024) f.delete()
        }
    }

    private suspend fun fetchAll(tiles: List<Pair<Int, Int>>, z: Int): List<Triple<Int, Int, ByteArray>> = withContext(Dispatchers.IO) {
        val dir = cacheDir() ?: throw IOException("cache")
        prune(dir)
        // Adresse des tuiles demandée seulement s'il en manque (zone hors ligne : aucun réseau nécessaire)
        var tpl: String? = null
        val lock = Any()
        fun tpl(): String = synchronized(lock) { tpl ?: template(dir).also { tpl = it } }
        val gate = Semaphore(6)
        coroutineScope {
            tiles.map { (x, y) ->
                async {
                    gate.withPermit {
                        // Zone téléchargée pour le hors-ligne : rien à télécharger
                        OfflineData.roads(z, x, y)?.let { return@withPermit Triple(x, y, it.readBytes()) }
                        // En cache : seulement la couche des rues (5 à 10 fois plus léger que la tuile entière)
                        val t = tpl()
                        val f = File(dir, "${version(t)}_${z}_${x}_$y.roads")
                        val bytes = if (f.exists()) f.readBytes() else download(t, z, x, y).also { b -> runCatching { f.writeBytes(b) } }
                        Triple(x, y, bytes)
                    }
                }
            }.awaitAll()
        }
    }

    private fun version(tpl: String) = tpl.removePrefix(HOST).substringBefore("/{z}").replace(Regex("[^A-Za-z0-9_]"), "_")
    private fun url(tpl: String, z: Int, x: Int, y: Int) = tpl.replace("{z}", z.toString()).replace("{x}", x.toString()).replace("{y}", y.toString())

    /** Télécharge une tuile (deux essais) et n'en garde que les rues. */
    private fun download(tpl: String, z: Int, x: Int, y: Int): ByteArray {
        var last: IOException? = null
        for (attempt in 0 until 2) {
            try { return roadsOnly(get(url(tpl, z, x, y))) } catch (e: IOException) { last = e }
        }
        throw last ?: IOException("tile")
    }

    /** Hors ligne : enregistre les rues d'une tuile dans le dossier d'une zone (rien si elle y est déjà). */
    fun saveOffline(dir: File, z: Int, x: Int, y: Int) {
        val f = File(dir, OfflineData.roadsName(z, x, y))
        if (f.exists()) return
        val cache = cacheDir() ?: throw IOException("cache")
        val bytes = download(template(cache), z, x, y)
        val tmp = File(dir, f.name + ".tmp")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(f)) { tmp.delete(); throw IOException("write") }
    }

    /**
     * Taille d'une tuile complète de la carte telle qu'elle est stockée (compressée), sans la télécharger : sert à
     * estimer la place d'une zone hors ligne. -1 si inconnue.
     */
    fun tileBytes(z: Int, x: Int, y: Int): Long {
        val cache = cacheDir() ?: return -1
        val conn = (URL(url(template(cache), z, x, y)).openConnection() as HttpURLConnection).apply {
            requestMethod = "HEAD"; connectTimeout = 8_000; readTimeout = 8_000; instanceFollowRedirects = false
            setRequestProperty("User-Agent", USER_AGENT); setRequestProperty("Accept-Encoding", "gzip")
        }
        return try {
            when (conn.responseCode) {
                204, 404 -> 0L
                in 200..299 -> conn.getHeaderField("Content-Length")?.toLongOrNull() ?: -1L
                else -> -1L
            }
        } catch (e: IOException) { -1L } finally { conn.disconnect() }
    }

    // ---------- Lecture des tuiles (protobuf « Mapbox Vector Tile ») ----------

    private class Reader(val b: ByteArray, var i: Int, val end: Int) {
        fun more() = i < end
        fun varint(): Long {
            var r = 0L; var s = 0
            while (true) {
                val c = b[i++].toInt() and 0xFF
                r = r or ((c and 0x7F).toLong() shl s); s += 7
                if (c < 0x80) return r
            }
        }
        /** Lit l'en-tête d'un champ : numéro shl 3 | type. */
        fun key() = varint().toInt()
        fun sub(): Reader { val len = varint().toInt(); val r = Reader(b, i, i + len); i += len; return r }
        fun skip(wire: Int) {
            when (wire) {
                0 -> varint()
                1 -> i += 8
                2 -> { val len = varint().toInt(); i += len }
                5 -> i += 4
                else -> throw IOException("wire $wire")
            }
        }
        fun string(): String { val len = varint().toInt(); val s = String(b, i, len, Charsets.UTF_8); i += len; return s }
    }

    private fun zz(n: Long): Int = ((n ushr 1) xor -(n and 1)).toInt()

    /** Nom d'une couche (son premier champ « name »), sans lire le reste. */
    private fun layerName(layer: Reader): String? {
        val r = Reader(layer.b, layer.i, layer.end)
        while (r.more()) {
            val k = r.key()
            if (k ushr 3 == 1 && k and 7 == 2) return r.string()
            r.skip(k and 7)
        }
        return null
    }

    /** Ne garde de la tuile que la couche « transportation » (même format, pour le cache). */
    private fun roadsOnly(tile: ByteArray): ByteArray {
        val r = Reader(tile, 0, tile.size)
        while (r.more()) {
            val k = r.key()
            if (k ushr 3 != 3 || k and 7 != 2) { r.skip(k and 7); continue }
            val start = r.i
            val layer = r.sub()
            if (layerName(layer) == "transportation") {
                val out = java.io.ByteArrayOutputStream(r.i - start + 1)
                out.write(0x1A)                      // champ 3, type 2 (couche)
                out.write(tile, start, r.i - start)  // longueur (varint) + contenu
                return out.toByteArray()
            }
        }
        return ByteArray(0)
    }

    private fun value(r: Reader): Any? {
        var v: Any? = null
        while (r.more()) {
            val k = r.key()
            when (k ushr 3) {
                1 -> v = r.string()
                4, 5 -> v = r.varint()
                6 -> v = zz(r.varint()).toLong()
                7 -> v = r.varint() != 0L
                else -> r.skip(k and 7)
            }
        }
        return v
    }

    private val FORBIDDEN = setOf("motorway", "trunk", "rail", "transit", "ferry", "busway", "bus_guideway", "raceway", "aerialway", "pier")

    /**
     * Type de la voie (code [RoadClass]), ou null si elle n'est jamais utilisée pour ce mode. Le coefficient dépend
     * ensuite du type de trajet choisi (plus court, petites routes…).
     */
    private fun weight(cls: String?, sub: String?, access: String?, foot: String?, bicycle: String?, indoor: Boolean, bike: Boolean): Double? {
        if (cls == null || indoor || access == "no" || cls.endsWith("_construction") || cls in FORBIDDEN) return null
        if (bike && (bicycle == "no" || bicycle == "dismount")) return null
        if (!bike && foot == "no") return null
        val code = when (sub) {
            "footway", "bridleway" -> RoadClass.FOOTWAY
            "pedestrian" -> RoadClass.PEDESTRIAN
            "living_street" -> RoadClass.LIVING
            "path" -> RoadClass.PATH
            "steps" -> RoadClass.STEPS
            "cycleway" -> RoadClass.CYCLEWAY
            "residential" -> RoadClass.RESIDENTIAL
            "unclassified" -> RoadClass.UNCLASSIFIED
            "corridor", "platform" -> return null
            else -> when (cls) {
                "path" -> RoadClass.PATH
                "track" -> RoadClass.TRACK
                "minor" -> RoadClass.RESIDENTIAL
                "service" -> RoadClass.SERVICE
                "tertiary" -> RoadClass.TERTIARY
                "secondary" -> RoadClass.SECONDARY
                "primary" -> RoadClass.PRIMARY
                else -> return null
            }
        }
        // À vélo : ni trottoirs ni escaliers
        if (bike && (code == RoadClass.FOOTWAY || code == RoadClass.STEPS)) return null
        return code.toDouble()
    }

    /** Segments (coordonnées entières globales : tuile × 4096 + pixel), coefficient et niveau (pont/tunnel/étage). */
    private class Segs {
        var ax = IntArray(1 shl 14); var ay = IntArray(1 shl 14); var bx = IntArray(1 shl 14); var by = IntArray(1 shl 14)
        var w = DoubleArray(1 shl 14); var lvl = IntArray(1 shl 14); var size = 0
        fun add(a1: Int, a2: Int, b1: Int, b2: Int, f: Double, l: Int) {
            if (size == ax.size) {
                val c = size * 2
                ax = ax.copyOf(c); ay = ay.copyOf(c); bx = bx.copyOf(c); by = by.copyOf(c); w = w.copyOf(c); lvl = lvl.copyOf(c)
            }
            ax[size] = a1; ay[size] = a2; bx[size] = b1; by[size] = b2; w[size] = f; lvl[size] = l; size++
        }
    }

    private fun readTile(tx: Int, ty: Int, data: ByteArray, bike: Boolean, segs: Segs, levels: HashMap<String, Int>) {
        if (data.isEmpty()) return
        val tile = Reader(data, 0, data.size)
        val tags = IntArray(64)
        while (tile.more()) {
            val k = tile.key()
            if (k ushr 3 != 3 || k and 7 != 2) { tile.skip(k and 7); continue }
            val layer = tile.sub()
            if (layerName(layer) != "transportation") continue
            var extent = 4096
            val keys = ArrayList<String>(); val values = ArrayList<Any?>(); val feats = ArrayList<Reader>()
            while (layer.more()) {
                val lk = layer.key()
                when (lk ushr 3) {
                    2 -> feats.add(layer.sub())
                    3 -> keys.add(layer.string())
                    4 -> values.add(value(layer.sub()))
                    5 -> extent = layer.varint().toInt()
                    else -> layer.skip(lk and 7)
                }
            }
            val iClass = keys.indexOf("class"); val iSub = keys.indexOf("subclass"); val iAccess = keys.indexOf("access")
            val iFoot = keys.indexOf("foot"); val iBike = keys.indexOf("bicycle"); val iIndoor = keys.indexOf("indoor")
            val iBrunnel = keys.indexOf("brunnel"); val iLayer = keys.indexOf("layer"); val iLevel = keys.indexOf("level")
            val ox = tx * extent; val oy = ty * extent
            for (f in feats) {
                var type = 0
                var nTags = 0
                var geom: Reader? = null
                while (f.more()) {
                    val fk = f.key()
                    when (fk ushr 3) {
                        2 -> { val r = f.sub(); while (r.more()) { val v = r.varint().toInt(); if (nTags < tags.size) tags[nTags++] = v } }
                        3 -> type = f.varint().toInt()
                        4 -> geom = f.sub()
                        else -> f.skip(fk and 7)
                    }
                }
                if (type != 2 || geom == null) continue
                var cls: String? = null; var sub: String? = null; var access: String? = null; var foot: String? = null
                var bicycle: String? = null; var indoor = false; var brunnel = ""; var layerN = 0L; var levelN = 0L
                var t = 0
                while (t + 1 < nTags) {
                    val key = tags[t]; val v = values.getOrNull(tags[t + 1])
                    when (key) {
                        iClass -> cls = v as? String
                        iSub -> sub = v as? String
                        iAccess -> access = v?.toString()
                        iFoot -> foot = v?.toString()
                        iBike -> bicycle = v?.toString()
                        iIndoor -> indoor = v == true || v == 1L
                        iBrunnel -> brunnel = v?.toString().orEmpty()
                        iLayer -> layerN = (v as? Long) ?: 0L
                        iLevel -> levelN = (v as? Long) ?: 0L
                    }
                    t += 2
                }
                val wgt = weight(cls, sub, access, foot, bicycle, indoor, bike) ?: continue
                val lvl = if (brunnel.isEmpty() && layerN == 0L && levelN == 0L) 0 else levels.getOrPut("$brunnel|$layerN|$levelN") { levels.size + 1 }
                // Géométrie : MoveTo / LineTo en coordonnées relatives (zigzag)
                var x = 0; var y = 0; var px = 0; var py = 0
                while (geom.more()) {
                    val ci = geom.varint().toInt()
                    val cmd = ci and 7; val cnt = ci ushr 3
                    if (cmd == 7) continue
                    for (c in 0 until cnt) {
                        x += zz(geom.varint()); y += zz(geom.varint())
                        if (cmd == 2) {
                            // Segment gardé par la seule tuile qui contient son milieu (la marge des tuiles voisines se recouvre)
                            val mx2 = px + x; val my2 = py + y
                            if (mx2 >= 0 && my2 >= 0 && mx2 < 2 * extent && my2 < 2 * extent && (px != x || py != y)) {
                                segs.add(ox + px, oy + py, ox + x, oy + y, wgt, lvl)
                            }
                        }
                        px = x; py = y
                    }
                }
            }
        }
    }

    // ---------- Reconstruction des croisements, puis réseau ----------

    /** Table entier 64 bits → entier, sans objets (adressage ouvert). */
    private class LongIntMap(expected: Int) {
        private var cap = Integer.highestOneBit((expected * 2).coerceAtLeast(16)) * 2
        private var keys = LongArray(cap) { EMPTY }
        private var vals = IntArray(cap)
        private var size = 0
        private fun slot(k: Long, mask: Int): Int { var h = k * -0x61c8864680b583ebL; h = h xor (h ushr 29); return (h.toInt()) and mask }
        fun get(k: Long): Int {
            val mask = cap - 1
            var i = slot(k, mask)
            while (true) { val c = keys[i]; if (c == EMPTY) return -1; if (c == k) return vals[i]; i = (i + 1) and mask }
        }
        fun put(k: Long, v: Int) {
            if (size * 2 >= cap) grow()
            val mask = cap - 1
            var i = slot(k, mask)
            while (keys[i] != EMPTY && keys[i] != k) i = (i + 1) and mask
            if (keys[i] == EMPTY) size++
            keys[i] = k; vals[i] = v
        }
        private fun grow() {
            val ok = keys; val ov = vals
            cap *= 2; keys = LongArray(cap) { EMPTY }; vals = IntArray(cap); size = 0
            for (i in ok.indices) if (ok[i] != EMPTY) put(ok[i], ov[i])
        }
        companion object { const val EMPTY = Long.MIN_VALUE }
    }

    /** Listes sans objets. */
    private class Ints(cap: Int = 1024) { var a = IntArray(cap); var size = 0; fun add(v: Int) { if (size == a.size) a = a.copyOf(size * 2); a[size++] = v } }
    private class Longs(cap: Int = 1024) { var a = LongArray(cap); var size = 0; fun add(v: Long) { if (size == a.size) a = a.copyOf(size * 2); a[size++] = v } }

    private fun pack(x: Long, y: Long) = (x shl 32) or (y and 0xFFFFFFFFL)

    suspend fun network(tiles: List<Pair<Int, Int>>, bike: Boolean, z: Int = DETAIL): RawNetwork {
        val data = fetchAll(tiles, z)
        return withContext(Dispatchers.Default) { build(data, bike, z) }
    }

    /** Points de coupure trouvés : (segment, point entier). */
    private class Cuts { val seg = Ints(1 shl 14); val pt = Longs(1 shl 14)
        fun add(i: Int, x: Double, y: Double) { seg.add(i); pt.add((x.roundToLong() shl 32) or (y.roundToLong() and 0xFFFFFFFFL)) }
    }

    private suspend fun build(data: List<Triple<Int, Int, ByteArray>>, bike: Boolean, z: Int): RawNetwork = coroutineScope {
        val segs = Segs(); val levels = HashMap<String, Int>()
        for ((x, y, bytes) in data) readTile(x, y, bytes, bike, segs, levels)
        val n = segs.size
        if (n == 0) throw IOException("empty")

        // 1. Grille : une entrée (cellule, segment) par cellule touchée, triée → segments regroupés par cellule
        var minX = Int.MAX_VALUE; var minY = Int.MAX_VALUE; var maxX = Int.MIN_VALUE; var maxY = Int.MIN_VALUE
        for (i in 0 until n) {
            minX = minOf(minX, segs.ax[i], segs.bx[i]); maxX = maxOf(maxX, segs.ax[i], segs.bx[i])
            minY = minOf(minY, segs.ay[i], segs.by[i]); maxY = maxOf(maxY, segs.ay[i], segs.by[i])
        }
        val ox = minX - 8; val oy = minY - 8
        val cols = (maxX + 8 - ox) / CELL + 1
        fun cx(v: Double) = floor((v - ox) / CELL).toInt()
        fun cy(v: Double) = floor((v - oy) / CELL).toInt()
        val entries = Longs(n * 2)
        for (i in 0 until n) {
            val x0 = cx(minOf(segs.ax[i], segs.bx[i]) - SNAP); val x1 = cx(maxOf(segs.ax[i], segs.bx[i]) + SNAP)
            val y0 = cy(minOf(segs.ay[i], segs.by[i]) - SNAP); val y1 = cy(maxOf(segs.ay[i], segs.by[i]) + SNAP)
            for (gx in x0..x1) for (gy in y0..y1) entries.add(((gy.toLong() * cols + gx) shl 24) or i.toLong())
        }
        java.util.Arrays.sort(entries.a, 0, entries.size)

        // 2. Coupures : croisements au même niveau, et extrémités posées sur une autre voie.
        //    Chaque événement n'est compté que dans la cellule qui contient son point (pas de doublon).
        // Paquets de cellules entières répartis sur les cœurs ; chaque paquet a ses propres coupures, réunies ensuite
        val chunks = (Runtime.getRuntime().availableProcessors() * 4).coerceAtLeast(1)
        val bounds = ArrayList<Int>().apply {
            add(0)
            for (c in 1 until chunks) {
                var b = (entries.size.toLong() * c / chunks).toInt().coerceAtLeast(last())
                while (b in 1 until entries.size && entries.a[b] ushr 24 == entries.a[b - 1] ushr 24) b++
                if (b > last() && b < entries.size) add(b)
            }
            add(entries.size)
        }
        val partCuts = (0 until bounds.size - 1).map { c -> async(Dispatchers.Default) {
        val cuts = Cuts()
        var start = bounds[c]
        while (start < bounds[c + 1]) {
            val cellId = entries.a[start] ushr 24
            var end = start
            while (end < entries.size && entries.a[end] ushr 24 == cellId) end++
            val gcx = (cellId % cols).toInt(); val gcy = (cellId / cols).toInt()
            for (p in start until end) {
                val i = (entries.a[p] and 0xFFFFFF).toInt()
                val ax = segs.ax[i].toDouble(); val ay = segs.ay[i].toDouble()
                val bx = segs.bx[i].toDouble(); val by = segs.by[i].toDouble()
                val rx = bx - ax; val ry = by - ay
                val iMinX = minOf(ax, bx) - SNAP; val iMaxX = maxOf(ax, bx) + SNAP
                val iMinY = minOf(ay, by) - SNAP; val iMaxY = maxOf(ay, by) + SNAP
                for (q in p + 1 until end) {
                    val j = (entries.a[q] and 0xFFFFFF).toInt()
                    if (segs.lvl[i] != segs.lvl[j]) continue
                    val cxj = segs.ax[j].toDouble(); val cyj = segs.ay[j].toDouble()
                    val dxj = segs.bx[j].toDouble(); val dyj = segs.by[j].toDouble()
                    // Rejet rapide : boîtes qui ne se touchent pas
                    if (maxOf(cxj, dxj) < iMinX || minOf(cxj, dxj) > iMaxX || maxOf(cyj, dyj) < iMinY || minOf(cyj, dyj) > iMaxY) continue
                    // Déjà reliés par un point commun : rien à faire
                    if ((ax == cxj && ay == cyj) || (ax == dxj && ay == dyj) || (bx == cxj && by == cyj) || (bx == dxj && by == dyj)) continue
                    val sx = dxj - cxj; val sy = dyj - cyj
                    val den = rx * sy - ry * sx
                    if (den != 0.0) {
                        val tt = ((cxj - ax) * sy - (cyj - ay) * sx) / den
                        val uu = ((cxj - ax) * ry - (cyj - ay) * rx) / den
                        val ei = SNAP / hypot(rx, ry); val ej = SNAP / hypot(sx, sy)
                        if (tt >= -ei && tt <= 1 + ei && uu >= -ej && uu <= 1 + ej) {
                            val tc = tt.coerceIn(0.0, 1.0)
                            val px = ax + tc * rx; val py = ay + tc * ry
                            if (cx(px) == gcx && cy(py) == gcy) {
                                cuts.add(i, px, py); cuts.add(j, px, py)
                                val uc = uu.coerceIn(0.0, 1.0)
                                val qx = cxj + uc * sx; val qy = cyj + uc * sy
                                if (hypot(qx - px, qy - py) > 0.5) { cuts.add(i, qx, qy); cuts.add(j, qx, qy) }
                            }
                            continue
                        }
                    }
                    // Voies presque parallèles qui se chevauchent (bords de tuiles) : extrémité posée sur l'autre voie
                    onto(ax, ay, cxj, cyj, sx, sy, j, i, gcx, gcy, ox, oy, cuts)
                    onto(bx, by, cxj, cyj, sx, sy, j, i, gcx, gcy, ox, oy, cuts)
                    onto(cxj, cyj, ax, ay, rx, ry, i, j, gcx, gcy, ox, oy, cuts)
                    onto(dxj, dyj, ax, ay, rx, ry, i, j, gcx, gcy, ox, oy, cuts)
                }
            }
            start = end
        }
        cuts
        } }.awaitAll()
        val cuts = Cuts()
        for (pc in partCuts) for (k in 0 until pc.seg.size) { cuts.seg.add(pc.seg.a[k]); cuts.pt.add(pc.pt.a[k]) }

        // 3. Coupures regroupées par segment (tri par comptage)
        val cs = cuts.seg; val cp = cuts.pt
        val cutStart = IntArray(n + 1)
        for (k in 0 until cs.size) cutStart[cs.a[k] + 1]++
        for (i in 0 until n) cutStart[i + 1] += cutStart[i]
        val fill = cutStart.copyOf(n)
        val sortedPts = LongArray(cs.size)
        for (k in 0 until cs.size) sortedPts[fill[cs.a[k]]++] = cp.a[k]

        // 4. Nœuds (points entiers) et arêtes, segment par segment, points dans l'ordre le long du segment
        val index = LongIntMap(n)
        var lat = DoubleArray(1 shl 14); var lng = DoubleArray(1 shl 14); var count = 0
        val world = 4096.0 * (1 shl z)
        fun node(key: Long): Int {
            val known = index.get(key)
            if (known >= 0) return known
            if (count == lat.size) { lat = lat.copyOf(count * 2); lng = lng.copyOf(count * 2) }
            val x = (key shr 32).toDouble(); val y = key.toInt().toDouble()
            lng[count] = x / world * 360.0 - 180.0
            lat[count] = Math.toDegrees(atan(sinh(Math.PI * (1 - 2 * y / world))))
            index.put(key, count)
            return count++
        }
        val ea = Ints(n * 2); val eb = Ints(n * 2); var ef = DoubleArray(n * 2)
        var pts = LongArray(16); var par = DoubleArray(16)
        for (i in 0 until n) {
            val m = cutStart[i + 1] - cutStart[i] + 2
            if (m > pts.size) { pts = LongArray(m * 2); par = DoubleArray(m * 2) }
            val ax = segs.ax[i].toDouble(); val ay = segs.ay[i].toDouble()
            val rx = segs.bx[i] - ax; val ry = segs.by[i] - ay
            val l2 = (rx * rx + ry * ry).coerceAtLeast(1e-9)
            pts[0] = pack(segs.ax[i].toLong(), segs.ay[i].toLong()); par[0] = -1.0
            var k = 1
            for (c in cutStart[i] until cutStart[i + 1]) {
                val p = sortedPts[c]
                val t = (((p shr 32).toDouble() - ax) * rx + (p.toInt().toDouble() - ay) * ry) / l2
                // insertion triée (peu de points par segment)
                var h = k
                while (h > 1 && par[h - 1] > t) { pts[h] = pts[h - 1]; par[h] = par[h - 1]; h-- }
                pts[h] = p; par[h] = t; k++
            }
            pts[k] = pack(segs.bx[i].toLong(), segs.by[i].toLong()); k++
            var prev = -1
            for (h in 0 until k) {
                val v = node(pts[h])
                if (prev != -1 && prev != v) {
                    if (ea.size == ef.size) ef = ef.copyOf(ef.size * 2)
                    ef[ea.size] = segs.w[i]; ea.add(prev); eb.add(v)
                }
                prev = v
            }
        }
        RawNetwork(lat.copyOf(count), lng.copyOf(count), ea.a, eb.a, ef, ea.size)
    }

    /** Extrémité (ex, ey) posée (à moins de SNAP) sur le segment (ox, oy)+(dx, dy) : on coupe les deux là. */
    private fun onto(ex: Double, ey: Double, sx: Double, sy: Double, dx: Double, dy: Double, target: Int, self: Int,
                     gcx: Int, gcy: Int, ox: Int, oy: Int, cuts: Cuts) {
        if (floor((ex - ox) / CELL).toInt() != gcx || floor((ey - oy) / CELL).toInt() != gcy) return
        val l2 = dx * dx + dy * dy
        if (l2 == 0.0) return
        val t = (((ex - sx) * dx + (ey - sy) * dy) / l2).coerceIn(0.0, 1.0)
        val fx = sx + t * dx; val fy = sy + t * dy
        if (hypot(ex - fx, ey - fy) <= SNAP) { cuts.add(target, fx, fy); cuts.add(self, fx, fy) }
    }
}
