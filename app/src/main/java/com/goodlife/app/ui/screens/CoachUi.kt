@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.goodlife.app.ui.screens

import androidx.compose.foundation.layout.FlowRow
import com.goodlife.app.i18n.t

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.goodlife.app.coach.Coach
import com.goodlife.app.coach.CoachNotifier
import com.goodlife.app.data.Profile
import com.goodlife.app.data.Repo
import com.goodlife.app.data.Settings
import com.goodlife.app.game.GameSummary
import com.goodlife.app.ui.ChefMascot
import com.goodlife.app.ui.SectionCard

/** Choix des notifications du coach. */
data class NotifPrefs(val morning: Boolean, val noon: Boolean, val evening: Boolean, val weekly: Boolean) {
    val any get() = morning || noon || evening || weekly

    companion object {
        val ALL = NotifPrefs(true, true, true, true)
        fun of(s: Settings) = NotifPrefs(s.notifMorning, s.notifNoon, s.notifEvening, s.notifWeekly)
    }
}

/** Enregistre les choix et (re)programme les rappels. */
fun saveNotifPrefs(context: android.content.Context, p: NotifPrefs) {
    Repo.updateSettings {
        it.copy(notifMorning = p.morning, notifNoon = p.noon, notifEvening = p.evening, notifWeekly = p.weekly, notifAsked = true)
    }
    CoachNotifier.schedule(context)
}

/**
 * Demande l'autorisation d'afficher des notifications (Android 13+) puis appelle [onResult].
 * Avant Android 13, l'autorisation est accordée d'office.
 */
@Composable
fun rememberNotifPermission(onResult: (Boolean) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> onResult(granted) }
    return {
        if (Build.VERSION.SDK_INT >= 33 && !CoachNotifier.permissionGranted(context)) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        else onResult(CoachNotifier.permissionGranted(context))
    }
}

@Composable
fun NotifChoices(p: NotifPrefs, onChange: (NotifPrefs) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        NotifLine(t("Bilan du matin"), t("Vers 8 h 30 : ton score d'hier, ta série, et des félicitations aux grands paliers."), p.morning) { onChange(p.copy(morning = it)) }
        NotifLine(t("Le mot de midi"), t("Vers 11 h 45 : le repas prévu dans ton planning et un petit encouragement."), p.noon) { onChange(p.copy(noon = it)) }
        NotifLine(t("Série en danger"), t("Vers 20 h, seulement si tu n'as rien noté de la journée et qu'une série est en cours."), p.evening) { onChange(p.copy(evening = it)) }
        NotifLine(t("Bilan de la semaine"), t("Le dimanche vers 19 h : jours validés, pas, séances, et tes progrès."), p.weekly) { onChange(p.copy(weekly = it)) }
    }
}

@Composable
private fun NotifLine(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Le mot du chef sur l'accueil, avec l'accès à la conversation. */
@Composable
fun CoachCard(summary: GameSummary, profile: Profile, onOpen: () -> Unit) {
    val (mood, text) = remember(summary, profile) { Coach.homeMessage(summary, profile) }
    Surface(onClick = onOpen, shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            ChefMascot(size = 56.dp, mood = mood)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(t("Le mot du chef"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.Chat, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onTertiaryContainer)
                    Spacer(Modifier.width(6.dp))
                    Text(t("Parler au chef"), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer)
                }
            }
        }
    }
}

/** Proposé une seule fois sur l'accueil aux personnes inscrites avant la v0.9. */
@Composable
fun NotifOptInCard() {
    val context = LocalContext.current
    val ask = rememberNotifPermission { granted -> saveNotifPrefs(context, if (granted) NotifPrefs.ALL else NotifPrefs(false, false, false, false)) }
    SectionCard(title = t("Le chef peut t'accompagner")) {
        Text(
            t("Un bilan le matin, un petit mot à midi, un rappel le soir seulement si ta série est en danger, et un bilan le dimanche. Préparés sur ton téléphone, jamais plus d'un à la fois, et réglables dans Paramètres."),
            style = MaterialTheme.typography.bodyMedium
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Button(onClick = ask) { Text(t("Activer")) }
            TextButton(onClick = { saveNotifPrefs(context, NotifPrefs(false, false, false, false)) }) { Text(t("Non merci")) }
        }
    }
}

/** Réglages des notifications (Paramètres). */
@Composable
fun NotifSettingsBlock(settings: Settings) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    val granted = remember(refresh, settings) { CoachNotifier.permissionGranted(context) }
    var pending by remember { androidx.compose.runtime.mutableStateOf<NotifPrefs?>(null) }
    val ask = rememberNotifPermission { ok ->
        refresh++
        pending?.let { saveNotifPrefs(context, if (ok) it else NotifPrefs(false, false, false, false)) }
        pending = null
    }
    NotifChoices(NotifPrefs.of(settings)) { p ->
        if (p.any && !CoachNotifier.permissionGranted(context)) { pending = p; ask() } else saveNotifPrefs(context, p)
    }
    if (settings.anyNotif && !granted) {
        Text(
            t("Android bloque les notifications de Lifoody : autorise-les pour recevoir les messages du chef."),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error
        )
        TextButton(onClick = {
            runCatching {
                context.startActivity(Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }) { Text(t("Ouvrir les réglages de notification")) }
    }
    if (settings.anyNotif && granted) FilledTonalButton(onClick = { CoachNotifier.preview(context) }) { Text(t("Voir un exemple")) }
}
