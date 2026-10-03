@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package com.goodlife.app.ui.screens

import androidx.compose.foundation.layout.FlowRow
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material3.AssistChip
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
fun GoodMap(handle: MapHandle, modifier: Modifier = Modifier, alwaysResumed: Boolean = false) {
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
                // Mini-fenêtre : l'activité est « en pause » mais la carte doit continuer à s'afficher
                Lifecycle.Event.ON_PAUSE -> if (!alwaysResumed) mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStart()
        if (alwaysResumed || lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onResume()
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

private fun MapLibreMap.fit(points: List<TrackPoint>, bottomDp: Int = 24) {
    if (points.size < 2) return
    val b = LatLngBounds.Builder()
    points.forEach { b.include(LatLng(it.lat, it.lng)) }
    // Marges : le haut est couvert par le choix du mode et la recherche, la droite par les boutons ronds
    val d = android.content.res.Resources.getSystem().displayMetrics.density
    runCatching { moveCamera(CameraUpdateFactory.newLatLngBounds(b.build(), (24 * d).toInt(), (130 * d).toInt(), (64 * d).toInt(), ((bottomDp + 16) * d).toInt())) }
}

@SuppressLint("MissingPermission")
private fun showMe(
    map: MapLibreMap, style: Style, context: android.content.Context, follow: Boolean,
    type: OutingType = OutingType.RUN, avatar: Boolean = false, small: Boolean = false
) {
    if (!TrackingService.hasPermission(context)) return
    runCatching {
        val lc = map.locationComponent
        // Suivi plus vif : la caméra rattrape la position deux fois plus vite
        val options = org.maplibre.android.location.LocationComponentOptions.builder(context)
            .trackingAnimationDurationMultiplier(0.5f)
            .compassAnimationEnabled(true)
            .apply {
                // Petite fenêtre (mode réduit) : flèche plus petite, sinon elle cache la carte
                if (small) minZoomIconScale(0.45f).maxZoomIconScale(0.45f).accuracyAlpha(0f)
                // Pendant une sortie, le petit bonhomme animé remplace le point bleu
                if (avatar) {
                    val hidden = com.goodlife.app.R.drawable.map_puck_hidden
                    foregroundDrawable(hidden).foregroundDrawableStale(hidden).backgroundDrawable(hidden).backgroundDrawableStale(hidden)
                        .bearingDrawable(hidden).gpsDrawable(hidden).accuracyAlpha(0f)
                }
            }
            .build()
        if (!lc.isLocationComponentActivated) lc.activateLocationComponent(
            LocationComponentActivationOptions.builder(context, style)
                .locationComponentOptions(options)
                // Une position par seconde (une demi-seconde au plus vite), haute précision
                .locationEngineRequest(
                    org.maplibre.android.location.engine.LocationEngineRequest.Builder(1000L)
                        .setPriority(org.maplibre.android.location.engine.LocationEngineRequest.PRIORITY_HIGH_ACCURACY)
                        .setFastestInterval(500L).build()
                )
                .build()
        ) else lc.applyStyle(options)
        lc.isLocationComponentEnabled = true
        // À pied : la carte tourne avec la boussole (immédiat) ; course et vélo : dans le sens du déplacement (GPS)
        val compass = type == OutingType.WALK
        lc.renderMode = if (follow && !compass) RenderMode.GPS else RenderMode.COMPASS
        // Suivi + zoom en une seule transition (un zoom séparé annulerait le suivi) : bien zoomé, comme un GPS
        val zoom = if (small) 16.0 else if (type == OutingType.BIKE) 17.5 else 18.0
        if (follow) lc.setCameraMode(if (compass) CameraMode.TRACKING_COMPASS else CameraMode.TRACKING_GPS, 500L, zoom, null, null, null)
        else lc.cameraMode = CameraMode.NONE
        lc.lastKnownLocation?.let { if (!follow) map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(it.latitude, it.longitude), 14.0)) }
    }
}

// ---------------------------------------------------------------------------------------------
// Onglet « Carte »
// ---------------------------------------------------------------------------------------------

/** Demande de localisation déjà faite pendant cette session (pour ne pas redemander à chaque ouverture). */
private var mapPermissionAsked = false

/** Durée estimée avec ta vitesse moyenne habituelle (tes sorties enregistrées), sinon une vitesse typique. */
private fun estimate(lengthM: Double, t: OutingType): String = com.goodlife.app.track.etaDuration(lengthM, com.goodlife.app.track.Navigator.personalSpeed(t))

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
    // Choix d'une zone hors ligne sur la carte (le cadre)
    var offlinePicking by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = screen.isNotEmpty()) { screen = if (screen.startsWith("detail:")) "history" else "" }
    when {
        screen == "history" -> OutingHistory(onBack = { screen = "" }, onOpen = { screen = "detail:$it" })
        screen == "offline" -> OfflineZonesScreen(onBack = { screen = "" }, onPick = { offlinePicking = true; screen = "" })
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
            offlinePicking = offlinePicking,
            onOfflinePicking = { offlinePicking = it },
            onOfflineZones = { offlinePicking = false; screen = "offline" }
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@SuppressLint("MissingPermission")
@Composable
private fun OutingsMap(
    retryRoute: Long, onClearRetry: () -> Unit, onHistory: () -> Unit,
    offlinePicking: Boolean, onOfflinePicking: (Boolean) -> Unit, onOfflineZones: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val handle = remember { MapHandle() }
    val live by Tracker.live.collectAsState()
    val planned by Tracker.planned.collectAsState()
    val outings by Repo.outings.collectAsState()
    val settings by Repo.settings.collectAsState()
    val type = runCatching { OutingType.valueOf(settings.preferredOuting) }.getOrDefault(OutingType.RUN)
    fun setType(t: OutingType) = Repo.updateSettings { it.copy(preferredOuting = t.name) }
    val style = com.goodlife.app.track.RouteStyle.of(settings.routeStyle)
    var drawing by remember { mutableStateOf(false) }
    // Un seul calcul à la fois : un nouveau choix annule le précédent (le dernier choix gagne toujours)
    var routeJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

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

    fun routeTo(dest: LatLng, s: com.goodlife.app.track.RouteStyle = style, t0: OutingType = type) {
        if (!hasAnyLocation(context)) { routeError = t("Autorise la localisation pour calculer l'itinéraire depuis ta position."); return }
        busy = t("Calcul de l'itinéraire…"); routeError = null
        routeJob?.cancel()
        routeJob = scope.launch {
            // Position de la carte, sinon une position GPS toute fraîche (téléphone qui n'en connaît pas encore)
            val from = myPosition() ?: freshPosition(context)
            if (from == null) { routeError = t("Position introuvable pour l'instant. Vérifie que la localisation est activée."); busy = null; return@launch }
            try { Tracker.setPlanned(Routing.toDestination(from.latitude, from.longitude, dest.latitude, dest.longitude, t0, s)); busy = null }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { routeError = e.message; busy = null }
        }
    }
    /** Trajet qui suit le trait dessiné au pinceau (et finit à la destination si elle est choisie). */
    fun routeAlong(stroke: List<LatLng>, s: com.goodlife.app.track.RouteStyle = style, t0: OutingType = type, dest: LatLng? = destination) {
        if (stroke.size < 2) return
        busy = t("Calcul du trajet dessiné…"); routeError = null
        routeJob?.cancel()
        routeJob = scope.launch {
            val from = myPosition() ?: (if (hasAnyLocation(context)) freshPosition(context) else null) ?: stroke.first()
            try {
                Tracker.setPlanned(Routing.alongStroke(from.latitude, from.longitude, stroke.map { it.latitude to it.longitude }, t0, s,
                    dest?.latitude ?: Double.NaN, dest?.longitude ?: Double.NaN))
                busy = null
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { routeError = e.message; busy = null }
        }
    }
    /**
     * Recalcule l'itinéraire affiché avec un autre type de trajet ou une autre activité. La « recette » (trait dessiné,
     * destination) est gardée dans l'itinéraire lui-même : ça marche même après être passé par un autre onglet.
     */
    fun recompute(s: com.goodlife.app.track.RouteStyle = style, t0: OutingType = type) {
        val p = Tracker.planned.value ?: return
        val dest = if (p.hasDestination) LatLng(p.destLat, p.destLng) else null
        when {
            p.stroke.size >= 2 -> routeAlong(p.stroke.map { LatLng(it.first, it.second) }, s, t0, dest)
            dest != null -> routeTo(dest, s, t0)
        }
    }
    fun setStyle(s: com.goodlife.app.track.RouteStyle) {
        Repo.updateSettings { it.copy(routeStyle = s.name) }
        loops = emptyList()
        recompute(s)
    }
    /** Retire l'itinéraire (et la destination) : retour à la carte seule. */
    fun cancelRoute() {
        routeJob?.cancel(); busy = null; routeError = null
        Tracker.setPlanned(null); destination = null; loops = emptyList(); onClearRetry()
    }
    // Retour du téléphone sur la carte : on défait d'abord ce qui est ouvert (dessin, club, itinéraire, boucles)
    BackHandler(enabled = live == null && (offlinePicking || drawing || selected != null || planned != null || destination != null || loops.isNotEmpty() || mode != "activite")) {
        when {
            offlinePicking -> onOfflinePicking(false)
            drawing -> drawing = false
            selected != null -> selected = null
            planned != null || destination != null -> cancelRoute()
            loops.isNotEmpty() -> loops = emptyList()
            else -> mode = "activite"
        }
    }

    // ---- Dessins sur la carte ----
    LaunchedEffect(handle.style, live != null, live?.gpsOk, locationGranted) {
        val map = handle.map ?: return@LaunchedEffect
        val style = handle.style ?: return@LaunchedEffect
        showMe(map, style, context, follow = live != null, type = live?.type ?: type, avatar = live != null)
    }
    // Première position connue (parfois quelques secondes après l'ouverture) : la carte se centre une fois sur toi
    LaunchedEffect(handle.map, locationGranted) {
        val m = handle.map ?: return@LaunchedEffect
        if (!locationGranted || Tracker.planned.value != null) return@LaunchedEffect
        repeat(30) {
            val loc = runCatching { m.locationComponent.takeIf { it.isLocationComponentActivated }?.lastKnownLocation }.getOrNull()
            if (loc != null) {
                if (m.cameraPosition.zoom < 10 && Tracker.planned.value == null) m.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(loc.latitude, loc.longitude), 14.0))
                return@LaunchedEffect
            }
            delay(500)
        }
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
        if (pts.size >= 2) map.fit(pts, if (planned != null || loops.isNotEmpty()) 210 else 168)
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

    val sheet = androidx.compose.material3.rememberBottomSheetScaffoldState()
    // Partie visible du tiroir replié : l'essentiel (trajet, GO) ; plus haute pendant une sortie
    val picking = offlinePicking && live == null
    var offlineBounds by remember { mutableStateOf<LatLngBounds?>(null) }
    val offlineZones by com.goodlife.app.track.OfflineMaps.zones.collectAsState()
    // Téléchargements hors ligne interrompus : ils reprennent à l'ouverture de la carte
    LaunchedEffect(Unit) { com.goodlife.app.track.OfflineMaps.refresh(context) }
    val peek = when {
        picking -> 340.dp
        live != null -> 300.dp
        planned != null || loops.isNotEmpty() || mode == "clubs" -> 210.dp
        else -> 168.dp
    }
    val density = androidx.compose.ui.platform.LocalDensity.current
    // Pendant une sortie, ta position est placée au-dessus du tiroir (et pas cachée dessous)
    LaunchedEffect(handle.map, live != null, peek) {
        val m = handle.map ?: return@LaunchedEffect
        if (live == null) return@LaunchedEffect
        delay(800)
        val bottom = with(density) { peek.toPx().toDouble() }
        runCatching { m.locationComponent.paddingWhileTracking(doubleArrayOf(0.0, 0.0, 0.0, bottom * 0.7), 300L) }
    }
    val searchVisible = live == null && !drawing && !picking && mode == "activite"
    val topInset = if (live == null && !drawing && !picking) (if (searchVisible) 112.dp else 60.dp) else 12.dp
    androidx.compose.material3.BottomSheetScaffold(
        scaffoldState = sheet,
        sheetPeekHeight = peek,
        sheetMaxWidth = 640.dp,
        sheetShadowElevation = 12.dp,
        sheetContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        sheetContent = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 560.dp).verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Calcul en cours ou erreur : tout en haut du tiroir, visible même replié
                if (busy != null && live == null) Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp)); Text(busy!!, style = MaterialTheme.typography.bodySmall)
                }
                if (routeError != null && live == null) Text(routeError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                val l = live
                when {
                    picking -> OfflinePickPanel(
                        offlineBounds, offlineZones.size,
                        onCancel = { onOfflinePicking(false) },
                        onZones = onOfflineZones,
                        onStarted = onOfflineZones
                    )
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
                        onClose = { selected = null },
                        // « Y aller » : l'itinéraire est calculé ici, dans GoodLife
                        onGo = { p ->
                            val d = LatLng(p.lat, p.lng)
                            selected = null; mode = "activite"; destination = d; loops = emptyList()
                            routeTo(d)
                        }
                    )
                    mode == "parcours" && planned == null -> {
                    // Pendant qu'on règle la distance, les rues autour sont déjà préparées (tuiles de la carte)
                    LaunchedEffect(loopKm, type, loops.isEmpty()) {
                        if (loops.isNotEmpty()) return@LaunchedEffect
                        kotlinx.coroutines.delay(700)
                        val from = myPosition() ?: handle.map?.cameraPosition?.target ?: return@LaunchedEffect
                        Routing.warmUp(from.latitude, from.longitude, loopKm * 1000.0, type)
                    }
                    LoopsPanel(
                        type, ::setType, style, ::setStyle, loopKm, { loopKm = it }, loops, loopIndex, { loopIndex = it },
                        loading = busy != null,
                        onPropose = {
                            busy = t("Calcul des boucles…"); routeError = null; loopIndex = 0
                            routeJob?.cancel()
                            routeJob = scope.launch {
                                val from = myPosition() ?: (if (hasAnyLocation(context)) freshPosition(context) else null) ?: handle.map?.cameraPosition?.target
                                if (from == null) { routeError = t("Autorise la localisation pour proposer des boucles autour de toi."); busy = null; return@launch }
                                try { loops = Routing.loops(from.latitude, from.longitude, loopKm * 1000.0, type, style); busy = null }
                                catch (e: kotlinx.coroutines.CancellationException) { throw e }
                                catch (e: Exception) { routeError = e.message; loops = emptyList(); busy = null }
                            }
                        },
                        onChoose = { Tracker.setPlanned(loops.getOrNull(loopIndex)) },
                        onReset = { loops = emptyList() }
                    )
                    }
                    else -> StartPanel(
                        type = type, onType = { t -> setType(t); recompute(t0 = t) },
                        style = style, onStyle = ::setStyle,
                        planned = planned, destination = destination != null, retry = retryRoute != 0L, best = best,
                        activities = outings.size,
                        onStart = { start() },
                        onCancelRoute = { cancelRoute() },
                        onHistory = onHistory
                    )
                }
            }
        }
    ) { _ ->
        Box(Modifier.fillMaxSize()) {
            GoodMap(handle, Modifier.fillMaxSize())

            // Petit bonhomme animé à ta position pendant une sortie
            val l = live
            if (l != null) {
                var spot by remember { mutableStateOf<androidx.compose.ui.geometry.Offset?>(null) }
                var faceLeft by remember { mutableStateOf(false) }
                var prev by remember { mutableStateOf<LatLng?>(null) }
                val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
                LaunchedEffect(lifecycle) {
                    // Seulement quand l'écran est visible, et au rythme de l'affichage : écran éteint ou app cachée,
                    // la boucle s'arrête (pas de calcul pour rien pendant une longue sortie)
                    lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                    while (true) {
                        androidx.compose.runtime.withFrameNanos { }
                        val m = handle.map
                        val loc = runCatching { m?.locationComponent?.lastKnownLocation }.getOrNull()
                        spot = if (m != null && loc != null) m.projection.toScreenLocation(LatLng(loc.latitude, loc.longitude)).let { androidx.compose.ui.geometry.Offset(it.x, it.y) } else null
                        // Tourné vers la gauche quand on avance vers la gauche de l'écran (d'après les deux dernières positions)
                        if (m != null && loc != null) {
                            val here = LatLng(loc.latitude, loc.longitude)
                            val before = prev
                            if (before == null) prev = here
                            else if (before.distanceTo(here) > 4.0) {
                                val dx = m.projection.toScreenLocation(here).x - m.projection.toScreenLocation(before).x
                                val dy = m.projection.toScreenLocation(here).y - m.projection.toScreenLocation(before).y
                                if (kotlin.math.abs(dx) > kotlin.math.abs(dy) * 0.3f) faceLeft = dx < 0
                                prev = here
                            }
                        }
                        delay(33)
                    }
                    }
                }
                spot?.let { pt ->
                    val half = with(density) { 28.dp.roundToPx() }
                    MapAvatar(
                        l.type, moving = !l.paused && !l.autoPaused && l.speed > 0.6,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.offset { androidx.compose.ui.unit.IntOffset(pt.x.toInt() - half, pt.y.toInt() - half * 2 + 6) }
                            .graphicsLayer { scaleX = if (faceLeft) -1f else 1f }
                    )
                }
            }

            // Boutons ronds à droite (sous la barre de recherche)
            Column(Modifier.align(Alignment.TopEnd).padding(top = topInset + 8.dp, end = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SmallFloatingActionButton(onClick = {
                    if (!locationGranted) askMapLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    val m = handle.map; val s = handle.style
                    if (m != null && s != null) showMe(m, s, context, follow = live != null, type = live?.type ?: type, avatar = live != null)
                }, containerColor = MaterialTheme.colorScheme.surface) { Icon(Icons.Filled.MyLocation, t("Ma position")) }
                if (live == null && !picking) SmallFloatingActionButton(
                    onClick = { drawing = false; onOfflinePicking(true) },
                    containerColor = MaterialTheme.colorScheme.surface
                ) { Icon(Icons.Filled.DownloadForOffline, t("Cartes hors ligne")) }
                // Pinceau : dessiner son trajet
                if (live == null && !picking && mode != "clubs") SmallFloatingActionButton(
                    onClick = { drawing = true; mode = "activite"; loops = emptyList(); routeError = null },
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                ) { Icon(Icons.Filled.Brush, t("Dessiner un trajet")) }
            }
            // En haut : le choix du mode (compact) et la recherche d'un lieu dessous (mode Activité)
            if (live == null && !drawing && !picking) Column(
                Modifier.align(Alignment.TopCenter).padding(top = 8.dp, start = 12.dp, end = 12.dp).widthIn(max = 480.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(shape = RoundedCornerShape(20.dp), shadowElevation = 3.dp, color = MaterialTheme.colorScheme.surface) {
                    Row(Modifier.padding(3.dp)) {
                        listOf("activite" to t("Activité"), "parcours" to t("Parcours"), "clubs" to t("Clubs")).forEach { (id, label) ->
                            val on = mode == id
                            Surface(
                                onClick = { mode = id; selected = null; routeError = null },
                                shape = RoundedCornerShape(18.dp),
                                color = if (on) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent
                            ) {
                                Text(
                                    label, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
                if (searchVisible) MapSearchBar(
                    near = { myPosition() ?: handle.map?.cameraPosition?.target },
                    onPick = { place ->
                        val d = LatLng(place.lat, place.lng)
                        destination = d; loops = emptyList()
                        handle.map?.animateCamera(CameraUpdateFactory.newLatLngZoom(d, 13.0))
                        routeTo(d)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                // Itinéraire affiché dans un autre mode (clubs, boucles) : on peut le retirer d'ici
                if (planned != null && mode != "activite") AssistChip(
                    onClick = { cancelRoute() },
                    label = { Text(t("Retirer l'itinéraire")) },
                    leadingIcon = { Icon(Icons.Filled.Close, null, Modifier.size(18.dp)) },
                    colors = androidx.compose.material3.AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surface)
                )
            }
            // Sans réseau : la carte téléchargée utilisée ici (ou aucune)
            if (!picking) OfflineBanner(
                handle.map,
                Modifier.align(Alignment.TopStart).padding(top = topInset + (if (planned != null && mode != "activite") 56.dp else 8.dp), start = 12.dp, end = 64.dp)
            )
            if (picking) OfflineFrame(handle.map, top = 72.dp, bottom = peek + 16.dp, onBounds = { offlineBounds = it })
            if (drawing) BrushOverlay(handle.map, onStroke = { stroke -> drawing = false; routeAlong(stroke, dest = destination) }, onCancel = { drawing = false })
            // Localisation refusée : explication
            if (!locationGranted && live == null) Surface(
                shape = RoundedCornerShape(16.dp), shadowElevation = 3.dp,
                modifier = Modifier.align(Alignment.TopStart).padding(top = topInset + 8.dp, start = 12.dp).widthIn(max = 260.dp)
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(t("Localisation désactivée"), style = MaterialTheme.typography.titleSmall)
                    Text(t("Autorise-la pour te voir sur la carte et suivre tes activités."), style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = {
                        askMapLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    }) { Text(t("Autoriser")) }
                }
            }
            // Mentions des données, juste au-dessus du tiroir (en les touchant : sources, licences et liens)
            var credits by remember { mutableStateOf(false) }
            Text(
                t("OpenFreeMap © OpenMapTiles © OpenStreetMap"),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 6.dp, bottom = peek + 4.dp)
                    .background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.75f), RoundedCornerShape(6.dp))
                    .clickable { credits = true }
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                color = androidx.compose.ui.graphics.Color.DarkGray,
                textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
            )
            if (credits) MapCreditsDialog(onDismiss = { credits = false })
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

/** Position GPS toute fraîche (10 s au plus), quand la carte n'en connaît pas encore. */
@SuppressLint("MissingPermission")
private suspend fun freshPosition(context: android.content.Context): LatLng? = kotlinx.coroutines.withTimeoutOrNull(10_000) {
    kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        runCatching {
            com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(context)
                .getCurrentLocation(com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { loc -> if (cont.isActive) cont.resumeWith(Result.success(loc?.let { LatLng(it.latitude, it.longitude) })) }
                .addOnFailureListener { if (cont.isActive) cont.resumeWith(Result.success(null)) }
        }.onFailure { if (cont.isActive) cont.resumeWith(Result.success(null)) }
    }
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
    best: Outing?, activities: Int, onStart: () -> Unit, onCancelRoute: () -> Unit, onHistory: () -> Unit,
    style: com.goodlife.app.track.RouteStyle = com.goodlife.app.track.RouteStyle.BALANCED,
    onStyle: (com.goodlife.app.track.RouteStyle) -> Unit = {}
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
        ElevationProfile(planned)
        // Le type de trajet ne s'applique qu'aux itinéraires recalculables (destination, trait dessiné)
        if (!retry && planned.reroutable) StylePicker(style, onStyle)
    } else {
        Text(
            if (destination) t("Destination choisie") else t("Cherche un lieu, fais un appui long sur la carte, ou dessine ton trajet ✏️"),
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
    type: OutingType, onType: (OutingType) -> Unit,
    style: com.goodlife.app.track.RouteStyle, onStyle: (com.goodlife.app.track.RouteStyle) -> Unit,
    km: Float, onKm: (Float) -> Unit,
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
        StylePicker(style, onStyle)
        Button(onClick = onPropose, enabled = !loading, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.Search, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(t("Proposer des boucles autour de moi"))
        }
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
                    Text("${km(r.lengthM)} · ${estimate(r.lengthM, type)}" + (if (r.hasElevation) " · D+ ${r.gainM.toInt()} m" else ""), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        loops.getOrNull(index)?.let { ElevationProfile(it) }
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
    val nav by com.goodlife.app.track.Navigator.state.collectAsState()
    // L'heure d'arrivée avance avec le temps, même sans nouvelle position
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(15_000); now = System.currentTimeMillis() } }
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
    // Guidage : reste, heure d'arrivée et temps restant (avec ta vitesse habituelle), recalcul si besoin
    val n = nav
    if (planned != null && n != null) {
        val speed = com.goodlife.app.track.Navigator.etaSpeed(l)
        val arrived = n.remainingM < 40
        val justRerouted = n.reroutedAt > 0 && now - n.reroutedAt < 20_000
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = when {
                arrived -> successColor.copy(alpha = 0.18f)
                n.offM > com.goodlife.app.track.Navigator.OFF_ROUTE_M -> MaterialTheme.colorScheme.errorContainer
                else -> androidx.compose.ui.graphics.Color(ROUTE_COLOR).copy(alpha = 0.14f)
            }
        ) {
            Column(Modifier.fillMaxWidth().padding(10.dp)) {
                if (arrived) Text(t("Arrivé ! Bravo 🎉"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                else Row(Modifier.fillMaxWidth()) {
                    NavStat(t("restants"), km(n.remainingM), Modifier.weight(1f))
                    NavStat(t("arrivée"), com.goodlife.app.track.etaClock(n.remainingM, speed), Modifier.weight(1f))
                    NavStat(t("temps restant"), com.goodlife.app.track.etaDuration(n.remainingM, speed), Modifier.weight(1f))
                }
                val info = when {
                    arrived -> null
                    n.rerouting -> t("Hors itinéraire : nouveau calcul…")
                    justRerouted -> t("Itinéraire recalculé depuis ta position")
                    n.offM > com.goodlife.app.track.Navigator.OFF_ROUTE_M ->
                        if (planned.hasDestination) t("Tu t'éloignes de l'itinéraire (%1\$s m)", n.offM.toInt())
                        else t("Tu t'éloignes du parcours (%1\$s m)", n.offM.toInt())
                    else -> planned.label
                }
                if (info != null) Text(info, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp).align(Alignment.CenterHorizontally))
            }
        }
    }
    // En direct : vitesse, distance, temps
    Row(Modifier.fillMaxWidth()) {
        NavStat("km/h", formatKmh(l.speed), Modifier.weight(1f))
        NavStat(t("km"), "%.2f".format(l.distanceM / 1000), Modifier.weight(1f))
        NavStat(t("temps"), formatClock(l.movingMs), Modifier.weight(1f))
    }
    Text(
        listOfNotNull(
            t("moy. %1\$s km/h", formatKmh(l.avgSpeed)),
            if (l.type == OutingType.RUN) t("allure %1\$s /km", formatPace(l.avgSpeed)) else null,
            t("D+ %1\$s m", "%.0f".format(l.elevGainM))
        ).joinToString(" · "),
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center
    )
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
    onSearch: () -> Unit, onPick: (SportPlace) -> Unit, onClose: () -> Unit, onGo: (SportPlace) -> Unit
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
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (selected.website.isNotBlank()) Button(onClick = {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(selected.website))) }
            }) { Icon(Icons.Filled.Language, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(t("Site du club")) }
            OutlinedButton(onClick = { onGo(selected) }) { Text(t("Y aller")) }
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
                        Modifier.fillMaxWidth().clickable { onPick(p) }.padding(vertical = 8.dp),
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
                Text("${o.type.emoji} " + t("%1\$s km en %2\$s", "%.2f".format(o.distanceM / 1000), formatClock(o.movingMs)), style = MaterialTheme.typography.titleMedium)
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
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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

/**
 * Mini-fenêtre (Picture-in-Picture) pendant une sortie : la carte suit ta position avec l'itinéraire, et en bas la
 * vitesse, les km restants et l'heure d'arrivée. Aucune interaction : toucher la fenêtre rouvre l'app.
 */
@Composable
fun PipNavigation() {
    val context = LocalContext.current
    val handle = remember { MapHandle() }
    val live by Tracker.live.collectAsState()
    val planned by Tracker.planned.collectAsState()
    val nav by com.goodlife.app.track.Navigator.state.collectAsState()
    LaunchedEffect(handle.style) {
        val map = handle.map ?: return@LaunchedEffect
        val style = handle.style ?: return@LaunchedEffect
        showMe(map, style, context, follow = true, type = live?.type ?: OutingType.RUN, small = true)
    }
    LaunchedEffect(handle.style, live?.points?.size, planned) {
        val style = handle.style ?: return@LaunchedEffect
        val route = planned?.points.orEmpty()
        style.line("planned-casing", route, android.graphics.Color.WHITE, 9f)
        style.line("planned", route, ROUTE_COLOR, 6f)
        style.line("track", live?.points.orEmpty(), TRACK_COLOR, 5f)
    }
    Box(Modifier.fillMaxSize()) {
        GoodMap(handle, Modifier.fillMaxSize(), alwaysResumed = true)
        val l = live
        if (l != null) Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
        ) {
            Column(Modifier.padding(horizontal = 6.dp, vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${formatKmh(l.speed)} km/h", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                val n = nav
                Text(
                    if (planned != null && n != null) {
                        if (n.remainingM < 40) t("Arrivé !")
                        else t("%1\$s · %2\$s", km(n.remainingM), com.goodlife.app.track.etaClock(n.remainingM, com.goodlife.app.track.Navigator.etaSpeed(l)))
                    } else "%.2f km · %s".format(l.distanceM / 1000, formatClock(l.movingMs)),
                    style = MaterialTheme.typography.labelMedium, maxLines = 1
                )
            }
        }
    }
}
