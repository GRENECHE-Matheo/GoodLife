package com.goodlife.app.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.goodlife.app.data.OutingType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Petit bonhomme à ta position pendant une sortie : à vélo, il pédale quand tu avances et pose un pied à terre quand
 * tu t'arrêtes ; à pied, il marche (ou court) quand tu avances et s'arrête avec toi.
 */
@Composable
internal fun MapAvatar(type: OutingType, moving: Boolean, color: Color, modifier: Modifier = Modifier, size: Dp = 56.dp) {
    val transition = rememberInfiniteTransition(label = "avatar")
    // Un tour de pédalier, ou un pas, toutes les 0,7 s (0,45 s en courant) ; figé à l'arrêt
    val cycle by transition.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(if (type == OutingType.RUN) 450 else 700, easing = LinearEasing), RepeatMode.Restart),
        label = "cycle"
    )
    val phase = if (moving) cycle else 0f
    Canvas(modifier.size(size)) {
        val u = this.size.width / 56f
        val outline = Color.White
        fun line(a: Offset, b: Offset, c: Color, w: Float) = drawLine(c, a * u, b * u, w * u, cap = StrokeCap.Round)
        fun limb(a: Offset, b: Offset) { line(a, b, outline, 7f); line(a, b, color, 4f) }
        fun head(c: Offset) { drawCircle(outline, 7f * u, c * u); drawCircle(color, 5f * u, c * u) }
        if (type == OutingType.BIKE) drawBike(phase, moving, ::limb, ::head, u, color, outline)
        else drawWalker(phase, moving, type == OutingType.RUN, ::limb, ::head)
    }
}

private operator fun Offset.times(k: Float) = Offset(x * k, y * k)

private fun DrawScope.drawBike(
    phase: Float, moving: Boolean, limb: (Offset, Offset) -> Unit, head: (Offset) -> Unit, u: Float, color: Color, outline: Color
) {
    val back = Offset(14f, 42f); val front = Offset(43f, 42f)
    // Roues
    for (c in listOf(back, front)) {
        drawCircle(outline, 10f * u, c * u, style = Stroke(6f * u))
        drawCircle(color, 10f * u, c * u, style = Stroke(3f * u))
    }
    val crank = Offset(27f, 42f); val seat = Offset(23f, 26f); val bar = Offset(38f, 24f)
    // Cadre
    limb(back, crank); limb(crank, seat); limb(seat, back); limb(crank, Offset(36f, 28f)); limb(Offset(36f, 28f), front); limb(Offset(36f, 28f), bar)
    // Pédalier : tourne quand on avance
    val a = phase * 2f * PI.toFloat()
    val p1 = Offset(crank.x + 6f * cos(a), crank.y + 6f * sin(a))
    val p2 = Offset(crank.x - 6f * cos(a), crank.y - 6f * sin(a))
    val hip = Offset(23f, 23f); val shoulder = Offset(31f, 12f)
    // Jambes : genou un peu en avant entre la hanche et la pédale
    fun knee(foot: Offset) = Offset((hip.x + foot.x) / 2 + 5f, (hip.y + foot.y) / 2 - 3f)
    limb(hip, knee(p1)); limb(knee(p1), p1)
    if (moving) { limb(hip, knee(p2)); limb(knee(p2), p2) }
    else {
        // À l'arrêt : un pied posé par terre
        val ground = Offset(16f, 54f)
        limb(hip, Offset(18f, 39f)); limb(Offset(18f, 39f), ground)
    }
    limb(hip, shoulder); limb(shoulder, bar)
    head(Offset(34f, 6f))
}

private fun drawWalker(phase: Float, moving: Boolean, run: Boolean, limb: (Offset, Offset) -> Unit, head: (Offset) -> Unit) {
    val hip = Offset(28f, 33f); val shoulder = Offset(28f, 16f)
    val swing = if (moving) sin(phase * 2f * PI.toFloat()) * (if (run) 0.75f else 0.5f) else 0f
    val legLen = 20f; val armLen = 14f
    fun at(o: Offset, len: Float, angle: Float) = Offset(o.x + len * sin(angle), o.y + len * cos(angle))
    // Jambes et bras en opposition ; tout droit à l'arrêt
    limb(hip, at(hip, legLen, swing)); limb(hip, at(hip, legLen, -swing))
    limb(shoulder, at(shoulder, armLen, -swing * 0.9f)); limb(shoulder, at(shoulder, armLen, swing * 0.9f))
    limb(hip, shoulder)
    head(Offset(28f, 8f))
}
