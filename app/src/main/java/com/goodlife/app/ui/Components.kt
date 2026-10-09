package com.goodlife.app.ui

import androidx.compose.ui.draw.drawWithContent
import com.goodlife.app.i18n.t
import com.goodlife.app.i18n.tp

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.imePadding
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
            Modifier.padding(20.dp),
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
 * Section repliable (Paramètres) : seul le titre est visible ; un appui l'ouvre ou la referme. L'état est retenu
 * pendant la session (rotation comprise).
 */
@Composable
fun FoldableSection(
    title: String,
    icon: ImageVector,
    summary: String = "",
    content: @Composable ColumnScope.() -> Unit
) {
    var open by androidx.compose.runtime.saveable.rememberSaveable(title) { androidx.compose.runtime.mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                    if (summary.isNotBlank() && !open) Text(summary, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                Icon(
                    if (open) androidx.compose.material.icons.Icons.Filled.ExpandLess else androidx.compose.material.icons.Icons.Filled.ExpandMore,
                    if (open) t("Replier") else t("Ouvrir")
                )
            }
            // Le contenu s'ouvre et se ferme avec la carte (pas de décalage entre le texte et le bas de la carte)
            androidx.compose.animation.AnimatedVisibility(open, enter = androidx.compose.animation.expandVertically(expandFrom = androidx.compose.ui.Alignment.Top) + androidx.compose.animation.fadeIn(), exit = androidx.compose.animation.shrinkVertically(shrinkTowards = androidx.compose.ui.Alignment.Top)) {
                Column(
                    Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = content
                )
            }
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

/**
 * Colonne défilante commune à tous les écrans : largeur max 640 dp et centrée (tablettes, paysage).
 * Elle se raccourcit au-dessus du clavier : le champ en cours de saisie défile alors pour rester visible.
 */
@Composable
fun ScreenColumn(content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().imePadding(), contentAlignment = Alignment.TopCenter) {
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

/**
 * Texte sur une seule ligne qui rétrécit un peu (jusqu'aux deux tiers) s'il ne tient pas, au lieu d'être coupé au
 * milieu d'un mot : pour les onglets et la barre du bas, avec un grand texte ou un petit écran.
 */
@Composable
fun FitText(text: String, modifier: Modifier = Modifier, style: androidx.compose.ui.text.TextStyle = androidx.compose.material3.LocalTextStyle.current) {
    var scale by androidx.compose.runtime.remember(text) { androidx.compose.runtime.mutableFloatStateOf(1f) }
    var ready by androidx.compose.runtime.remember(text) { androidx.compose.runtime.mutableStateOf(false) }
    Text(
        text, maxLines = 1, softWrap = false,
        style = style.copy(fontSize = style.fontSize * scale),
        modifier = modifier.drawWithContent { if (ready) drawContent() },
        onTextLayout = { r -> if (r.didOverflowWidth && scale > 0.67f) scale *= 0.92f else ready = true }
    )
}
