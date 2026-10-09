package com.goodlife.app.ui

import com.goodlife.app.i18n.t

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.goodlife.app.data.Repo
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Visite guidée de l'app (proposée une fois, jamais imposée) : 5 étapes, une par onglet. L'onglet est éclairé,
 * le reste assombri, et le chef explique en une phrase. « Passer » à chaque étape ; on peut la revoir dans
 * Moi › Paramètres › À propos.
 */
object Tour {
    /** Étape en cours (0 à 4 = l'onglet montré), ou -1 quand la visite n'est pas lancée. */
    val step = MutableStateFlow(-1)

    /** Position à l'écran de chaque élément montré (onglets « tab0 »… et boutons « home.add »…). */
    val targets = mutableStateMapOf<String, Rect>()

    fun start() { step.value = 0 }
    fun stop() { step.value = -1 }

    /** Modificateur à poser sur un élément pour que la visite sache où l'éclairer. */
    fun Modifier.tourTarget(key: String): Modifier = onGloballyPositioned { c ->
        val p = c.positionInRoot()
        targets[key] = Rect(p, Size(c.size.width.toFloat(), c.size.height.toFloat()))
    }
    fun Modifier.tourTarget(index: Int): Modifier = tourTarget("tab$index")
}

/** Une étape : l'onglet à ouvrir, l'élément à éclairer, et ce que le chef en dit. */
private data class TourStep(val tab: Int, val target: String, val emoji: String, val title: String, val text: String)

private fun steps() = listOf(
    TourStep(0, "tab0", "☀️", t("Aujourd'hui"), t("Ta journée en un coup d'œil : calories, repas du jour et eau. Le quiz et les missions du jour sont juste en dessous.")),
    TourStep(0, "home.add", "➕", t("Ajouter un repas"), t("Ce bouton (ou l'onglet Ajouter) : photo de ton assiette et l'IA estime les calories, code-barres d'un produit, saisie à la main, ou un repas habituel en un appui.")),
    TourStep(0, "home.chef", "👨‍🍳", t("Ton coach"), t("Touche le chef pour lui parler : idées de repas, recettes, conseils sport. Il connaît tes chiffres de la semaine.")),
    TourStep(2, "tab2", "🍽️", t("Repas"), t("Ton planning de la semaine, des idées de repas, ta liste de courses et ce qu'il y a dans ton frigo.")),
    TourStep(3, "tab3", "💪", t("Forme"), t("Ton programme sportif, la carte pour tes sorties GPS, et ton sommeil.")),
    TourStep(4, "moi.progress", "🏆", t("Mes progrès"), t("Ton niveau, ta série, tes badges, ton poids et tes courbes. C'est aussi ici que tu te pèses.")),
    TourStep(4, "moi.friends", "👥", t("Ajouter un ami"), t("Ouvre Amis puis « Ajouter un ami » : colle vos deux téléphones (Tap to Sync), scanne son QR code ou envoie-lui ta carte par message. Vous comparez vos séries et vous vous encouragez.")),
    TourStep(4, "moi.settings", "⚙️", t("Les réglages"), t("Notifications, objectifs d'eau et de pas, IA, sauvegarde et tes données : tout est dans Paramètres. Tu peux revoir cette visite dans Paramètres › À propos.")),
)

/** Fenêtre « Je te fais visiter ? », une seule fois (nouveaux comptes, et anciens comptes après la réorganisation). */
@Composable
fun TourOffer() {
    val settings by Repo.settings.collectAsState()
    if (settings.tourOffered) return
    var visible by remember { mutableStateOf(false) }
    // Petit délai : la fenêtre arrive une fois l'accueil affiché, pas pendant la transition
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(700); visible = true }
    if (!visible) return
    val done = { Repo.updateSettings { it.copy(tourOffered = true) } }
    AlertDialog(
        onDismissRequest = { done() },
        icon = { ChefMascot(size = 72.dp, mood = ChefMood.CLIN) },
        title = { Text(t("Je te fais visiter ?")) },
        text = {
            Text(
                if (Repo.meals.value.isEmpty()) t("En 30 secondes, je te montre où tout se trouve. Tu pourras passer à tout moment.")
                else t("L'app a été rangée : un onglet pour chaque chose. En 30 secondes, je te montre où tout se trouve."),
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = { Button(onClick = { done(); Tour.start() }) { Text(t("Oui, montre-moi")) } },
        dismissButton = { TextButton(onClick = { done() }) { Text(t("Plus tard")) } }
    )
}

/** La visite elle-même, par-dessus l'app. [onTab] change d'onglet à chaque étape. */
@Composable
fun TourOverlay(onTab: (Int) -> Unit) {
    val step by Tour.step.collectAsState()
    val all = remember { steps() }
    LaunchedEffect(step) {
        if (step !in all.indices) return@LaunchedEffect
        // Onglet Moi : on revient à son menu (au cas où un écran y était ouvert)
        if (all[step].tab == 4) com.goodlife.app.social.AppNav.request.value = "moi:"
        onTab(all[step].tab)
    }
    AnimatedVisibility(visible = step in all.indices, enter = fadeIn(tween(250)), exit = fadeOut(tween(200))) {
        val current = step.coerceIn(0, all.lastIndex)
        val target = Tour.targets[all[current].target]
        val density = LocalDensity.current
        val pad = with(density) { 6.dp.toPx() }
        // Le halo glisse d'un onglet à l'autre
        val cx by animateFloatAsState(target?.center?.x ?: 0f, tween(350), label = "tourX")
        val cy by animateFloatAsState(target?.center?.y ?: 0f, tween(350), label = "tourY")
        val w = (target?.width ?: 0f) + pad * 2
        val h = (target?.height ?: 0f) + pad * 2
        var boxTop by remember { mutableStateOf(0f) }
        var boxH by remember { mutableStateOf(0f) }
        Box(
            Modifier.fillMaxSize()
                .onGloballyPositioned { boxTop = it.positionInRoot().y; boxH = it.size.height.toFloat() }
                // La visite ne laisse pas toucher l'app derrière (seulement ses boutons)
                .pointerInput(Unit) { detectTapGestures { } }
        ) {
            Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
                drawRect(Color.Black.copy(alpha = 0.55f))
                if (target != null) drawRoundRect(
                    Color.Transparent, topLeft = Offset(cx - w / 2, cy - boxTop - h / 2), size = Size(w, h),
                    cornerRadius = CornerRadius(minOf(h / 2, 28.dp.toPx()), minOf(h / 2, 28.dp.toPx())), blendMode = BlendMode.Clear
                )
            }
            // Bulle du chef : au-dessus d'un élément du bas de l'écran, au-dessous d'un élément du haut
            val margin = with(density) { 16.dp.toPx() }
            val topInBox = (target?.top ?: boxH) - boxTop
            val bottomInBox = (target?.bottom ?: 0f) - boxTop
            val below = target != null && boxH > 0f && (topInBox + bottomInBox) / 2 < boxH / 2
            val gap = with(density) {
                (if (below) bottomInBox + margin else if (target != null && boxH > 0f) boxH - topInBox + margin else margin * 2)
                    .coerceAtLeast(margin * 2).toDp()   // jamais négatif (taille pas encore connue au 1er affichage)
            }
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
                modifier = Modifier.align(if (below) Alignment.TopCenter else Alignment.BottomCenter)
                    .padding(start = 16.dp, end = 16.dp, top = if (below) gap else 0.dp, bottom = if (below) 0.dp else gap)
                    .widthIn(max = 520.dp).fillMaxWidth()
            ) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AnimatedContent(current, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) }, label = "tourText") { i ->
                        Row(verticalAlignment = Alignment.Top) {
                            ChefMascot(size = 56.dp, mood = if (i == all.lastIndex) ChefMood.BRAVO else ChefMood.CONTENT)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("${all[i].emoji} ${all[i].title}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(all[i].text, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // ●●○○○ : où on en est
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                            all.indices.forEach { i ->
                                val size by animateDpAsState(if (i == current) 10.dp else 7.dp, label = "dot")
                                Box(Modifier.size(size).clip(CircleShape).background(
                                    if (i <= current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                ))
                            }
                        }
                        if (current < all.lastIndex) TextButton(onClick = { Tour.stop(); onTab(0) }) { Text(t("Passer")) }
                        Button(onClick = {
                            if (current < all.lastIndex) Tour.step.value = current + 1 else { Tour.stop(); onTab(0) }
                        }) { Text(if (current < all.lastIndex) t("Suivant") else t("C'est parti !")) }
                    }
                }
            }
        }
    }
}
