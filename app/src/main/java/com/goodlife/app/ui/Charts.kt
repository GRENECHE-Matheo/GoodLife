package com.goodlife.app.ui

import androidx.compose.foundation.Canvas
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
    Column {
        Canvas(Modifier.fillMaxWidth().height(140.dp)) {
            if (present.isEmpty()) return@Canvas
            val lo = minY ?: (minOf(present.min(), reference ?: present.min()) - 1f)
            val hi = maxY ?: (maxOf(present.max(), reference ?: present.max()) + 1f)
            val span = (hi - lo).takeIf { it > 0f } ?: 1f
            val stepX = if (values.size > 1) size.width / (values.size - 1) else 0f
            fun y(v: Float) = size.height - (v - lo) / span * size.height
            for (i in 0..3) {
                val gy = size.height * i / 3f
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
                val pt = Offset(i * stepX, y(v))
                if (!started) { path.moveTo(pt.x, pt.y); started = true } else path.lineTo(pt.x, pt.y)
            }
            drawPath(path, color, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            values.forEachIndexed { i, v ->
                if (v != null) drawCircle(color, 4.dp.toPx(), Offset(i * stepX, y(v)))
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
