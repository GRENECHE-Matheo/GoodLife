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
                    Text(t("Niv. %1\$s · ", summary.level.level), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Icon(
                        Icons.Filled.LocalFireDepartment, t("Série"), tint = FLAME,
                        modifier = Modifier.size(20.dp).graphicsLayer { scaleX = scale; scaleY = scale }
                    )
                    Text("${summary.streak}", fontWeight = FontWeight.Bold, color = FLAME)
                    if (game.freezes > 0) Text("  ❄️${game.freezes}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.width(4.dp))
            // Le chef en haut ouvre la conversation avec le coach
            Surface(onClick = onCoach, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = with(com.goodlife.app.ui.Tour) { Modifier.tourTarget("home.chef") }) {
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

        // Un seul bouton pour ajouter un repas : tout se fait dans l'onglet Ajouter (photo, code-barres, à la main, refaire)
        Button(onClick = onScan, modifier = with(com.goodlife.app.ui.Tour) { Modifier.fillMaxWidth().height(56.dp).tourTarget("home.add") }) {
            Icon(Icons.Filled.Add, null)
            Spacer(Modifier.width(8.dp))
            Text(t("Ajouter un repas"), style = MaterialTheme.typography.titleMedium)
        }
        FridgeUndoCard()

        // Toujours à la même place : les repas du jour, puis l'eau
        SectionCard(title = t("Repas du jour"), icon = Icons.Filled.Restaurant) {
            if (today.isEmpty()) {
                Text(
                    t("Aucun repas enregistré. Appuie sur « Ajouter un repas » pour commencer."),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            today.forEachIndexed { i, m ->
                if (i > 0) HorizontalDivider()
                MealRow(m, favorite = favs.any { it.name.trim().equals(m.name.trim(), ignoreCase = true) }, onFavorite = { Repo.toggleFavorite(m) }) { Repo.deleteMeal(m.id) }
            }
        }

        WaterCard()

        // Ensuite, ce qui dépend du jour : quiz, série à sauver, humeur, missions, badge, nouvelle version…
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
        } else if (!summary.quizDoneToday) {
            // Quiz du jour : une carte claire (avant, c'était un 🧠 sans nom dans l'en-tête)
            Surface(onClick = onQuiz, shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("🧠", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t("Quiz du jour"), style = MaterialTheme.typography.titleMedium)
                        Text(t("%1\$s questions du chef pour gagner de l'XP et garder ta série", QuizBank.PER_DAY), style = MaterialTheme.typography.bodySmall)
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                }
            }
        }

        FeelingCard()
        MissionsCard(summary, onQuiz = onQuiz)
        NewBadgeCard(summary, onOpen = onProgress)
        CareCard(summary)
        if (!settings.notifAsked) NotifOptInCard()

        CoachCard(summary, p, onOpen = onCoach)
        // Actus : en bas, et aussi dans Moi › Actus du jour (accessibles même sans thème choisi)
        NewsTeaser(onOpen = onNews)
        Spacer(Modifier.height(8.dp))
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
