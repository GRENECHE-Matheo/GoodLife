package com.goodlife.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.goodlife.app.game.Game
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private val GOLD = Color(0xFFFFC107)

/** Ease-out « quint » : rapide au départ, décélération longue et douce sur la fin. */
private val XpEasing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

/** Une particule : étincelle (rond) ou confetti (petit rectangle qui tourne). */
private class Particle(
    var x: Float, var y: Float, var vx: Float, var vy: Float,
    val life: Float, val color: Color, val size: Float, val confetti: Boolean,
    var rotation: Float = Random.nextFloat() * 360f, val spin: Float = (Random.nextFloat() - 0.5f) * 720f,
    var age: Float = 0f, val gravity: Float
)

/**
 * Carte de récompense : la barre d'XP se remplit de [startXp] à [endXp] avec des étincelles au bout
 * de la barre, des confettis à la fin et une animation spéciale en cas de passage de niveau.
 */
@Composable
fun XpGainCard(startXp: Int, endXp: Int, modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val track = MaterialTheme.colorScheme.surfaceVariant
    val palette = listOf(primary, GOLD, tertiary, Color(0xFFFF8A00), Color(0xFF34A853))
    val haptics = LocalHapticFeedback.current

    val xp = remember { Animatable(startXp.toFloat()) }
    val particles = remember { ArrayList<Particle>() }
    var frame by remember { mutableLongStateOf(0L) }
    var bar by remember { mutableStateOf(Rect.Zero) }
    var leveledUp by remember { mutableStateOf(false) }
    var filling by remember { mutableStateOf(false) }
    val badge = remember { Animatable(1f) }
    val density = androidx.compose.ui.platform.LocalDensity.current.density

    val info = Game.levelFor(xp.value.toInt())
    val progress = info.progress.coerceIn(0f, 1f)
    fun tip() = Offset(bar.left + bar.width * progress, bar.center.y)

    fun sparkle() {
        val t = tip()
        repeat(2) {
            particles += Particle(
                t.x, t.y + (Random.nextFloat() - 0.5f) * bar.height,
                vx = (Random.nextFloat() - 0.7f) * 120f * density, vy = -(40f + Random.nextFloat() * 120f) * density,
                life = 0.45f + Random.nextFloat() * 0.35f, color = if (Random.nextBoolean()) GOLD else primary,
                size = (1.5f + Random.nextFloat() * 2f) * density, confetti = false, gravity = 60f * density
            )
        }
    }

    fun burst(count: Int) {
        val t = tip()
        repeat(count) {
            val angle = (-PI / 2 + (Random.nextDouble() - 0.5) * PI * 1.3).toFloat()
            val speed = (140f + Random.nextFloat() * 320f) * density
            particles += Particle(
                t.x, t.y, vx = cos(angle) * speed, vy = sin(angle) * speed,
                life = 0.9f + Random.nextFloat() * 0.8f, color = palette.random(),
                size = (3f + Random.nextFloat() * 3f) * density, confetti = Random.nextFloat() < 0.65f,
                gravity = 520f * density
            )
        }
    }

    // Moteur de particules : une mise à jour par image, seulement pendant l'animation
    LaunchedEffect(Unit) {
        var last = 0L
        while (isActive) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else ((now - last) / 1e9f).coerceAtMost(0.05f)
                last = now
                val it = particles.iterator()
                while (it.hasNext()) {
                    val p = it.next()
                    p.age += dt
                    if (p.age >= p.life) { it.remove(); continue }
                    p.vy += p.gravity * dt
                    p.vx *= 0.985f
                    p.x += p.vx * dt; p.y += p.vy * dt
                    p.rotation += p.spin * dt
                }
                frame = now
            }
            if (particles.isEmpty() && !filling) { last = 0L; delay(120) }
        }
    }

    LaunchedEffect(startXp, endXp) {
        if (endXp <= startXp) { xp.snapTo(endXp.toFloat()); return@LaunchedEffect }
        delay(500)
        filling = true
        var lastLevel = Game.levelFor(startXp).level
        val gained = endXp - startXp
        // Un « tic » tous les [tickStep] XP : comme la barre ralentit, les tics s'espacent avec elle
        val tickStep = maxOf(1f, gained / 14f)
        var nextTick = startXp + tickStep
        var previous = startXp.toFloat()
        xp.animateTo(
            endXp.toFloat(),
            // Démarre vite puis ralentit de plus en plus en approchant du but (ease-out prononcé)
            tween(durationMillis = (1300 + gained * 22).coerceAtMost(2800), easing = XpEasing)
        ) {
            // Étincelles proportionnelles à la vitesse : elles se raréfient quand la barre ralentit
            val speed = (value - previous) / gained.coerceAtLeast(1)
            previous = value
            if (Random.nextFloat() < (speed * 60f).coerceIn(0.15f, 1f)) sparkle()
            val lvl = Game.levelFor(value.toInt()).level
            if (lvl > lastLevel) {
                lastLevel = lvl
                leveledUp = true
                burst(40)
                Sounds.play(Sfx.LEVEL_UP)
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                launch { badge.snapTo(0.6f); badge.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 300f)) }
            }
            if (value >= nextTick && value < endXp - 0.5f) {
                nextTick += tickStep
                Sounds.play(Sfx.XP_TICK)
            }
        }
        filling = false
        burst(if (leveledUp) 30 else 36)
        if (!leveledUp) Sounds.play(Sfx.LEVEL_UP)
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    val shimmer = rememberInfiniteTransition(label = "shimmer")
    val shine by shimmer.animateFloat(
        -0.3f, 1.3f, infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart), label = "shine"
    )

    Box(modifier.fillMaxWidth()) {
        Column(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Niveau ${info.level} · ${info.title}",
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f).graphicsLayer { scaleX = badge.value; scaleY = badge.value; transformOrigin = TransformOrigin(0f, 0.5f) }
                )
                Text(
                    "+${(xp.value - startXp).toInt().coerceAtLeast(0)} XP",
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = primary
                )
            }
            // Barre d'XP dessinée à la main : dégradé + reflet qui passe
            Canvas(
                Modifier.fillMaxWidth().height(16.dp)
                    .onGloballyPositioned { bar = it.boundsInParent() }
            ) {
                val r = CornerRadius(size.height / 2)
                drawRoundRect(track, cornerRadius = r)
                val w = size.width * progress
                if (w > 0f) {
                    drawRoundRect(
                        Brush.horizontalGradient(listOf(primary, GOLD), endX = size.width),
                        size = Size(w.coerceAtLeast(size.height), size.height), cornerRadius = r
                    )
                    val sx = size.width * shine
                    drawRoundRect(
                        Brush.horizontalGradient(
                            listOf(Color.Transparent, Color.White.copy(alpha = 0.45f), Color.Transparent),
                            startX = sx - 40.dp.toPx(), endX = sx + 40.dp.toPx()
                        ),
                        size = Size(w.coerceAtLeast(size.height), size.height), cornerRadius = r
                    )
                }
            }
            Text(
                if (leveledUp) "Niveau supérieur ! Plus que ${info.xpForNext - info.xpInLevel} XP pour le suivant."
                else "${info.xpInLevel} / ${info.xpForNext} XP · ${info.xpForNext - info.xpInLevel} XP avant le niveau ${info.level + 1}",
                style = MaterialTheme.typography.bodySmall,
                color = if (leveledUp) primary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (leveledUp) FontWeight.Medium else FontWeight.Normal
            )
        }
        // Calque des particules, par-dessus la carte (non rogné : elles peuvent déborder)
        Canvas(Modifier.matchParentSize()) {
            frame // relit l'état à chaque image : redessine seulement ce calque
            for (p in particles) {
                val a = (1f - p.age / p.life).coerceIn(0f, 1f)
                val c = p.color.copy(alpha = a)
                if (p.confetti) {
                    rotate(p.rotation, Offset(p.x, p.y)) {
                        drawRect(c, Offset(p.x - p.size, p.y - p.size / 2), Size(p.size * 2, p.size))
                    }
                } else {
                    drawCircle(c, p.size * (0.6f + 0.4f * a), Offset(p.x, p.y))
                }
            }
        }
    }
}
