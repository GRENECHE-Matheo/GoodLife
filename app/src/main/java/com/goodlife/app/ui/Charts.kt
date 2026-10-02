package com.goodlife.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Touche ou glisse le doigt sur un graphique : la colonne la plus proche est sélectionnée et sa valeur s'affiche
 * dans une bulle au-dessus (Material : « tooltip » de graphique).
 */
private fun Modifier.pickColumn(count: Int, onPick: (Int) -> Unit): Modifier =
    pointerInput(count) {
        detectTapGestures { p -> onPick((p.x / size.width * count).toInt().coerceIn(0, count - 1)) }
    }.pointerInput(count) {
        detectHorizontalDragGestures { change, _ ->
            onPick((change.position.x / size.width * count).toInt().coerceIn(0, count - 1))
        }
    }

/** Bulle de valeur, centrée sur la colonne [index] et gardée dans la largeur du graphique. */
@Composable
private fun ChartTip(text: String, index: Int, count: Int, widthPx: Int) {
    var tipW by remember { mutableIntStateOf(0) }
    val slot = widthPx.toFloat() / count.coerceAtLeast(1)
    val center = slot * (index + 0.5f)
    val x = (center - tipW / 2f).coerceIn(0f, (widthPx - tipW).toFloat().coerceAtLeast(0f))
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.inverseSurface,
        modifier = Modifier.offset { IntOffset(x.roundToInt(), 0) }.onSizeChanged { tipW = it.width }
    ) {
        Text(
            text, Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.inverseOnSurface
        )
    }
}

/**
 * Courbe simple : [values] (null = pas de donnée), ligne de référence facultative, étiquettes sous l'axe.
 * [tips] = libellé complet de chaque point (date) et [format] = valeur affichée dans la bulle quand on touche.
 */
@Composable
fun LineChart(
    values: List<Float?>,
    labels: List<String>,
    color: Color,
    minY: Float? = null,
    maxY: Float? = null,
    reference: Float? = null,
    tips: List<String>? = null,
    format: (Float) -> String = { "%.0f".format(it) }
) {
    val grid = MaterialTheme.colorScheme.surfaceVariant
    val refColor = MaterialTheme.colorScheme.tertiary
    val guide = MaterialTheme.colorScheme.outline
    val present = values.filterNotNull()
    val reveal = remember { Animatable(0f) }
    var selected by remember(values) { mutableIntStateOf(-1) }
    var widthPx by remember { mutableIntStateOf(0) }
    LaunchedEffect(values) { reveal.snapTo(0f); reveal.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
    Column {
        // Bulle de la valeur touchée (place réservée pour que le graphique ne saute pas)
        Box(Modifier.fillMaxWidth().height(30.dp)) {
            if (selected in values.indices && widthPx > 0) {
                val v = values[selected]
                val label = tips?.getOrNull(selected) ?: labels.getOrNull(selected).orEmpty()
                ChartTip((if (label.isNotBlank()) "$label · " else "") + (v?.let(format) ?: "—"), selected, values.size, widthPx)
            }
        }
        Canvas(
            Modifier.fillMaxWidth().height(140.dp)
                .onSizeChanged { widthPx = it.width }
                .pickColumn(values.size) { selected = it }
        ) {
            if (present.isEmpty()) return@Canvas
            val rawLo = minOf(present.min(), reference ?: present.min())
            val rawHi = maxOf(present.max(), reference ?: present.max())
            val headroom = maxOf((rawHi - rawLo) * 0.08f, 1f)
            val lo = minY ?: (rawLo - headroom)
            val hi = maxY ?: (rawHi + headroom)
            val span = (hi - lo).takeIf { it > 0f } ?: 1f
            // Marge pour que les points et le trait ne soient jamais coupés par les bords
            val pad = 8.dp.toPx()
            val plotH = size.height - 2 * pad
            // Chaque point est centré dans la colonne de son étiquette (même découpage que la ligne de dates)
            val slot = size.width / values.size.coerceAtLeast(1)
            fun x(i: Int) = slot * (i + 0.5f)
            fun y(v: Float) = pad + plotH - (v - lo) / span * plotH
            for (i in 0..3) {
                val gy = pad + plotH * i / 3f
                drawLine(grid, Offset(0f, gy), Offset(size.width, gy), 1.dp.toPx())
            }
            if (reference != null) {
                drawLine(
                    refColor, Offset(0f, y(reference)), Offset(size.width, y(reference)), 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f))
                )
            }
            if (selected in values.indices) {
                drawLine(guide, Offset(x(selected), 0f), Offset(x(selected), size.height), 1.5.dp.toPx())
            }
            val path = Path()
            var started = false
            values.forEachIndexed { i, v ->
                if (v == null) return@forEachIndexed
                val pt = Offset(x(i), y(v))
                if (!started) { path.moveTo(pt.x, pt.y); started = true } else path.lineTo(pt.x, pt.y)
            }
            // La courbe se dessine de gauche à droite
            clipRect(right = size.width * reveal.value + 6.dp.toPx()) {
                drawPath(path, color, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                values.forEachIndexed { i, v ->
                    if (v != null) drawCircle(color, (if (i == selected) 7 else 4).dp.toPx(), Offset(x(i), y(v)))
                }
            }
        }
        Row(Modifier.fillMaxWidth()) {
            labels.forEach {
                Text(
                    it, Modifier.weight(1f), textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Petit histogramme sur 7 jours avec une ligne d'objectif ; touche une barre pour voir sa valeur. */
@Composable
fun WeekBars(
    values: List<Float>,
    goal: Float,
    labels: List<String>,
    barColor: Color,
    format: (Float) -> String = { "%.0f".format(it) }
) {
    val goalColor = MaterialTheme.colorScheme.tertiary
    val track = MaterialTheme.colorScheme.surfaceVariant
    val grow = remember { Animatable(0f) }
    var selected by remember(values) { mutableIntStateOf(-1) }
    var widthPx by remember { mutableIntStateOf(0) }
    LaunchedEffect(values) { grow.snapTo(0f); grow.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
    Column {
        Box(Modifier.fillMaxWidth().height(30.dp)) {
            if (selected in values.indices && widthPx > 0) {
                ChartTip(labels[selected] + " · " + format(values[selected]), selected, values.size, widthPx)
            }
        }
        Canvas(
            Modifier.fillMaxWidth().height(120.dp)
                .onSizeChanged { widthPx = it.width }
                .pickColumn(values.size) { selected = it }
        ) {
            val max = maxOf(goal * 1.2f, values.maxOrNull() ?: 0f, 1f)
            val slot = size.width / values.size
            val barW = slot * 0.5f
            values.forEachIndexed { i, v ->
                val x = i * slot + (slot - barW) / 2
                drawRoundRect(track, Offset(x, 0f), Size(barW, size.height), CornerRadius(barW / 2))
                val h = size.height * (v / max) * grow.value
                if (h > 0f) {
                    drawRoundRect(
                        if (selected == -1 || selected == i) barColor else barColor.copy(alpha = 0.45f),
                        Offset(x, size.height - h), Size(barW, h), CornerRadius(barW / 2)
                    )
                }
            }
            val gy = size.height - size.height * (goal / max)
            drawLine(goalColor, Offset(0f, gy), Offset(size.width, gy), strokeWidth = 2.dp.toPx())
        }
        Row(Modifier.fillMaxWidth()) {
            labels.forEach {
                Text(
                    it, Modifier.weight(1f), textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
