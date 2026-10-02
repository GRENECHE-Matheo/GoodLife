package com.goodlife.app.ui.screens

import com.goodlife.app.i18n.t
import android.graphics.PointF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.goodlife.app.track.FoundPlace
import com.goodlife.app.track.PlaceSearch
import com.goodlife.app.track.PlannedRoute
import com.goodlife.app.track.RouteStyle
import com.goodlife.app.track.Tracker
import kotlinx.coroutines.launch
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap

/** Choix du type de trajet : une rangée de pastilles (défilante). */
@Composable
internal fun StylePicker(style: RouteStyle, onStyle: (RouteStyle) -> Unit, modifier: Modifier = Modifier) {
    val scroll = rememberScrollState()
    val density = androidx.compose.ui.platform.LocalDensity.current
    // Le type choisi reste visible (la rangée défile jusqu'à lui)
    androidx.compose.runtime.LaunchedEffect(style) {
        scroll.animateScrollTo(with(density) { (style.ordinal * 118).dp.roundToPx() }.coerceAtMost(scroll.maxValue))
    }
    Row(modifier.horizontalScroll(scroll), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        RouteStyle.entries.forEach { s ->
            FilterChip(selected = s == style, onClick = { onStyle(s) }, label = { Text("${s.emoji} ${s.label}") })
        }
    }
}

/**
 * Profil de dénivelé d'un itinéraire : la courbe de l'altitude sur la distance, et le dénivelé positif et négatif.
 * Rien n'est affiché si l'altitude n'est pas connue (hors ligne…).
 */
@Composable
internal fun ElevationProfile(route: PlannedRoute, modifier: Modifier = Modifier) {
    if (!route.hasElevation || route.points.size < 2) return
    val color = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val data = remember(route) {
        var d = 0.0
        route.points.mapIndexed { i, p ->
            if (i > 0) d += Tracker.haversine(route.points[i - 1].lat, route.points[i - 1].lng, p.lat, p.lng)
            d to p.alt
        }.filter { !it.second.isNaN() }
    }
    if (data.size < 2) return
    val lo = data.minOf { it.second }; val hi = data.maxOf { it.second }
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(t("⛰️ D+ %1\$s m · D− %2\$s m", route.gainM.toInt(), route.lossM.toInt()), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text(t("altitude %1\$s–%2\$s m", lo.toInt(), hi.toInt()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Canvas(Modifier.fillMaxWidth().height(56.dp).padding(top = 4.dp)) {
            val total = data.last().first.coerceAtLeast(1.0)
            // Au moins 30 m d'écart vertical, pour ne pas exagérer les petites bosses
            val span = (hi - lo).coerceAtLeast(30.0)
            val base = lo - (span - (hi - lo)) / 2
            fun x(d: Double) = (d / total * size.width).toFloat()
            fun y(a: Double) = (size.height - (a - base) / span * size.height).toFloat()
            drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
            val line = Path(); val area = Path()
            data.forEachIndexed { i, (d, a) ->
                if (i == 0) { line.moveTo(x(d), y(a)); area.moveTo(x(d), size.height); area.lineTo(x(d), y(a)) }
                else { line.lineTo(x(d), y(a)); area.lineTo(x(d), y(a)) }
            }
            area.lineTo(x(data.last().first), size.height); area.close()
            drawPath(area, color.copy(alpha = 0.22f))
            drawPath(line, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/**
 * Barre de recherche d'une ville ou d'une adresse (en haut de la carte). La recherche part à l'appui sur la loupe ou
 * sur « Rechercher » du clavier ; les résultats s'affichent dessous.
 */
@Composable
internal fun MapSearchBar(near: () -> LatLng?, onPick: (FoundPlace) -> Unit, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val results = remember { mutableStateListOf<FoundPlace>() }
    fun go() {
        if (query.isBlank()) return
        focus.clearFocus()
        loading = true; error = null; results.clear()
        val c = near()
        scope.launch {
            try {
                val r = PlaceSearch.search(query, c?.latitude, c?.longitude)
                results.addAll(r)
                if (r.isEmpty()) error = t("Aucun lieu trouvé. Essaie avec le nom de la ville.")
            } catch (e: Exception) { error = e.message } finally { loading = false }
        }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Surface(shape = RoundedCornerShape(28.dp), shadowElevation = 4.dp, color = MaterialTheme.colorScheme.surface) {
            TextField(
                value = query, onValueChange = { query = it.take(120) },
                placeholder = { Text(t("Où vas-tu ? Ville, adresse…")) },
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                trailingIcon = {
                    when {
                        loading -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        query.isNotEmpty() -> IconButton(onClick = { query = ""; results.clear(); error = null }) { Icon(Icons.Filled.Close, t("Effacer")) }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { go() }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (results.isNotEmpty() || error != null) Surface(shape = RoundedCornerShape(20.dp), shadowElevation = 4.dp, color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState()).padding(vertical = 4.dp)) {
                error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp)) }
                results.forEachIndexed { i, p ->
                    if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 12.dp))
                    Row(
                        Modifier.fillMaxWidth().clickable { results.clear(); query = p.name; onPick(p) }.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Place, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (p.detail.isNotBlank()) Text(p.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                Text(t("Recherche : Nominatim © OpenStreetMap"), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
            }
        }
    }
}

/**
 * Pinceau : on dessine un trait au doigt sur la carte (qui ne bouge plus pendant ce temps) ; à la fin du trait, il est
 * converti en positions et le trajet est calculé pour le suivre au plus près.
 */
@Composable
internal fun BoxScope.BrushOverlay(map: MapLibreMap?, onStroke: (List<LatLng>) -> Unit, onCancel: () -> Unit) {
    val points = remember { mutableStateListOf<Offset>() }
    val color = MaterialTheme.colorScheme.tertiary
    Canvas(
        Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.08f)).pointerInput(map) {
            detectDragGestures(
                onDragStart = { points.clear(); points.add(it) },
                onDrag = { change, _ -> points.add(change.position) },
                onDragEnd = {
                    val m = map
                    if (m != null && points.size >= 2) {
                        // On garde un point tous les 12 px environ, convertis en latitude / longitude
                        val kept = ArrayList<Offset>()
                        points.forEach { p -> if (kept.isEmpty() || (p - kept.last()).getDistance() >= 12f) kept += p }
                        if (kept.last() != points.last()) kept += points.last()
                        onStroke(kept.map { m.projection.fromScreenLocation(PointF(it.x, it.y)) })
                    }
                }
            )
        }
    ) {
        if (points.size >= 2) {
            val path = Path().apply { moveTo(points[0].x, points[0].y); points.drop(1).forEach { lineTo(it.x, it.y) } }
            drawPath(path, Color.White, style = Stroke(12.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            drawPath(path, color, style = Stroke(7.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
    Surface(
        shape = RoundedCornerShape(20.dp), shadowElevation = 4.dp,
        modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp, start = 12.dp, end = 12.dp).widthIn(max = 420.dp)
    ) {
        Row(Modifier.padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(t("✏️ Dessine ton trajet avec le doigt : il suivra les chemins les plus proches."),
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(vertical = 10.dp))
            TextButton(onClick = onCancel) { Text(t("Annuler")) }
        }
    }
}

/** Grand chiffre de navigation (valeur en gros, libellé dessous). */
@Composable
internal fun NavStat(label: String, value: String, modifier: Modifier = Modifier, big: Boolean = true) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = if (big) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}
