package com.goodlife.app.ui

import com.goodlife.app.i18n.t
import com.goodlife.app.i18n.tp

import androidx.compose.foundation.Canvas
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.IconButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ScreenTitle(title: String, subtitle: String? = null) {
    Column(Modifier.padding(top = 8.dp, bottom = 4.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Normal)
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SectionCard(
    title: String? = null,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = container)
    ) {
        Column(
            Modifier.animateContentSize(spring(stiffness = Spring.StiffnessMediumLow)).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (title != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (icon != null) {
                        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                }
            }
            content()
        }
    }
}

/**
 * Anneau des calories ; si [stepsProgress] est donné, un second anneau intérieur montre les pas
 * (même écran, pas de carte en plus).
 */
@Composable
fun CalorieRing(consumed: Int, target: Int, size: Dp = 180.dp, stepsProgress: Float? = null) {
    val progress by animateFloatAsState(
        if (target > 0) consumed.toFloat() / target else 0f,
        animationSpec = tween(900, easing = FastOutSlowInEasing), label = "ring"
    )
    val stepsAnim by animateFloatAsState(
        (stepsProgress ?: 0f).coerceIn(0f, 1f),
        animationSpec = tween(900, easing = FastOutSlowInEasing), label = "stepsRing"
    )
    val stepsColor = MaterialTheme.colorScheme.tertiary
    val shown by animateIntAsState(consumed, animationSpec = tween(900, easing = FastOutSlowInEasing), label = "kcal")
    val over = progress > 1f
    val track = MaterialTheme.colorScheme.surfaceVariant
    val color by animateColorAsState(
        if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, label = "ringColor"
    )
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = 18.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(track, 135f, 270f, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(
                color, 135f, 270f * progress.coerceIn(0f, 1f), false, Offset(inset, inset), arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
            if (stepsProgress != null) {
                val s2 = 10.dp.toPx()
                val gap = stroke + 6.dp.toPx()
                val o2 = gap + s2 / 2
                val size2 = Size(this.size.width - 2 * o2, this.size.height - 2 * o2)
                drawArc(track, 135f, 270f, false, Offset(o2, o2), size2, style = Stroke(s2, cap = StrokeCap.Round))
                if (stepsAnim > 0f) drawArc(
                    stepsColor, 135f, 270f * stepsAnim, false, Offset(o2, o2), size2,
                    style = Stroke(s2, cap = StrokeCap.Round)
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$shown", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Medium)
            Text(
                t("sur %1\$s kcal", target),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun MacroBar(label: String, value: Double, target: Int, color: Color) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                t("%1\$s / %2\$s g", value.toInt(), target),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(6.dp))
        val animated by animateFloatAsState(
            if (target > 0) (value / target).toFloat().coerceIn(0f, 1f) else 0f,
            animationSpec = tween(800, easing = FastOutSlowInEasing), label = "macro"
        )
        LinearProgressIndicator(
            progress = { animated },
            modifier = Modifier.fillMaxWidth().height(8.dp),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeCap = StrokeCap.Round
        )
    }
}

/** Colonne défilante commune à tous les écrans : largeur max 640 dp et centrée (tablettes, paysage). */
@Composable
fun ScreenColumn(content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 640.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content
        )
    }
}

/** En-tête commun des sous-écrans : bouton retour + titre. */
@Composable
fun SubScreenHeader(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, t("Retour")) }
        Spacer(Modifier.width(4.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall)
    }
}

fun formatTime(ms: Long): String = SimpleDateFormat("HH:mm", com.goodlife.app.i18n.Lang.locale).format(Date(ms))
fun formatDay(ms: Long): String = SimpleDateFormat("EEE d MMM", com.goodlife.app.i18n.Lang.locale).format(Date(ms))
/** « 1 jour », « 3 jours ». */
fun days(n: Int): String = tp(n, "%1\$s jour", "%1\$s jours")
fun formatDuration(min: Long): String = "${min / 60} h ${"%02d".format(min % 60)}"
fun String.toNumber(): Double? = replace(',', '.').trim().toDoubleOrNull()
