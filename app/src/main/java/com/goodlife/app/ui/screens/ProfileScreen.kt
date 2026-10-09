@file:OptIn(ExperimentalMaterial3Api::class)

package com.goodlife.app.ui.screens

import com.goodlife.app.i18n.t

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Surface
import androidx.compose.ui.platform.LocalContext
import com.goodlife.app.ui.Avatar
import com.goodlife.app.ui.SlideSwitch
import com.goodlife.app.ui.avatarJpegFromUri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.ui.unit.dp
import com.goodlife.app.ai.Gemini
import com.goodlife.app.ai.Nutrition
import com.goodlife.app.data.Repo
import com.goodlife.app.ui.ScreenTitle
import com.goodlife.app.ui.ScreenColumn
import com.goodlife.app.ui.SectionCard
import com.goodlife.app.ui.AiContentFooter
import kotlinx.coroutines.launch

/**
 * Onglet « Moi » : tout ce qui me concerne, présenté comme un menu clair (une entrée = un écran, avec une ligne
 * qui dit ce qu'on y trouve). Mes progrès, Nutridex, Amis, mon objectif, les actus et les paramètres.
 */
@Composable
fun ProfileScreen() {
    var overlay by rememberSaveable { mutableStateOf("") }
    val navRequest by com.goodlife.app.social.AppNav.request.collectAsState()
    LaunchedEffect(navRequest) {
        // Demandes venues d'ailleurs (notification d'amis, mission « Ajoute un ami », accueil…)
        val r = navRequest ?: return@LaunchedEffect
        val target = when {
            r == "friends" -> "friends"
            r.startsWith("moi:") -> r.removePrefix("moi:")
            else -> null
        }
        if (target != null) { overlay = target; com.goodlife.app.social.AppNav.request.value = null }
    }
    val profile by Repo.profile.collectAsState()
    val meals by Repo.meals.collectAsState()
    val game by Repo.game.collectAsState()
    val steps by Repo.steps.collectAsState()
    val p = profile ?: return
    val summary = remember(meals, p, game, steps) {
        com.goodlife.app.game.Game.summarize(meals, p, game, steps.days, newRulesFrom = Repo.settings.value.scoreRulesFrom,
            foodOnlyFrom = Repo.settings.value.foodOnlyFrom, richFrom = Repo.settings.value.richScoreFrom)
    }
    SlideSwitch(overlay, depth = { when { it.isEmpty() -> 0; it == "quiz" -> 2; else -> 1 } }) { screen ->
        when (screen) {
            "settings" -> {
                BackHandler { overlay = "" }
                SettingsScreen(onBack = { overlay = "" })
            }
            "dex" -> NutridexScreen(onBack = { overlay = "" })
            "friends" -> FriendsScreen(onBack = { overlay = "" })
            "progress" -> ProgressScreen(summary, onBack = { overlay = "" }, onQuiz = { overlay = "quiz" })
            "weighin" -> ProgressScreen(summary, onBack = { overlay = "" }, onQuiz = { overlay = "quiz" }, focus = "weight", startWeighIn = true)
            "quiz" -> QuizScreen(summary.recoverableStreak, summary.quizDoneToday, summary.level.totalXp, onClose = { overlay = "progress" })
            "objective" -> ObjectiveScreen(onBack = { overlay = "" })
            "news" -> NewsScreen(onBack = { overlay = "" })
            else -> ProfileContent(summary, onOpen = { overlay = it })
        }
    }
}

@Composable
private fun ProfileContent(summary: com.goodlife.app.game.GameSummary, onOpen: (String) -> Unit) {
    val profile by Repo.profile.collectAsState()
    val settings by Repo.settings.collectAsState()
    val p = profile ?: return

    val context = LocalContext.current
    val avatar by Repo.avatar.collectAsState()
    var avatarMenu by remember { mutableStateOf(false) }
    val pickAvatar = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) avatarJpegFromUri(context, uri)?.let { Repo.saveAvatar(it) }
    }

    ScreenColumn {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box {
                Avatar(avatar, p.name, 72.dp, Modifier.clickable { avatarMenu = true })
                Surface(
                    onClick = { avatarMenu = true },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.BottomEnd).size(26.dp)
                ) {
                    Icon(Icons.Filled.Edit, t("Changer la photo"), tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.padding(5.dp))
                }
                DropdownMenu(expanded = avatarMenu, onDismissRequest = { avatarMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(t("Choisir une photo")) },
                        onClick = {
                            avatarMenu = false
                            pickAvatar.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }
                    )
                    if (avatar != null) {
                        DropdownMenuItem(text = { Text(t("Retirer la photo")) }, onClick = { avatarMenu = false; Repo.saveAvatar(null) })
                    }
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(p.name.ifBlank { t("Moi") }, style = MaterialTheme.typography.headlineSmall)
                Text(
                    t("Niveau %1\$s · %2\$s", summary.level.level, summary.level.title),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Mes progrès : niveau, XP et série en un coup d'œil ; tout le détail (poids, graphiques, badges) derrière
        Surface(onClick = { onOpen("progress") }, shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primaryContainer) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.EmojiEvents, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text(t("Mes progrès"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(t("🔥 %1\$s", com.goodlife.app.ui.days(summary.streak)), style = MaterialTheme.typography.labelLarge, color = FLAME)
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
                }
                LinearProgressIndicator(progress = { summary.level.progress }, modifier = Modifier.fillMaxWidth().height(8.dp), strokeCap = StrokeCap.Round)
                Text(
                    t("Niveau, badges, poids, calories et pas sur la durée, quiz du chef"),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        NutridexCard(onOpen = { onOpen("dex") })
        FriendsCard(onOpen = { onOpen("friends") })

        // Le reste, en simple menu : une ligne = un écran
        Card(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
            MenuRow(Icons.Filled.Flag, t("Mon objectif et mes infos"), t("%1\$s kcal par jour · %2\$s", p.targetKcal, p.goal.label)) { onOpen("objective") }
            HorizontalDivider(Modifier.padding(horizontal = 20.dp))
            MenuRow(Icons.Filled.Newspaper, t("Actus du jour"), t("Articles et anecdotes, choix des thèmes")) { onOpen("news") }
            HorizontalDivider(Modifier.padding(horizontal = 20.dp))
            MenuRow(Icons.Filled.Settings, t("Paramètres"), t("Notifications, objectifs, IA, sauvegarde, mes données")) { onOpen("settings") }
        }
    }
}

/** Une ligne de menu : icône, titre, ce qu'on y trouve, flèche. */
@Composable
private fun MenuRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
    }
}

/** « Mon objectif et mes infos » : l'objectif calorique (calculé ou par l'IA) et le profil, modifiables. */
@Composable
private fun ObjectiveScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val profile by Repo.profile.collectAsState()
    val settings by Repo.settings.collectAsState()
    val scope = rememberCoroutineScope()
    val p = profile ?: return
    var editing by remember { mutableStateOf(false) }
    var aiLoading by remember { mutableStateOf(false) }
    var aiError by remember { mutableStateOf<String?>(null) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        ScreenColumn {
            com.goodlife.app.ui.SubScreenHeader(t("Mon objectif et mes infos"), onBack)
            SectionCard(title = t("Objectif quotidien"), icon = Icons.Filled.AutoAwesome) {
                Text("${p.targetKcal} kcal", style = MaterialTheme.typography.displaySmall)
                Text(
                    t("Protéines %1\$s g · Glucides %2\$s g · Lipides %3\$s g", p.proteinG, p.carbsG, p.fatG),
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    // Calcul par formule : texte refait dans la langue de l'app (celui enregistré peut être dans l'autre langue)
                    (if (p.targetSource == "ia") t("Recommandé par l'IA : ") + p.targetExplanation
                    else if (p.targetSource == "formule") Nutrition.formulaTarget(p).targetExplanation else p.targetExplanation),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (p.targetSource == "ia") {
                    AiContentFooter(t("Objectif calorique : %1\$s kcal\n%2\$s", p.targetKcal, p.targetExplanation))
                }
                if (aiError != null) Text(aiError!!, color = MaterialTheme.colorScheme.error)
                // Seulement si l'IA est activée et prête (avant, le bouton était là même sans IA et renvoyait une erreur)
                if (settings.aiEnabled && com.goodlife.app.ai.AiAccess.ready(settings)) FilledTonalButton(
                    enabled = !aiLoading,
                    onClick = {
                        aiLoading = true; aiError = null
                        scope.launch {
                            try {
                                Repo.saveProfile(Gemini(settings.apiKey, settings.model).recommendTarget(p, com.goodlife.app.steps.Steps.weekAverage()))
                            } catch (e: Exception) {
                                aiError = e.message
                            } finally {
                                aiLoading = false
                            }
                        }
                    }
                ) {
                    if (aiLoading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Filled.AutoAwesome, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(t("Calculer avec l'IA"))
                }
            }

            SectionCard(title = t("Mes infos"), icon = Icons.Filled.Person) {
                if (editing) {
                    ProfileForm(initial = p, saveLabel = t("Enregistrer")) { updated ->
                        Repo.saveProfile(Nutrition.formulaTarget(updated))
                        editing = false
                    }
                    TextButton(onClick = { editing = false }) { Text(t("Annuler")) }
                } else {
                    InfoLine(t("Prénom"), p.name.ifBlank { "—" })
                    InfoLine(t("Âge"), t("%1\$s ans", p.age))
                    InfoLine(t("Sexe"), p.sex.label)
                    InfoLine(t("Poids"), "${p.weightKg} kg")
                    InfoLine(t("Taille"), "${p.heightCm} cm")
                    InfoLine(t("Activité"), p.activity.label)
                    InfoLine(t("Objectif"), p.goal.label)
                    InfoLine(t("Habitudes"), p.habits.ifBlank { "—" })
                    InfoLine(t("Allergies"), p.allergies.ifBlank { "—" })
                    OutlinedButton(onClick = { editing = true }) { Text(t("Modifier mes infos")) }
                }
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            label, Modifier.width(96.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
