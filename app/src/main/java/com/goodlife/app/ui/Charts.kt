package com.goodlife.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Courbe simple : [values] (null = pas de donnée), ligne de référence facultative, étiquettes sous l'axe. */
@Composable
fun LineChart(
    values: List<Float?>,
    labels: List<String>,
    color: Color,
    minY: Float? = null,
    maxY: Float? = null,
    reference: Float? = null
) {
    val grid = MaterialTheme.colorScheme.surfaceVariant
    val refColor = MaterialTheme.colorScheme.tertiary
    val present = values.filterNotNull()
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(values) { reveal.snapTo(0f); reveal.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
    Column {
        Canvas(Modifier.fillMaxWidth().height(140.dp)) {
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
                    if (v != null) drawCircle(color, 4.dp.toPx(), Offset(x(i), y(v)))
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
