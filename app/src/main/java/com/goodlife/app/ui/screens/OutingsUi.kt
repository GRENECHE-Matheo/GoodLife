@file:OptIn(ExperimentalMaterial3Api::class)

package com.goodlife.app.ui.screens

import com.goodlife.app.i18n.t

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.DownloadForOffline
import com.goodlife.app.track.OfflineMaps
import com.goodlife.app.track.PlannedRoute
import com.goodlife.app.track.Routing
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
            map.uiSettings.isLogoEnabled = false
            map.uiSettings.isAttributionEnabled = false
            map.uiSettings.setCompassMargins(0, 260, 36, 0)
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
// Onglet « Carte »
// ---------------------------------------------------------------------------------------------

/** Demande de localisation déjà faite pendant cette session (pour ne pas redemander à chaque ouverture). */
private var mapPermissionAsked = false

/** Vitesse moyenne indicative (m/s) pour estimer une durée. */
private fun typicalSpeed(t: OutingType) = when (t) { OutingType.RUN -> 2.8; OutingType.WALK -> 1.35; OutingType.BIKE -> 4.5 }

private fun estimate(lengthM: Double, t: OutingType): String {
    val min = (lengthM / typicalSpeed(t) / 60).toInt().coerceAtLeast(1)
    return if (min >= 60) "${min / 60} h ${"%02d".format(min % 60)}" else "$min min"
}

private fun km(m: Double) = "%.1f km".format(m / 1000)

private val LOOP_COLORS = listOf(
    android.graphics.Color.rgb(255, 138, 0),
    android.graphics.Color.rgb(142, 36, 170),
    android.graphics.Color.rgb(0, 150, 136)
)

@Composable
fun OutingsTab() {
    var screen by rememberSaveable { mutableStateOf("") }
    var retry by rememberSaveable { mutableStateOf(0L) }
    var offlineBounds by remember { mutableStateOf<LatLngBounds?>(null) }
    BackHandler(enabled = screen.isNotEmpty()) { screen = if (screen.startsWith("detail:")) "history" else "" }
    when {
        screen == "history" -> OutingHistory(onBack = { screen = "" }, onOpen = { screen = "detail:$it" })
        screen == "offline" -> OfflineZonesScreen(offlineBounds, onBack = { screen = "" })
        screen.startsWith("detail:") -> OutingDetail(
            id = screen.removePrefix("detail:").toLong(),
            onBack = { screen = "history" },
            onRetry = { routeId ->
                val track = Repo.routeTrack(routeId)
                Tracker.setPlanned(PlannedRoute(track, Tracker.length(track), t("Parcours à refaire")))
                retry = routeId; screen = ""
            }
        )
        else -> OutingsMap(
            retryRoute = retry,
            onClearRetry = { retry = 0L },
            onHistory = { screen = "history" },
            onOffline = { b -> offlineBounds = b; screen = "offline" }
        )
    }
}

@SuppressLint("MissingPermission")
@Composable
private fun OutingsMap(retryRoute: Long, onClearRetry: () -> Unit, onHistory: () -> Unit, onOffline: (LatLngBounds?) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val handle = remember { MapHandle() }
    val live by Tracker.live.collectAsState()
    val planned by Tracker.planned.collectAsState()
    val outings by Repo.outings.collectAsState()
    val settings by Repo.settings.collectAsState()
    val type = runCatching { OutingType.valueOf(settings.preferredOuting) }.getOrDefault(OutingType.RUN)
    fun setType(t: OutingType) = Repo.updateSettings { it.copy(preferredOuting = t.name) }

    var mode by rememberSaveable { mutableStateOf("activite") }
    var destination by remember { mutableStateOf<LatLng?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }         // calcul en cours (texte)
    var routeError by remember { mutableStateOf<String?>(null) }
    var loops by remember { mutableStateOf<List<PlannedRoute>>(emptyList()) }
    var loopIndex by remember { mutableStateOf(0) }
    var loopKm by rememberSaveable { mutableStateOf(5f) }
    var confirmStop by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf<FinishedOuting?>(null) }
    var places by remember { mutableStateOf<List<SportPlace>>(emptyList()) }
    var placesError by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<SportPlace?>(null) }
    var locationGranted by remember { mutableStateOf(hasAnyLocation(context)) }
    val best = remember(retryRoute, outings) { if (retryRoute != 0L) Repo.bestOn(retryRoute) else null }

    // ---- Localisation : demandée dès l'ouverture de la carte ----
    val askMapLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        locationGranted = res.values.any { it }
        val m = handle.map; val s = handle.style
        if (locationGranted && m != null && s != null) showMe(m, s, context, follow = false)
    }
    LaunchedEffect(Unit) {
        if (!locationGranted && !mapPermissionAsked) {
            mapPermissionAsked = true
            askMapLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }
    val askStart = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            locationGranted = true
            TrackingService.start(context, type, retryRoute)
        } else routeError = t("Sans la localisation précise, impossible de suivre ton activité.")
    }
    fun start() {
        routeError = null
        if (TrackingService.hasPermission(context)) TrackingService.start(context, type, retryRoute)
        else askStart.launch(
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION) +
                if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.POST_NOTIFICATIONS) else emptyArray()
        )
    }
    fun myPosition(): LatLng? = runCatching {
        handle.map?.locationComponent?.takeIf { it.isLocationComponentActivated }?.lastKnownLocation?.let { LatLng(it.latitude, it.longitude) }
    }.getOrNull()

    fun routeTo(dest: LatLng) {
        val from = myPosition()
        if (from == null) { routeError = t("Autorise la localisation pour calculer l'itinéraire depuis ta position."); return }
        busy = t("Calcul de l'itinéraire…"); routeError = null
        scope.launch {
            try { Tracker.setPlanned(Routing.toDestination(from.latitude, from.longitude, dest.latitude, dest.longitude, type)) }
            catch (e: Exception) { routeError = e.message; Tracker.setPlanned(null) }
            finally { busy = null }
        }
    }

    // ---- Dessins sur la carte ----
    LaunchedEffect(handle.style, live != null, live?.gpsOk, locationGranted) {
        val map = handle.map ?: return@LaunchedEffect
        val style = handle.style ?: return@LaunchedEffect
        showMe(map, style, context, follow = live != null)
    }
    LaunchedEffect(handle.style, live?.points?.size, planned, loops, loopIndex, mode) {
        val style = handle.style ?: return@LaunchedEffect
        // Boucles proposées (celle choisie plus épaisse)
        LOOP_COLORS.indices.forEach { i ->
            val pts = if (mode == "parcours" && live == null && planned == null) loops.getOrNull(i)?.points.orEmpty() else emptyList()
            style.line("loop$i", pts, LOOP_COLORS[i], if (i == loopIndex) 7f else 4f)
        }
        val route = planned?.points.orEmpty()
        style.line("planned-casing", route, android.graphics.Color.WHITE, 11f)
        style.line("planned", route, ROUTE_COLOR, 7f)
        style.line("track-casing", live?.points.orEmpty(), android.graphics.Color.WHITE, 10f)
        style.line("track", live?.points.orEmpty(), TRACK_COLOR, 6f)
    }
    // Cadre automatiquement les boucles proposées, la boucle choisie ou l'itinéraire
    LaunchedEffect(handle.map, loops, loopIndex, planned) {
        if (Tracker.live.value != null) return@LaunchedEffect
        val map = handle.map ?: return@LaunchedEffect
        val pts = planned?.points ?: if (mode == "parcours") loops.flatMap { it.points } else emptyList()
        if (pts.size >= 2) map.fit(pts)
    }
    LaunchedEffect(handle.style, destination) {
        val d = destination
        handle.style?.points("dest", if (d == null) emptyList() else listOf(SportPlace("dest", d.latitude, d.longitude, "", "", "", "", "", "", "", "")), ROUTE_COLOR)
    }
    LaunchedEffect(handle.style, places, mode) {
        handle.style?.points("places", if (mode == "clubs") places else emptyList(), PLACE_COLOR)
    }
    // Appui court : fiche d'un club ; appui long : destination
    DisposableEffect(handle.map) {
        val map = handle.map
        val click = MapLibreMap.OnMapClickListener { latLng ->
            if (mode != "clubs" || map == null) return@OnMapClickListener false
            val f = map.queryRenderedFeatures(map.projection.toScreenLocation(latLng), "places-layer").firstOrNull()
            val i = f?.getNumberProperty("i")?.toInt()
            selected = i?.let { places.getOrNull(it) }
            i != null
        }
        val longClick = MapLibreMap.OnMapLongClickListener { latLng ->
            if (Tracker.live.value != null) return@OnMapLongClickListener false
            destination = latLng; mode = "activite"; loops = emptyList()
            routeTo(latLng)
            true
        }
        map?.addOnMapClickListener(click)
        map?.addOnMapLongClickListener(longClick)
        onDispose { map?.removeOnMapClickListener(click); map?.removeOnMapLongClickListener(longClick) }
    }
    // Fin d'une activité : récapitulatif
    var wasLive by remember { mutableStateOf(live != null) }
    LaunchedEffect(live == null) {
        if (live == null && wasLive) {
            repeat(20) {
                TrackingService.finished?.let { f ->
                    finished = f; TrackingService.finished = null
                    Sounds.play(Sfx.LEVEL_UP)
                    Tracker.setPlanned(null); destination = null; onClearRetry()
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

            // Sélecteur de mode, en haut
            if (live == null) Surface(
                shape = RoundedCornerShape(24.dp), shadowElevation = 4.dp,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp)
            ) {
                Row(Modifier.padding(4.dp)) {
                    listOf("activite" to t("Activité"), "parcours" to t("Parcours"), "clubs" to t("Clubs")).forEach { (id, label) ->
                        val on = mode == id
                        Surface(
                            onClick = { mode = id; selected = null; routeError = null },
                            shape = RoundedCornerShape(20.dp),
                            color = if (on) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent
                        ) {
                            Text(
                                label, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.labelLarge,
                                color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
            // Boutons ronds à droite
            Column(Modifier.align(Alignment.TopEnd).padding(top = 64.dp, end = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SmallFloatingActionButton(onClick = {
                    if (!locationGranted) askMapLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    val m = handle.map; val s = handle.style
                    if (m != null && s != null) showMe(m, s, context, follow = live != null)
                }, containerColor = MaterialTheme.colorScheme.surface) { Icon(Icons.Filled.MyLocation, t("Ma position")) }
                if (live == null) SmallFloatingActionButton(
                    onClick = { onOffline(handle.map?.projection?.visibleRegion?.latLngBounds) },
                    containerColor = MaterialTheme.colorScheme.surface
                ) { Icon(Icons.Filled.DownloadForOffline, t("Cartes hors ligne")) }
            }
            // Localisation refusée : explication
            if (!locationGranted && live == null) Surface(
                shape = RoundedCornerShape(16.dp), shadowElevation = 3.dp,
                modifier = Modifier.align(Alignment.TopStart).padding(top = 64.dp, start = 12.dp).widthIn(max = 260.dp)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(t("Localisation désactivée"), style = MaterialTheme.typography.titleSmall)
                    Text(t("Autorise-la pour te voir sur la carte et suivre tes activités."), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = {
                        askMapLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    }) { Text(t("Autoriser")) }
                }
            }
            Text(
                t("© OpenMapTiles · © OpenStreetMap"),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp)
                    .background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.75f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 4.dp),
                color = androidx.compose.ui.graphics.Color.DarkGray
            )
            if (busy != null) Surface(
                shape = RoundedCornerShape(20.dp), shadowElevation = 3.dp,
                modifier = Modifier.align(Alignment.Center)
            ) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp)); Text(busy!!)
                }
            }
        }

        // ---- Panneau du bas ----
        Surface(
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            tonalElevation = 2.dp, shadowElevation = 12.dp,
            modifier = Modifier.align(Alignment.CenterHorizontally).widthIn(max = 640.dp).fillMaxWidth()
        ) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Poignée
                Box(
                    Modifier.padding(top = 8.dp).align(Alignment.CenterHorizontally).size(width = 36.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.outlineVariant)
                )
                val l = live
                when {
                    l != null -> LivePanel(l, best, planned, onPause = { Tracker.togglePause() }, onStop = { confirmStop = true })
                    mode == "clubs" -> ClubsPanel(
                        selected, places, handle.map?.cameraPosition?.target, busy != null, placesError,
                        onSearch = {
                            val target = handle.map?.cameraPosition?.target ?: return@ClubsPanel
                            busy = t("Recherche des clubs…"); placesError = null; selected = null
                            scope.launch {
                                try { places = Places.around(target.latitude, target.longitude) }
                                catch (e: Exception) { placesError = e.message }
                                finally { busy = null }
                            }
                        },
                        onPick = { p ->
                            selected = p
                            handle.map?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(p.lat, p.lng), 15.5))
                        },
                        onClose = { selected = null }
                    )
                    mode == "parcours" && planned == null -> LoopsPanel(
                        type, ::setType, loopKm, { loopKm = it }, loops, loopIndex, { loopIndex = it },
                        loading = busy != null,
                        onPropose = {
                            val from = myPosition() ?: handle.map?.cameraPosition?.target
                            if (from == null) { routeError = t("Autorise la localisation pour proposer des boucles autour de toi."); return@LoopsPanel }
                            busy = t("Calcul des boucles…"); routeError = null; loopIndex = 0
                            scope.launch {
                                try { loops = Routing.loops(from.latitude, from.longitude, loopKm * 1000.0, type) }
                                catch (e: Exception) { routeError = e.message; loops = emptyList() }
                                finally { busy = null }
                            }
                        },
                        onChoose = { Tracker.setPlanned(loops.getOrNull(loopIndex)) },
                        onReset = { loops = emptyList() }
                    )
                    else -> StartPanel(
                        type = type, onType = { t ->
                            setType(t)
                            destination?.let { if (planned?.label == t("Vers la destination")) routeTo(it) }
                        },
                        planned = planned, destination = destination != null, retry = retryRoute != 0L, best = best,
                        activities = outings.size,
                        onStart = { start() },
                        onCancelRoute = {
                            Tracker.setPlanned(null); destination = null; loops = emptyList(); onClearRetry()
                        },
                        onHistory = onHistory
                    )
                }
                if (routeError != null && live == null) Text(routeError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    if (confirmStop) AlertDialog(
        onDismissRequest = { confirmStop = false },
        title = { Text(t("Terminer l'activité ?")) },
        text = { Text(t("Elle sera enregistrée (si tu as parcouru au moins 50 m).")) },
        confirmButton = { TextButton(onClick = { confirmStop = false; TrackingService.stop(context) }) { Text(t("Terminer")) } },
        dismissButton = { TextButton(onClick = { confirmStop = false }) { Text(t("Continuer")) } }
    )
    finished?.let { f -> FinishedDialog(f, onDismiss = { finished = null }) }
}

private fun hasAnyLocation(context: android.content.Context) =
    androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
        androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED

/** Choix de l'activité : trois grandes pastilles. */
@Composable
private fun TypePicker(type: OutingType, onType: (OutingType) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutingType.entries.forEach { t ->
            val on = t == type
            Surface(
                onClick = { onType(t) },
                shape = RoundedCornerShape(18.dp),
                color = if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                border = if (on) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                modifier = Modifier.weight(1f)
            ) {
                Column(Modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(t.emoji, style = MaterialTheme.typography.titleLarge)
                    Text(t.label, style = MaterialTheme.typography.labelMedium, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
    }
}

/** Gros bouton rond « GO ». */
@Composable
private fun GoButton(onClick: () -> Unit, enabled: Boolean = true) {
    Button(
        onClick = onClick, enabled = enabled, shape = androidx.compose.foundation.shape.CircleShape,
        modifier = Modifier.size(72.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
    ) { Text("GO", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black) }
}

@Composable
private fun StartPanel(
    type: OutingType, onType: (OutingType) -> Unit, planned: PlannedRoute?, destination: Boolean, retry: Boolean,
    best: Outing?, activities: Int, onStart: () -> Unit, onCancelRoute: () -> Unit, onHistory: () -> Unit
) {
    if (planned != null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(androidx.compose.foundation.shape.CircleShape).background(androidx.compose.ui.graphics.Color(ROUTE_COLOR)))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(planned.label, style = MaterialTheme.typography.titleMedium)
                Text(
                    t("%1\$s · environ %2\$s", km(planned.lengthM), estimate(planned.lengthM, type)) +
                        (best?.let { t(" · record %1\$s", formatClock(it.movingMs)) } ?: ""),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = onCancelRoute) { Text(t("Annuler")) }
        }
    } else {
        Text(
            if (destination) t("Destination choisie") else t("Appui long sur la carte pour choisir une destination"),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        TypePicker(type, onType, Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        GoButton(onClick = onStart)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onHistory) {
            Icon(Icons.AutoMirrored.Filled.List, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(t("Mes activités (%1\$s)", activities))
        }
        Spacer(Modifier.weight(1f))
        Text(t("GPS sans internet · tracé privé"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LoopsPanel(
    type: OutingType, onType: (OutingType) -> Unit, km: Float, onKm: (Float) -> Unit,
    loops: List<PlannedRoute>, index: Int, onIndex: (Int) -> Unit, loading: Boolean,
    onPropose: () -> Unit, onChoose: () -> Unit, onReset: () -> Unit
) {
    val range = if (type == OutingType.BIKE) 5f..60f else 1f..20f
    val value = km.coerceIn(range)
    if (loops.isNotEmpty()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(t("%1\$s Boucles de %%.0f km", type.emoji).format(value), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onReset) { Text(t("Modifier")) }
        }
    } else {
    TypePicker(type, onType)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(t("Boucle de"), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(8.dp))
        Text("%.0f km".format(value), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text("≈ ${estimate(value * 1000.0, type)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    androidx.compose.material3.Slider(
        value = value, onValueChange = { onKm(kotlin.math.round(it)) }, valueRange = range,
        steps = (range.endInclusive - range.start).toInt() - 1
    )
    }
    if (loops.isEmpty()) {
        Button(onClick = onPropose, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Search, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(t("Proposer des boucles autour de moi"))
        }
        Text(t("Par les chemins, parcs et rues calmes ; jamais d'autoroute ni de voie rapide."),
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        loops.forEachIndexed { i, r ->
            val on = i == index
            Surface(
                onClick = { onIndex(i) }, shape = RoundedCornerShape(16.dp),
                color = if (on) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(14.dp).clip(androidx.compose.foundation.shape.CircleShape).background(androidx.compose.ui.graphics.Color(LOOP_COLORS[i % LOOP_COLORS.size])))
                    Spacer(Modifier.width(10.dp))
                    Text(r.label, Modifier.weight(1f), fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
                    Text("${km(r.lengthM)} · ${estimate(r.lengthM, type)}", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onChoose, modifier = Modifier.weight(1f)) { Text(t("Choisir cette boucle")) }
            OutlinedButton(onClick = onPropose, enabled = !loading) { Icon(Icons.Filled.Replay, t("Autres boucles")) }
        }
    }
}

@Composable
private fun LivePanel(
    l: com.goodlife.app.track.LiveOuting, best: Outing?, planned: PlannedRoute?,
    onPause: () -> Unit, onStop: () -> Unit
) {
    val bike = l.type == OutingType.BIKE
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "${l.type.emoji} ${l.type.label}",
            style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f)
        )
        Text(
            when { !l.gpsOk -> t("Recherche du GPS…"); l.paused -> t("En pause"); l.autoPaused -> t("À l'arrêt"); else -> "" },
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    // Guidage : distance restante et écart au parcours
    val last = l.points.lastOrNull()
    if (planned != null && last != null) {
        val (remaining, off) = remember(l.points.size, planned) { Routing.progress(planned.points, last.lat, last.lng) }
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = when {
                remaining < 40 -> successColor.copy(alpha = 0.18f)
                off > 45 -> MaterialTheme.colorScheme.errorContainer
                else -> androidx.compose.ui.graphics.Color(ROUTE_COLOR).copy(alpha = 0.15f)
            }
        ) {
            Text(
                when {
                    remaining < 40 -> t("Arrivé ! Bravo 🎉")
                    off > 45 -> t("Tu t'éloignes du parcours (%1\$s m) · reste %2\$s", off.toInt(), km(remaining))
                    else -> t("Reste %1\$s · %2\$s", km(remaining), planned.label)
                },
                style = MaterialTheme.typography.titleSmall, modifier = Modifier.fillMaxWidth().padding(10.dp)
            )
        }
    }
    Row(Modifier.fillMaxWidth()) {
        Stat(t("Distance (km)"), "%.2f".format(l.distanceM / 1000), Modifier.weight(1f), big = true)
        Stat(t("Temps"), formatClock(l.movingMs), Modifier.weight(1f), big = true)
    }
    Row(Modifier.fillMaxWidth()) {
        if (bike) {
            Stat("km/h", formatKmh(l.speed), Modifier.weight(1f))
            Stat(t("Moy. km/h"), formatKmh(l.avgSpeed), Modifier.weight(1f))
        } else {
            Stat(t("Allure /km"), formatPace(l.speed), Modifier.weight(1f))
            Stat(t("Moy. /km"), formatPace(l.avgSpeed), Modifier.weight(1f))
        }
        Stat(t("Dénivelé +"), "%.0f m".format(l.elevGainM), Modifier.weight(1f))
    }
    if (best != null) Text(
        t("Record à battre : %1\$s pour %2\$s km", formatClock(best.movingMs), "%.2f".format(best.distanceM / 1000)),
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary
    )
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        FilledTonalButton(onClick = onPause, modifier = Modifier.weight(1f).height(52.dp)) {
            Icon(if (l.paused) Icons.Filled.PlayArrow else Icons.Filled.Pause, null)
            Spacer(Modifier.width(6.dp)); Text(if (l.paused) t("Reprendre") else t("Pause"))
        }
        Button(
            onClick = onStop, modifier = Modifier.weight(1f).height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        ) {
            Icon(Icons.Filled.Stop, null); Spacer(Modifier.width(6.dp)); Text(t("Terminer"))
        }
    }
}

@Composable
private fun ClubsPanel(
    selected: SportPlace?, places: List<SportPlace>, center: LatLng?, loading: Boolean, error: String?,
    onSearch: () -> Unit, onPick: (SportPlace) -> Unit, onClose: () -> Unit
) {
    val context = LocalContext.current
    if (selected != null) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(selected.name, style = MaterialTheme.typography.titleLarge)
                Text(
                    listOf(selected.kind, selected.sports).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary
                )
            }
            TextButton(onClick = onClose) { Text(t("Fermer")) }
        }
        if (selected.address.isNotBlank()) Text(selected.address, style = MaterialTheme.typography.bodySmall)
        Text(t("Tarif : %1\$s", selected.price.ifBlank { t("non renseigné") }), style = MaterialTheme.typography.bodyMedium)
        if (selected.hours.isNotBlank()) Text(t("Horaires : %1\$s", selected.hours), style = MaterialTheme.typography.bodySmall)
        if (selected.phone.isNotBlank()) Text(t("Téléphone : %1\$s", selected.phone), style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (selected.website.isNotBlank()) Button(onClick = {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(selected.website))) }
            }) { Icon(Icons.Filled.Language, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(t("Site du club")) }
            OutlinedButton(onClick = {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:${selected.lat},${selected.lng}?q=${selected.lat},${selected.lng}(${Uri.encode(selected.name)})")))
                }
            }) { Text(t("Y aller")) }
        }
        Text(t("Données © OpenStreetMap, à vérifier auprès du club."), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(t("Clubs et lieux de sport"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            FilledTonalButton(onClick = onSearch, enabled = !loading) {
                Icon(Icons.Filled.Search, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(t("Chercher ici"))
            }
        }
        if (places.isEmpty()) Text(
            t("Place la carte sur ta zone, puis cherche les clubs, salles, piscines et stades autour."),
            style = MaterialTheme.typography.bodySmall
        ) else {
            val sorted = remember(places, center) {
                places.sortedBy { p -> center?.let { Tracker.haversine(it.latitude, it.longitude, p.lat, p.lng) } ?: 0.0 }.take(8)
            }
            Column(Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
                sorted.forEachIndexed { i, p ->
                    if (i > 0) HorizontalDivider()
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onPick(p) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(10.dp).clip(androidx.compose.foundation.shape.CircleShape).background(androidx.compose.ui.graphics.Color(PLACE_COLOR)))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.name, fontWeight = FontWeight.Medium, maxLines = 1)
                            Text(listOf(p.kind, p.sports).filter { it.isNotBlank() }.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                        center?.let { c ->
                            Text(km(Tracker.haversine(c.latitude, c.longitude, p.lat, p.lng)), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
            Text(t("%1\$s lieux trouvés · touche un point ou un nom", places.size), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (error != null) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
}

/** Cartes hors ligne : zones téléchargées et téléchargement de la zone affichée. */
@Composable
private fun OfflineZonesScreen(bounds: LatLngBounds?, onBack: () -> Unit) {
    val context = LocalContext.current
    val zones by OfflineMaps.zones.collectAsState()
    var name by remember { mutableStateOf(t("Ma zone")) }
    var detailed by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { OfflineMaps.refresh(context) }
    val maxZoom = if (detailed) 16 else 14
    val tiles = bounds?.let { OfflineMaps.tileCount(it, maxZoom) } ?: 0L
    val bytes = bounds?.let { OfflineMaps.estimateBytes(it, maxZoom) } ?: 0L
    fun mo(b: Long) = "%.0f Mo".format(b / 1_048_576.0)

    com.goodlife.app.ui.ScreenColumn {
        com.goodlife.app.ui.SubScreenHeader(t("Cartes hors ligne"), onBack)
        Text(
            t("Le GPS marche sans internet ; une zone téléchargée affiche aussi le fond de carte sans réseau (campagne, forêt, montagne). Télécharge seulement les zones où tu vas, pour ne pas remplir ton téléphone."),
            style = MaterialTheme.typography.bodyMedium
        )
        com.goodlife.app.ui.SectionCard(title = t("Télécharger la zone affichée")) {
            if (bounds == null) Text(t("Ouvre la carte, cadre la zone voulue, puis reviens ici."), style = MaterialTheme.typography.bodySmall)
            else {
                OutlinedTextField(name, { name = it.take(40) }, label = { Text(t("Nom de la zone")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(detailed, { detailed = true }, label = { Text(t("Détaillée (chemins)")) })
                    FilterChip(!detailed, { detailed = false }, label = { Text(t("Légère (rues)")) })
                }
                val tooBig = tiles > OfflineMaps.MAX_TILES
                Text(
                    if (tooBig) t("Zone trop grande pour ce niveau de détail : zoome davantage sur la carte ou choisis « Légère ».")
                    else t("Taille estimée : environ %1\$s", mo(bytes)),
                    color = if (tooBig) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyMedium
                )
                Button(enabled = !tooBig && name.isNotBlank(), onClick = {
                    error = null
                    OfflineMaps.download(context, name.trim(), bounds, maxZoom) { error = it }
                }) { Icon(Icons.Filled.DownloadForOffline, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(t("Télécharger")) }
                Text(t("Garde l'app ouverte pendant le téléchargement."), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        com.goodlife.app.ui.SectionCard(title = t("Mes zones")) {
            if (zones.isEmpty()) Text(t("Aucune zone téléchargée."), color = MaterialTheme.colorScheme.onSurfaceVariant)
            zones.sortedByDescending { it.createdAt }.forEachIndexed { i, z ->
                if (i > 0) HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(z.name, fontWeight = FontWeight.Medium)
                        Text(
                            if (z.complete) t("Prête · %1\$s", mo(z.sizeBytes)) else t("Téléchargement %1\$s %% · %2\$s", (z.progress * 100).toInt(), mo(z.sizeBytes)),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!z.complete) androidx.compose.material3.LinearProgressIndicator(
                            progress = { z.progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                        )
                    }
                    TextButton(onClick = { OfflineMaps.delete(z) }) { Icon(Icons.Filled.Delete, t("Supprimer")) }
                }
            }
        }
        Text(t("Carte : OpenFreeMap © OpenMapTiles, données © contributeurs OpenStreetMap."),
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
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
private fun FinishedDialog(f: FinishedOuting, onDismiss: () -> Unit) {
    val o = f.outing
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.EmojiEvents, null, tint = MaterialTheme.colorScheme.tertiary) },
        title = { Text(if (f.record) t("Nouveau record !") else t("Bravo, activité terminée !")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${o.type.emoji} ${"%.2f".format(o.distanceM / 1000)} km en ${formatClock(o.movingMs)}", style = MaterialTheme.typography.titleMedium)
                Text(
                    (if (o.type == OutingType.BIKE) t("Moyenne %1\$s km/h", formatKmh(o.avgSpeed)) else t("Allure moyenne %1\$s min/km", formatPace(o.avgSpeed))) +
                        t(" · dénivelé +%1\$s m", "%.0f".format(o.elevGainM)),
                    style = MaterialTheme.typography.bodyMedium
                )
                if (f.previousBest != null) Text(
                    if (f.record) t("Tu bats ton record de %1\$s !", formatClock(f.previousBest.movingMs - o.movingMs))
                    else if (!o.valid) t("Parcours pas entièrement suivi : pas compté pour le record.")
                    else t("Record : %1\$s. Tu y es presque !", formatClock(f.previousBest.movingMs)),
                    color = if (f.record) successColor else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(if (f.xp > 0) t("+%1\$s XP", f.xp) else t("XP sport du jour déjà au maximum"), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(t("Super")) } }
    )
}

// ---------------------------------------------------------------------------------------------
// Historique et détail
// ---------------------------------------------------------------------------------------------

@Composable
private fun OutingHistory(onBack: () -> Unit, onOpen: (Long) -> Unit) {
    val outings by Repo.outings.collectAsState()
    com.goodlife.app.ui.ScreenColumn {
        com.goodlife.app.ui.SubScreenHeader(t("Mes activités"), onBack)
        if (outings.isEmpty()) Text(
            t("Aucune activité pour l'instant. Lance-toi depuis la carte !"),
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
                    if (record) Icon(Icons.Filled.EmojiEvents, t("Record"), tint = MaterialTheme.colorScheme.tertiary)
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
            Text(t("%1\$s à %2\$s", formatDay(o.start), formatTime(o.start)), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth()) {
                Stat(t("Distance (km)"), "%.2f".format(o.distanceM / 1000), Modifier.weight(1f))
                Stat(t("Temps"), formatClock(o.movingMs), Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth()) {
                if (o.type == OutingType.BIKE) {
                    Stat(t("Moyenne (km/h)"), formatKmh(o.avgSpeed), Modifier.weight(1f))
                    Stat(t("Max (km/h)"), formatKmh(o.maxSpeed), Modifier.weight(1f))
                } else {
                    Stat(t("Allure (min/km)"), formatPace(o.avgSpeed), Modifier.weight(1f))
                    Stat(t("Moyenne (km/h)"), formatKmh(o.avgSpeed), Modifier.weight(1f))
                }
                Stat(t("Dénivelé + (m)"), "%.0f".format(o.elevGainM), Modifier.weight(1f))
            }
            if (attempts.size > 1 && best != null) Text(
                t("Record sur ce parcours : %1\$s (%2\$s) · %3\$s passages", formatClock(best.movingMs), formatDay(best.start), attempts.size),
                color = MaterialTheme.colorScheme.tertiary
            )
            if (!o.valid) Text(t("Parcours pas entièrement suivi : cette sortie ne compte pas pour le record."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = { onRetry(o.routeId) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Replay, null); Spacer(Modifier.width(8.dp)); Text(t("Refaire ce parcours et battre mon record"))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { rename = true }) { Text(t("Nommer ce parcours")) }
                TextButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Filled.Delete, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text(t("Supprimer"))
                }
            }
            Text(t("Carte : OpenFreeMap © OpenMapTiles, données © contributeurs OpenStreetMap."),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (rename) {
        var name by remember { mutableStateOf(o.name) }
        AlertDialog(
            onDismissRequest = { rename = false },
            title = { Text(t("Nom du parcours")) },
            text = { OutlinedTextField(name, { name = it.take(40) }, singleLine = true, placeholder = { Text(t("Ex : Tour du lac")) }) },
            confirmButton = { TextButton(onClick = { Repo.renameOuting(o.routeId, name.trim()); rename = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { rename = false }) { Text(t("Annuler")) } }
        )
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text(t("Supprimer cette activité ?")) },
        text = { Text(t("Le tracé et les chiffres seront effacés. L'XP déjà gagnée reste.")) },
        confirmButton = { TextButton(onClick = { confirmDelete = false; Repo.deleteOuting(o.id); onBack() }) { Text(t("Supprimer"), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(t("Annuler")) } }
    )
}
