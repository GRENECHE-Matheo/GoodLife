package com.goodlife.app.ui.screens

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

val FLAME = Color(0xFFFF8A00)

fun statusLabel(s: DayStatus) = when (s) {
    DayStatus.REUSSI -> "Réussi"
    DayStatus.RATTRAPE -> "Rattrapé"
    DayStatus.RATE -> "Hors objectif"
    DayStatus.VIDE -> "Rien noté"
}

@Composable
fun ProgressScreen(summary: GameSummary, onBack: () -> Unit, onQuiz: () -> Unit) {
    BackHandler(onBack = onBack)
    val profile by Repo.profile.collectAsState()
    val game by Repo.game.collectAsState()
    val p = profile ?: return
    var weighIn by remember { mutableStateOf(false) }
    val lvl = summary.level

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        ScreenColumn {
            SubScreenHeader("Mes progrès", onBack)

            // ---- Niveau ----
            SectionCard(container = MaterialTheme.colorScheme.primaryContainer) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ChefMascot(size = 84.dp, mood = ChefMood.CONTENT)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Niveau ${lvl.level}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(lvl.title, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        val animated by animateFloatAsState(lvl.progress, tween(900, easing = FastOutSlowInEasing), label = "level")
                        LinearProgressIndicator(
                            progress = { animated },
                            modifier = Modifier.fillMaxWidth().height(10.dp),
                            strokeCap = StrokeCap.Round
                        )
                        Text(
                            "${lvl.xpInLevel} / ${lvl.xpForNext} XP · ${lvl.totalXp} XP au total",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // ---- Série ----
            SectionCard(title = "Série", icon = Icons.Filled.LocalFireDepartment) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.LocalFireDepartment, null, tint = FLAME, modifier = Modifier.padding(end = 8.dp))
                    Text(
                        days(summary.streak),
                        style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold
                    )
                }
                Text("Record : ${days(summary.bestStreak)}", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Aujourd'hui : ${summary.today.kcal} / ${p.targetKcal} kcal · score ${summary.today.score}/100 " +
                        if (summary.today.status == DayStatus.REUSSI) "(dans l'objectif pour l'instant)" else "",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    Game.rulesText(p.goal) + " Chaque jour est validé à minuit.",
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
                        Text("Quiz du jour", style = MaterialTheme.typography.titleMedium)
                        Text(
                            when {
                                summary.recoverableStreak > 0 ->
                                    "Ta série de ${days(summary.recoverableStreak)} s'est arrêtée hier. " +
                                        "${QuizBank.PASS} bonnes réponses sur ${QuizBank.PER_DAY} pour la sauver !"
                                summary.quizDoneToday -> "Fait aujourd'hui : ${game.quizResults[summary.today.date] ?: 0}/${QuizBank.PER_DAY}. Nouvelles questions demain."
                                else -> "${QuizBank.PER_DAY} questions sur l'alimentation, jusqu'à ${Game.quizXp(QuizBank.PER_DAY)} XP."
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                Button(onClick = onQuiz) { Text(if (summary.quizDoneToday) "Rejouer (entraînement)" else "Commencer le quiz") }
            }

            // ---- Score quotidien ----
            val last14 = (summary.history.takeLast(13) + summary.today)
            SectionCard(title = "Score par jour", icon = Icons.AutoMirrored.Filled.ShowChart) {
                LineChart(
                    values = last14.map { if (it.kcal > 0) it.score.toFloat() else null },
                    labels = last14.mapIndexed { i, d -> if (i % 2 == last14.size % 2) d.date.takeLast(2) else "" },
                    color = MaterialTheme.colorScheme.primary,
                    minY = 0f, maxY = 100f
                )
                Text(
                    "100 = pile dans ton objectif. Le dernier point est aujourd'hui (en cours).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ---- Calories ----
            SectionCard(title = "Calories", icon = Icons.AutoMirrored.Filled.ShowChart) {
                LineChart(
                    values = last14.map { if (it.kcal > 0) it.kcal.toFloat() else null },
                    labels = last14.mapIndexed { i, d -> if (i % 2 == last14.size % 2) d.date.takeLast(2) else "" },
                    color = MaterialTheme.colorScheme.secondary,
                    minY = 0f,
                    reference = p.targetKcal.toFloat()
                )
                Text(
                    "Pointillés : ton objectif (${p.targetKcal} kcal).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ---- Pas ----
            val stepsData by Repo.steps.collectAsState()
            if (stepsData.days.isNotEmpty()) {
                SectionCard(title = "Pas", icon = Icons.AutoMirrored.Filled.DirectionsWalk) {
                    val stepDays = last14.map { stepsData.days[it.date] }
                    LineChart(
                        values = stepDays.map { it?.steps?.toFloat() },
                        labels = last14.mapIndexed { i, d -> if (i % 2 == last14.size % 2) d.date.takeLast(2) else "" },
                        color = MaterialTheme.colorScheme.tertiary,
                        minY = 0f,
                        reference = com.goodlife.app.steps.Steps.goal().toFloat()
                    )
                    Text(
                        "Pointillés : ton objectif du jour (${formatSteps(com.goodlife.app.steps.Steps.goal())} pas).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ---- Poids ----
            SectionCard(title = "Poids", icon = Icons.Filled.FitnessCenter) {
                val w = game.weights.takeLast(20)
                if (w.size >= 2) {
                    LineChart(
                        values = w.map { it.second.toFloat() },
                        labels = w.mapIndexed { i, e -> if (i == 0 || i == w.lastIndex) e.first.substring(5) else "" },
                        color = MaterialTheme.colorScheme.tertiary
                    )
                    val diff = w.last().second - w.first().second
                    Text(
                        "Depuis le ${w.first().first.substring(8)}/${w.first().first.substring(5, 7)} : " +
                            "${if (diff >= 0) "+" else ""}${"%.1f".format(diff)} kg",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    Text(
                        "Ajoute tes pesées pour voir ta courbe (une par semaine suffit).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                FilledTonalButton(onClick = { weighIn = true }) { Text("Nouvelle pesée") }
            }

            // ---- Historique ----
            SectionCard(title = "7 derniers jours") {
                val week = summary.history.takeLast(7).reversed()
                if (week.isEmpty()) {
                    Text("Ton historique apparaîtra ici dès demain.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                week.forEachIndexed { i, d ->
                    if (i > 0) HorizontalDivider()
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(dayLabel(d.date).replaceFirstChar { it.uppercase() }, fontWeight = FontWeight.Medium)
                            Text(
                                "${d.kcal} kcal" + (if (d.stepGoal > 0) " · ${formatSteps(d.steps)} pas" else "") +
                                    " · score ${d.score} · +${d.xp} XP",
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
            title = { Text("Nouvelle pesée") },
            text = {
                OutlinedTextField(
                    kg, { kg = it }, label = { Text("Poids (kg)") }, singleLine = true,
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
                ) { Text("Enregistrer") }
            },
            dismissButton = { TextButton(onClick = { weighIn = false }) { Text("Annuler") } }
        )
    }
}
