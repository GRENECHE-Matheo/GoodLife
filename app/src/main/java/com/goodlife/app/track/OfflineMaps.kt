package com.goodlife.app.track

import com.goodlife.app.i18n.t

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import org.maplibre.android.MapLibre
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.offline.OfflineManager
import org.maplibre.android.offline.OfflineRegion
import org.maplibre.android.offline.OfflineRegionError
import org.maplibre.android.offline.OfflineRegionStatus
import org.maplibre.android.offline.OfflineTilePyramidRegionDefinition
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.tan

/** Zone de carte téléchargée (ou en cours). */
data class OfflineZone(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val sizeBytes: Long,
    val progress: Float,     // 0..1
    val complete: Boolean,
    val region: OfflineRegion
)

/**
 * Cartes hors ligne : on télécharge seulement la zone choisie (et jusqu'au niveau de détail choisi), pour ne pas
 * remplir le téléphone. Le GPS fonctionne sans internet ; la zone téléchargée sert de fond de carte sans réseau.
 */
object OfflineMaps {
    const val STYLE = "https://tiles.openfreemap.org/styles/bright"
    private const val MIN_ZOOM = 10.0
    /** Taille moyenne observée d'une tuile vectorielle (octets) : sert seulement à l'estimation affichée. */
    private const val AVG_TILE = 28_000L
    private const val STYLE_OVERHEAD = 4_000_000L
    const val MAX_TILES = 12_000L

    private val _zones = MutableStateFlow<List<OfflineZone>>(emptyList())
    val zones: StateFlow<List<OfflineZone>> = _zones

    private fun manager(context: Context): OfflineManager {
        MapLibre.getInstance(context)
        return OfflineManager.getInstance(context).also { it.setOfflineMapboxTileCountLimit(MAX_TILES) }
    }

    private fun tileX(lng: Double, z: Int) = floor((lng + 180) / 360 * 2.0.pow(z)).toLong()
    private fun tileY(lat: Double, z: Int): Long {
        val r = Math.toRadians(lat)
        return floor((1 - ln(tan(r) + 1 / kotlin.math.cos(r)) / PI) / 2 * 2.0.pow(z)).toLong()
    }

    /** Nombre de tuiles pour une zone et un zoom maximal. */
    fun tileCount(bounds: LatLngBounds, maxZoom: Int): Long = (MIN_ZOOM.toInt()..maxZoom).sumOf { z ->
        val x = kotlin.math.abs(tileX(bounds.longitudeEast, z) - tileX(bounds.longitudeWest, z)) + 1
        val y = kotlin.math.abs(tileY(bounds.latitudeSouth, z) - tileY(bounds.latitudeNorth, z)) + 1
        x * y
    }

    /** Taille estimée (octets) avant téléchargement. */
    fun estimateBytes(bounds: LatLngBounds, maxZoom: Int): Long = tileCount(bounds, maxZoom) * AVG_TILE + STYLE_OVERHEAD

    fun refresh(context: Context) {
        manager(context).listOfflineRegions(object : OfflineManager.ListOfflineRegionsCallback {
            override fun onList(offlineRegions: Array<OfflineRegion>?) {
                val regions = offlineRegions ?: emptyArray()
                _zones.value = regions.map { r -> zoneOf(r, null) }
                regions.forEach { r ->
                    r.getStatus(object : OfflineRegion.OfflineRegionStatusCallback {
                        override fun onStatus(status: OfflineRegionStatus?) { status?.let { update(r, it) } }
                        override fun onError(error: String?) = Unit
                    })
                }
            }
            override fun onError(error: String) = Unit
        })
    }

    private fun zoneOf(r: OfflineRegion, status: OfflineRegionStatus?): OfflineZone {
        val meta = runCatching { JSONObject(String(r.metadata, Charsets.UTF_8)) }.getOrNull()
        return OfflineZone(
            id = r.id,
            name = meta?.optString("name")?.ifBlank { null } ?: t("Zone"),
            createdAt = meta?.optLong("createdAt") ?: 0L,
            sizeBytes = status?.completedResourceSize ?: 0L,
            progress = status?.let { if (it.requiredResourceCount > 0) it.completedResourceCount.toFloat() / it.requiredResourceCount else 0f } ?: 0f,
            complete = status?.isComplete ?: false,
            region = r
        )
    }

    private fun update(r: OfflineRegion, status: OfflineRegionStatus) {
        val z = zoneOf(r, status)
        _zones.value = _zones.value.filterNot { it.id == r.id } + z
    }

    /** Télécharge la zone : garde l'app ouverte pendant le téléchargement. */
    fun download(context: Context, name: String, bounds: LatLngBounds, maxZoom: Int, onError: (String) -> Unit) {
        val definition = OfflineTilePyramidRegionDefinition(
            STYLE, bounds, MIN_ZOOM, maxZoom.toDouble(), context.resources.displayMetrics.density
        )
        val meta = JSONObject().put("name", name.take(40)).put("createdAt", System.currentTimeMillis())
            .toString().toByteArray(Charsets.UTF_8)
        manager(context).createOfflineRegion(definition, meta, object : OfflineManager.CreateOfflineRegionCallback {
            override fun onCreate(offlineRegion: OfflineRegion) {
                _zones.value = _zones.value + zoneOf(offlineRegion, null)
                offlineRegion.setObserver(object : OfflineRegion.OfflineRegionObserver {
                    override fun onStatusChanged(status: OfflineRegionStatus) {
                        update(offlineRegion, status)
                        if (status.isComplete) offlineRegion.setDownloadState(OfflineRegion.STATE_INACTIVE)
                    }
                    override fun onError(error: OfflineRegionError) = Unit   // erreurs passagères : MapLibre réessaie
                    override fun mapboxTileCountLimitExceeded(limit: Long) {
                        offlineRegion.setDownloadState(OfflineRegion.STATE_INACTIVE)
                        onError(t("Zone trop grande (%1\$s tuiles maximum) : choisis une zone plus petite ou moins détaillée.", limit))
                    }
                })
                offlineRegion.setDownloadState(OfflineRegion.STATE_ACTIVE)
            }
            override fun onError(error: String) = onError(t("Téléchargement impossible : %1\$s", error))
        })
    }

    fun delete(zone: OfflineZone) {
        zone.region.setDownloadState(OfflineRegion.STATE_INACTIVE)
        zone.region.delete(object : OfflineRegion.OfflineRegionDeleteCallback {
            override fun onDelete() { _zones.value = _zones.value.filterNot { it.id == zone.id } }
            override fun onError(error: String) = Unit
        })
    }
}
