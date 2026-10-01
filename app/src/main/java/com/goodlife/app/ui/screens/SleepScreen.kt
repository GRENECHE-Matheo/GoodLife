package com.goodlife.app.ui.screens

import com.goodlife.app.i18n.t

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.goodlife.app.data.Repo
import com.goodlife.app.data.SleepSession
import com.goodlife.app.sleep.SleepTracker
import com.goodlife.app.ui.ScreenTitle
import com.goodlife.app.ui.ScreenColumn
import com.goodlife.app.ui.SectionCard
import com.goodlife.app.ui.WeekBars
import com.goodlife.app.ui.formatDay
import com.goodlife.app.ui.formatDuration
import com.goodlife.app.ui.formatTime
import kotlinx.coroutines.delay

@Composable
fun SleepScreen(showTitle: Boolean = true) {
    val context = LocalContext.current
    val sessions by Repo.sleep.collectAsState()
    val settings by Repo.settings.collectAsState()
    var message by remember { mutableStateOf<String?>(null) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) { delay(30_000); now = System.currentTimeMillis() }
    }

    val askActivity = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) SleepTracker.subscribe(context) { ok, err -> message = if (ok) null else err }
        else message = t("Sans l'autorisation « Activité physique », la détection automatique ne peut pas fonctionner.")
    }

    // Durée de sommeil par jour (attribuée au jour du réveil)
    val days = (-6..0).toList()
    val perDay = days.map { d ->
        val (from, to) = Repo.dayBounds(d)
        sessions.filter { it.end in from until to }.sumOf { it.durationMin }
    }
    val lastNight = perDay.last()

    ScreenColumn {
        if (showTitle) ScreenTitle(t("Sommeil"), t("Objectif : 7 à 9 h par nuit"))
        else Text(t("Objectif : 7 à 9 h par nuit"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        SectionCard(title = t("Cette nuit"), icon = Icons.Filled.NightsStay) {
            Text(
                if (lastNight > 0) formatDuration(lastNight) else t("Pas encore de données"),
                style = MaterialTheme.typography.displaySmall
            )
            Text(
                when {
                    lastNight == 0L -> t("La nuit apparaîtra ici après ton réveil.")
                    lastNight < 6 * 60 -> t("Nuit courte. Essaie de te coucher un peu plus tôt ce soir.")
                    lastNight <= 9 * 60 -> t("Belle nuit, c'est dans la zone recommandée.")
                    else -> t("Nuit longue. Si c'est fréquent, surveille ta fatigue.")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        SectionCard(title = t("Détection automatique"), icon = Icons.Filled.PhoneAndroid) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    t("Détecter mon sommeil automatiquement"),
                    Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge
                )
                Switch(
                    checked = settings.sleepAuto,
                    onCheckedChange = { on ->
                        message = null
                        if (!on) SleepTracker.unsubscribe(context)
                        else if (!SleepTracker.hasPermission(context) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                            askActivity.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                        else SleepTracker.subscribe(context) { ok, err -> message = if (ok) null else err }
                    }
                )
            }
            Text(
                t("Utilise la Sleep API de Google (comme Google Fit) : le téléphone combine ses mouvements, la lumière ambiante et l'utilisation de l'écran pour savoir quand tu dors. Laisse le téléphone près de toi la nuit. Les nuits sont ajoutées quelques heures après le réveil."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (settings.sleepAuto && settings.lastSleepConfidence >= 0 &&
                now - settings.lastSleepConfidenceAt < 2 * 3600_000L
            ) {
                Text(
                    t("Probabilité de sommeil détectée à %1\$s : %2\$s %%", formatTime(settings.lastSleepConfidenceAt), settings.lastSleepConfidence),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            if (message != null) Text(message!!, color = MaterialTheme.colorScheme.error)
        }

        SectionCard(title = t("Mode manuel"), icon = Icons.Filled.WbSunny) {
            val start = settings.manualSleepStart
            if (start > 0) {
                Text(t("Au lit depuis %1\$s (%2\$s)", formatTime(start), formatDuration((now - start) / 60_000)))
                Button(onClick = {
                    Repo.addSleep(SleepSession(start = start, end = System.currentTimeMillis(), source = "manuel"))
                    Repo.updateSettings { it.copy(manualSleepStart = 0L) }
                }) { Text(t("Je me réveille")) }
            } else {
                Text(
                    t("Pratique si la détection auto n'est pas dispo sur ton téléphone."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(onClick = {
                    Repo.updateSettings { it.copy(manualSleepStart = System.currentTimeMillis()) }
                }) {
                    Icon(Icons.Filled.NightsStay, null)
                    Spacer(Modifier.width(8.dp))
                    Text(t("Je vais dormir"))
                }
            }
        }

        SectionCard(title = t("7 derniers jours"), icon = Icons.Filled.History) {
            val labels = days.map { d -> formatDay(Repo.dayBounds(d).first).take(3).replaceFirstChar { it.uppercase() } }
            WeekBars(perDay.map { it / 60f }, 8f, labels, MaterialTheme.colorScheme.secondary)
            val withData = perDay.filter { it > 0 }
            Text(
                if (withData.isEmpty()) t("Aucune nuit enregistrée pour l'instant.")
                else t("Moyenne : %1\$s · la ligne jaune = 8 h", formatDuration(withData.average().toLong())),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (sessions.isNotEmpty()) {
            SectionCard(title = t("Historique")) {
                sessions.take(14).forEachIndexed { i, s ->
                    if (i > 0) HorizontalDivider()
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(formatDuration(s.durationMin), fontWeight = FontWeight.Medium)
                            Text(
                                "${formatDay(s.start)} ${formatTime(s.start)} → ${formatTime(s.end)} · " +
                                    if (s.source == "auto") "auto" else "manuel",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { Repo.deleteSleep(s.id) }) { Icon(Icons.Filled.Delete, t("Supprimer")) }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
