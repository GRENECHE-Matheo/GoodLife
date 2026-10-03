package com.goodlife.app.ui.screens

import com.goodlife.app.i18n.t

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.goodlife.app.i18n.Lang
import com.goodlife.app.track.OfflineEstimate
import com.goodlife.app.track.OfflineMaps
import com.goodlife.app.track.OfflineQuality
import com.goodlife.app.track.Tracker
import com.goodlife.app.ui.ScreenColumn
import com.goodlife.app.ui.SectionCard
import com.goodlife.app.ui.SubScreenHeader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import kotlin.math.roundToInt

/** Place sur le téléphone, comme dans les réglages d'Android (1 Go = 1 000 Mo). */
internal fun storage(b: Long): String =
    if (b >= 1_000_000_000L) t("%1\$s Go", String.format(Lang.locale, "%.1f", b / 1e9))
    else t("%1\$s Mo", String.format(Lang.locale, "%.0f", (b / 1e6).coerceAtLeast(1.0)))

private fun boundsOf(m: MapLibreMap, r: Rect): LatLngBounds? = runCatching {
    val p = m.projection
    val b = LatLngBounds.Builder()
    listOf(r.topLeft, r.topRight, r.bottomLeft, r.bottomRight).forEach { b.include(p.fromScreenLocation(android.graphics.PointF(it.x, it.y))) }
    b.build()
}.getOrNull()

/**
 * Cadre à poser sur la carte pour choisir la zone à télécharger : on déplace et zoome la carte dessous, et on tire
 * les coins pour l'agrandir ou la réduire. La zone choisie est renvoyée à chaque changement.
 */
@Composable
internal fun OfflineFrame(map: MapLibreMap?, top: Dp, bottom: Dp, onBounds: (LatLngBounds?) -> Unit) {
    val density = LocalDensity.current
    val primary = MaterialTheme.colorScheme.primary
    val report by rememberUpdatedState(onBounds)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = constraints.maxWidth.toFloat(); val h = constraints.maxHeight.toFloat()
        val minY = with(density) { top.toPx() }
        val maxY = (h - with(density) { bottom.toPx() }).coerceAtLeast(minY + with(density) { 120.dp.toPx() })
        val margin = with(density) { 12.dp.toPx() }
        val minSide = with(density) { 64.dp.toPx() }
        var rect by remember(w, minY, maxY) {
            val ih = maxY - minY
            mutableStateOf(Rect(w * 0.1f, minY + ih * 0.12f, w * 0.9f, maxY - ih * 0.12f))
        }
        // Zone choisie : recalculée quand la carte ou le cadre bougent (au rythme de l'affichage)
        LaunchedEffect(map) {
            var lastCam: CameraPosition? = null; var lastRect: Rect? = null
            while (true) {
                withFrameNanos { }
                val m = map
                if (m != null) {
                    val cam = m.cameraPosition; val r = rect
                    if (cam != lastCam || r != lastRect) { lastCam = cam; lastRect = r; report(boundsOf(m, r)) }
                }
                delay(120)
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            val dim = Color.Black.copy(alpha = 0.32f)
            drawRect(dim, Offset.Zero, Size(size.width, rect.top))
            drawRect(dim, Offset(0f, rect.bottom), Size(size.width, size.height - rect.bottom))
            drawRect(dim, Offset(0f, rect.top), Size(rect.left, rect.height))
            drawRect(dim, Offset(rect.right, rect.top), Size(size.width - rect.right, rect.height))
            drawRect(Color.White, rect.topLeft, rect.size, style = Stroke(3.dp.toPx()))
            listOf(rect.topLeft, rect.topRight, rect.bottomLeft, rect.bottomRight).forEach {
                drawCircle(Color.White, 12.dp.toPx(), it); drawCircle(primary, 8.dp.toPx(), it)
            }
        }
        Surface(
            shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, shadowElevation = 3.dp,
            modifier = Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 12.dp, end = 64.dp)
        ) {
            Text(t("Déplace et zoome la carte, ou tire les coins du cadre."), style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
        }
        // Coins à tirer (zone tactile plus grande que le rond)
        val touch = with(density) { 28.dp.toPx() }
        for (c in 0..3) {
            val p = when (c) { 0 -> rect.topLeft; 1 -> rect.topRight; 2 -> rect.bottomLeft; else -> rect.bottomRight }
            Box(
                Modifier.offset { IntOffset((p.x - touch).roundToInt(), (p.y - touch).roundToInt()) }.size(56.dp)
                    .pointerInput(c) {
                        detectDragGestures { change, d ->
                            change.consume()
                            val r = rect
                            val left = (r.left + d.x).coerceIn(margin, r.right - minSide)
                            val right = (r.right + d.x).coerceIn(r.left + minSide, w - margin)
                            val topY = (r.top + d.y).coerceIn(minY, r.bottom - minSide)
                            val bottomY = (r.bottom + d.y).coerceIn(r.top + minSide, maxY)
                            rect = when (c) {
                                0 -> r.copy(left = left, top = topY)
                                1 -> r.copy(right = right, top = topY)
                                2 -> r.copy(left = left, bottom = bottomY)
                                else -> r.copy(right = right, bottom = bottomY)
                            }
                        }
                    }
            )
        }
    }
}

/** Panneau du bas pendant le choix d'une zone : qualité, taille estimée en direct, place libre, téléchargement. */
@Composable
internal fun OfflinePickPanel(bounds: LatLngBounds?, zones: Int, onCancel: () -> Unit, onZones: () -> Unit, onStarted: () -> Unit) {
    val context = LocalContext.current
    var quality by rememberSaveable { mutableStateOf(OfflineQuality.FULL) }
    var name by rememberSaveable { mutableStateOf(t("Ma zone")) }
    var estimate by remember { mutableStateOf<OfflineEstimate?>(null) }
    var estimating by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val free = remember { OfflineMaps.freeBytes(context) }
    LaunchedEffect(bounds, quality) {
        val b = bounds ?: return@LaunchedEffect
        estimating = true
        delay(600)   // on attend que la carte ne bouge plus
        while (true) {
            estimate = try { OfflineMaps.estimate(b, quality) } catch (e: CancellationException) { throw e } catch (e: Exception) { null }
            estimating = false
            if (estimate != null) break
            delay(5_000)   // pas de réseau : on réessaie tout seul
        }
    }
    val est = estimate
    // On garde de la marge pour le téléphone (300 Mo)
    val fits = est == null || free < 0 || est.bytes + 300_000_000L < free

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(t("Carte hors ligne"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onZones) { Text(if (zones > 0) t("Mes zones (%1\$s)", zones) else t("Mes zones")) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(quality == OfflineQuality.FULL, { quality = OfflineQuality.FULL }, label = { Text(t("Complète")) })
            FilterChip(quality == OfflineQuality.LIGHT, { quality = OfflineQuality.LIGHT }, label = { Text(t("Légère")) })
        }
        Text(
            if (quality == OfflineQuality.FULL) t("Toute la carte (chemins, rues, bâtiments), et les itinéraires et le dénivelé marchent sans réseau.")
            else t("Carte simplifiée (villes, grandes routes) pour se repérer ; sans réseau, pas de calcul d'itinéraire."),
            style = MaterialTheme.typography.bodySmall
        )
        Surface(shape = RoundedCornerShape(16.dp), color = if (fits) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(
                    when {
                        est != null -> t("≈ %1\$s", storage(est.bytes)) + if (estimating) " …" else ""
                        estimating || bounds == null -> t("Calcul de la taille…")
                        else -> t("Taille inconnue : pas de réseau.")
                    },
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold
                )
                val zone = bounds?.let { b ->
                    val mid = (b.latitudeNorth + b.latitudeSouth) / 2
                    val wKm = Tracker.haversine(mid, b.longitudeWest, mid, b.longitudeEast) / 1000
                    val hKm = Tracker.haversine(b.latitudeSouth, b.longitudeWest, b.latitudeNorth, b.longitudeWest) / 1000
                    t("Zone de %1\$s × %2\$s km", "%.0f".format(wKm.coerceAtLeast(1.0)), "%.0f".format(hKm.coerceAtLeast(1.0)))
                }
                val room = if (free >= 0) t("libre sur ton téléphone : %1\$s", storage(free)) else null
                Text(listOfNotNull(zone, room).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                if (!fits) Text(t("Pas assez de place : réduis la zone ou choisis « Légère »."), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Medium)
                else if (est != null && est.bytes > 1_000_000_000L) Text(
                    t("Grosse zone : le téléchargement peut durer longtemps. Le Wi-Fi est conseillé."), style = MaterialTheme.typography.bodySmall
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text(t("Annuler")) }
            Button(
                enabled = bounds != null && est != null && fits && name.isNotBlank(),
                onClick = {
                    val b = bounds ?: return@Button
                    error = null
                    OfflineMaps.download(context, name.trim(), b, quality) { error = it }
                    onStarted()
                },
                modifier = Modifier.weight(1f)
            ) { Icon(Icons.Filled.DownloadForOffline, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(t("Télécharger")) }
        }
        if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(name, { name = it.take(40) }, label = { Text(t("Nom de la zone")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Text(t("Garde l'app ouverte pendant le téléchargement ; s'il est coupé, il reprend à la prochaine ouverture de la carte."),
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Cartes hors ligne : les zones téléchargées (ou en cours), avec leur taille. */
@Composable
internal fun OfflineZonesScreen(onBack: () -> Unit, onPick: () -> Unit) {
    val context = LocalContext.current
    val zones by OfflineMaps.zones.collectAsState()
    val extras by OfflineMaps.extras.collectAsState()
    LaunchedEffect(Unit) { OfflineMaps.refresh(context) }

    ScreenColumn {
        SubScreenHeader(t("Cartes hors ligne"), onBack)
        Text(
            t("Le GPS marche sans internet ; une zone téléchargée affiche aussi la carte sans réseau (campagne, forêt, montagne), et en qualité « Complète », les itinéraires et le dénivelé se calculent sans réseau."),
            style = MaterialTheme.typography.bodyMedium
        )
        Button(onClick = onPick, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.DownloadForOffline, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(t("Choisir une zone sur la carte"))
        }
        SectionCard(title = t("Mes zones")) {
            if (zones.isEmpty()) Text(t("Aucune zone téléchargée."), color = MaterialTheme.colorScheme.onSurfaceVariant)
            zones.sortedByDescending { it.createdAt }.forEachIndexed { i, z ->
                if (i > 0) HorizontalDivider()
                val x = extras[z.id]
                val extrasDone = !z.quality.extras || x?.complete == true
                val ready = z.mapComplete && extrasDone
                val total = z.sizeBytes + (x?.bytes ?: 0L)
                val kind = if (z.quality == OfflineQuality.FULL) t("Complète") else t("Légère")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
                        Text(z.name, fontWeight = FontWeight.Medium)
                        if (ready) Text("$kind · " + t("Prête · %1\$s", storage(total)),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        else {
                            val mapPct = (z.progress * 100).toInt().coerceIn(0, 100)
                            val xp = if (x == null || x.total == 0) 0f else x.done.toFloat() / x.total
                            Text(
                                "$kind · " + (if (z.quality.extras) t("carte %1\$s %% · itinéraires et dénivelé %2\$s %%", mapPct, (xp * 100).toInt())
                                else t("carte %1\$s %%", mapPct)) + " · " + storage(total),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            LinearProgressIndicator(
                                progress = { (if (z.quality.extras) (z.progress + xp) / 2 else z.progress).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                            )
                            if (x != null && !x.running && x.failed > 0) Text(
                                t("%1\$s morceaux n'ont pas pu être téléchargés (réseau ?) : touche Reprendre.", x.failed),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    if (!ready && x?.running != true) TextButton(onClick = { OfflineMaps.retry(z) }) { Icon(Icons.Filled.Refresh, t("Reprendre")) }
                    TextButton(onClick = { OfflineMaps.delete(z) }) { Icon(Icons.Filled.Delete, t("Supprimer")) }
                }
            }
        }
        Text(t("Carte : OpenFreeMap © OpenMapTiles, données © contributeurs OpenStreetMap ; altitude : Terrain Tiles (Amazon)."),
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Sans réseau, sur la carte : indique si l'endroit affiché est couvert par une zone téléchargée (et si les
 * itinéraires y marchent), ou s'il n'y a pas de carte ici.
 */
@Composable
internal fun OfflineBanner(map: MapLibreMap?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var online by remember { mutableStateOf(true) }
    androidx.compose.runtime.DisposableEffect(Unit) {
        val cm = context.getSystemService(android.net.ConnectivityManager::class.java)
        fun check() = cm?.activeNetwork?.let { cm.getNetworkCapabilities(it) }
            ?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        online = check()
        val cb = object : android.net.ConnectivityManager.NetworkCallback() {
            // Réseau par défaut : un nouveau réseau arrive par onAvailable, sa perte par onLost (sans réseau de secours)
            override fun onAvailable(network: android.net.Network) { online = true }
            override fun onLost(network: android.net.Network) { online = false }
        }
        runCatching { cm?.registerDefaultNetworkCallback(cb) }
        onDispose { runCatching { cm?.unregisterNetworkCallback(cb) } }
    }
    if (online) return
    val zones by OfflineMaps.zones.collectAsState()
    LaunchedEffect(Unit) { OfflineMaps.refresh(context) }
    var here by remember { mutableStateOf<org.maplibre.android.geometry.LatLng?>(null) }
    LaunchedEffect(map) { while (true) { here = map?.cameraPosition?.target; delay(1000) } }
    val zone = here?.let { p -> zones.filter { it.bounds?.contains(p) == true }.maxByOrNull { if (it.quality == OfflineQuality.FULL) 1 else 0 } }
    Surface(
        shape = RoundedCornerShape(16.dp), shadowElevation = 3.dp, modifier = modifier,
        color = if (zone != null) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer
    ) {
        Text(
            when {
                zone == null -> t("Hors ligne · pas de carte téléchargée ici")
                zone.quality == OfflineQuality.FULL -> t("Hors ligne · carte « %1\$s » (itinéraires disponibles)", zone.name)
                else -> t("Hors ligne · carte « %1\$s » (sans itinéraires)", zone.name)
            },
            style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}
