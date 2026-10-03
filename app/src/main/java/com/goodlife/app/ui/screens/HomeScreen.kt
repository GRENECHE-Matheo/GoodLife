@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package com.goodlife.app.ui.screens

import androidx.compose.foundation.layout.FlowRow
import com.goodlife.app.i18n.t

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.graphicsLayer
import com.goodlife.app.ui.SlideSwitch
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
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
    val summary = remember(meals, p, game, steps) { Game.summarize(meals, p, game, steps.days, newRulesFrom = Repo.settings.value.scoreRulesFrom, foodOnlyFrom = Repo.settings.value.foodOnlyFrom, richFrom = Repo.settings.value.richScoreFrom) }
    var overlay by rememberSaveable { mutableStateOf("") }
    SlideSwitch(overlay, depth = { when { it == "" -> 0; it.startsWith("progress") || it == "news" || it == "coach" -> 1; else -> 2 } }) { screen ->
        when (screen) {
            "news" -> NewsScreen(onBack = { overlay = "" })
            "coach" -> CoachScreen(onBack = { overlay = "" })
            "progress" -> ProgressScreen(summary, onBack = { overlay = "" }, onQuiz = { overlay = "quiz" })
            // Ouvert depuis « Mon suivi » : directement sur le bon graphique (ou la pesée)
            "progress:kcal", "progress:steps", "progress:weight", "progress:weighin" -> ProgressScreen(
                summary, onBack = { overlay = "" }, onQuiz = { overlay = "quiz" },
                focus = screen.substringAfter(':').let { if (it == "weighin") "weight" else it },
                startWeighIn = screen == "progress:weighin"
            )
            "quiz" -> QuizScreen(summary.recoverableStreak, summary.quizDoneToday, summary.level.totalXp, onClose = { overlay = "" })
            "quizgel" -> QuizScreen(summary.recoverableStreak, summary.quizDoneToday, summary.level.totalXp, onClose = { overlay = "" }, gelMode = true)
            else -> HomeContent(
                onScan, summary, onProgress = { overlay = "progress" }, onQuiz = { overlay = "quiz" },
                onOpenProfile = onOpenProfile, onNews = { overlay = "news" }, onCoach = { overlay = "coach" },
                onGel = { overlay = "quizgel" }, onTrack = { overlay = "progress:$it" }
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
    onCoach: () -> Unit,
    onGel: () -> Unit,
    onTrack: (String) -> Unit
) {
    val profile by Repo.profile.collectAsState()
    val meals by Repo.meals.collectAsState()
    val settings by Repo.settings.collectAsState()
    val scope = rememberCoroutineScope()
    val avatar by Repo.avatar.collectAsState()
    val p = profile ?: return

    val today = Repo.mealsOfDay(meals)
    val favs by Repo.favMeals.collectAsState()
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
    var fridgeOpen by remember { mutableStateOf(false) }
    val recipes = remember { mutableStateMapOf<String, Recipe>() }

    val uri = LocalUriHandler.current
    val game by Repo.game.collectAsState()
    LaunchedEffect(summary.streak) {
        if (Game.freezesAfter(Repo.game.value, summary.streak) != null) Repo.updateGame { g -> Game.freezesAfter(g, summary.streak) ?: g }
    }

    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val hello = if (hour < 18) t("Bonjour") else t("Bonsoir")

    ScreenColumn {
        // En-tête : photo, salutation, série et accès au quiz
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Appuyer sur sa photo ou son nom ouvre les infos du compte
            Row(
                Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).clickable(onClick = onOpenProfile).padding(6.dp),
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
                        Icons.Filled.LocalFireDepartment, t("Série"), tint = FLAME,
                        modifier = Modifier.size(20.dp).graphicsLayer { scaleX = scale; scaleY = scale }
                    )
                    Text("${summary.streak}", fontWeight = FontWeight.Bold, color = FLAME)
                    if (game.freezes > 0) Text("  ❄️${game.freezes}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.width(4.dp))
            // Quiz du jour : en un appui depuis l'en-tête (un point tant qu'il n'est pas fait aujourd'hui)
            Box {
                Surface(onClick = onQuiz, shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                    Text("🧠", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                }
                if (!summary.quizDoneToday) Box(
                    Modifier.align(Alignment.TopEnd).size(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error)
                )
            }
            Spacer(Modifier.width(4.dp))
            // Le chef en haut ouvre la conversation avec le coach
            Surface(onClick = onCoach, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                ChefMascot(Modifier.padding(2.dp), size = 44.dp, mood = if (summary.quizDoneToday) ChefMood.CONTENT else ChefMood.QUESTION)
            }
        }

        // D'abord l'essentiel : les calories du jour et le bouton photo
        StepsAutoRefresh()
        SectionCard {
            val stepsData by Repo.steps.collectAsState()
            val stepsProgress = if (settings.stepsEnabled) {
                (stepsData.days[com.goodlife.app.data.localDay(0)]?.steps ?: 0).toFloat() / com.goodlife.app.steps.Steps.goal()
            } else null
            val details: @Composable () -> Unit = {
                StepsLine()
                MacroBar(t("Protéines"), today.sumOf { it.proteinG }, p.proteinG, GoogleRed)
                MacroBar(t("Glucides"), today.sumOf { it.carbsG }, p.carbsG, GoogleYellow)
                MacroBar(t("Lipides"), today.sumOf { it.fatG }, p.fatG, GoogleGreen)
            }
            val remainingText = if (remaining >= 0) t("Il te reste %1\$s kcal aujourd'hui", remaining)
                                else t("Objectif dépassé de %1\$s kcal", -remaining)
            // Téléphone en paysage : l'anneau à gauche, le détail à droite (sinon l'anneau prend tout l'écran)
            val conf = androidx.compose.ui.platform.LocalConfiguration.current
            if (conf.screenWidthDp > conf.screenHeightDp && conf.screenHeightDp < 600) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    CalorieRing(consumed = eaten, target = p.targetKcal, size = 160.dp, stepsProgress = stepsProgress)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(remainingText, style = MaterialTheme.typography.titleMedium)
                        details()
                    }
                }
            } else {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CalorieRing(
                        consumed = eaten, target = p.targetKcal,
                        size = if (stepsProgress != null) 200.dp else 180.dp,
                        stepsProgress = stepsProgress
                    )
                }
                Text(
                    remainingText,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                details()
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onScan, modifier = Modifier.weight(1f).height(52.dp)) {
                Icon(Icons.Filled.PhotoCamera, null)
                Spacer(Modifier.width(8.dp))
                Text(t("Scanner"))
            }
            FilledTonalButton(onClick = { showAdd = true }, modifier = Modifier.weight(1f).height(52.dp)) {
                Icon(Icons.Filled.Add, null)
                Spacer(Modifier.width(8.dp))
                Text(t("Saisir"))
            }
        }
        QuickMeals()
        // Actus : tout en haut, faciles à trouver
        NewsTeaser(onOpen = onNews)
        FridgeUndoCard()

        // Mise à jour disponible : bien visible, en haut (vérifiée à chaque ouverture de l'app)
        val update = if (settings.checkUpdates) Updater.availableUpdate() else null
        if (update != null && update.tag != settings.dismissedTag) {
            SectionCard(
                title = t("Nouvelle version %1\$s disponible", update.tag),
                icon = Icons.Filled.SystemUpdate,
                container = MaterialTheme.colorScheme.primaryContainer
            ) {
                UpdatePanel(update, showDismiss = true)
            }
        }

        CareCard(summary)
        NewBadgeCard(summary, onOpen = onProgress)

        // Série cassée hier : le cuisto propose de la sauver
        if (summary.recoverableStreak > 0) {
            SectionCard(container = FLAME.copy(alpha = 0.12f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ChefMascot(size = 64.dp, mood = ChefMood.TRISTE)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t("Ta série de %1\$s s'est arrêtée hier", days(summary.recoverableStreak)), style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (game.freezes > 0) t("Utilise un gel ❄️ : réponds aux %1\$s questions du chef, et ta série est sauvée, quel que soit ton score (il t'en reste %2\$s).", Game.FREEZE_QUESTIONS, game.freezes)
                            else t("Réponds au quiz du chef (%1\$s/%2\$s) aujourd'hui pour la sauver. Astuce : tous les %3\$s jours de série, tu gagnes un gel.", QuizBank.PASS, QuizBank.PER_DAY, Game.FREEZE_EVERY),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                if (game.freezes > 0) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Button(onClick = onGel) { Text(t("Utiliser un gel ❄️")) }
                        TextButton(onClick = onQuiz) { Text(t("Quiz classique (%1\$s/%2\$s)", QuizBank.PASS, QuizBank.PER_DAY)) }
                    }
                } else Button(onClick = onQuiz) { Text(t("Sauver ma série")) }
            }
        }

        // Niveau et XP
        Surface(onClick = onProgress, shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primaryContainer) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        t("Niveau %1\$s · %2\$s", summary.level.level, summary.level.title),
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(t("Score du jour : %1\$s", summary.today.score), style = MaterialTheme.typography.labelLarge)
                }
                val xp by animateFloatAsState(summary.level.progress, tween(900, easing = FastOutSlowInEasing), label = "xp")
                LinearProgressIndicator(
                    progress = { xp },
                    modifier = Modifier.fillMaxWidth().height(10.dp),
                    strokeCap = StrokeCap.Round
                )
                Text(
                    t("%1\$s XP avant le niveau %2\$s · voir mes progrès", summary.level.xpForNext - summary.level.xpInLevel, summary.level.level + 1),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        // Juste sous le niveau : ce qu'on touche le plus dans la journée (repas, puis l'eau)
        SectionCard(title = t("Repas du jour"), icon = Icons.Filled.Restaurant) {
            if (today.isEmpty()) {
                Text(
                    t("Aucun repas enregistré. Prends ton assiette en photo pour commencer."),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            today.forEachIndexed { i, m ->
                if (i > 0) HorizontalDivider()
                MealRow(m, favorite = favs.any { it.name.trim().equals(m.name.trim(), ignoreCase = true) }, onFavorite = { Repo.toggleFavorite(m) }) { Repo.deleteMeal(m.id) }
            }
        }

        WaterCard()

        TrackCard(onTrack)
        CoachCard(summary, p, onOpen = onCoach)
        MissionsCard(summary)
        if (!settings.notifAsked) NotifOptInCard()

        FeelingCard()

        SectionCard(title = t("Idées de repas"), icon = Icons.Filled.AutoAwesome) {
            if (!settings.aiEnabled) {
                Text(
                    t("Les idées de repas utilisent l'IA (désactivée). Tu peux l'activer dans Paramètres."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    t("Selon ce qu'il te reste, tes habitudes et tes allergies. Touche une idée pour les détails."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // Pour quel repas ?
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = sugSlot == null, onClick = { sugSlot = null }, label = { Text(t("Toute la journée")) })
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
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilledTonalButton(enabled = !sugLoading, onClick = { ask(append = false) }) {
                        if (sugLoading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Icon(if (suggestions.isEmpty()) Icons.Filled.AutoAwesome else Icons.Filled.Refresh, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (suggestions.isEmpty()) t("Proposer des repas") else t("Régénérer"))
                    }
                    TextButton(onClick = { fridgeOpen = true }) { Text(t("🧊 Mon frigo")) }
                    if (suggestions.isNotEmpty()) {
                        TextButton(enabled = !sugLoading, onClick = { ask(append = true) }) {
                            Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(t("Plus d'idées"))
                        }
                    }
                }
            }
        }

        SectionCard(title = t("7 derniers jours"), icon = Icons.Filled.BarChart) {
            val days = (-6..0).toList()
            val values = days.map { d -> Repo.mealsOfDay(meals, d).sumOf { it.kcal }.toFloat() }
            val labels = days.map { d ->
                formatDay(Repo.dayBounds(d).first).take(3).replaceFirstChar { it.uppercase() }
            }
            WeekBars(values, p.targetKcal.toFloat(), labels, MaterialTheme.colorScheme.primary, format = { "%.0f kcal".format(it) })
            Text(
                t("Moyenne : %1\$s kcal/jour · la ligne jaune = ton objectif", values.average().toInt()),
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

    if (fridgeOpen) FridgeDialog(onDismiss = { fridgeOpen = false })
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
        AnimatedVisibility(visible = expanded, enter = androidx.compose.animation.expandVertically(expandFrom = androidx.compose.ui.Alignment.Top) + androidx.compose.animation.fadeIn(), exit = androidx.compose.animation.shrinkVertically(shrinkTowards = androidx.compose.ui.Alignment.Top)) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                Text(s.description, style = MaterialTheme.typography.bodyMedium)
                if (s.why.isNotBlank()) {
                    Text(
                        s.why, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                AiContentFooter(t("Idée de repas : %1\$s (%2\$s kcal)\n%3\$s\n%4\$s", s.name, s.kcal, s.description, s.why))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilledTonalButton(onClick = onPlan) {
                        Icon(Icons.Filled.DateRange, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(t("Planifier"))
                    }
                    OutlinedButton(onClick = onRecipe) {
                        Icon(Icons.Filled.Restaurant, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(t("Recette"))
                    }
                }
            }
        }
    }
}

@Composable
private fun MealRow(m: Meal, favorite: Boolean, onFavorite: () -> Unit, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(m.name, fontWeight = FontWeight.Medium)
            Text(
                formatTime(m.timestamp) + " · " + when (m.source) { "photo" -> t("photo IA"); "ciqual" -> "Ciqual"; "code-barres" -> t("code-barres"); "planning" -> t("planning"); "refait" -> t("refait"); "frigo" -> t("idée du chef"); else -> t("saisie") },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text("${m.kcal} kcal", style = MaterialTheme.typography.titleSmall)
        IconButton(onClick = onFavorite) {
            Icon(if (favorite) Icons.Filled.Star else Icons.Filled.StarBorder, if (favorite) t("Retirer des favoris") else t("Ajouter aux favoris"),
                tint = if (favorite) GoogleYellow else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, t("Supprimer")) }
    }
}

/**
 * « Mon suivi » : calories, pas et poids en un coup d'œil. Chaque case ouvre directement son graphique ;
 * « Me peser » ouvre la pesée sans chercher.
 */
@Composable
private fun TrackCard(onTrack: (String) -> Unit) {
    val meals by Repo.meals.collectAsState()
    val steps by Repo.steps.collectAsState()
    val game by Repo.game.collectAsState()
    val profile by Repo.profile.collectAsState()
    val settings by Repo.settings.collectAsState()
    val p = profile ?: return
    val avgKcal = remember(meals) {
        (-7..-1).map { d -> Repo.mealsOfDay(meals, d).sumOf { it.kcal } }.filter { it > 0 }.takeIf { it.isNotEmpty() }?.average()?.toInt()
    }
    val todaySteps = steps.days[com.goodlife.app.data.localDay(0)]?.steps
    val weight = game.weights.lastOrNull()?.second ?: p.weightKg
    SectionCard(title = t("Mon suivi"), icon = Icons.Filled.BarChart) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Max)) {
            TrackTile("🔥", avgKcal?.let { t("%1\$s kcal", it) } ?: "—", t("moyenne 7 j"), Modifier.weight(1f)) { onTrack("kcal") }
            TrackTile("👣", if (settings.stepsEnabled && todaySteps != null) formatSteps(todaySteps) else "—", t("pas aujourd'hui"), Modifier.weight(1f)) { onTrack("steps") }
            TrackTile("⚖️", "%.1f kg".format(weight), t("dernier poids"), Modifier.weight(1f)) { onTrack("weight") }
        }
        FilledTonalButton(onClick = { onTrack("weighin") }, modifier = Modifier.fillMaxWidth()) {
            Text(t("⚖️ Me peser"))
        }
    }
}

@Composable
private fun TrackTile(emoji: String, value: String, label: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = modifier.fillMaxHeight()) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(emoji, style = MaterialTheme.typography.titleMedium)
            Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 2)
        }
    }
}

/** Après « Retirer du frigo » : ce qui a été retiré, avec « Annuler ». */
@Composable
private fun FridgeUndoCard() {
    val last by com.goodlife.app.data.Fridge.lastRemoval.collectAsState()
    val (text, _) = last ?: return
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(t("🧊 Retiré du frigo : %1\$s", text), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(vertical = 10.dp))
            TextButton(onClick = { com.goodlife.app.data.Fridge.undoLastRemoval() }) { Text(t("Annuler")) }
            IconButton(onClick = { com.goodlife.app.data.Fridge.lastRemoval.value = null }) { Icon(Icons.Filled.Close, t("Fermer")) }
        }
    }
}
