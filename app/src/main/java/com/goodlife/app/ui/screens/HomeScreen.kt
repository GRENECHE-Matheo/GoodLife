@file:OptIn(ExperimentalMaterial3Api::class)

package com.goodlife.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import com.goodlife.app.ui.SlideSwitch
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.graphics.StrokeCap
import com.goodlife.app.game.Game
import com.goodlife.app.game.GameSummary
import com.goodlife.app.game.QuizBank
import com.goodlife.app.ui.Avatar
import com.goodlife.app.ui.ChefMascot
import com.goodlife.app.ui.ChefMood
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.goodlife.app.ai.Gemini
import com.goodlife.app.data.Meal
import com.goodlife.app.data.MealSlot
import com.goodlife.app.data.MealSuggestion
import com.goodlife.app.data.Recipe
import com.goodlife.app.data.Repo
import com.goodlife.app.net.Updater
import com.goodlife.app.ui.CalorieRing
import com.goodlife.app.ui.MacroBar
import com.goodlife.app.ui.ScreenTitle
import com.goodlife.app.ui.ScreenColumn
import com.goodlife.app.ui.SectionCard
import com.goodlife.app.ui.WeekBars
import com.goodlife.app.ui.formatDay
import com.goodlife.app.ui.formatTime
import com.goodlife.app.ui.theme.GoogleGreen
import com.goodlife.app.ui.theme.GoogleRed
import com.goodlife.app.ui.theme.GoogleYellow
import com.goodlife.app.ui.toNumber
import com.goodlife.app.ui.AiContentFooter
import com.goodlife.app.ui.days
import kotlinx.coroutines.launch
import java.util.Calendar

@Composable
fun HomeScreen(onScan: () -> Unit, onOpenProfile: () -> Unit) {
    val profile by Repo.profile.collectAsState()
    val meals by Repo.meals.collectAsState()
    val game by Repo.game.collectAsState()
    val p = profile ?: return
    val steps by Repo.steps.collectAsState()
    val summary = remember(meals, p, game, steps) { Game.summarize(meals, p, game, steps.days, newRulesFrom = Repo.settings.value.scoreRulesFrom) }
    var overlay by rememberSaveable { mutableStateOf("") }
    SlideSwitch(overlay, depth = { when (it) { "" -> 0; "progress", "news", "coach" -> 1; else -> 2 } }) { screen ->
        when (screen) {
            "news" -> NewsScreen(onBack = { overlay = "" })
            "coach" -> CoachScreen(onBack = { overlay = "" })
            "progress" -> ProgressScreen(summary, onBack = { overlay = "" }, onQuiz = { overlay = "quiz" })
            "quiz" -> QuizScreen(summary.recoverableStreak, summary.quizDoneToday, summary.level.totalXp, onClose = { overlay = "" })
            else -> HomeContent(
                onScan, summary, onProgress = { overlay = "progress" }, onQuiz = { overlay = "quiz" },
                onOpenProfile = onOpenProfile, onNews = { overlay = "news" }, onCoach = { overlay = "coach" }
            )
        }
    }
}

@Composable
private fun HomeContent(
    onScan: () -> Unit,
    summary: GameSummary,
    onProgress: () -> Unit,
    onQuiz: () -> Unit,
    onOpenProfile: () -> Unit,
    onNews: () -> Unit,
    onCoach: () -> Unit
) {
    val profile by Repo.profile.collectAsState()
    val meals by Repo.meals.collectAsState()
    val settings by Repo.settings.collectAsState()
    val scope = rememberCoroutineScope()
    val avatar by Repo.avatar.collectAsState()
    val p = profile ?: return

    val today = Repo.mealsOfDay(meals)
    val eaten = today.sumOf { it.kcal }
    val remaining = p.targetKcal - eaten

    var showAdd by remember { mutableStateOf(false) }
    var suggestions by remember { mutableStateOf<List<MealSuggestion>>(emptyList()) }
    var sugLoading by remember { mutableStateOf(false) }
    var sugError by remember { mutableStateOf<String?>(null) }
    var expandedIdx by remember { mutableIntStateOf(-1) }
    var sugSlot by remember { mutableStateOf<MealSlot?>(null) }
    var planFor by remember { mutableStateOf<MealSuggestion?>(null) }
    var recipeFor by remember { mutableStateOf<MealSuggestion?>(null) }
    val recipes = remember { mutableStateMapOf<String, Recipe>() }

    val uri = LocalUriHandler.current
    LaunchedEffect(Unit) { Updater.check() }

    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val hello = if (hour < 18) "Bonjour" else "Bonsoir"

    ScreenColumn {
        // En-tête : photo, salutation, série et accès au quiz
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Appuyer sur sa photo ou son nom ouvre les infos du compte
            Row(
                Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).clickable(onClick = onOpenProfile).padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
            Avatar(avatar, p.name, 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (p.name.isBlank()) hello else "$hello ${p.name}",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    formatDay(System.currentTimeMillis()).replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            }
            Surface(
                onClick = onProgress,
                shape = RoundedCornerShape(50),
                color = FLAME.copy(alpha = 0.15f)
            ) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    val pulse = rememberInfiniteTransition(label = "flame")
                    val scale by pulse.animateFloat(
                        1f, if (summary.streak > 0) 1.15f else 1f,
                        infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "flameScale"
                    )
                    Icon(
                        Icons.Filled.LocalFireDepartment, "Série", tint = FLAME,
                        modifier = Modifier.size(20.dp).graphicsLayer { scaleX = scale; scaleY = scale }
                    )
                    Text("${summary.streak}", fontWeight = FontWeight.Bold, color = FLAME)
                }
            }
            Spacer(Modifier.width(4.dp))
            Surface(onClick = onQuiz, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                ChefMascot(Modifier.padding(2.dp), size = 44.dp, mood = if (summary.quizDoneToday) ChefMood.CONTENT else ChefMood.QUESTION)
            }
        }

        CoachCard(summary, p, onOpen = onCoach)
        if (!settings.notifAsked) NotifOptInCard()
        NewsTeaser(onOpen = onNews)

        // Série cassée hier : le cuisto propose de la sauver
        if (summary.recoverableStreak > 0) {
            SectionCard(container = FLAME.copy(alpha = 0.12f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ChefMascot(size = 64.dp, mood = ChefMood.TRISTE)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Ta série de ${days(summary.recoverableStreak)} s'est arrêtée hier", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Réponds au quiz du chef (${QuizBank.PASS}/${QuizBank.PER_DAY}) aujourd'hui pour la sauver.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                Button(onClick = onQuiz) { Text("Sauver ma série") }
            }
        }

        // Niveau et XP
        Surface(onClick = onProgress, shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primaryContainer) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Niveau ${summary.level.level} · ${summary.level.title}",
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    Text("Score du jour : ${summary.today.score}", style = MaterialTheme.typography.labelLarge)
                }
                val xp by animateFloatAsState(summary.level.progress, tween(900, easing = FastOutSlowInEasing), label = "xp")
                LinearProgressIndicator(
                    progress = { xp },
                    modifier = Modifier.fillMaxWidth().height(10.dp),
                    strokeCap = StrokeCap.Round
                )
                Text(
                    "${summary.level.xpForNext - summary.level.xpInLevel} XP avant le niveau ${summary.level.level + 1} · voir mes progrès",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        val update = if (settings.checkUpdates) Updater.availableUpdate() else null
        if (update != null && update.tag != settings.dismissedTag) {
            SectionCard(
                title = "Nouvelle version ${update.tag} disponible",
                icon = Icons.Filled.SystemUpdate,
                container = MaterialTheme.colorScheme.primaryContainer
            ) {
                UpdatePanel(update, showDismiss = true)
            }
        }

        StepsAutoRefresh()
        SectionCard {
            val stepsData by Repo.steps.collectAsState()
            val stepsProgress = if (settings.stepsEnabled) {
                (stepsData.days[com.goodlife.app.data.localDay(0)]?.steps ?: 0).toFloat() / com.goodlife.app.steps.Steps.goal()
            } else null
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CalorieRing(
                    consumed = eaten, target = p.targetKcal,
                    size = if (stepsProgress != null) 200.dp else 180.dp,
                    stepsProgress = stepsProgress
                )
            }
            Text(
                if (remaining >= 0) "Il te reste $remaining kcal aujourd'hui"
                else "Objectif dépassé de ${-remaining} kcal",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            StepsLine()
            MacroBar("Protéines", today.sumOf { it.proteinG }, p.proteinG, GoogleRed)
            MacroBar("Glucides", today.sumOf { it.carbsG }, p.carbsG, GoogleYellow)
            MacroBar("Lipides", today.sumOf { it.fatG }, p.fatG, GoogleGreen)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onScan, modifier = Modifier.weight(1f).height(52.dp)) {
                Icon(Icons.Filled.PhotoCamera, null)
                Spacer(Modifier.width(8.dp))
                Text("Scanner")
            }
            FilledTonalButton(onClick = { showAdd = true }, modifier = Modifier.weight(1f).height(52.dp)) {
                Icon(Icons.Filled.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Saisir")
            }
        }

        SectionCard(title = "Repas du jour", icon = Icons.Filled.Restaurant) {
            if (today.isEmpty()) {
                Text(
                    "Aucun repas enregistré. Prends ton assiette en photo pour commencer.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            today.forEachIndexed { i, m ->
                if (i > 0) HorizontalDivider()
                MealRow(m) { Repo.deleteMeal(m.id) }
            }
        }

        SectionCard(title = "Idées de repas", icon = Icons.Filled.AutoAwesome) {
            if (!settings.aiEnabled) {
                Text(
                    "Les idées de repas utilisent l'IA (désactivée). Tu peux l'activer dans Paramètres.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    "Selon ce qu'il te reste, tes habitudes et tes allergies. Touche une idée pour les détails.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // Pour quel repas ?
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = sugSlot == null, onClick = { sugSlot = null }, label = { Text("Toute la journée") })
                    MealSlot.entries.forEach { slot ->
                        FilterChip(selected = sugSlot == slot, onClick = { sugSlot = slot }, label = { Text(slot.label) })
                    }
                }
                suggestions.forEachIndexed { i, s ->
                    if (i > 0) HorizontalDivider()
                    SuggestionRow(
                        s = s,
                        expanded = expandedIdx == i,
                        onToggle = { expandedIdx = if (expandedIdx == i) -1 else i },
                        onPlan = { planFor = s },
                        onRecipe = { recipeFor = s }
                    )
                }
                if (sugError != null) Text(sugError!!, color = MaterialTheme.colorScheme.error)
                // « Régénérer » remplace la liste ; « Plus d'idées » en ajoute d'autres, différentes
                fun ask(append: Boolean) {
                    sugLoading = true; sugError = null
                    scope.launch {
                        try {
                            val fresh = Gemini(settings.apiKey, settings.model)
                                .suggestMeals(p, today, sugSlot, avoid = suggestions.map { it.name })
                            suggestions = if (append) suggestions + fresh else fresh
                            if (!append) { expandedIdx = -1; recipes.clear() }
                        } catch (e: Exception) {
                            sugError = e.message
                        } finally {
                            sugLoading = false
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalButton(enabled = !sugLoading, onClick = { ask(append = false) }) {
                        if (sugLoading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Icon(if (suggestions.isEmpty()) Icons.Filled.AutoAwesome else Icons.Filled.Refresh, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (suggestions.isEmpty()) "Proposer des repas" else "Régénérer")
                    }
                    if (suggestions.isNotEmpty()) {
                        TextButton(enabled = !sugLoading, onClick = { ask(append = true) }) {
                            Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Plus d'idées")
                        }
                    }
                }
            }
        }

        SectionCard(title = "7 derniers jours", icon = Icons.Filled.BarChart) {
            val days = (-6..0).toList()
            val values = days.map { d -> Repo.mealsOfDay(meals, d).sumOf { it.kcal }.toFloat() }
            val labels = days.map { d ->
                formatDay(Repo.dayBounds(d).first).take(3).replaceFirstChar { it.uppercase() }
            }
            WeekBars(values, p.targetKcal.toFloat(), labels, MaterialTheme.colorScheme.primary)
            Text(
                "Moyenne : ${values.average().toInt()} kcal/jour · la ligne jaune = ton objectif",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(8.dp))
    }

    planFor?.let { s ->
        AddToPlanDialog(
            initialName = s.name, initialKcal = s.kcal, initialSlot = MealSlot.guess(s.moment),
            description = s.description, recipe = recipes[s.name], editableName = false,
            onDismiss = { planFor = null }
        )
    }
    recipeFor?.let { s ->
        RecipeDialog(
            name = s.name, description = s.description, kcal = s.kcal, cached = recipes[s.name],
            onLoaded = { recipes[s.name] = it }, onDismiss = { recipeFor = null }
        )
    }

    if (showAdd) AddMealDialog(onDismiss = { showAdd = false }) { Repo.addMeal(it); showAdd = false }
}

@Composable
private fun SuggestionRow(
    s: MealSuggestion,
    expanded: Boolean,
    onToggle: () -> Unit,
    onPlan: () -> Unit,
    onRecipe: () -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(s.name, fontWeight = FontWeight.Medium)
                Text(
                    listOf(s.moment.replaceFirstChar { it.uppercase() }, s.summary).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text("${s.kcal} kcal", color = MaterialTheme.colorScheme.primary)
            Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null)
        }
        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                Text(s.description, style = MaterialTheme.typography.bodyMedium)
                if (s.why.isNotBlank()) {
                    Text(
                        s.why, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                AiContentFooter("Idée de repas : ${s.name} (${s.kcal} kcal)\n${s.description}\n${s.why}")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = onPlan) {
                        Icon(Icons.Filled.DateRange, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Planifier")
                    }
                    OutlinedButton(onClick = onRecipe) {
                        Icon(Icons.Filled.Restaurant, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Recette")
                    }
                }
            }
        }
    }
}

@Composable
private fun MealRow(m: Meal, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(m.name, fontWeight = FontWeight.Medium)
            Text(
                "${formatTime(m.timestamp)} · ${when (m.source) { "photo" -> "photo IA"; "ciqual" -> "Ciqual"; "code-barres" -> "code-barres"; "planning" -> "planning"; else -> "saisie" }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text("${m.kcal} kcal", style = MaterialTheme.typography.titleSmall)
        IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Supprimer") }
    }
}
