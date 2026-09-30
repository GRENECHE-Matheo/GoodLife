package com.goodlife.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class ChefMood { CONTENT, QUESTION, BRAVO, TRISTE }

private val WHITE = Color(0xFFFFFFFF)
private val OUTLINE = Color(0xFFD5DBE3)
private val SKIN = Color(0xFFFFD6B0)
private val SKIN_DARK = Color(0xFFF7C49A)
private val CHEEK = Color(0xB3FF9E9E)
private val INK = Color(0xFF2B2B2B)
private val MUSTACHE = Color(0xFF7A4A2A)
private val MOUTH = Color(0xFF8A3B2E)
private val SCARF = Color(0xFFEA4335)
private val APRON = Color(0xFF1A73E8)

/** Le petit cuisto de GoodLife, dessiné en vectoriel (100 × 100 unités mises à l'échelle). */
@Composable
fun ChefMascot(modifier: Modifier = Modifier, size: Dp = 96.dp, mood: ChefMood = ChefMood.CONTENT) {
    // Petit rebond à chaque changement d'humeur
    val pop = remember { Animatable(1f) }
    LaunchedEffect(mood) {
        pop.snapTo(0.88f)
        pop.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 320f))
    }
    Canvas(modifier.size(size).graphicsLayer { scaleX = pop.value; scaleY = pop.value; transformOrigin = TransformOrigin(0.5f, 1f) }) {
        drawChef(mood)
    }
}

/**
 * Le cuisto rendu en image (notifications, widgets) : même dessin que [ChefMascot], sans Compose à l'écran.
 */
fun chefBitmap(mood: ChefMood, px: Int = 192): android.graphics.Bitmap {
    val image = androidx.compose.ui.graphics.ImageBitmap(px, px)
    androidx.compose.ui.graphics.drawscope.CanvasDrawScope().draw(
        androidx.compose.ui.unit.Density(1f), androidx.compose.ui.unit.LayoutDirection.Ltr,
        androidx.compose.ui.graphics.Canvas(image), Size(px.toFloat(), px.toFloat())
    ) { drawChef(mood) }
    return image.asAndroidBitmap()
}

/** Dessine le cuisto dans la zone courante (100 × 100 unités mises à l'échelle). */
fun DrawScope.drawChef(mood: ChefMood) {
    run {
        val u = this.size.minDimension / 100f
        fun o(x: Float, y: Float) = Offset(x * u, y * u)
        fun p(block: Path.() -> Unit) = Path().apply(block)

        // Corps et tablier
        val body = p {
            moveTo(22 * u, 100 * u); quadraticTo(22 * u, 76 * u, 50 * u, 74 * u)
            quadraticTo(78 * u, 76 * u, 78 * u, 100 * u); close()
        }
        drawPath(body, WHITE)
        drawPath(body, OUTLINE, style = Stroke(1.5f * u))
        drawPath(p {
            moveTo(38 * u, 100 * u); lineTo(38 * u, 82 * u); quadraticTo(50 * u, 86 * u, 62 * u, 82 * u)
            lineTo(62 * u, 100 * u); close()
        }, APRON)
        drawPath(p {
            moveTo(40 * u, 73 * u); lineTo(60 * u, 73 * u); lineTo(56 * u, 80 * u)
            lineTo(50 * u, 77 * u); lineTo(44 * u, 80 * u); close()
        }, SCARF)

        // Toque : contour d'abord, puis remplissage pour masquer les traits intérieurs
        val hat: DrawScope.(Color, Stroke?) -> Unit = { c, st ->
            if (st == null) {
                drawCircle(c, 11 * u, o(36f, 22f)); drawCircle(c, 11 * u, o(64f, 22f)); drawCircle(c, 13 * u, o(50f, 16f))
                drawRect(c, o(31f, 22f), Size(38 * u, 14 * u))
            } else {
                drawCircle(c, 11 * u, o(36f, 22f), style = st); drawCircle(c, 11 * u, o(64f, 22f), style = st)
                drawCircle(c, 13 * u, o(50f, 16f), style = st); drawRect(c, o(31f, 22f), Size(38 * u, 14 * u), style = st)
            }
        }
        hat(OUTLINE, Stroke(3f * u))
        hat(WHITE, null)
        drawRoundRect(WHITE, o(30f, 31f), Size(40 * u, 8 * u), androidx.compose.ui.geometry.CornerRadius(3 * u))
        drawRoundRect(OUTLINE, o(30f, 31f), Size(40 * u, 8 * u), androidx.compose.ui.geometry.CornerRadius(3 * u), style = Stroke(1.5f * u))

        // Tête
        drawCircle(SKIN_DARK, 4 * u, o(30f, 56f))
        drawCircle(SKIN_DARK, 4 * u, o(70f, 56f))
        drawCircle(SKIN, 20 * u, o(50f, 55f))
        drawOval(CHEEK, o(34f, 58.4f), Size(8 * u, 5.2f * u))
        drawOval(CHEEK, o(58f, 58.4f), Size(8 * u, 5.2f * u))

        // Yeux
        val line = Stroke(1.8f * u, cap = StrokeCap.Round)
        when (mood) {
            ChefMood.BRAVO -> {
                drawPath(p { moveTo(39 * u, 53 * u); quadraticTo(42 * u, 48 * u, 45 * u, 53 * u) }, INK, style = line)
                drawPath(p { moveTo(55 * u, 53 * u); quadraticTo(58 * u, 48 * u, 61 * u, 53 * u) }, INK, style = line)
            }
            else -> {
                drawOval(INK, o(39.4f, 48.6f), Size(5.2f * u, 6.8f * u))
                drawOval(INK, o(55.4f, 48.6f), Size(5.2f * u, 6.8f * u))
                drawCircle(WHITE, 0.9f * u, o(43f, 51f))
                drawCircle(WHITE, 0.9f * u, o(59f, 51f))
            }
        }
        // Sourcils
        when (mood) {
            ChefMood.TRISTE -> {
                drawLine(MUSTACHE, o(38f, 45f), o(45f, 43f), 1.6f * u, StrokeCap.Round)
                drawLine(MUSTACHE, o(55f, 43f), o(62f, 45f), 1.6f * u, StrokeCap.Round)
            }
            ChefMood.QUESTION -> {
                drawLine(MUSTACHE, o(38f, 44f), o(45f, 44f), 1.6f * u, StrokeCap.Round)
                drawLine(MUSTACHE, o(55f, 42f), o(62f, 43.5f), 1.6f * u, StrokeCap.Round)
            }
            else -> Unit
        }

        // Moustache
        drawPath(p {
            moveTo(50 * u, 61 * u); quadraticTo(44 * u, 57 * u, 38 * u, 60 * u)
            quadraticTo(41 * u, 64 * u, 50 * u, 62 * u); quadraticTo(59 * u, 64 * u, 62 * u, 60 * u)
            quadraticTo(56 * u, 57 * u, 50 * u, 61 * u); close()
        }, MUSTACHE)

        // Bouche
        when (mood) {
            ChefMood.CONTENT -> drawPath(p { moveTo(45 * u, 65 * u); quadraticTo(50 * u, 70 * u, 55 * u, 65 * u) }, MOUTH, style = line)
            ChefMood.BRAVO -> drawPath(p {
                moveTo(44 * u, 64.5f * u); quadraticTo(50 * u, 73 * u, 56 * u, 64.5f * u); close()
            }, MOUTH)
            ChefMood.QUESTION -> drawOval(MOUTH, o(48f, 64.5f), Size(4 * u, 4 * u))
            ChefMood.TRISTE -> drawPath(p { moveTo(45 * u, 68 * u); quadraticTo(50 * u, 63.5f * u, 55 * u, 68 * u) }, MOUTH, style = line)
        }
    }
}
