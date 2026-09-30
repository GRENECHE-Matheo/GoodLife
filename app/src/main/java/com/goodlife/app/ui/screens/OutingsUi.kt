@file:OptIn(ExperimentalMaterial3Api::class)

package com.goodlife.app.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.goodlife.app.data.Outing
import com.goodlife.app.data.OutingType
import com.goodlife.app.data.Repo
import com.goodlife.app.data.TrackPoint
import com.goodlife.app.track.FinishedOuting
import com.goodlife.app.track.Places
import com.goodlife.app.track.SportPlace
import com.goodlife.app.track.Tracker
import com.goodlife.app.track.TrackingService
import com.goodlife.app.track.formatClock
import com.goodlife.app.track.formatKmh
import com.goodlife.app.track.formatPace
import com.goodlife.app.ui.Sfx
import com.goodlife.app.ui.Sounds
import com.goodlife.app.ui.formatDay
import com.goodlife.app.ui.formatTime
import com.goodlife.app.ui.theme.successColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

// ---------------------------------------------------------------------------------------------
// Carte (MapLibre + fonds OpenFreeMap, données © OpenStreetMap) : aucune clé API
// ---------------------------------------------------------------------------------------------

/** « bright » : à plat, couleurs douces, chemins bien visibles (le plus lisible des styles testés). */
private const val STYLE_LIGHT = "https://tiles.openfreemap.org/styles/bright"
private const val STYLE_DARK = "https://tiles.openfreemap.org/styles/dark"

/** Couleurs du tracé, comme un GPS : bleu vif (bord blanc) et parcours à refaire en orange. */
private val TRACK_COLOR = android.graphics.Color.rgb(26, 115, 232)
private val ROUTE_COLOR = android.graphics.Color.rgb(255, 138, 0)
private val PLACE_COLOR = android.graphics.Color.rgb(234, 67, 53)

/** Carte prête : la carte et son style chargé (null tant que ce n'est pas prêt). */
class MapHandle {
    var map by mutableStateOf<MapLibreMap?>(null)
    var style by mutableStateOf<Style?>(null)
}

@Composable
fun GoodMap(handle: MapHandle, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val mapView = remember {
        MapLibre.getInstance(context)
        // Rendu un peu plus gros (textes, icônes, traits) pour être lisible en courant
        val options = org.maplibre.android.maps.MapLibreMapOptions.createFromAttributes(context)
            .pixelRatio(context.resources.displayMetrics.density * 1.2f)
        MapView(context, options).apply { onCreate(null) }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, e ->
            when (e) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStart()
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onResume()
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onPause(); mapView.onStop(); mapView.onDestroy()
            handle.map = null; handle.style = null
        }
    }
    LaunchedEffect(dark) {
        mapView.getMapAsync { map ->
            if (map.cameraPosition.zoom < 3) {
                map.cameraPosition = CameraPosition.Builder().target(LatLng(46.6, 2.4)).zoom(5.0).build()
            }
            map.setStyle(if (dark) STYLE_DARK else STYLE_LIGHT) { style ->
                handle.map = map
                handle.style = style
            }
        }
    }
    AndroidView(factory = { mapView }, modifier = modifier)
}

/** Trace (ou met à jour) une ligne sur la carte. */
private fun Style.line(id: String, points: List<TrackPoint>, color: Int, width: Float, dashed: Boolean = false) {
    val geometry = LineString.fromLngLats(points.map { Point.fromLngLat(it.lng, it.lat) })
    val src = getSourceAs<GeoJsonSource>(id)
    if (src != null) { src.setGeoJson(geometry); return }
    addSource(GeoJsonSource(id).apply { setGeoJson(geometry) })
    addLayer(LineLayer("$id-layer", id).withProperties(
        PropertyFactory.lineColor(color), PropertyFactory.lineWidth(width),
        PropertyFactory.lineCap(Property.LINE_CAP_ROUND), PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
        *(if (dashed) arrayOf(PropertyFactory.lineDasharray(arrayOf(1.5f, 1.5f))) else emptyArray())
    ))
}

private fun Style.points(id: String, places: List<SportPlace>, color: Int) {
    val fc = FeatureCollection.fromFeatures(places.mapIndexed { i, p ->
        Feature.fromGeometry(Point.fromLngLat(p.lng, p.lat)).apply { addNumberProperty("i", i) }
    })
    val src = getSourceAs<GeoJsonSource>(id)
    if (src != null) { src.setGeoJson(fc); return }
    addSource(GeoJsonSource(id).apply { setGeoJson(fc) })
    addLayer(CircleLayer("$id-layer", id).withProperties(
        PropertyFactory.circleColor(color), PropertyFactory.circleRadius(10f),
        PropertyFactory.circleStrokeColor(android.graphics.Color.WHITE), PropertyFactory.circleStrokeWidth(3f)
    ))
}

private fun MapLibreMap.fit(points: List<TrackPoint>) {
    if (points.size < 2) return
    val b = LatLngBounds.Builder()
    points.forEach { b.include(LatLng(it.lat, it.lng)) }
    runCatching { moveCamera(CameraUpdateFactory.newLatLngBounds(b.build(), 80)) }
}

@SuppressLint("MissingPermission")
private fun showMe(map: MapLibreMap, style: Style, context: android.content.Context, follow: Boolean) {
    if (!TrackingService.hasPermission(context)) return
    runCatching {
        val lc = map.locationComponent
        if (!lc.isLocationComponentActivated) lc.activateLocationComponent(LocationComponentActivationOptions.builder(context, style).build())
        lc.isLocationComponentEnabled = true
        // Pendant une sortie : comme un GPS, la carte suit la position et tourne dans le sens de la marche
        lc.renderMode = if (follow) RenderMode.GPS else RenderMode.COMPASS
        // Suivi + zoom en une seule transition (un zoom séparé annulerait le suivi)
        if (follow) lc.setCameraMode(CameraMode.TRACKING_GPS, 750L, 17.0, null, null, null)
        else lc.cameraMode = CameraMode.NONE
        lc.lastKnownLocation?.let { if (!follow) map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(it.latitude, it.longitude), 14.0)) }
    }
}

// ---------------------------------------------------------------------------------------------
// Onglet « Sorties »
// ---------------------------------------------------------------------------------------------

@Composable
fun OutingsTab() {
    var screen by rememberSaveable { mutableStateOf("") }
    var retry by rememberSaveable { mutableStateOf(0L) }
    BackHandler(enabled = screen.isNotEmpty()) { screen = if (screen.startsWith("detail:")) "history" else "" }
    when {
        screen == "history" -> OutingHistory(onBack = { screen = "" }, onOpen = { screen = "detail:$it" })
        screen.startsWith("detail:") -> OutingDetail(
            id = screen.removePrefix("detail:").toLong(),
            onBack = { screen = "history" },
            onRetry = { routeId -> retry = routeId; screen = "" }
        )
        else -> OutingsMap(retryRoute = retry, onClearRetry = { retry = 0L }, onHistory = { screen = "history" })
    }
}

@Composable
private fun OutingsMap(retryRoute: Long, onClearRetry: () -> Unit, onHistory: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val handle = remember { MapHandle() }
    val live by Tracker.live.collectAsState()
    val outings by Repo.outings.collectAsState()
    var mode by rememberSaveable { mutableStateOf("sortie") }
    var type by rememberSaveable { mutableStateOf(OutingType.RUN) }
    var confirmStop by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf<FinishedOuting?>(null) }
    var places by remember { mutableStateOf<List<SportPlace>>(emptyList()) }
    var placesLoading by remember { mutableStateOf(false) }
    var placesError by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<SportPlace?>(null) }
    var permMessage by remember { mutableStateOf<String?>(null) }
    val primary = MaterialTheme.colorScheme.primary.toArgb()
    val tertiary = MaterialTheme.colorScheme.tertiary.toArgb()
    val routeTrack = remember(retryRoute) { if (retryRoute != 0L) Repo.routeTrack(retryRoute) else emptyList() }
    val best = remember(retryRoute, outings) { if (retryRoute != 0L) Repo.bestOn(retryRoute) else null }

    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            TrackingService.start(context, type, retryRoute)
            permMessage = null
        } else permMessage = "Sans la localisation précise, impossible de suivre ta sortie."
    }
    fun start() {
        if (TrackingService.hasPermission(context)) TrackingService.start(context, type, retryRoute)
        else askLocation.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION) +
                if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.POST_NOTIFICATIONS) else emptyArray()
        )
    }

    // Carte prête : ma position, tracé en cours, parcours à refaire, lieux
    LaunchedEffect(handle.style, live != null, live?.gpsOk) {
        val map = handle.map ?: return@LaunchedEffect
        val style = handle.style ?: return@LaunchedEffect
        showMe(map, style, context, follow = live != null)
        if (routeTrack.isNotEmpty() && live == null) map.fit(routeTrack)
    }
    LaunchedEffect(handle.style, live?.points?.size, routeTrack) {
        val style = handle.style ?: return@LaunchedEffect
        style.line("route", routeTrack, ROUTE_COLOR, 6f, dashed = true)
        style.line("track-casing", live?.points ?: emptyList(), android.graphics.Color.WHITE, 10f)
        style.line("track", live?.points ?: emptyList(), TRACK_COLOR, 6f)
    }
    LaunchedEffect(handle.style, places) {
        handle.style?.points("places", if (mode == "clubs") places else emptyList(), PLACE_COLOR)
    }
    LaunchedEffect(mode, handle.style) { handle.style?.points("places", if (mode == "clubs") places else emptyList(), PLACE_COLOR) }
    DisposableEffect(handle.map) {
        val map = handle.map
        val listener = MapLibreMap.OnMapClickListener { latLng ->
            val f = map?.queryRenderedFeatures(map.projection.toScreenLocation(latLng), "places-layer")?.firstOrNull()
            val i = f?.getNumberProperty("i")?.toInt()
            selected = i?.let { places.getOrNull(it) }
            i != null
        }
        map?.addOnMapClickListener(listener)
        onDispose { map?.removeOnMapClickListener(listener) }
    }
    // Fin d'une sortie : le service a enregistré le résultat
    var wasLive by remember { mutableStateOf(live != null) }
    LaunchedEffect(live == null) {
        if (live == null && wasLive) {
            repeat(20) {
                TrackingService.finished?.let { f ->
                    finished = f; TrackingService.finished = null
                    Sounds.play(Sfx.LEVEL_UP)
                    onClearRetry()
                    return@LaunchedEffect
                }
                delay(100)
            }
        }
        wasLive = live != null
    }

    Column(Modifier.fillMaxSize()) {
    Box(Modifier.weight(1f).fillMaxWidth()) {
        GoodMap(handle, Modifier.fillMaxSize())

        // Choix du mode, en haut
        if (live == null) Surface(
            shape = RoundedCornerShape(24.dp), tonalElevation = 3.dp, shadowElevation = 3.dp,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp)
        ) {
            Row(Modifier.padding(horizontal = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(mode == "sortie", { mode = "sortie" }, label = { Text("Activité") })
                FilterChip(mode == "clubs", { mode = "clubs"; selected = null }, label = { Text("Clubs") })
            }
        }
        SmallFloatingActionButton(
            onClick = { val m = handle.map; val s = handle.style; if (m != null && s != null) showMe(m, s, context, follow = live != null) },
            modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)
        ) { Icon(Icons.Filled.MyLocation, "Ma position") }

        Text(
            "© OpenStreetMap · OpenFreeMap",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp)
                .background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                .padding(horizontal = 4.dp),
            color = androidx.compose.ui.graphics.Color.DarkGray
        )
    }

        // Panneau du bas (sous la carte)
        Surface(
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            tonalElevation = 3.dp, shadowElevation = 8.dp,
            modifier = Modifier.align(Alignment.CenterHorizontally).widthIn(max = 640.dp).fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val l = live
                when {
                    l != null -> LivePanel(l, best, onPause = { Tracker.togglePause() }, onStop = { confirmStop = true })
                    mode == "clubs" -> ClubsPanel(
                        selected, places.size, placesLoading, placesError,
                        onSearch = {
                            val target = handle.map?.cameraPosition?.target ?: return@ClubsPanel
                            placesLoading = true; placesError = null; selected = null
                            scope.launch {
                                try { places = Places.around(target.latitude, target.longitude) }
                                catch (e: Exception) { placesError = e.message }
                                finally { placesLoading = false }
                            }
                        },
                        onClose = { selected = null }
                    )
                    else -> {
                        if (retryRoute != 0L) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.EmojiEvents, null, tint = MaterialTheme.colorScheme.tertiary)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Refaire ce parcours" + (best?.let { " · record ${formatClock(it.movingMs)}" } ?: ""),
                                    style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = onClearRetry) { Text("Annuler") }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutingType.entries.forEach { t ->
                                FilterChip(type == t, { type = t }, label = { Text("${t.emoji} ${t.label}") })
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(onClick = { start() }, modifier = Modifier.weight(1f).height(52.dp)) {
                                Icon(Icons.Filled.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Démarrer")
                            }
                            OutlinedButton(onClick = onHistory, modifier = Modifier.height(52.dp)) {
                                Icon(Icons.AutoMirrored.Filled.List, null); Spacer(Modifier.width(6.dp)); Text("Mes activités (${outings.size})")
                            }
                        }
                        if (permMessage != null) Text(permMessage!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        Text(
                            "Le GPS fonctionne même sans internet ; ton tracé reste sur ton téléphone.",
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    if (confirmStop) AlertDialog(
        onDismissRequest = { confirmStop = false },
        title = { Text("Terminer l'activité ?") },
        text = { Text("Elle sera enregistrée (si tu as parcouru au moins 50 m).") },
        confirmButton = { TextButton(onClick = { confirmStop = false; TrackingService.stop(context) }) { Text("Terminer") } },
        dismissButton = { TextButton(onClick = { confirmStop = false }) { Text("Continuer") } }
    )
    finished?.let { f -> FinishedDialog(f, onDismiss = { finished = null }) }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier, big: Boolean = false) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = if (big) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Medium, textAlign = TextAlign.Center
        )
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LivePanel(l: com.goodlife.app.track.LiveOuting, best: Outing?, onPause: () -> Unit, onStop: () -> Unit) {
    val bike = l.type == OutingType.BIKE
    Text(
        "${l.type.emoji} ${l.type.label}" + when {
            !l.gpsOk -> " · recherche du GPS…"
            l.paused -> " · en pause"
            l.autoPaused -> " · à l'arrêt"
            else -> ""
        },
        style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary
    )
    Row(Modifier.fillMaxWidth()) {
        Stat("Temps", formatClock(l.movingMs), Modifier.weight(1f), big = true)
        Stat("Distance (km)", "%.2f".format(l.distanceM / 1000), Modifier.weight(1f), big = true)
    }
    Row(Modifier.fillMaxWidth()) {
        if (bike) {
            Stat("Vitesse (km/h)", formatKmh(l.speed), Modifier.weight(1f))
            Stat("Moyenne (km/h)", formatKmh(l.avgSpeed), Modifier.weight(1f))
        } else {
            Stat("Allure (min/km)", formatPace(l.speed), Modifier.weight(1f))
            Stat("Moyenne (min/km)", formatPace(l.avgSpeed), Modifier.weight(1f))
        }
        Stat("Dénivelé + (m)", "%.0f".format(l.elevGainM), Modifier.weight(1f))
    }
    if (best != null) Text(
        "Record à battre : ${formatClock(best.movingMs)} pour ${"%.2f".format(best.distanceM / 1000)} km",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary
    )
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        FilledTonalButton(onClick = onPause, modifier = Modifier.weight(1f).height(52.dp)) {
            Icon(if (l.paused) Icons.Filled.PlayArrow else Icons.Filled.Pause, null)
            Spacer(Modifier.width(6.dp)); Text(if (l.paused) "Reprendre" else "Pause")
        }
        Button(
            onClick = onStop, modifier = Modifier.weight(1f).height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
            Icon(Icons.Filled.Stop, null); Spacer(Modifier.width(6.dp)); Text("Terminer")
        }
    }
}

@Composable
private fun ClubsPanel(
    selected: SportPlace?, count: Int, loading: Boolean, error: String?,
    onSearch: () -> Unit, onClose: () -> Unit
) {
    val context = LocalContext.current
    if (selected != null) {
        Text(selected.name, style = MaterialTheme.typography.titleLarge)
        Text(
            listOf(selected.kind, selected.sports).filter { it.isNotBlank() }.joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary
        )
        if (selected.address.isNotBlank()) Text(selected.address, style = MaterialTheme.typography.bodySmall)
        Text("Tarif : ${selected.price.ifBlank { "non renseigné" }}", style = MaterialTheme.typography.bodyMedium)
        if (selected.hours.isNotBlank()) Text("Horaires : ${selected.hours}", style = MaterialTheme.typography.bodySmall)
        if (selected.phone.isNotBlank()) Text("Téléphone : ${selected.phone}", style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (selected.website.isNotBlank()) Button(onClick = {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(selected.website))) }
            }) { Icon(Icons.Filled.Language, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Site du club") }
            OutlinedButton(onClick = {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:${selected.lat},${selected.lng}?q=${selected.lat},${selected.lng}(${Uri.encode(selected.name)})")))
                }
            }) { Text("Y aller") }
            TextButton(onClick = onClose) { Text("Fermer") }
        }
        Text("Données © OpenStreetMap, à vérifier auprès du club.", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        Text("Clubs et lieux de sport", style = MaterialTheme.typography.titleMedium)
        Text(
            if (count > 0) "$count lieux trouvés. Touche un point sur la carte pour les détails."
            else "Place la carte sur ta zone, puis cherche les clubs, salles, piscines et stades autour.",
            style = MaterialTheme.typography.bodySmall
        )
        Button(onClick = onSearch, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
            if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else Icon(Icons.Filled.Search, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Chercher dans cette zone")
        }
        if (error != null) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun FinishedDialog(f: FinishedOuting, onDismiss: () -> Unit) {
    val o = f.outing
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.EmojiEvents, null, tint = MaterialTheme.colorScheme.tertiary) },
        title = { Text(if (f.record) "Nouveau record !" else "Bravo, activité terminée !") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${o.type.emoji} ${"%.2f".format(o.distanceM / 1000)} km en ${formatClock(o.movingMs)}", style = MaterialTheme.typography.titleMedium)
                Text(
                    (if (o.type == OutingType.BIKE) "Moyenne ${formatKmh(o.avgSpeed)} km/h" else "Allure moyenne ${formatPace(o.avgSpeed)} min/km") +
                        " · dénivelé +${"%.0f".format(o.elevGainM)} m",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (f.previousBest != null) Text(
                    if (f.record) "Tu bats ton record de ${formatClock(f.previousBest.movingMs - o.movingMs)} !"
                    else if (!o.valid) "Parcours pas entièrement suivi : pas compté pour le record."
                    else "Record : ${formatClock(f.previousBest.movingMs)}. Tu y es presque !",
                    color = if (f.record) successColor else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(if (f.xp > 0) "+${f.xp} XP" else "XP sport du jour déjà au maximum", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Super") } }
    )
}

// ---------------------------------------------------------------------------------------------
// Historique et détail
// ---------------------------------------------------------------------------------------------

@Composable
private fun OutingHistory(onBack: () -> Unit, onOpen: (Long) -> Unit) {
    val outings by Repo.outings.collectAsState()
    com.goodlife.app.ui.ScreenColumn {
        com.goodlife.app.ui.SubScreenHeader("Mes activités", onBack)
        if (outings.isEmpty()) Text(
            "Aucune activité pour l'instant. Lance-toi depuis la carte !",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        outings.forEach { o ->
            val record = Repo.bestOn(o.routeId)?.id == o.id && outings.count { it.routeId == o.routeId } > 1
            Surface(
                onClick = { onOpen(o.id) }, shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(o.type.emoji, style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(o.name.ifBlank { "${o.type.label} du ${formatDay(o.start)}" }, fontWeight = FontWeight.Medium)
                        Text(
                            "${"%.2f".format(o.distanceM / 1000)} km · ${formatClock(o.movingMs)} · " +
                                if (o.type == OutingType.BIKE) "${formatKmh(o.avgSpeed)} km/h" else "${formatPace(o.avgSpeed)} /km",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (record) Icon(Icons.Filled.EmojiEvents, "Record", tint = MaterialTheme.colorScheme.tertiary)
                }
            }
        }
    }
}

@Composable
private fun OutingDetail(id: Long, onBack: () -> Unit, onRetry: (Long) -> Unit) {
    val outings by Repo.outings.collectAsState()
    val o = outings.firstOrNull { it.id == id }
    if (o == null) { LaunchedEffect(Unit) { onBack() }; return }
    val handle = remember { MapHandle() }
    val track = remember(id) { Repo.track(id) }
    val primary = MaterialTheme.colorScheme.primary.toArgb()
    val attempts = outings.filter { it.routeId == o.routeId }
    val best = Repo.bestOn(o.routeId)
    var rename by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(handle.style) {
        val style = handle.style ?: return@LaunchedEffect
        style.line("track-casing", track, android.graphics.Color.WHITE, 10f)
        style.line("track", track, TRACK_COLOR, 6f)
        handle.map?.fit(track)
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().height(260.dp)) { GoodMap(handle, Modifier.fillMaxSize()) }
        com.goodlife.app.ui.ScreenColumn {
            com.goodlife.app.ui.SubScreenHeader(o.name.ifBlank { "${o.type.label} du ${formatDay(o.start)}" }, onBack)
            Text("${formatDay(o.start)} à ${formatTime(o.start)}", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth()) {
                Stat("Distance (km)", "%.2f".format(o.distanceM / 1000), Modifier.weight(1f))
                Stat("Temps", formatClock(o.movingMs), Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth()) {
                if (o.type == OutingType.BIKE) {
                    Stat("Moyenne (km/h)", formatKmh(o.avgSpeed), Modifier.weight(1f))
                    Stat("Max (km/h)", formatKmh(o.maxSpeed), Modifier.weight(1f))
                } else {
                    Stat("Allure (min/km)", formatPace(o.avgSpeed), Modifier.weight(1f))
                    Stat("Moyenne (km/h)", formatKmh(o.avgSpeed), Modifier.weight(1f))
                }
                Stat("Dénivelé + (m)", "%.0f".format(o.elevGainM), Modifier.weight(1f))
            }
            if (attempts.size > 1 && best != null) Text(
                "Record sur ce parcours : ${formatClock(best.movingMs)} (${formatDay(best.start)}) · ${attempts.size} passages",
                color = MaterialTheme.colorScheme.tertiary
            )
            if (!o.valid) Text("Parcours pas entièrement suivi : cette sortie ne compte pas pour le record.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = { onRetry(o.routeId) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Replay, null); Spacer(Modifier.width(8.dp)); Text("Refaire ce parcours et battre mon record")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { rename = true }) { Text("Nommer ce parcours") }
                TextButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Filled.Delete, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Supprimer")
                }
            }
            Text("Carte : OpenFreeMap © OpenMapTiles, données © contributeurs OpenStreetMap.",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (rename) {
        var name by remember { mutableStateOf(o.name) }
        AlertDialog(
            onDismissRequest = { rename = false },
            title = { Text("Nom du parcours") },
            text = { OutlinedTextField(name, { name = it.take(40) }, singleLine = true, placeholder = { Text("Ex : Tour du lac") }) },
            confirmButton = { TextButton(onClick = { Repo.renameOuting(o.routeId, name.trim()); rename = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { rename = false }) { Text("Annuler") } }
        )
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Supprimer cette activité ?") },
        text = { Text("Le tracé et les chiffres seront effacés. L'XP déjà gagnée reste.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; Repo.deleteOuting(o.id); onBack() }) { Text("Supprimer", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annuler") } }
    )
}
