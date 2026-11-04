@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.goodlife.app.ui.screens

import com.goodlife.app.i18n.t
import com.goodlife.app.i18n.tp

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.goodlife.app.coach.ActiveChallenge
import com.goodlife.app.coach.CoachBrain
import com.goodlife.app.coach.CoachFacts
import com.goodlife.app.coach.CoachMsg
import com.goodlife.app.coach.CoachNotifier
import com.goodlife.app.coach.CoachStore
import com.goodlife.app.coach.InsightKind
import com.goodlife.app.coach.Metric
import com.goodlife.app.coach.Weekly
import com.goodlife.app.coach.clock
import com.goodlife.app.coach.num
import com.goodlife.app.coach.slotNoun
import com.goodlife.app.data.MealSlot
import com.goodlife.app.data.Repo
import com.goodlife.app.data.localDay
import com.goodlife.app.social.AppNav
import com.goodlife.app.ui.ChefMascot
import com.goodlife.app.ui.ChefMood
import com.goodlife.app.ui.ScreenColumn
import com.goodlife.app.ui.SectionCard
import com.goodlife.app.ui.SubScreenHeader
import com.goodlife.app.ui.formatDay
import com.goodlife.app.ui.formatTime
import kotlinx.coroutines.flow.MutableStateFlow

/** « Tu as mangé ? » à ouvrir : créneau (null = toute la journée), jour, message d'origine (0 = aucun). */
data class MealCheckReq(val slot: MealSlot?, val day: String, val msgId: Long = 0L)

/** Ce qui est ouvert par-dessus l'app pour le coach (depuis une notification, l'accueil ou les paramètres). */
object CoachPanels {
    val message = MutableStateFlow<Long?>(null)
    val check = MutableStateFlow<MealCheckReq?>(null)
    val week = MutableStateFlow(false)
    val inbox = MutableStateFlow(false)
    val memory = MutableStateFlow(false)
    /** Texte proposé dans la conversation avec le chef (« Le chef t'explique »). */
    val chatPrefill = MutableStateFlow<String?>(null)

    /** Traite une demande venue d'une notification : true si elle concernait le coach. */
    fun handle(request: String): Boolean {
        val r = request.substringBefore('|')
        request.substringAfter("|msg:", "").toLongOrNull()?.let { opened(it) }
        when {
            r.startsWith("coach:msg:") -> {
                val id = r.removePrefix("coach:msg:").toLongOrNull() ?: return true
                val msg = CoachStore.message(id)
                opened(id)
                if (msg != null && msg.action.startsWith("check:")) parseCheck(msg.action, id)?.let { check.value = it }
                else message.value = id
            }
            r.startsWith("check:") -> {
                val id = r.substringAfter(":add:", "").toLongOrNull() ?: 0L
                if (id > 0) opened(id)
                parseCheck(r, id)?.let { check.value = it }
            }
            r == "coach:week" -> week.value = true
            r == "coach:inbox" -> inbox.value = true
            r == "coach:memory" -> memory.value = true
            else -> return false
        }
        return true
    }

    private fun opened(id: Long) {
        val msg = CoachStore.message(id) ?: return
        CoachStore.markRead(id)
        CoachStore.acted(msg.kind)
    }

    private fun parseCheck(action: String, id: Long): MealCheckReq? {
        val parts = action.split(':')
        val name = parts.getOrNull(1) ?: return null
        val slot = MealSlot.entries.firstOrNull { it.name == name }
        if (slot == null && name != "ALL") return null
        return MealCheckReq(slot, parts.getOrNull(2) ?: localDay(0), id)
    }
}

/** Les fenêtres du coach, posées par-dessus les onglets (dans MainTabs). */
@Composable
fun CoachOverlays() {
    val msgId by CoachPanels.message.collectAsState()
    val check by CoachPanels.check.collectAsState()
    val week by CoachPanels.week.collectAsState()
    val inbox by CoachPanels.inbox.collectAsState()
    val memory by CoachPanels.memory.collectAsState()
    FullPanel(inbox) { CoachInboxScreen(onBack = { CoachPanels.inbox.value = false }) }
    FullPanel(week) { WeeklyReportScreen(onBack = { CoachPanels.week.value = false }) }
    FullPanel(memory) { CoachMemoryScreen(onBack = { CoachPanels.memory.value = false }) }
    msgId?.let { id -> CoachStore.message(id)?.let { CoachMessageDialog(it) { CoachPanels.message.value = null } } ?: run { CoachPanels.message.value = null } }
    check?.let { req -> MealCheckDialog(req.slot, req.day, CoachStore.message(req.msgId)) { CoachPanels.check.value = null } }
}

@Composable
private fun FullPanel(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(visible = visible, enter = slideInVertically { it / 4 } + fadeIn(), exit = slideOutVertically { it / 4 } + fadeOut()) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).windowInsetsPadding(WindowInsets.safeDrawing)) { content() }
    }
}

private fun moodOfKind(kind: String): ChefMood = when (kind) {
    "meal" -> ChefMood.QUESTION; "move" -> ChefMood.SPORT; "weekly" -> ChefMood.BRAVO; "care" -> ChefMood.COEUR; else -> ChefMood.CONTENT
}

/** Libellé et destination du bouton principal d'un message. */
private fun actionOf(action: String): Pair<String, String>? {
    val a = action.substringBefore('|')
    return when {
        a.isBlank() -> null
        a == "coach:week" -> t("Voir mon bilan") to a
        a == "add" -> t("Ajouter un repas") to a
        a == "forme:carte" -> t("Je pars marcher") to a
        a.startsWith("forme") -> t("Voir ma séance") to a
        a.startsWith("repas:") -> t("Voir le planning") to a
        a == "home:quiz" -> t("Faire le quiz") to a
        else -> null
    }
}

/** Le message complet du chef, ouvert depuis sa notification. */
@Composable
fun CoachMessageDialog(msg: CoachMsg, onClose: () -> Unit) {
    val main = actionOf(msg.action)
    AlertDialog(
        onDismissRequest = onClose,
        icon = { ChefMascot(size = 72.dp, mood = moodOfKind(msg.kind)) },
        title = { Text(msg.title) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(msg.text, style = MaterialTheme.typography.bodyLarge)
                Text(formatDay(msg.at).replaceFirstChar { it.uppercase() } + " · " + formatTime(msg.at),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { onClose(); CoachPanels.inbox.value = true }) { Text(t("Tous les messages")) }
                    TextButton(onClick = { onClose(); AppNav.request.value = "home:coach" }) { Text(t("Parler au chef")) }
                }
            }
        },
        confirmButton = {
            if (main != null) Button(onClick = { onClose(); AppNav.request.value = main.second }) { Text(main.first) }
            else TextButton(onClick = onClose) { Text(t("Compris")) }
        },
        dismissButton = { if (main != null) TextButton(onClick = onClose) { Text(t("Plus tard")) } }
    )
}

/**
 * « Tu as mangé ce midi ? » : on note le repas comme on veut (il prend l'heure habituelle s'il est noté après coup),
 * ou on dit qu'on n'a pas mangé (sans commentaire, et sans casser la série), ou que ce repas est sauté volontairement.
 * [slot] null : toute la journée (« Aucune assiette aujourd'hui ? », « Ta journée d'hier ? ») : on répond repas par repas.
 */
@Composable
fun MealCheckDialog(slot: MealSlot?, day: String, origin: CoachMsg?, onClose: () -> Unit) {
    val missing = remember(day) { CoachBrain.missingOn(day) }
    var chosen by remember { mutableStateOf(slot ?: missing.singleOrNull()) }
    val current = chosen
    if (current == null) {
        DayCheckDialog(day, missing, origin, onPick = { chosen = it }, onClose = onClose)
        return
    }
    SlotCheckDialog(current, day, origin.takeIf { slot != null || missing.size <= 1 }, onBack = if (slot == null && missing.size > 1) ({ chosen = null }) else null, onClose = onClose)
}

/** Toute une journée : chaque repas pas noté, avec « Ajouter » ou « Pas mangé ». */
@Composable
private fun DayCheckDialog(day: String, missing: List<MealSlot>, origin: CoachMsg?, onPick: (MealSlot) -> Unit, onClose: () -> Unit) {
    val mem by CoachStore.mem.collectAsState()
    val today = day == localDay(0)
    val left = missing.filter { "$day#${it.name}" !in mem.skipped }
    AlertDialog(
        onDismissRequest = onClose,
        icon = { ChefMascot(size = 72.dp, mood = if (left.isEmpty()) ChefMood.CONTENT else ChefMood.QUESTION) },
        title = { Text(when { left.isEmpty() -> t("C'est noté 👍"); today -> t("Aucune assiette aujourd'hui ?"); else -> t("Ta journée d'hier ?") }) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(origin?.text?.takeIf { it.isNotBlank() && left.isNotEmpty() }
                    ?: if (left.isEmpty()) t("Tout est à jour pour cette journée. Merci, mes conseils seront justes.")
                    else t("Oubli de noter, ou pas mangé ? Dis-le-moi repas par repas : tes calories, ton score et mes conseils resteront justes."),
                    style = MaterialTheme.typography.bodyLarge)
                missing.forEach { s ->
                    val skipped = "$day#${s.name}" in mem.skipped
                    HorizontalDivider()
                    Text(s.label, style = MaterialTheme.typography.titleSmall)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                        if (skipped) TextButton(onClick = { CoachStore.unskip(day, s) }) { Text(t("Pas mangé · annuler")) }
                        else {
                            TextButton(onClick = { CoachStore.skip(day, s) }) { Text(t("Pas mangé")) }
                            Spacer(Modifier.width(8.dp))
                            FilledTonalButton(onClick = { onPick(s) }) { Text(t("Ajouter")) }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(if (left.isEmpty()) t("Fermer") else t("Plus tard")) } },
        dismissButton = if (left.size > 1) ({ TextButton(onClick = { CoachStore.skipAll(day, left) }) { Text(t("Rien mangé")) } }) else null
    )
}

@Composable
private fun SlotCheckDialog(slot: MealSlot, day: String, origin: CoachMsg?, onBack: (() -> Unit)?, onClose: () -> Unit) {
    val context = LocalContext.current
    val settings by Repo.settings.collectAsState()
    val noun = slotNoun(slot)
    val today = day == localDay(0)
    val usualAt = remember(slot, day) { CoachBrain.usualTimeOn(slot, day) }
    val late = System.currentTimeMillis() - usualAt > 45 * 60_000L
    val done = remember(slot, day) { CoachBrain.slotDone(slot, day) }
    var answered by remember { mutableStateOf<String?>(null) }
    val aiReady = settings.aiEnabled && com.goodlife.app.ai.AiAccess.ready(settings)

    fun add(mode: String) {
        if (late) CoachStore.backdateNext(usualAt)
        onClose()
        AppNav.request.value = "add:$mode"
    }

    AlertDialog(
        onDismissRequest = onClose,
        icon = { ChefMascot(size = 72.dp, mood = if (answered != null) ChefMood.CONTENT else ChefMood.QUESTION) },
        title = { Text(if (answered != null) t("C'est noté 👍") else if (today) CoachBrain.mealCheckTitle(slot) else t("Ton %1\$s d'hier ?", noun)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when {
                    answered != null -> Text(answered!!, style = MaterialTheme.typography.bodyLarge)
                    done -> Text(t("Ton %1\$s est déjà noté. Tu peux en ajouter un autre si besoin.", noun), style = MaterialTheme.typography.bodyMedium)
                    else -> {
                        origin?.text?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
                        Text(
                            if (late) t("Si tu as mangé, note-le comme tu préfères : il sera rangé à l'heure habituelle de ton %1\$s (%2\$s).", noun, clock(CoachBrain.minuteOf(usualAt)))
                            else t("Si tu as mangé, note-le comme tu préfères :"),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (answered == null && onBack != null) TextButton(onClick = onBack) { Text(t("← Les autres repas de la journée")) }
                if (answered == null) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (aiReady) FilledTonalButton(onClick = { add("photo") }) { Text(t("📸 En photo")) }
                        FilledTonalButton(onClick = { add("barcode") }) { Text(t("🏷️ Code-barres")) }
                        FilledTonalButton(onClick = { add("manual") }) { Text(t("✏️ À la main")) }
                        FilledTonalButton(onClick = { add("redo") }) { Text(t("🔁 Un repas déjà fait")) }
                    }
                    if (!done) {
                        HorizontalDivider()
                        OutlinedButton(onClick = {
                            CoachStore.skip(day, slot)
                            CoachNotifier.schedule(context)
                            answered = t("Merci de me l'avoir dit. Je le garde en tête pour mes conseils de la semaine, sans te juger.")
                        }, modifier = Modifier.fillMaxWidth()) { Text(t("Non, je n'ai pas mangé")) }
                        TextButton(onClick = {
                            CoachStore.setFasting(slot, true)
                            CoachStore.skip(day, slot)
                            CoachNotifier.schedule(context)
                            answered = t("D'accord : tu sautes ce repas volontairement (jeûne, horaires décalés…). Je ne te le demanderai plus. Tu peux changer ça dans Paramètres › Le coach.")
                        }, modifier = Modifier.fillMaxWidth()) { Text(t("Je saute toujours ce repas, c'est voulu")) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(if (answered != null) t("Fermer") else t("Plus tard")) } }
    )
}

/** Tous les messages du chef (notifications et constats), du plus récent au plus ancien. */
@Composable
fun CoachInboxScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val mem by CoachStore.mem.collectAsState()
    ScreenColumn {
        SubScreenHeader(t("Messages du chef"), onBack)
        if (mem.inbox.isEmpty()) Text(t("Aucun message pour l'instant. Les messages de tes notifications arriveront ici."), color = MaterialTheme.colorScheme.onSurfaceVariant)
        mem.inbox.sortedByDescending { it.at }.forEach { m ->
            Surface(onClick = { CoachPanels.handle("coach:msg:${m.id}") }, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.Top) {
                    ChefMascot(size = 40.dp, mood = moodOfKind(m.kind))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(m.title, style = MaterialTheme.typography.titleSmall, fontWeight = if (m.read) FontWeight.Normal else FontWeight.Bold, modifier = Modifier.weight(1f))
                            if (!m.read) Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                        }
                        Text(m.text, style = MaterialTheme.typography.bodySmall, maxLines = 2, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(formatDay(m.at) + " · " + formatTime(m.at), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

private val GREEN = Color(0xFF1E8E3E)

/** Le bilan de la semaine : réussite, chiffres comparés aux semaines d'avant, lien observé, points à travailler, défi. */
@Composable
fun WeeklyReportScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val settings by Repo.settings.collectAsState()
    val mem by CoachStore.mem.collectAsState()
    val report = remember(mem.challenge, mem.challengeDone) { CoachBrain.weekReport() }
    LaunchedEffect(Unit) { CoachStore.update { it.copy(reportSeen = localDay(0)) } }
    ScreenColumn {
        SubScreenHeader(t("Mon bilan de la semaine"), onBack)
        if (report == null) { Text(t("Complète ton profil pour voir ton bilan.")); return@ScreenColumn }
        SectionCard(container = MaterialTheme.colorScheme.tertiaryContainer) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChefMascot(size = 64.dp, mood = ChefMood.BRAVO)
                Spacer(Modifier.width(12.dp))
                Text(report.win, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
            }
        }
        SectionCard(title = t("Tes 7 derniers jours")) {
            if (report.firstWeek) Text(t("C'est ta première semaine : la comparaison avec les semaines d'avant arrivera la semaine prochaine."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            report.metrics.forEach { MetricRow(it) }
            Text(t("Comparé à ta médiane des 4 semaines d'avant. Vert = progrès ; le reste est neutre, jamais en rouge."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        report.lastChallenge?.let { SectionCard(title = t("Ton défi")) { Text(it) } }
        // Défi non mesurable (légumes…) : la personne dit elle-même si elle l'a tenu
        mem.challenge?.let { ch ->
            if (report.lastChallenge == null && CoachBrain.daysBetween(ch.start, localDay(0)) >= 6 && ch.id !in mem.challengeDone) SectionCard(title = t("Ton défi")) {
                Text(t("« %1\$s » : tu l'as tenu ?", ch.title))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { CoachStore.update { it.copy(challengeDone = it.challengeDone + (ch.id to 1)) } }) { Text(t("Oui !")) }
                    OutlinedButton(onClick = { CoachStore.update { it.copy(challengeDone = it.challengeDone + (ch.id to 0)) } }) { Text(t("Pas vraiment")) }
                }
            }
        }
        report.link?.let { SectionCard(title = t("Ce que j'ai remarqué")) { Text(it) } }
        if (report.findings.isNotEmpty()) SectionCard(title = t("Les points à travailler")) {
            report.findings.forEachIndexed { i, f ->
                if (i > 0) HorizontalDivider()
                Text(f.title, style = MaterialTheme.typography.titleSmall)
                Text(f.text, style = MaterialTheme.typography.bodyMedium)
                if (f.action.isNotBlank()) TextButton(onClick = { onBack(); openCoachAction(context, f.action) }) { Text(f.actionLabel) }
            }
        }
        ChallengePicker(report.proposals, mem.challenge)
        // L'IA explique, seulement avec les chiffres ci-dessus (Premium ou clé personnelle)
        if (Repo.aiAllowed() && com.goodlife.app.ai.AiAccess.ready(settings)) SectionCard(title = t("Le chef t'explique")) {
            Text(t("Envoie ce bilan au chef (IA) pour qu'il te l'explique avec ses mots et te donne un conseil concret. Tu verras le message avant de l'envoyer."),
                style = MaterialTheme.typography.bodySmall)
            FilledTonalButton(onClick = {
                CoachPanels.chatPrefill.value = explainPrompt(report)
                onBack()
                AppNav.request.value = "home:coach"
            }) { Text(t("Le chef t'explique")) }
        }
        Text(t("Lifoody est une application de bien-être, pas un dispositif médical. Ces chiffres viennent de tes saisies et d'estimations photo (souvent à ±30 %)."),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Message proposé à l'IA : seulement les chiffres du bilan (pas le sommeil détaillé, pas de photos). */
private fun explainPrompt(r: com.goodlife.app.coach.WeekReport): String {
    val lines = r.metrics.filter { it.id != "sleep" }.joinToString("\n") { m -> "- ${m.label} : ${m.value}" + (m.before?.let { t(" (avant : %1\$s)", it) } ?: "") }
    val pts = r.findings.joinToString("\n") { "- " + it.title }
    return t("Explique-moi mon bilan de la semaine simplement, puis donne-moi un seul conseil concret pour la semaine prochaine.") + "\n" + lines +
        (if (pts.isNotBlank()) "\n" + t("Points relevés :") + "\n" + pts else "")
}

@Composable
private fun MetricRow(m: Metric) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(m.emoji, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(m.label, style = MaterialTheme.typography.bodyMedium)
            m.before?.let { Text(t("avant : %1\$s", it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Text(m.value, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.width(6.dp))
        Text(when (m.trend) { 1 -> "↑"; -1 -> "↓"; else -> "→" }, style = MaterialTheme.typography.titleMedium,
            color = if (m.good) GREEN else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Choisir 1 défi parmi 2 ou 3, et la phrase « quand…, je… » qui va avec (le plan qui marche le mieux, d'après les études). */
@Composable
private fun ChallengePicker(proposals: List<com.goodlife.app.coach.Challenge>, active: ActiveChallenge?) {
    val today = localDay(0)
    // -1 : défi choisi le dimanche, qui commence demain
    val running = active != null && CoachBrain.daysBetween(active.start, today) in -1..6
    var change by remember { mutableStateOf(false) }
    SectionCard(title = t("Ton défi de la semaine")) {
        if (running && !change) {
            Text(active!!.title, style = MaterialTheme.typography.titleSmall)
            if (active.plan.isNotBlank()) Text(t("Ton plan : %1\$s", active.plan), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { change = true }) { Text(t("Changer de défi")) }
            return@SectionCard
        }
        if (proposals.isEmpty()) { Text(t("Pas de défi à proposer pour l'instant.")); return@SectionCard }
        Text(t("Choisis-en un seul : c'est toi qui décides."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        var pick by remember { mutableStateOf(proposals.first()) }
        var plan by remember(pick) { mutableStateOf(pick.plans.firstOrNull().orEmpty()) }
        proposals.forEach { c ->
            Row(Modifier.fillMaxWidth().clickable { pick = c }, verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = pick.id == c.id, onClick = { pick = c })
                Text(c.title, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (pick.plans.isNotEmpty()) {
            Text(t("Ton plan :"), style = MaterialTheme.typography.labelLarge)
            pick.plans.forEach { p -> FilterChip(selected = plan == p, onClick = { plan = p }, label = { Text(p) }) }
        }
        Button(onClick = {
            // Le dimanche, le défi commence demain ; sinon tout de suite
            val start = if (java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK) == java.util.Calendar.SUNDAY) localDay(1) else today
            CoachStore.update { it.copy(challenge = ActiveChallenge(pick.id, pick.title, pick.target, plan, start)) }
            change = false
        }) { Text(t("C'est parti")) }
    }
}

/** Ce que le chef a appris (lire, corriger, effacer) : tout reste sur le téléphone. */
@Composable
fun CoachMemoryScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val mem by CoachStore.mem.collectAsState()
    val meals by Repo.meals.collectAsState()
    val input = remember(meals, mem) { CoachBrain.input() }
    val facts = remember(input) { input?.let { CoachFacts(it) } }
    var confirm by remember { mutableStateOf(false) }
    ScreenColumn {
        SubScreenHeader(t("Ce que le chef a appris"), onBack)
        Text(t("Le chef apprend de tes repas, de tes pas et de ton sommeil pour t'écrire au bon moment. Tout reste chiffré sur ton téléphone : rien n'est envoyé."),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SectionCard(title = t("Tes heures de repas")) {
            listOf(MealSlot.PETIT_DEJ, MealSlot.DEJEUNER, MealSlot.DINER).forEach { slot ->
                val usual = facts?.usualTime(slot)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(slot.label, style = MaterialTheme.typography.bodyLarge)
                        Text(if (usual != null) t("D'habitude vers %1\$s", clock(usual)) else t("Pas encore appris (il faut au moins 4 jours)"),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(t("Je le saute"), style = MaterialTheme.typography.labelSmall)
                        Switch(checked = slot in mem.fasting, onCheckedChange = { CoachStore.setFasting(slot, it); CoachNotifier.schedule(context) })
                    }
                }
            }
            Text(t("« Je le saute » : le chef ne te demandera plus si tu as mangé à ce repas (jeûne, horaires décalés, travail de nuit…)."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SectionCard(title = t("Ton rythme")) {
            val bed = facts?.usualBed
            Text(if (bed != null) t("Coucher habituel : vers %1\$s (pas de rappel 30 min avant, ni la nuit).", clock(bed)) else t("Heure de coucher : pas encore apprise. Pas de rappel après 21 h 30 ni avant 8 h."))
            if (facts != null && facts.stepsUsual > 0) Text(t("Pas habituels : %1\$s par jour (médiane de 4 semaines).", num(facts.stepsUsual)))
            val skips = mem.skipped.count { CoachBrain.daysBetween(it.substringBefore('#'), localDay(0)) in 0..6 }
            if (skips > 0) Text(tp(skips, "Repas « pas mangé » cette semaine : %1\$s.", "Repas « pas mangé » cette semaine : %1\$s."))
        }
        val calm = mem.calmUntil.filterValues { it > System.currentTimeMillis() }.keys + mem.muteAsk
        if (calm.isNotEmpty()) SectionCard(title = t("Rappels mis en veille")) {
            Text(t("Quand tu ne réponds pas à un rappel plusieurs fois, le chef en envoie moins. Tu peux les relancer :"), style = MaterialTheme.typography.bodySmall)
            calm.forEach { k ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(when (k) { "meal" -> t("« Tu as mangé ? »"); "move" -> t("« On bouge ? »"); "water" -> t("Rappel d'eau"); "evening" -> t("Série en danger"); else -> k },
                        modifier = Modifier.weight(1f))
                    TextButton(onClick = { CoachStore.update { it.copy(calmUntil = it.calmUntil - k, muteAsk = it.muteAsk - k, ignored = it.ignored - k, pending = it.pending - k) } }) { Text(t("Relancer")) }
                }
            }
        }
        OutlinedButton(onClick = { confirm = true }) { Text(t("Tout oublier")) }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text(t("Tout oublier ?")) },
        text = { Text(t("Le chef oublie ses réglages appris, tes réponses « pas mangé », ses messages et ton défi. Tes repas, pas et sommeil restent.")) },
        confirmButton = { TextButton(onClick = { CoachStore.forget(); CoachNotifier.schedule(context); confirm = false }) { Text(t("Oublier")) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text(t("Annuler")) } }
    )
}
