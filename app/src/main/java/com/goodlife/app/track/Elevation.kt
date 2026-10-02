package com.goodlife.app.track

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.goodlife.app.data.Repo
import com.goodlife.app.data.TrackPoint
import com.goodlife.app.net.USER_AGENT
import com.goodlife.app.net.readCapped
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

/**
 * Altitude du terrain, pour le dénivelé des itinéraires : tuiles de relief ouvertes « Terrain Tiles » (Mapzen, données
 * SRTM, EU-DEM… hébergées en accès libre par Amazon), zoom 12 (un point tous les 25 m environ en France). Le serveur ne
 * reçoit que les numéros de tuiles de la zone concernée. Les tuiles sont gardées en cache sur le téléphone.
 */
object Elevation {
    private const val URL_TEMPLATE = "https://s3.amazonaws.com/elevation-tiles-prod/terrarium/12/%d/%d.png"
    private const val Z = 12
    private const val CACHE_DAYS = 60L

    /** Une tuile décodée : 256 × 256 altitudes (m). */
    private class Tile(val h: FloatArray)

    private val memory = object : LinkedHashMap<Long, Tile>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Tile>?) = size > 24
    }

    private fun key(x: Int, y: Int) = (x.toLong() shl 32) or (y.toLong() and 0xFFFFFFFFL)
    private fun tx(lng: Double) = (lng + 180.0) / 360.0 * (1 shl Z)
    private fun ty(lat: Double): Double { val r = Math.toRadians(lat); return (1.0 - ln(tan(r) + 1.0 / cos(r)) / Math.PI) / 2.0 * (1 shl Z) }

    private fun dir(): File? = Repo.appContext?.cacheDir?.let { File(it, "elev_tiles") }?.also { it.mkdirs() }

    private fun prune(d: File) {
        val limit = System.currentTimeMillis() - CACHE_DAYS * 86_400_000L
        val files = d.listFiles().orEmpty()
        files.filter { it.lastModified() < limit }.forEach { it.delete() }
        var total = 0L
        files.filter { it.exists() }.sortedByDescending { it.lastModified() }.forEach { f -> total += f.length(); if (total > 20L * 1024 * 1024) f.delete() }
    }

    private fun download(x: Int, y: Int): ByteArray {
        val conn = (URL(String.format(java.util.Locale.US, URL_TEMPLATE, x, y)).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000; readTimeout = 20_000; instanceFollowRedirects = false
            setRequestProperty("User-Agent", USER_AGENT)
        }
        try {
            if (conn.responseCode !in 200..299) throw IOException("HTTP ${conn.responseCode}")
            return conn.inputStream.use { it.readCapped(2 * 1024 * 1024) }
        } finally { conn.disconnect() }
    }

    /** Hors ligne : enregistre une tuile d'altitude (zoom 12) dans le dossier d'une zone (rien si elle y est déjà). */
    fun saveOffline(dir: File, x: Int, y: Int) {
        val f = File(dir, OfflineData.elevName(x, y))
        if (f.exists()) return
        var last: IOException? = null
        for (attempt in 0 until 2) {
            try {
                val tmp = File(dir, f.name + ".tmp")
                tmp.writeBytes(download(x, y))
                if (!tmp.renameTo(f)) { tmp.delete(); throw IOException("write") }
                return
            } catch (e: IOException) { last = e }
        }
        throw last ?: IOException("elevation")
    }

    /** Terrarium : altitude = R × 256 + G + B / 256 − 32 768. */
    private fun decode(png: ByteArray): Tile? {
        val bmp = BitmapFactory.decodeByteArray(png, 0, png.size, BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }) ?: return null
        if (bmp.width != 256 || bmp.height != 256) { bmp.recycle(); return null }
        val px = IntArray(256 * 256)
        bmp.getPixels(px, 0, 256, 0, 0, 256, 256)
        bmp.recycle()
        return Tile(FloatArray(px.size) { i ->
            val c = px[i]
            (((c shr 16) and 0xFF) * 256f + ((c shr 8) and 0xFF) + (c and 0xFF) / 256f) - 32768f
        })
    }

    private suspend fun tiles(keys: Set<Long>): Map<Long, Tile> = withContext(Dispatchers.IO) {
        val d = dir() ?: throw IOException("cache")
        prune(d)
        val gate = Semaphore(4)
        coroutineScope {
            keys.map { k ->
                async {
                    synchronized(memory) { memory[k] }?.let { return@async k to it }
                    gate.withPermit {
                        val x = (k shr 32).toInt(); val y = k.toInt()
                        val f = OfflineData.elevation(x, y) ?: File(d, "t12_${x}_$y.png")
                        val bytes = if (f.exists()) f.readBytes() else download(x, y).also { b -> runCatching { f.writeBytes(b) } }
                        decode(bytes)?.let { t -> synchronized(memory) { memory[k] = t }; k to t }
                    }
                }
            }.awaitAll().filterNotNull().toMap()
        }
    }

    /** Altitude (m) de chaque point, ou null si les données ne sont pas disponibles (hors ligne…). Au plus 90 tuiles. */
    suspend fun of(lat: DoubleArray, lng: DoubleArray): FloatArray? {
        if (lat.isEmpty()) return FloatArray(0)
        val keys = HashSet<Long>()
        for (i in lat.indices) {
            // Les 4 pixels de l'interpolation (ils peuvent tomber sur la tuile voisine, en bord de tuile)
            val x0 = floor(tx(lng[i]) * 256.0 - 0.5).toLong(); val y0 = floor(ty(lat[i]) * 256.0 - 0.5).toLong()
            for (dx in 0..1) for (dy in 0..1) keys += key(((x0 + dx) shr 8).toInt(), ((y0 + dy) shr 8).toInt())
            if (keys.size > 90) return null
        }
        val t = runCatching { tiles(keys) }.getOrNull() ?: return null
        if (t.size < keys.size) return null
        return withContext(Dispatchers.Default) {
            FloatArray(lat.size) { i -> sample(t, tx(lng[i]) * 256.0 - 0.5, ty(lat[i]) * 256.0 - 0.5) }
        }
    }

    /** Interpolation bilinéaire entre les 4 pixels voisins (coordonnées en pixels du monde au zoom 12). */
    private fun sample(t: Map<Long, Tile>, px: Double, py: Double): Float {
        val x0 = floor(px).toLong(); val y0 = floor(py).toLong()
        val fx = (px - x0).toFloat(); val fy = (py - y0).toFloat()
        fun at(x: Long, y: Long): Float {
            val tile = t[key((x shr 8).toInt(), (y shr 8).toInt())] ?: return Float.NaN
            return tile.h[((y and 255) * 256 + (x and 255)).toInt()]
        }
        val a = at(x0, y0); val b = at(x0 + 1, y0); val c = at(x0, y0 + 1); val d = at(x0 + 1, y0 + 1)
        if (a.isNaN() || b.isNaN() || c.isNaN() || d.isNaN()) return listOf(a, b, c, d).firstOrNull { !it.isNaN() } ?: 0f
        return (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy
    }

    /**
     * Profil d'un itinéraire : les points avec leur altitude, et le dénivelé positif / négatif. Les variations de moins
     * de 3 m ne comptent pas (bruit des données).
     */
    suspend fun annotate(points: List<TrackPoint>): Triple<List<TrackPoint>, Double, Double>? {
        if (points.size < 2) return null
        val z = of(DoubleArray(points.size) { points[it].lat }, DoubleArray(points.size) { points[it].lng }) ?: return null
        var gain = 0.0; var loss = 0.0; var ref = z[0].toDouble()
        for (v in z) {
            val d = v - ref
            if (d > 3.0) { gain += d; ref = v.toDouble() } else if (d < -3.0) { loss -= d; ref = v.toDouble() }
        }
        return Triple(points.mapIndexed { i, p -> p.copy(alt = z[i].toDouble()) }, gain, loss)
    }
}
