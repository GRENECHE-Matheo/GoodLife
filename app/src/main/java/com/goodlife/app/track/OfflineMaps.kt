package com.goodlife.app.track

import com.goodlife.app.i18n.t

import android.content.Context
import android.os.StatFs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.maplibre.android.MapLibre
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.math.tan

/** Qualité d'une zone hors ligne. */
enum class OfflineQuality(val mapMaxZoom: Int, val extras: Boolean) {
    /** Carte détaillée (chemins, bâtiments), plus les rues et l'altitude : itinéraires et dénivelé sans réseau. */
    FULL(14, true),
    /** Carte simplifiée (villes, grandes routes) pour se repérer ; pas d'itinéraire sans réseau. */
    LIGHT(12, false)
}

/** Zone de carte téléchargée (ou en cours). */
data class OfflineZone(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val quality: OfflineQuality,
    val sizeBytes: Long,
    val progress: Float,     // 0..1, fond de carte
    val mapComplete: Boolean,
    val region: OfflineRegion
)

/** Avancement des rues et de l'altitude d'une zone « Complète ». */
data class ExtrasProgress(val done: Int, val total: Int, val failed: Int, val running: Boolean, val complete: Boolean, val bytes: Long)

/** Place estimée d'une zone : mesurée sur quelques tuiles de la zone elle-même. */
data class OfflineEstimate(val bytes: Long, val mapTiles: Long, val extraFiles: Long)

/**
 * Cartes hors ligne. Le fond de carte est téléchargé par MapLibre (OpenFreeMap) ; en qualité « Complète », l'app
 * enregistre aussi les rues de la zone (pour calculer des itinéraires sans réseau) et l'altitude (pour le dénivelé).
 * Pas de limite de taille : seulement la place libre du téléphone.
 */
object OfflineMaps {
    const val STYLE = "https://tiles.openfreemap.org/styles/bright"
    private const val MIN_ZOOM = 6
    /** Style, polices et icônes de la carte (mesuré : quelques Mo, une seule fois par zone). */
    private const val STYLE_OVERHEAD = 4_000_000L
    /** Part des rues dans une tuile (mesurée sur des tuiles de Paris, de Normandie et des Alpes). */
    private const val ROADS_SHARE_14 = 0.16
    private const val ROADS_SHARE_12 = 0.4
    /** Tuile d'altitude au zoom 12 : 89 Ko en plaine, 134 Ko en montagne (mesuré). */
    private const val ELEVATION_TILE = 110_000L

    private val _zones = MutableStateFlow<List<OfflineZone>>(emptyList())
    val zones: StateFlow<List<OfflineZone>> = _zones

    private val _extras = MutableStateFlow<Map<Long, ExtrasProgress>>(emptyMap())
    val extras: StateFlow<Map<Long, ExtrasProgress>> = _extras

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<Long, Job>()

    private fun manager(context: Context): OfflineManager {
        MapLibre.getInstance(context)
        // Plus de limite du nombre de tuiles : c'est la place libre du téléphone qui décide
        return OfflineManager.getInstance(context).also { it.setOfflineMapboxTileCountLimit(Long.MAX_VALUE) }
    }

    // ---------- Tuiles d'une zone ----------

    private fun tileX(lng: Double, z: Int) = floor((lng + 180) / 360 * 2.0.pow(z)).toLong().coerceIn(0L, (1L shl z) - 1)
    private fun tileY(lat: Double, z: Int): Long {
        val r = Math.toRadians(lat.coerceIn(-85.0, 85.0))
        return floor((1 - ln(tan(r) + 1 / kotlin.math.cos(r)) / PI) / 2 * 2.0.pow(z)).toLong().coerceIn(0L, (1L shl z) - 1)
    }
    private fun xRange(b: LatLngBounds, z: Int) = tileX(b.longitudeWest, z)..tileX(b.longitudeEast, z)
    private fun yRange(b: LatLngBounds, z: Int) = tileY(b.latitudeNorth, z)..tileY(b.latitudeSouth, z)

    fun tileCount(b: LatLngBounds, z: Int): Long = (xRange(b, z).last - xRange(b, z).first + 1) * (yRange(b, z).last - yRange(b, z).first + 1)

    private fun tiles(b: LatLngBounds, z: Int): Sequence<Pair<Int, Int>> = sequence {
        for (x in xRange(b, z)) for (y in yRange(b, z)) yield(x.toInt() to y.toInt())
    }

    /** Place libre sur le téléphone (octets). */
    fun freeBytes(context: Context): Long = runCatching { StatFs(context.filesDir.absolutePath).availableBytes }.getOrDefault(-1L)

    // ---------- Estimation ----------

    private val sizes = ConcurrentHashMap<Long, Long>()

    /** Taille moyenne réelle d'une tuile de la zone à ce zoom : `n` tuiles réparties dans la zone, sans les télécharger. */
    private suspend fun sampleAverage(b: LatLngBounds, z: Int, n: Int): Double? = coroutineScope {
        val xs = xRange(b, z); val ys = yRange(b, z)
        val nx = xs.last - xs.first + 1; val ny = ys.last - ys.first + 1
        // Grille régulière (toujours les mêmes tuiles pour une même zone : l'estimation ne saute pas)
        val side = sqrt(n.toDouble()).toInt().coerceAtLeast(1)
        val picks = (0 until side).flatMap { i -> (0 until side).map { j ->
            (xs.first + (nx * (2 * i + 1)) / (2 * side)).toInt() to (ys.first + (ny * (2 * j + 1)) / (2 * side)).toInt()
        } }.distinct()
        val gate = Semaphore(6)
        val got = picks.map { (x, y) ->
            async {
                gate.withPermit {
                    val key = (z.toLong() shl 58) or (x.toLong() shl 29) or y.toLong()
                    sizes[key] ?: RoadTiles.tileBytes(z, x, y).also { if (it >= 0) sizes[key] = it }
                }
            }
        }.awaitAll().filter { it >= 0 }
        if (got.isEmpty()) null else got.average()
    }

    /** Place estimée (null sans réseau) : tailles réelles de tuiles de la zone, multipliées par leur nombre. */
    suspend fun estimate(b: LatLngBounds, q: OfflineQuality): OfflineEstimate? = withContext(Dispatchers.IO) {
        val top = q.mapMaxZoom
        val avgTop = sampleAverage(b, top, 16) ?: return@withContext null
        val avg12 = if (top == 12) avgTop else sampleAverage(b, 12, 9) ?: return@withContext null
        var bytes = STYLE_OVERHEAD.toDouble()
        var count = 0L
        for (z in MIN_ZOOM..top) {
            val c = tileCount(b, z)
            count += c
            val avg = when {
                z == top -> avgTop
                z == 12 -> avg12
                z > 12 -> sqrt(avgTop * avg12)              // entre les deux zooms mesurés
                // Tuiles moins nombreuses mais plus chargées (mesuré : 1 à 4 fois une tuile du zoom 12), plafonnées
                else -> minOf(avg12 * 2.0.pow(minOf(12 - z, 2)), 400_000.0)
            }
            bytes += c * avg
        }
        var extra = 0L
        if (q.extras) {
            val c14 = tileCount(b, RoadTiles.DETAIL); val c12 = tileCount(b, RoadTiles.LONG)
            extra = c14 + 2 * c12
            bytes += c14 * avgTop * ROADS_SHARE_14 + c12 * avg12 * ROADS_SHARE_12 + c12 * ELEVATION_TILE
        }
        OfflineEstimate(bytes.toLong(), count, extra)
    }

    // ---------- Zones ----------

    private fun meta(r: OfflineRegion) = runCatching { JSONObject(String(r.metadata, Charsets.UTF_8)) }.getOrNull()
    private fun qualityOf(m: JSONObject?) = runCatching { OfflineQuality.valueOf(m?.optString("quality").orEmpty()) }.getOrDefault(OfflineQuality.LIGHT)
    private fun boundsOf(m: JSONObject?): LatLngBounds? = m?.takeIf { it.has("s") }?.let {
        LatLngBounds.Builder().include(LatLng(it.getDouble("s"), it.getDouble("w"))).include(LatLng(it.getDouble("n"), it.getDouble("e"))).build()
    }

    fun refresh(context: Context) {
        manager(context).listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(offlineRegions: Array<OfflineRegion>?) {
                val regions = offlineRegions ?: emptyArray()
                _zones.value = regions.map { r -> zoneOf(r, null) }
                regions.forEach { r ->
                    r.getStatus(object : OfflineRegion.OfflineRegionStatusCallback {
                        override fun onStatus(status: OfflineRegionStatus?) {
                            status ?: return
                            update(r, status)
                            // Téléchargement interrompu (app fermée, réseau coupé) : il reprend là où il s'était arrêté
                            if (!status.isComplete) { observe(r); r.setDownloadState(OfflineRegion.STATE_ACTIVE) }
                        }
                        override fun onError(error: String?) = Unit
                    })
                    resumeExtras(r)
                }
            }
            override fun onError(error: String) = Unit
        })
    }

    private fun zoneOf(r: OfflineRegion, status: OfflineRegionStatus?): OfflineZone {
        val m = meta(r)
        return OfflineZone(
            id = r.id,
            name = m?.optString("name")?.ifBlank { null } ?: t("Zone"),
            createdAt = m?.optLong("createdAt") ?: 0L,
            quality = qualityOf(m),
            sizeBytes = status?.completedResourceSize ?: 0L,
            progress = status?.let { if (it.requiredResourceCount > 0) it.completedResourceCount.toFloat() / it.requiredResourceCount else 0f } ?: 0f,
            mapComplete = status?.isComplete ?: false,
            region = r
        )
    }

    private fun update(r: OfflineRegion, status: OfflineRegionStatus) {
        val z = zoneOf(r, status)
        _zones.value = _zones.value.filterNot { it.id == r.id } + z
    }

    private fun observe(r: OfflineRegion) {
        r.setObserver(object : OfflineRegion.OfflineRegionObserver {
            override fun onStatusChanged(status: OfflineRegionStatus) {
                update(r, status)
                if (status.isComplete) r.setDownloadState(OfflineRegion.STATE_INACTIVE)
            }
            override fun onError(error: OfflineRegionError) = Unit   // erreurs passagères : MapLibre réessaie
            override fun mapboxTileCountLimitExceeded(limit: Long) = Unit
        })
    }

    /** Télécharge la zone (et, en qualité « Complète », ses rues et son altitude). */
    fun download(context: Context, name: String, bounds: LatLngBounds, quality: OfflineQuality, onError: (String) -> Unit) {
        val definition = OfflineTilePyramidRegionDefinition(
            STYLE, bounds, MIN_ZOOM.toDouble(), quality.mapMaxZoom.toDouble(), context.resources.displayMetrics.density
        )
        val meta = JSONObject().put("name", name.take(40)).put("createdAt", System.currentTimeMillis()).put("quality", quality.name)
            .put("s", bounds.latitudeSouth).put("w", bounds.longitudeWest).put("n", bounds.latitudeNorth).put("e", bounds.longitudeEast)
            .toString().toByteArray(Charsets.UTF_8)
        manager(context).createOfflineRegion(definition, meta, object : OfflineManager.CreateOfflineRegionCallback {
            override fun onCreate(offlineRegion: OfflineRegion) {
                _zones.value = _zones.value + zoneOf(offlineRegion, null)
                observe(offlineRegion)
                offlineRegion.setDownloadState(OfflineRegion.STATE_ACTIVE)
                if (quality.extras) startExtras(offlineRegion.id, bounds)
            }
            override fun onError(error: String) = onError(t("Téléchargement impossible : %1\$s", error))
        })
    }

    private fun resumeExtras(r: OfflineRegion) {
        val m = meta(r)
        if (!qualityOf(m).extras) return
        val dir = OfflineData.zoneDir(r.id) ?: return
        if (File(dir, "done").exists()) {
            if (_extras.value[r.id] == null) _extras.value = _extras.value + (r.id to ExtrasProgress(1, 1, 0, false, true, dirBytes(dir)))
            return
        }
        boundsOf(m)?.let { startExtras(r.id, it) }
    }

    private fun dirBytes(dir: File) = dir.listFiles()?.sumOf { it.length() } ?: 0L

    /** Rues (zooms 14 et 12) et altitude de la zone, enregistrées une par une ; reprend sans refaire ce qui est fait. */
    private fun startExtras(id: Long, b: LatLngBounds) {
        if (jobs[id]?.isActive == true) return
        val dir = OfflineData.zoneDir(id) ?: return
        dir.mkdirs(); OfflineData.invalidate()
        jobs[id] = scope.launch {
            val c14 = tileCount(b, RoadTiles.DETAIL).toInt(); val c12 = tileCount(b, RoadTiles.LONG).toInt()
            val total = c14 + 2 * c12
            val done = AtomicInteger(); val failed = AtomicInteger()
            fun publish(running: Boolean) {
                val complete = !running && failed.get() == 0
                _extras.value = _extras.value + (id to ExtrasProgress(done.get(), total, failed.get(), running, complete, dirBytes(dir)))
            }
            publish(true)
            val tasks: Sequence<() -> Unit> =
                tiles(b, RoadTiles.DETAIL).map { (x, y) -> { RoadTiles.saveOffline(dir, RoadTiles.DETAIL, x, y) } } +
                tiles(b, RoadTiles.LONG).map { (x, y) -> { RoadTiles.saveOffline(dir, RoadTiles.LONG, x, y) } } +
                tiles(b, 12).map { (x, y) -> { Elevation.saveOffline(dir, x, y) } }
            val gate = Semaphore(4)
            // Par paquets : une zone immense ne lance pas des centaines de milliers de tâches d'un coup
            for (chunk in tasks.chunked(64)) {
                if (!dir.exists()) return@launch          // zone supprimée entre-temps
                coroutineScope {
                    chunk.map { task ->
                        async { gate.withPermit { try { task() } catch (e: IOException) { failed.incrementAndGet() }; done.incrementAndGet() } }
                    }.awaitAll()
                }
                publish(true)
            }
            if (failed.get() == 0 && dir.exists()) runCatching { File(dir, "done").writeText("1") }
            publish(false)
        }
    }

    /** Reprend une zone incomplète (après une coupure de réseau). */
    fun retry(zone: OfflineZone) {
        observe(zone.region)
        zone.region.setDownloadState(OfflineRegion.STATE_ACTIVE)
        if (zone.quality.extras) boundsOf(meta(zone.region))?.let { startExtras(zone.id, it) }
    }

    /** Effacer mes données : toutes les zones hors ligne. */
    fun deleteAll(context: Context) {
        jobs.values.forEach { it.cancel() }; jobs.clear()
        OfflineData.deleteAll()
        _extras.value = emptyMap()
        // MapLibre se pilote depuis le fil principal
        android.os.Handler(android.os.Looper.getMainLooper()).post { deleteRegions(context) }
    }

    private fun deleteRegions(context: Context) {
        manager(context).listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(offlineRegions: Array<OfflineRegion>?) {
                offlineRegions?.forEach { r ->
                    r.setDownloadState(OfflineRegion.STATE_INACTIVE)
                    r.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
                        override fun onDelete() = Unit
                        override fun onError(error: String) = Unit
                    })
                }
                _zones.value = emptyList()
            }
            override fun onError(error: String) = Unit
        })
    }

    fun delete(zone: OfflineZone) {
        jobs.remove(zone.id)?.cancel()
        zone.region.setDownloadState(OfflineRegion.STATE_INACTIVE)
        OfflineData.delete(zone.id)
        _extras.value = _extras.value - zone.id
        zone.region.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
            override fun onDelete() { _zones.value = _zones.value.filterNot { it.id == zone.id } }
            override fun onError(error: String) = Unit
        })
    }
}
