@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.goodlife.app.ui.screens

import androidx.compose.foundation.layout.FlowRow
import com.goodlife.app.i18n.t
import com.goodlife.app.i18n.tp

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
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
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.goodlife.app.coach.Coach
import com.goodlife.app.coach.CoachBrain
import com.goodlife.app.coach.CoachNotifier
import com.goodlife.app.coach.CoachStore
import com.goodlife.app.coach.Insight
import com.goodlife.app.coach.InsightKind
import com.goodlife.app.coach.Tone
import com.goodlife.app.data.MealSlot
import com.goodlife.app.data.Profile
import com.goodlife.app.data.Repo
import com.goodlife.app.data.Settings
import com.goodlife.app.game.GameSummary
import com.goodlife.app.social.AppNav
import com.goodlife.app.ui.ChefMascot
import com.goodlife.app.ui.ChefMood
import com.goodlife.app.ui.SectionCard

/** Choix des notifications du coach. */
data class NotifPrefs(
    val morning: Boolean, val noon: Boolean, val evening: Boolean, val weekly: Boolean,
    val meal: Boolean = false, val move: Boolean = false
) {
    val any get() = morning || noon || evening || weekly || meal || move

    companion object {
        val ALL = NotifPrefs(true, true, true, true, true, true)
        val NONE = NotifPrefs(false, false, false, false, false, false)
        fun of(s: Settings) = NotifPrefs(s.notifMorning, s.notifNoon, s.notifEvening, s.notifWeekly, s.notifMeal, s.notifMove)
    }
}

/** Enregistre les choix et (re)programme les rappels. */
fun saveNotifPrefs(context: android.content.Context, p: NotifPrefs) {
    Repo.updateSettings {
        it.copy(notifMorning = p.morning, notifNoon = p.noon, notifEvening = p.evening, notifWeekly = p.weekly,
            notifMeal = p.meal, notifMove = p.move, notifAsked = true, coachNewAsked = true)
    }
    CoachNotifier.schedule(context)
}

/** Ouvre les réglages Android des notifications de Lifoody (ou d'un seul type, [channel]). */
fun openNotifSettings(context: android.content.Context, channel: String? = null) {
    val intent = if (channel != null) Intent(AndroidSettings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
        .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName).putExtra(AndroidSettings.EXTRA_CHANNEL_ID, channel)
    else Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName)
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.onFailure {
        // Certains téléphones n'ont pas cet écran : la fiche de l'app, où se trouvent aussi les notifications
        runCatching {
            context.startActivity(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:" + context.packageName))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

/** « Les notifications sont coupées » : pourquoi le chef ne peut pas écrire, et un bouton vers les réglages Android. */
@Composable
fun NotifBlockedDialog(onOpen: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { ChefMascot(size = 64.dp, mood = ChefMood.QUESTION) },
        title = { Text(t("Les notifications sont coupées")) },
        text = {
            Text(t("Android bloque les notifications de Lifoody : le chef ne peut pas t'écrire (« Tu as mangé ? », bilans, rappels). Appuie sur « Ouvrir les réglages », active « Autoriser les notifications », puis reviens ici : c'est tout."),
                style = MaterialTheme.typography.bodyMedium)
        },
        confirmButton = { Button(onClick = onOpen) { Text(t("Ouvrir les réglages")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Plus tard")) } }
    )
}

/**
 * Demande l'autorisation d'afficher des notifications puis appelle [onResult]. Si Android la refuse (refusée
 * plusieurs fois, ou notifications coupées dans les réglages), une fenêtre explique pourquoi et mène aux réglages
 * Android ; au retour dans l'app, on revérifie tout seul et le choix est appliqué si les notifications sont activées.
 */
@Composable
fun rememberNotifPermission(onResult: (Boolean) -> Unit): () -> Unit {
    val context = LocalContext.current
    var blocked by remember { mutableStateOf(false) }
    var waiting by remember { mutableStateOf(false) }   // partie dans les réglages Android : on revérifie au retour
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && CoachNotifier.permissionGranted(context)) onResult(true) else blocked = true
    }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    androidx.compose.runtime.DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, e ->
            if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME && waiting) { waiting = false; onResult(CoachNotifier.permissionGranted(context)) }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    if (blocked) NotifBlockedDialog(
        onOpen = { blocked = false; waiting = true; openNotifSettings(context) },
        onDismiss = { blocked = false; onResult(false) }
    )
    return {
        when {
            CoachNotifier.permissionGranted(context) -> onResult(true)
            Build.VERSION.SDK_INT >= 33 && androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                android.content.pm.PackageManager.PERMISSION_GRANTED -> launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            else -> blocked = true   // autorisation donnée, mais notifications coupées dans les réglages Android
        }
    }
}

@Composable
fun NotifChoices(p: NotifPrefs, onChange: (NotifPrefs) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        NotifLine(t("« Tu as mangé ? »"), t("Si un repas n'est pas noté 1 h 30 après ton heure habituelle (le chef l'apprend de tes repas). Tu réponds en un geste : je le note, pas mangé, plus tard."), p.meal) { onChange(p.copy(meal = it)) }
        NotifLine(t("« On bouge ? »"), t("Le jour d'une séance de ton programme, ou quand tu marches moins que d'habitude depuis 3 jours. Rarement, jamais pour un seul mauvais jour."), p.move) { onChange(p.copy(move = it)) }
        NotifLine(t("Bilan du matin"), t("Vers 8 h 30 : ton score d'hier, ta série et le point du jour."), p.morning) { onChange(p.copy(morning = it)) }
        NotifLine(t("Le mot de midi"), t("Vers 11 h 45 : le repas prévu dans ton planning et un petit encouragement."), p.noon) { onChange(p.copy(noon = it)) }
        NotifLine(t("Série en danger"), t("Vers 20 h, seulement si tu n'as rien noté de la journée et qu'une série est en cours."), p.evening) { onChange(p.copy(evening = it)) }
        NotifLine(t("Bilan de la semaine"), t("Le dimanche vers 19 h : ta semaine comparée aux précédentes, et un défi à choisir."), p.weekly) { onChange(p.copy(weekly = it)) }
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

/** Humeur du chef selon le constat. */
fun moodOf(i: Insight?): ChefMood = when (i?.kind) {
    InsightKind.WIN -> ChefMood.BRAVO
    InsightKind.ASK -> ChefMood.QUESTION
    InsightKind.CARE -> ChefMood.COEUR
    else -> if (i?.area == com.goodlife.app.coach.Area.MOVE) ChefMood.SPORT else ChefMood.CONTENT
}

/** Ouvre une destination de l'app (« forme:carte », « repas:idees »…) ou exécute une petite action du coach. */
fun openCoachAction(context: android.content.Context, action: String) {
    when {
        action.startsWith("coach:fast:") -> {
            val slot = MealSlot.entries.firstOrNull { it.name == action.removePrefix("coach:fast:") } ?: return
            CoachStore.setFasting(slot, true)
            android.widget.Toast.makeText(context, t("D'accord, je n'en parlerai plus. Tu peux changer ça dans Paramètres › Le coach."), android.widget.Toast.LENGTH_LONG).show()
            CoachNotifier.schedule(context)
        }
        action.isNotBlank() -> AppNav.request.value = action
    }
}

/**
 * Le point du chef sur l'accueil : la chose la plus utile du moment (une seule), calculée sur le téléphone.
 * En dessous : le bilan de la semaine, les messages du chef et la conversation.
 */
@Composable
fun CoachCard(summary: GameSummary, profile: Profile, onOpen: () -> Unit) {
    val context = LocalContext.current
    val meals by Repo.meals.collectAsState()
    val mem by CoachStore.mem.collectAsState()
    val settings by Repo.settings.collectAsState()
    val insight = remember(summary, profile, meals, mem.fasting, mem.skipped, settings.guardSnoozeUntil, settings.coachTone) { CoachBrain.thingOfTheDay() }
    val fallback = remember(summary, profile) { Coach.homeMessage(summary, profile) }
    val unread = mem.inbox.count { !it.read }
    var expanded by remember { mutableStateOf(false) }
    Surface(onClick = { expanded = !expanded }, shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = with(com.goodlife.app.ui.Tour) { Modifier.tourTarget("home.coach") }) {
        Column(Modifier.fillMaxWidth().padding(14.dp).animateContentSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                ChefMascot(size = 56.dp, mood = if (insight != null) moodOf(insight) else fallback.first)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(t("Le point du chef"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                    if (insight != null) {
                        Text(insight.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onTertiaryContainer)
                        Text(insight.text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onTertiaryContainer,
                            maxLines = if (expanded) Int.MAX_VALUE else 3, overflow = TextOverflow.Ellipsis)
                        if (!expanded && insight.text.length > 140) Text(t("Lire la suite"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    } else {
                        Text(fallback.second, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                if (insight != null && insight.action.isNotBlank()) FilledTonalButton(onClick = { openCoachAction(context, insight.action) }) { Text(insight.actionLabel) }
                if (insight?.kind == InsightKind.CARE) TextButton(onClick = {
                    Repo.updateSettings { it.copy(guardSnoozeUntil = System.currentTimeMillis() + 7 * 86_400_000L) }
                }) { Text(t("Merci, ne plus afficher cette semaine")) }
                AssistChip(onClick = onOpen, label = { Text(t("Parler au chef")) }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.Chat, null, Modifier.size(18.dp)) })
                AssistChip(onClick = { CoachPanels.week.value = true }, label = { Text(t("Mon bilan")) }, leadingIcon = { Icon(Icons.Filled.Insights, null, Modifier.size(18.dp)) })
                if (mem.inbox.isNotEmpty()) AssistChip(onClick = { CoachPanels.inbox.value = true },
                    label = { Text(if (unread > 0) tp(unread, "%1\$s message", "%1\$s messages") else t("Messages")) },
                    leadingIcon = { Icon(Icons.Filled.MarkEmailUnread, null, Modifier.size(18.dp)) })
            }
        }
    }
}

/** Proposé une seule fois sur l'accueil aux personnes inscrites avant la v0.9. */
@Composable
fun NotifOptInCard() {
    val context = LocalContext.current
    val ask = rememberNotifPermission { granted -> saveNotifPrefs(context, if (granted) NotifPrefs.ALL else NotifPrefs.NONE) }
    SectionCard(title = t("Le chef peut t'accompagner")) {
        Text(
            t("« Tu as mangé ? » quand un repas manque, un coup de pouce pour bouger, un bilan le matin et un bilan le dimanche. Préparés sur ton téléphone, jamais la nuit, et réglables dans Paramètres."),
            style = MaterialTheme.typography.bodyMedium
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Button(onClick = ask) { Text(t("Activer")) }
            TextButton(onClick = { saveNotifPrefs(context, NotifPrefs.NONE) }) { Text(t("Non merci")) }
        }
    }
}

/** Le choix du ton : doux, équilibré ou exigeant. */
@Composable
fun ToneChooser(current: String, onPick: (Tone) -> Unit) {
    val tone = Tone.of(current)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Tone.entries.forEach { tn ->
                FilterChip(selected = tn == tone, onClick = { onPick(tn) }, label = { Text(toneName(tn)) })
            }
        }
        Text(toneHint(tone), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

fun toneName(tn: Tone): String = when (tn) { Tone.DOUX -> t("Doux"); Tone.EQUILIBRE -> t("Équilibré"); Tone.EXIGEANT -> t("Exigeant") }

fun toneHint(tn: Tone): String = when (tn) {
    Tone.DOUX -> t("Peu de rappels (1 par jour au plus), des mots tout en douceur.")
    Tone.EQUILIBRE -> t("Le chef te dit les choses franchement mais gentiment, 2 rappels par jour au plus.")
    Tone.EXIGEANT -> t("Comme un coach de salle : direct, objectifs plus hauts, jusqu'à 3 rappels par jour. Jamais méchant pour autant.")
}

/**
 * Nouveau coach (v0.20) : proposé une fois aux personnes déjà inscrites, pour activer « Tu as mangé ? » et « On bouge ? »
 * et choisir le ton. Rien n'est activé sans leur accord.
 */
@Composable
fun NewCoachCard() {
    val context = LocalContext.current
    val settings by Repo.settings.collectAsState()
    val ask = rememberNotifPermission { granted ->
        if (granted) saveNotifPrefs(context, NotifPrefs.of(Repo.settings.value).copy(meal = true, move = true))
        else Repo.updateSettings { it.copy(coachNewAsked = true) }
    }
    SectionCard(container = MaterialTheme.colorScheme.tertiaryContainer) {
        Row(verticalAlignment = Alignment.Top) {
            ChefMascot(size = 56.dp, mood = ChefMood.SPORT)
            Spacer(Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(t("Nouveau : un vrai coach"), style = MaterialTheme.typography.titleMedium)
                Text(t("Le chef regarde maintenant plus que les calories : protéines, rythme des repas, pas, sommeil. Il peut aussi te demander si tu as mangé quand un repas manque, et te pousser à bouger. Tout est calculé sur ton téléphone."),
                    style = MaterialTheme.typography.bodyMedium)
            }
        }
        Text(t("Quel coach veux-tu ?"), style = MaterialTheme.typography.labelLarge)
        ToneChooser(settings.coachTone) { tn -> Repo.updateSettings { it.copy(coachTone = tn.name) } }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Button(onClick = ask) { Text(t("Activer les rappels")) }
            TextButton(onClick = { Repo.updateSettings { it.copy(coachNewAsked = true) } }) { Text(t("Pas maintenant")) }
        }
    }
}

/** Rappels ignorés 5 fois de suite : le chef propose de les couper plutôt que d'insister. */
@Composable
fun MuteAskCard() {
    val context = LocalContext.current
    val mem by CoachStore.mem.collectAsState()
    val kind = mem.muteAsk.firstOrNull() ?: return
    val name = when (kind) {
        "meal" -> t("« Tu as mangé ? »"); "move" -> t("« On bouge ? »"); "water" -> t("« Pense à boire »"); "evening" -> t("« Série en danger »"); else -> kind
    }
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ChefMascot(size = 48.dp, mood = ChefMood.QUESTION)
            Spacer(Modifier.width(12.dp))
            Text(t("Tu ne réponds plus aux rappels %1\$s. Je les coupe ? Tu pourras les remettre dans Paramètres.", name), style = MaterialTheme.typography.bodyMedium)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                Repo.updateSettings {
                    when (kind) {
                        "meal" -> it.copy(notifMeal = false); "move" -> it.copy(notifMove = false)
                        "water" -> it.copy(notifWater = false); "evening" -> it.copy(notifEvening = false); else -> it
                    }
                }
                CoachStore.acted(kind)
                CoachNotifier.schedule(context)
            }) { Text(t("Les couper")) }
            TextButton(onClick = { CoachStore.acted(kind) }) { Text(t("Les garder")) }
        }
    }
}

/** Réglages des notifications (Paramètres). */
@Composable
fun NotifSettingsBlock(settings: Settings) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    // Retour des réglages Android : on revérifie ce qui est autorisé
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    androidx.compose.runtime.DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) refresh++ }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val granted = remember(refresh, settings) { CoachNotifier.permissionGranted(context) }
    val blockedChannels = remember(refresh, settings) { if (granted) CoachNotifier.blockedChannels(context) else emptyList() }
    var pending by remember { mutableStateOf<NotifPrefs?>(null) }
    val ask = rememberNotifPermission { ok ->
        refresh++
        // Refusé ou « Plus tard » : on garde les choix d'avant (rien n'est coché sans que ça puisse marcher)
        pending?.let { if (ok) saveNotifPrefs(context, it) }
        pending = null
    }
    // Notifications coupées dans Android : on le dit tout de suite, avant même de toucher un interrupteur
    if (!granted) Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.errorContainer) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(t("Les notifications de Lifoody sont coupées dans Android : le chef ne peut pas t'écrire."),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
            Button(onClick = { openNotifSettings(context) }) { Text(t("Activer les notifications")) }
        }
    }
    NotifChoices(NotifPrefs.of(settings)) { p ->
        // Seulement quand on active quelque chose : couper un rappel ne demande rien
        val cur = NotifPrefs.of(settings)
        val enabling = (p.morning && !cur.morning) || (p.noon && !cur.noon) || (p.evening && !cur.evening) ||
            (p.weekly && !cur.weekly) || (p.meal && !cur.meal) || (p.move && !cur.move)
        if (enabling && !CoachNotifier.permissionGranted(context)) { pending = p; ask() } else saveNotifPrefs(context, p)
    }
    // Un type de message coupé dans Android alors qu'il est activé ici
    blockedChannels.forEach { (id, name) ->
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(t("« %1\$s » est coupé dans les réglages Android : tu ne recevras pas ces messages.", name),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
            TextButton(onClick = { openNotifSettings(context, id) }) { Text(t("Activer")) }
        }
    }
    if (settings.anyNotif && granted) FilledTonalButton(onClick = { CoachNotifier.preview(context) }) { Text(t("Voir un exemple")) }
}
