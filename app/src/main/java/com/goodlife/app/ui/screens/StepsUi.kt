@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.goodlife.app.ui.screens

import com.goodlife.app.ui.FoldableSection
import com.goodlife.app.i18n.t

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.PermissionController
import com.goodlife.app.ai.Gemini
import com.goodlife.app.data.Repo
import com.goodlife.app.data.localDay
import com.goodlife.app.steps.Steps
import com.goodlife.app.ui.AiContentFooter
import com.goodlife.app.ui.SectionCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

fun formatSteps(n: Int): String = NumberFormat.getIntegerInstance(com.goodlife.app.i18n.Lang.locale).format(n)

/**
 * Active le suivi des pas : demande l'autorisation « Activité physique » (capteur) puis relève les pas.
 * Renvoie une fonction à appeler depuis un bouton.
 */
@Composable
fun rememberEnableSteps(onMessage: (String?) -> Unit = {}): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    fun enable() {
        Repo.updateSettings { it.copy(stepsEnabled = true) }
        Steps.schedule(context)
        scope.launch { Steps.refresh(context) }
        onMessage(null)
    }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) enable()
        else onMessage(t("Sans l'autorisation « Activité physique », le téléphone ne peut pas compter tes pas."))
    }
    // Téléphone sans capteur de pas : on passe par Health Connect (pas d'une montre, Samsung Health…)
    val askHc = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
        if (granted.containsAll(Steps.HC_PERMISSIONS)) {
            Repo.updateSettings { it.copy(stepsSource = "hc") }
            enable()
        } else onMessage(t("Accès aux pas refusé dans Health Connect."))
    }
    return {
        when {
            !Steps.sensorAvailable(context) && Steps.healthConnectAvailable(context) ->
                askHc.launch(Steps.HC_PERMISSIONS)
            !Steps.sensorAvailable(context) ->
                onMessage(t("Ce téléphone n'a pas de capteur de pas. Tu peux passer par Health Connect si tu l'installes."))
            !Steps.hasActivityPermission(context) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                ask.launch(Manifest.permission.ACTIVITY_RECOGNITION)
            else -> enable()
        }
    }
}

/** Relève les pas quand l'écran est affiché, puis toutes les minutes tant qu'il reste ouvert. */
@Composable
fun StepsAutoRefresh() {
    val context = LocalContext.current
    val enabled = Repo.settings.collectAsState().value.stepsEnabled
    LaunchedEffect(enabled) {
        while (enabled) {
            runCatching { Steps.refresh(context) }
            delay(60_000)
        }
    }
}

/** Ligne compacte sous l'anneau de l'accueil : pas du jour / objectif, ou bouton pour activer. */
@Composable
fun StepsLine() {
    val settings by Repo.settings.collectAsState()
    val steps by Repo.steps.collectAsState()
    var message by remember { mutableStateOf<String?>(null) }
    val enable = rememberEnableSteps { message = it }
    if (settings.stepsEnabled) {
        val today = steps.days[localDay(0)]?.steps ?: 0
        val goal = Steps.goal()
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Icon(Icons.AutoMirrored.Filled.DirectionsWalk, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text(t("%1\$s / %2\$s pas", formatSteps(today), formatSteps(goal)), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
        }
    } else {
        TextButton(onClick = enable, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.AutoMirrored.Filled.DirectionsWalk, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(t("Suivre aussi mes pas"))
        }
    }
    if (message != null) Text(message!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
}

/** Section « Pas » des Paramètres : activation, source, objectif (auto, fixé, IA). */
@Composable
fun StepsSettingsSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by Repo.settings.collectAsState()
    val profile by Repo.profile.collectAsState()
    var message by remember { mutableStateOf<String?>(null) }
    val enable = rememberEnableSteps { message = it }
    var manual by remember(settings.stepsGoalManual) { mutableStateOf(settings.stepsGoalManual.toString()) }
    var iaLoading by remember { mutableStateOf(false) }
    val hcAvailable = remember { Steps.healthConnectAvailable(context) }

    val askHc = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
        if (granted.containsAll(Steps.HC_PERMISSIONS)) {
            Repo.updateSettings { it.copy(stepsSource = "hc") }
            Steps.schedule(context)
            scope.launch { Steps.refresh(context) }
        } else message = t("Accès aux pas refusé dans Health Connect.")
    }

    FoldableSection(
        t("Pas"), Icons.AutoMirrored.Filled.DirectionsWalk,
        if (settings.stepsEnabled) t("Activé · objectif %1\$s pas",formatSteps(Steps.goal())) else t("Désactivé")
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                Text(t("Suivre mes pas"), style = MaterialTheme.typography.bodyLarge)
                Text(
                    t("Compté sur le téléphone, jamais envoyé. +%1\$s XP les jours où tu atteins ton objectif.", com.goodlife.app.game.Game.STEP_GOAL_XP),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = settings.stepsEnabled,
                onCheckedChange = { on ->
                    if (on) enable() else {
                        Repo.updateSettings { it.copy(stepsEnabled = false) }
                        Steps.schedule(context)
                    }
                }
            )
        }
        if (settings.stepsEnabled) {
            if (hcAvailable) {
                Text(t("Source"), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = settings.stepsSource == "sensor",
                        onClick = { Repo.updateSettings { it.copy(stepsSource = "sensor") }; Steps.schedule(context) },
                        label = { Text(t("Capteur du téléphone")) }
                    )
                    FilterChip(
                        selected = settings.stepsSource == "hc",
                        onClick = { askHc.launch(Steps.HC_PERMISSIONS) },
                        label = { Text(t("Health Connect")) }
                    )
                }
                Text(
                    t("Health Connect reprend les pas de Samsung Health, Google Fit ou d'une montre (lecture seule)."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(t("Objectif par jour"), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(settings.stepsGoalMode == "auto", { Repo.updateSettings { it.copy(stepsGoalMode = "auto") } }, label = { Text(t("Automatique")) })
                FilterChip(settings.stepsGoalMode == "manual", { Repo.updateSettings { it.copy(stepsGoalMode = "manual") } }, label = { Text(t("Je choisis")) })
                if (settings.aiEnabled) {
                    FilterChip(settings.stepsGoalMode == "ia", { Repo.updateSettings { it.copy(stepsGoalMode = "ia") } }, label = { Text(t("Conseil de l'IA")) })
                }
            }
            when (settings.stepsGoalMode) {
                "manual" -> Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        manual, { manual = it.filter(Char::isDigit).take(5) },
                        label = { Text(t("Pas par jour")) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        enabled = manual.toIntOrNull() in 1000..40_000,
                        onClick = { Repo.updateSettings { it.copy(stepsGoalManual = manual.toInt()) } }
                    ) { Text("OK") }
                }
                "ia" -> {
                    if (settings.stepsGoalIa > 0) {
                        Text(t("%1\$s pas par jour", formatSteps(settings.stepsGoalIa)), fontWeight = FontWeight.Medium)
                        Text(
                            t("Recalculé chaque jour à la première ouverture de l'app, à partir de tes pas des 7 derniers jours (au plus 15 % de changement d'un jour à l'autre)."),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (settings.stepsGoalIaWhy.isNotBlank()) {
                            Text(settings.stepsGoalIaWhy, style = MaterialTheme.typography.bodySmall)
                            AiContentFooter(t("Objectif de pas : %1\$s\n%2\$s", settings.stepsGoalIa, settings.stepsGoalIaWhy))
                        }
                    }
                    FilledTonalButton(
                        enabled = !iaLoading && profile != null,
                        onClick = {
                            iaLoading = true; message = null
                            scope.launch {
                                try {
                                    val (goal, why) = Gemini(settings.apiKey, settings.model).recommendSteps(profile!!, Steps.weekAverage())
                                    Repo.updateSettings { it.copy(stepsGoalIa = goal, stepsGoalIaWhy = why) }
                                } catch (e: Exception) {
                                    message = e.message
                                } finally {
                                    iaLoading = false
                                }
                            }
                        }
                    ) {
                        if (iaLoading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Filled.AutoAwesome, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (settings.stepsGoalIa > 0) t("Recalculer") else t("Demander à l'IA"))
                    }
                }
                else -> Text(
                    t("%1\$s pas : ta moyenne des 7 derniers jours + 10 %% (entre 5 000 et 12 000). Il monte doucement quand tu y arrives.", formatSteps(Steps.autoGoal())),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        if (message != null) Text(message!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
}
