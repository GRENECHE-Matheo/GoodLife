package com.goodlife.app.ui.screens

import com.goodlife.app.i18n.t

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.goodlife.app.ai.Nutrition
import com.goodlife.app.data.Repo
import com.goodlife.app.game.DayStatus
import com.goodlife.app.game.Game
import com.goodlife.app.game.GameSummary
import com.goodlife.app.game.QuizBank
import com.goodlife.app.ui.ChefMascot
import com.goodlife.app.ui.ChefMood
import com.goodlife.app.ui.LineChart
import com.goodlife.app.ui.ScreenColumn
import com.goodlife.app.ui.SubScreenHeader
import com.goodlife.app.ui.SectionCard
import com.goodlife.app.ui.toNumber
import com.goodlife.app.ui.days
import com.goodlife.app.ui.theme.successColor
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.LaunchedEffect

val FLAME = Color(0xFFFF8A00)

fun statusLabel(s: DayStatus) = when (s) {
    DayStatus.REUSSI -> t("Réussi")
    DayStatus.RATTRAPE -> t("Rattrapé")
    DayStatus.RATE -> t("Hors objectif")
    DayStatus.VIDE -> t("Rien noté")
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProgressScreen(summary: GameSummary, onBack: () -> Unit, onQuiz: () -> Unit, focus: String = "", startWeighIn: Boolean = false) {
    BackHandler(onBack = onBack)
    val profile by Repo.profile.collectAsState()
    val game by Repo.game.collectAsState()
    val p = profile ?: return
    var weighIn by remember { mutableStateOf(startWeighIn) }
    // Ouvert depuis l'accueil sur une section précise (poids, pas, calories) : on la fait défiler à l'écran
    val requesters = remember { HashMap<String, BringIntoViewRequester>() }
    fun focusReq(key: String) = requesters.getOrPut(key) { BringIntoViewRequester() }
    LaunchedEffect(focus) {
        if (focus.isNotEmpty()) { kotlinx.coroutines.delay(250); requesters[focus]?.bringIntoView() }
    }
    val lvl = summary.level

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        ScreenColumn {
            SubScreenHeader(t("Mes progrès"), onBack)

            // ---- Niveau ----
            SectionCard(container = MaterialTheme.colorScheme.primaryContainer) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ChefMascot(size = 84.dp, mood = ChefMood.CONTENT)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t("Niveau %1\$s", lvl.level), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(lvl.title, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        val animated by animateFloatAsState(lvl.progress, tween(900, easing = FastOutSlowInEasing), label = "level")
                        LinearProgressIndicator(
                            progress = { animated },
                            modifier = Modifier.fillMaxWidth().height(10.dp),
                            strokeCap = StrokeCap.Round
                        )
                        Text(
                            t("%1\$s / %2\$s XP · %3\$s XP au total", lvl.xpInLevel, lvl.xpForNext, lvl.totalXp),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // ---- Suivi : calories, pas, poids, score (en haut, faciles à trouver ; touche un graphique pour voir la valeur) ----
            val last14 = (summary.history.takeLast(13) + summary.today)
            // ---- Calories ----
            SectionCard(modifier = Modifier.bringIntoViewRequester(focusReq("kcal")), title = t("Calories"), icon = Icons.AutoMirrored.Filled.ShowChart) {
                LineChart(
                    values = last14.map { if (it.kcal > 0) it.kcal.toFloat() else null },
                    labels = last14.mapIndexed { i, d -> if (i % 2 == last14.size % 2) d.date.takeLast(2) else "" },
                    color = MaterialTheme.colorScheme.secondary,
                    minY = 0f,
                    reference = p.targetKcal.toFloat(),
                    tips = last14.map { dayLabel(it.date).replaceFirstChar { c -> c.uppercase() } },
                    format = { "%.0f kcal".format(it) }
                )
                Text(
                    t("Pointillés : ton objectif (%1\$s kcal).", p.targetKcal),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ---- Pas ----
            val stepsData by Repo.steps.collectAsState()
            if (stepsData.days.isNotEmpty()) {
                SectionCard(modifier = Modifier.bringIntoViewRequester(focusReq("steps")), title = t("Pas"), icon = Icons.AutoMirrored.Filled.DirectionsWalk) {
                    val stepDays = last14.map { stepsData.days[it.date] }
                    LineChart(
                        values = stepDays.map { it?.steps?.toFloat() },
                        labels = last14.mapIndexed { i, d -> if (i % 2 == last14.size % 2) d.date.takeLast(2) else "" },
                        color = MaterialTheme.colorScheme.tertiary,
                        minY = 0f,
                        reference = com.goodlife.app.steps.Steps.goal().toFloat(),
                        tips = last14.map { dayLabel(it.date).replaceFirstChar { c -> c.uppercase() } },
                        format = { t("%1\$s pas", formatSteps(it.toInt())) }
                    )
                    Text(
                        t("Pointillés : ton objectif du jour (%1\$s pas).", formatSteps(com.goodlife.app.steps.Steps.goal())),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ---- Poids ----
            SectionCard(modifier = Modifier.bringIntoViewRequester(focusReq("weight")), title = t("Poids"), icon = Icons.Filled.FitnessCenter) {
                val w = game.weights.takeLast(20)
                if (w.size >= 2) {
                    LineChart(
                        values = w.map { it.second.toFloat() },
                        labels = w.mapIndexed { i, e -> if (i == 0 || i == w.lastIndex) e.first.substring(5) else "" },
                        color = MaterialTheme.colorScheme.tertiary,
                        tips = w.map { dayLabel(it.first).replaceFirstChar { c -> c.uppercase() } },
                        format = { "%.1f kg".format(it) }
                    )
                    val diff = w.last().second - w.first().second
                    Text(
                        t("Depuis le %1\$s : %2\$s kg", w.first().first.substring(8) + "/" + w.first().first.substring(5, 7), (if (diff >= 0) "+" else "") + "%.1f".format(diff)),
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    Text(
                        t("Ajoute tes pesées pour voir ta courbe (une par semaine suffit)."),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                FilledTonalButton(onClick = { weighIn = true }) { Text(t("Nouvelle pesée")) }
            }

            // ---- Score quotidien ----
            SectionCard(title = t("Score par jour"), icon = Icons.AutoMirrored.Filled.ShowChart) {
                LineChart(
                    values = last14.map { if (it.kcal > 0) it.score.toFloat() else null },
                    labels = last14.mapIndexed { i, d -> if (i % 2 == last14.size % 2) d.date.takeLast(2) else "" },
                    color = MaterialTheme.colorScheme.primary,
                    minY = 0f, maxY = 100f,
                    tips = last14.map { dayLabel(it.date).replaceFirstChar { c -> c.uppercase() } },
                    format = { "%.0f/100".format(it) }
                )
                Text(
                    t("100 = calories dans l'objectif, assez de protéines et des repas répartis. Le dernier point est aujourd'hui (en cours)."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ---- Série ----
            SectionCard(title = t("Série"), icon = Icons.Filled.LocalFireDepartment) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.LocalFireDepartment, null, tint = FLAME, modifier = Modifier.padding(end = 8.dp))
                    Text(
                        days(summary.streak),
                        style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold
                    )
                }
                Text(t("Record : %1\$s", days(summary.bestStreak)), style = MaterialTheme.typography.bodyMedium)
                // Gels de série : visibles, avec la règle pour en gagner
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text(t("Gels ❄️ : %1\$s / %2\$s", game.freezes, Game.MAX_FREEZES), style = MaterialTheme.typography.titleSmall)
                        Text(
                            t("Tu gagnes un gel tous les %1\$s jours de série (%2\$s au plus). Si ta série casse, un gel la sauve : il suffit de réussir un quiz de %3\$s questions le lendemain.", Game.FREEZE_EVERY, Game.MAX_FREEZES, Game.FREEZE_QUESTIONS) +
                                (if (game.freezes < Game.MAX_FREEZES) " " + t("Prochain gel dans %1\$s.", days(Game.FREEZE_EVERY - summary.streak % Game.FREEZE_EVERY)) else ""),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                Text(
                    t("Aujourd'hui : %1\$s / %2\$s kcal · score %3\$s/100 ", summary.today.kcal, p.targetKcal, summary.today.score) +
                        if (summary.today.status == DayStatus.REUSSI) t("(dans l'objectif pour l'instant)") else "",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    Game.rulesText(p.goal) + t(" Chaque jour est validé à minuit."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ---- Quiz ----
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ChefMascot(size = 64.dp, mood = if (summary.recoverableStreak > 0) ChefMood.TRISTE else ChefMood.QUESTION)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t("Quiz du jour"), style = MaterialTheme.typography.titleMedium)
                        Text(
                            when {
                                summary.recoverableStreak > 0 ->
                                    t("Ta série de %1\$s s'est arrêtée hier. %2\$s bonnes réponses sur %3\$s pour la sauver !", days(summary.recoverableStreak), QuizBank.PASS, QuizBank.PER_DAY)
                                summary.quizDoneToday -> t("Fait aujourd'hui : %1\$s/%2\$s. Nouvelles questions demain.", game.quizResults[summary.today.date] ?: 0, QuizBank.PER_DAY) +
                                    (if (game.freezes > 0) t(" Gels de série : %1\$s ❄️", game.freezes) else "")
                                else -> t("%1\$s questions sur l'alimentation, jusqu'à %2\$s XP.", QuizBank.PER_DAY, Game.quizXp(QuizBank.PER_DAY))
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                Button(onClick = onQuiz) { Text(if (summary.quizDoneToday) t("Rejouer (entraînement)") else t("Commencer le quiz")) }
            }

            BadgesSection(summary)
            FeelingInsights()

            // ---- Historique ----
            SectionCard(title = t("7 derniers jours")) {
                val week = summary.history.takeLast(7).reversed()
                if (week.isEmpty()) {
                    Text(t("Ton historique apparaîtra ici dès demain."), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                week.forEachIndexed { i, d ->
                    if (i > 0) HorizontalDivider()
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(dayLabel(d.date).replaceFirstChar { it.uppercase() }, fontWeight = FontWeight.Medium)
                            Text(
                                "${d.kcal} kcal" + (if (d.stepGoal > 0) t(" · %1\$s pas", formatSteps(d.steps)) else "") +
                                    t(" · score %1\$s · +%2\$s XP", d.score, d.xp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            statusLabel(d.status),
                            color = when (d.status) {
                                DayStatus.REUSSI, DayStatus.RATTRAPE -> successColor
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (weighIn) {
        var kg by remember { mutableStateOf(p.weightKg.toString()) }
        val v = kg.toNumber()
        AlertDialog(
            onDismissRequest = { weighIn = false },
            title = { Text(t("Nouvelle pesée")) },
            text = {
                OutlinedTextField(
                    kg, { kg = it }, label = { Text(t("Poids (kg)")) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
            },
            confirmButton = {
                TextButton(
                    enabled = v != null && v in 25.0..350.0,
                    onClick = {
                        val updated = p.copy(weightKg = v!!)
                        // L'objectif calculé par formule suit le poids ; un objectif IA reste tel quel.
                        Repo.saveProfile(if (p.targetSource == "formule") Nutrition.formulaTarget(updated) else updated)
                        Repo.logWeight(v)
                        weighIn = false
                    }
                ) { Text(t("Enregistrer")) }
            },
            dismissButton = { TextButton(onClick = { weighIn = false }) { Text(t("Annuler")) } }
        )
    }
}
