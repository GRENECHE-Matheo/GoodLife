@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.goodlife.app.ui.screens

import com.goodlife.app.i18n.t
import com.goodlife.app.i18n.tp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.goodlife.app.coach.Coach
import com.goodlife.app.data.Repo
import com.goodlife.app.game.Badge
import com.goodlife.app.game.GameSummary
import com.goodlife.app.game.Milestones
import com.goodlife.app.ui.ChefMascot
import com.goodlife.app.ui.ChefMood
import com.goodlife.app.ui.SectionCard
import java.util.Locale

private const val GLASS_ML = 250

/** Hydratation : un appui = un verre (250 ml). */
@Composable
fun WaterCard() {
    val water by Repo.water.collectAsState()
    val settings by Repo.settings.collectAsState()
    val today = rememberToday()
    val ml = water[today] ?: 0
    val goal = remember(settings) { Repo.waterGoal() }
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.WaterDrop, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(t("Eau : %1\$s / %2\$s ml", Coach.fmt(ml), Coach.fmt(goal)), style = MaterialTheme.typography.titleSmall)
                Text(tp(ml / GLASS_ML, "%1\$s verre de 250 ml", "%1\$s verres de 250 ml") + if (ml >= goal) t(" · objectif atteint 💧") else "",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(enabled = ml > 0, onClick = { Repo.addWater(-GLASS_ML) }) { Text("−") }
            FilledTonalButton(onClick = { Repo.addWater(GLASS_ML) }) { Text(t("+ 1 verre")) }
        }
        LinearProgressIndicator(progress = { (ml.toFloat() / goal).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(8.dp), strokeCap = StrokeCap.Round)
        if (Repo.waterGoalFromAi() && settings.waterGoalIaWhy.isNotBlank()) {
            Text(t("Objectif du jour conseillé par l'IA : %1\$s", settings.waterGoalIaWhy),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private val MOODS = listOf("😞", "🙁", "😐", "🙂", "😄")
private val ENERGY = listOf("🪫", "😴", "🙂", "💪", "⚡")

/** « Comment tu te sens ? » : humeur et énergie du jour, en deux appuis. */
@Composable
fun FeelingCard() {
    val feelings by Repo.feelings.collectAsState()
    val today = rememberToday()
    val f = feelings[today]
    var mood by remember(today) { mutableStateOf(f?.mood ?: 0) }
    var energy by remember(today) { mutableStateOf(f?.energy ?: 0) }
    var editing by remember(today) { mutableStateOf(f == null) }
    if (!editing && f != null) {
        Surface(onClick = { editing = true }, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(t("Aujourd'hui : humeur %1\$s · énergie %2\$s", MOODS[f.mood - 1], ENERGY[f.energy - 1]), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(t("Modifier"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
        return
    }
    SectionCard(title = t("Comment tu te sens aujourd'hui ?")) {
        Text(t("Humeur"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ScaleRow(MOODS, mood) { mood = it }
        Text(t("Énergie"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ScaleRow(ENERGY, energy) { energy = it }
        Button(enabled = mood > 0 && energy > 0, onClick = { Repo.setFeeling(mood, energy); editing = false }) { Text(t("Enregistrer")) }
        Text(t("Reste sur ton téléphone. Après quelques jours, le chef te montre ce qui semble t'aider (dans Mes progrès)."),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ScaleRow(icons: List<String>, selected: Int, onPick: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        icons.forEachIndexed { i, e ->
            Surface(
                onClick = { onPick(i + 1) }, shape = RoundedCornerShape(16.dp),
                color = if (selected == i + 1) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
            ) { Text(e, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) }
        }
    }
}

/** Missions de la première semaine (XP donnée une seule fois). */
@Composable
fun MissionsCard(summary: GameSummary, onQuiz: () -> Unit = {}) {
    val meals by Repo.meals.collectAsState(); val game by Repo.game.collectAsState(); val water by Repo.water.collectAsState()
    val feelings by Repo.feelings.collectAsState(); val social by Repo.social.collectAsState(); val outings by Repo.outings.collectAsState()
    val missions = remember(meals, game, water, feelings, social, outings, summary) { Milestones.missions(summary) }
    LaunchedEffect(missions) { Milestones.rewardMissions(missions) }
    if (!Milestones.showMissions(missions)) return
    // Repliée par défaut : une ligne avec la prochaine mission ; on déplie pour voir la liste
    var open by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val done = missions.count { it.done }
    val next = missions.firstOrNull { !it.done }
    Surface(onClick = { open = !open }, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.EmojiEvents, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(t("Missions de départ · %1\$s/%2\$s", done, missions.size), style = MaterialTheme.typography.titleSmall)
                    if (!open && next != null) Text(t("Prochaine : %1\$s", "${next.emoji} ${next.title}"),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
                Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, if (open) t("Replier") else t("Voir les missions"))
            }
            LinearProgressIndicator(progress = { done.toFloat() / missions.size }, modifier = Modifier.fillMaxWidth().height(6.dp), strokeCap = StrokeCap.Round)
            if (open) missions.forEach { m ->
                // Une mission à faire mène directement là où on la fait (avant, il fallait chercher)
                val go: (() -> Unit)? = if (m.done) null else when (m.id) {
                    "repas", "scan" -> ({ com.goodlife.app.social.AppNav.request.value = "add" })
                    "quiz" -> onQuiz
                    "sport" -> ({ com.goodlife.app.social.AppNav.request.value = "forme" })
                    "ami" -> ({ com.goodlife.app.social.AppNav.request.value = "moi:friends" })
                    else -> null
                }
                Row(Modifier.fillMaxWidth().then(if (go != null) Modifier.clickable(onClick = go) else Modifier).padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (m.done) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked, null,
                        tint = if (m.done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("${m.emoji} ${m.title}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
                        color = if (m.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                    Text(t("+%1\$s XP", m.xp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    if (go != null) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

/** Petite annonce quand un badge vient d'être débloqué. */
@Composable
fun NewBadgeCard(summary: GameSummary, onOpen: () -> Unit) {
    val dex by Repo.dex.collectAsState(); val game by Repo.game.collectAsState(); val outings by Repo.outings.collectAsState()
    val social by Repo.social.collectAsState(); val water by Repo.water.collectAsState(); val steps by Repo.steps.collectAsState()
    val all = remember(summary, dex, game, outings, social, water, steps) { Milestones.badges(summary) }
    var fresh by remember(all) { mutableStateOf(Milestones.newBadges(all)) }
    if (fresh.isEmpty()) return
    // Une seule ligne : on la touche pour voir les badges, ou on la ferme
    Surface(
        onClick = { Milestones.markBadgesSeen(all); fresh = emptyList(); onOpen() },
        shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.tertiaryContainer
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(fresh.first().emoji, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(if (fresh.size > 1) t("%1\$s nouveaux badges !", fresh.size) else t("Nouveau badge !"), style = MaterialTheme.typography.labelLarge)
                Text(fresh.first().title + if (fresh.size > 1) t(" et %1\$s autres", fresh.size - 1) else "",
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1)
            }
            androidx.compose.material3.IconButton(onClick = { Milestones.markBadgesSeen(all); fresh = emptyList() }) {
                Icon(Icons.Filled.Close, t("Fermer"))
            }
        }
    }
}

/** Badges (dans Mes progrès). */
@Composable
fun BadgesSection(summary: GameSummary) {
    val all = remember(summary) { Milestones.badges(summary) }
    LaunchedEffect(all) { Milestones.markBadgesSeen(all) }
    SectionCard(title = t("Badges · %1\$s/%2\$s", all.count { it.unlocked }, all.size), icon = Icons.Filled.EmojiEvents) {
        // Grille qui remplit toute la largeur : autant de colonnes que la place le permet (≥ 92 dp chacune),
        // de la plus petite à la plus grande largeur de téléphone ou de tablette
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
            val gap = 8.dp
            val cols = ((maxWidth + gap) / (92.dp + gap)).toInt().coerceIn(2, 8)
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                all.chunked(cols).forEach { row ->
                    Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(gap)) {
                        row.forEach { b -> BadgeChip(b, Modifier.weight(1f).fillMaxHeight()) }
                        repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun BadgeChip(b: Badge, modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(16.dp), color = if (b.unlocked) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier) {
        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (b.unlocked) b.emoji else "🔒", style = MaterialTheme.typography.headlineSmall)
            Text(b.title, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 2,
                color = if (b.unlocked) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
            if (!b.unlocked) Text(b.how, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3)
        }
    }
}

/**
 * Ce qui semble t'aider : comparaisons simples entre tes propres jours (moyennes réelles), affichées seulement
 * quand il y a assez de jours de chaque côté. Une tendance, pas une preuve.
 */
@Composable
fun FeelingInsights() {
    val feelings by Repo.feelings.collectAsState()
    val steps by Repo.steps.collectAsState()
    val sleep by Repo.sleep.collectAsState()
    val lines = remember(feelings, steps, sleep) {
        val out = mutableListOf<String>()
        fun avg(l: List<Int>) = String.format(com.goodlife.app.i18n.Lang.locale, "%.1f", l.average())
        // Pas : objectif atteint ou non → énergie
        val withSteps = feelings.mapNotNull { (d, f) -> steps.days[d]?.takeIf { it.goal > 0 }?.let { (it.steps >= it.goal) to f.energy } }
        val hit = withSteps.filter { it.first }.map { it.second }; val miss = withSteps.filter { !it.first }.map { it.second }
        if (hit.size >= 4 && miss.size >= 4) out += t("Les jours où tu atteins ton objectif de pas, ton énergie moyenne est de %1\$s/5, contre %2\$s/5 les autres jours (%3\$s jours notés).", avg(hit), avg(miss), withSteps.size)
        // Sommeil de la nuit précédente : 7 h ou plus → humeur
        val sleepByDay = sleep.groupBy { java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).format(java.util.Date(it.end)) }
            .mapValues { e -> e.value.sumOf { it.end - it.start } / 3_600_000.0 }
        val withSleep = feelings.mapNotNull { (d, f) -> sleepByDay[d]?.let { (it >= 7.0) to f.mood } }
        val good = withSleep.filter { it.first }.map { it.second }; val short = withSleep.filter { !it.first }.map { it.second }
        if (good.size >= 4 && short.size >= 4) out += t("Après une nuit de 7 h ou plus, ton humeur moyenne est de %1\$s/5, contre %2\$s/5 après une nuit plus courte (%3\$s jours notés).", avg(good), avg(short), withSleep.size)
        out
    }
    SectionCard(title = t("Ce qui semble t'aider")) {
        if (lines.isEmpty()) Text(
            t("Note ton humeur et ton énergie quelques jours (sur l'accueil) : dès qu'il y a assez de jours à comparer, tu verras ici des tendances calculées sur tes propres données."),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
        ) else {
            lines.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
            Text(t("Ce sont des moyennes sur tes propres jours : une tendance observée, pas une preuve ni un avis médical."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Garde-fou bienveillant : quelques jours avec très peu de calories notées. */
@Composable
fun CareCard(summary: GameSummary) {
    val settings by Repo.settings.collectAsState()
    if (!remember(summary, settings.guardSnoozeUntil) { Milestones.undereating(summary) }) return
    SectionCard(container = MaterialTheme.colorScheme.secondaryContainer) {
        Row(verticalAlignment = Alignment.Top) {
            ChefMascot(size = 56.dp, mood = ChefMood.COEUR)
            Spacer(Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(t("Le chef prend de tes nouvelles"), style = MaterialTheme.typography.titleSmall)
                Text(
                    t("Ces trois derniers jours, tes repas notés font moins de la moitié de tes besoins estimés. Peut-être que tu n'as pas tout noté, et c'est très bien ainsi ! Mais si manger est compliqué en ce moment, c'est important d'en parler à un proche ou à un médecin. Fil Santé Jeunes répond aussi, gratuitement et anonymement, au 0 800 235 236."),
                    style = MaterialTheme.typography.bodyMedium
                )
                TextButton(onClick = { Repo.updateSettings { it.copy(guardSnoozeUntil = System.currentTimeMillis() + 7 * 86_400_000L) } }) {
                    Text(t("Merci, ne plus afficher cette semaine"))
                }
            }
        }
    }
}
