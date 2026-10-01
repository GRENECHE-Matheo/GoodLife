@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.goodlife.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.goodlife.app.ai.Gemini
import com.goodlife.app.data.Repo
import com.goodlife.app.data.SportProgram
import com.goodlife.app.data.SportSession
import com.goodlife.app.data.localDay
import com.goodlife.app.game.Game
import com.goodlife.app.ui.AiContentFooter
import com.goodlife.app.ui.ChefMascot
import com.goodlife.app.ui.ChefMood
import com.goodlife.app.ui.SectionCard
import com.goodlife.app.ui.Sfx
import com.goodlife.app.ui.Sounds
import com.goodlife.app.ui.theme.successColor
import kotlinx.coroutines.launch
import java.util.Calendar

private val GOALS = listOf("Me remettre en forme", "Perdre du poids", "Me muscler", "Endurance / cardio", "Souplesse et mobilité", "Santé et bien-être")
private val LEVELS = listOf("Débutant", "Intermédiaire", "Confirmé")
private val EQUIPMENT = listOf(
    "Haltères", "Élastiques", "Barre de traction", "Kettlebell", "Tapis", "Corde à sauter",
    "Banc", "Vélo (dehors)", "Vélo d'appartement", "Salle de sport"
)
private val DAYS = listOf("Lundi", "Mardi", "Mercredi", "Jeudi", "Vendredi", "Samedi", "Dimanche")

/** Jour de la semaine d'aujourd'hui : 1 = lundi … 7 = dimanche. */
private fun todayIndex(): Int = ((Calendar.getInstance().get(Calendar.DAY_OF_WEEK) + 5) % 7) + 1

/** Onglet « Programme » de l'espace Forme. */
@Composable
fun ProgramTab() {
    val sport by Repo.sport.collectAsState()
    val settings by Repo.settings.collectAsState()
    val profile by Repo.profile.collectAsState()
    var setup by rememberSaveable { mutableStateOf(false) }
    val adult = (profile?.age ?: 0) >= 18

    val program = sport.program
    when {
        !adult -> SectionCard(title = "Programme sportif", icon = Icons.Filled.FitnessCenter) {
            Text(
                "Le programme sur mesure est créé par l'IA, réservée aux 18 ans et plus. En attendant, les sorties " +
                    "(onglet Sorties) et tes pas comptent pour ton score et ton XP !",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        !settings.aiEnabled -> SectionCard(title = "Programme sportif", icon = Icons.Filled.FitnessCenter) {
            Text(
                "Un programme sur mesure (but, niveau, matériel, envies) créé par l'IA. Active l'IA dans Paramètres pour l'utiliser.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
        setup -> ProgramSetup(onDone = { setup = false }, onCancel = { setup = false })
        program == null -> SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChefMascot(size = 72.dp, mood = ChefMood.BRAVO)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Ton programme sur mesure", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Dis ce que tu veux, ce que tu aimes et le matériel que tu as : l'IA prépare ta semaine. " +
                            "Chaque séance faite rapporte ${Game.SESSION_XP} XP.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Button(onClick = { setup = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.AutoAwesome, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Créer mon programme")
            }
        }
        else -> ProgramView(program, sport.done, onNew = { setup = true })
    }
}

@Composable
private fun ProgramSetup(onDone: () -> Unit, onCancel: () -> Unit) {
    val settings by Repo.settings.collectAsState()
    val profile by Repo.profile.collectAsState()
    val old = Repo.sport.collectAsState().value.program
    val scope = rememberCoroutineScope()
    var goal by rememberSaveable { mutableStateOf(old?.goal ?: GOALS.first()) }
    var level by rememberSaveable { mutableStateOf(old?.level ?: LEVELS.first()) }
    var days by rememberSaveable { mutableIntStateOf(old?.daysPerWeek ?: 3) }
    var minutes by rememberSaveable { mutableIntStateOf(old?.minutes ?: 30) }
    var equipment by remember { mutableStateOf(old?.equipment?.toSet() ?: emptySet()) }
    var likes by rememberSaveable { mutableStateOf(old?.likes ?: "") }
    var limits by rememberSaveable { mutableStateOf(old?.limits ?: "") }
    var safe by rememberSaveable { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    SectionCard(title = "Mon programme", icon = Icons.Filled.FitnessCenter) {
        Label("Ce que je veux")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            GOALS.forEach { g -> FilterChip(goal == g, { goal = g }, label = { Text(g) }) }
        }
        Label("Mon niveau")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            LEVELS.forEach { l -> FilterChip(level == l, { level = l }, label = { Text(l) }) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Séances par semaine", Modifier.weight(1f))
            IconButton(enabled = days > 1, onClick = { days-- }) { Icon(Icons.Filled.Remove, "Moins") }
            Text("$days", style = MaterialTheme.typography.titleMedium)
            IconButton(enabled = days < 6, onClick = { days++ }) { Icon(Icons.Filled.Add, "Plus") }
        }
        Label("Durée d'une séance")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(15, 20, 30, 45, 60).forEach { m -> FilterChip(minutes == m, { minutes = m }, label = { Text("$m min") }) }
        }
        Label("Mon matériel (rien coché = sans matériel)")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(equipment.isEmpty(), { equipment = emptySet() }, label = { Text("Sans matériel") })
            EQUIPMENT.forEach { e ->
                FilterChip(e in equipment, { equipment = if (e in equipment) equipment - e else equipment + e }, label = { Text(e) })
            }
        }
        OutlinedTextField(
            likes, { likes = it.take(200) }, label = { Text("Ce que j'aime (facultatif)") },
            placeholder = { Text("Ex : danser, jeux, dehors, musique… je déteste courir") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            limits, { limits = it.take(200) }, label = { Text("Douleurs ou limites (facultatif)") },
            placeholder = { Text("Ex : genou fragile, mal au dos") },
            modifier = Modifier.fillMaxWidth()
        )
        Row(Modifier.fillMaxWidth().toggleable(safe, role = Role.Checkbox) { safe = it }, verticalAlignment = Alignment.Top) {
            Checkbox(checked = safe, onCheckedChange = null)
            Spacer(Modifier.width(8.dp))
            Text(
                "Je n'ai pas de problème de santé connu qui m'interdit le sport (cœur, malaise, douleur à l'effort, " +
                    "grossesse…). Sinon, je demande d'abord l'avis d'un médecin.",
                style = MaterialTheme.typography.bodySmall
            )
        }
        if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                enabled = safe && !loading && profile != null,
                onClick = {
                    loading = true; error = null
                    scope.launch {
                        try {
                            val p = Gemini(settings.apiKey, settings.model).sportProgram(
                                profile!!, goal, level, EQUIPMENT.filter { it in equipment }, days, minutes, likes.trim(), limits.trim()
                            )
                            Repo.setProgram(p)
                            onDone()
                        } catch (e: Exception) {
                            error = e.message
                        } finally {
                            loading = false
                        }
                    }
                }
            ) {
                if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Icon(Icons.Filled.AutoAwesome, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (loading) "L'IA prépare ta semaine…" else "Créer mon programme")
            }
            TextButton(enabled = !loading, onClick = onCancel) { Text("Annuler") }
        }
    }
}

@Composable
private fun Label(text: String) =
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun ProgramView(program: SportProgram, done: Set<String>, onNew: () -> Unit) {
    var chat by remember { mutableStateOf(false) }
    var confirmNew by remember { mutableStateOf(false) }
    var reward by remember { mutableStateOf<String?>(null) }
    val today = todayIndex()
    // Séances faites cette semaine (lundi → aujourd'hui)
    val weekDays = (0 until today).map { localDay(-it) }.toSet()
    val doneThisWeek = done.count { it.substringBefore('#') in weekDays }

    SectionCard(container = MaterialTheme.colorScheme.primaryContainer) {
        Text(program.goal, style = MaterialTheme.typography.titleLarge)
        Text(
            "${program.level} · ${program.sessions.size} séances · ~${program.minutes} min · " +
                if (program.equipment.isEmpty()) "sans matériel" else program.equipment.joinToString(", "),
            style = MaterialTheme.typography.bodySmall
        )
        Text("Cette semaine : $doneThisWeek / ${program.sessions.size} séances", style = MaterialTheme.typography.labelLarge)
        LinearProgressIndicator(
            progress = { (doneThisWeek.toFloat() / program.sessions.size.coerceAtLeast(1)).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(8.dp), strokeCap = StrokeCap.Round
        )
        if (program.advice.isNotBlank()) Text(program.advice, style = MaterialTheme.typography.bodyMedium)
    }
    if (reward != null) Text(reward!!, color = successColor, fontWeight = FontWeight.Bold)

    program.sessions.forEachIndexed { i, s ->
        SessionCard(
            s, isToday = s.day == today, doneToday = "${localDay(0)}#$i" in done,
            onDone = {
                val xp = Repo.markSessionDone(i)
                if (xp != null) {
                    Sounds.play(Sfx.LEVEL_UP)
                    reward = if (xp > 0) "Bravo ! +$xp XP" else "Bravo ! (XP sport du jour déjà au maximum)"
                }
            }
        )
    }
    AiContentFooter("Programme sportif : " + program.sessions.joinToString(" | ") { "${it.title} : " + it.exercises.joinToString(", ") { e -> e.name } })
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(onClick = { chat = true }) {
            Icon(Icons.AutoMirrored.Filled.Chat, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Poser une question")
        }
        OutlinedButton(onClick = { confirmNew = true }) { Text("Nouveau programme") }
    }

    if (chat) AiChatDialog(
        title = "Mon programme : ${program.goal}",
        context = "Programme sportif de l'utilisateur (${program.level}, matériel : ${program.equipment.ifEmpty { listOf("aucun") }.joinToString(", ")}, " +
            "limites : ${program.limits.ifBlank { "aucune" }}). " + program.sessions.joinToString(" ") { s ->
                "${DAYS[s.day - 1]} « ${s.title} » (${s.minutes} min) : " + s.exercises.joinToString("; ") { "${it.name} ${it.detail}" } + "."
            },
        suggestions = listOf("Remplacer un exercice", "Plus facile ?", "Je n'ai que 10 min", "Que manger après ?"),
        onDismiss = { chat = false }
    )
    if (confirmNew) AlertDialog(
        onDismissRequest = { confirmNew = false },
        title = { Text("Nouveau programme ?") },
        text = { Text("Ton programme actuel sera remplacé. Les séances déjà faites et l'XP gagnée restent.") },
        confirmButton = { TextButton(onClick = { confirmNew = false; onNew() }) { Text("Continuer") } },
        dismissButton = { TextButton(onClick = { confirmNew = false }) { Text("Annuler") } }
    )
}

@Composable
private fun SessionCard(s: SportSession, isToday: Boolean, doneToday: Boolean, onDone: () -> Unit) {
    var open by rememberSaveable(s.title, s.day) { mutableStateOf(isToday) }
    SectionCard(container = if (isToday) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { open = !open },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    DAYS[s.day - 1] + if (isToday) " · aujourd'hui" else "",
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary
                )
                Text(s.title, style = MaterialTheme.typography.titleMedium)
                Text("${s.minutes} min · ${s.exercises.size} exercice${if (s.exercises.size > 1) "s" else ""}", style = MaterialTheme.typography.bodySmall)
            }
            if (doneToday) Icon(Icons.Filled.CheckCircle, "Faite", tint = successColor)
            Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null)
        }
        AnimatedVisibility(open) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (s.warmup.isNotBlank()) Text("Échauffement : ${s.warmup}", style = MaterialTheme.typography.bodyMedium)
                s.exercises.forEachIndexed { i, e ->
                    if (i > 0) HorizontalDivider()
                    Row {
                        Text("${i + 1}.", modifier = Modifier.width(24.dp), fontWeight = FontWeight.Bold)
                        Column {
                            Text(e.name, fontWeight = FontWeight.Medium)
                            Text(
                                listOf(e.detail, e.rest.takeIf { it.isNotBlank() }?.let { "repos $it" }).filterNotNull().joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary
                            )
                            if (e.tip.isNotBlank()) Text(e.tip, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if (s.cooldown.isNotBlank()) Text("Retour au calme : ${s.cooldown}", style = MaterialTheme.typography.bodyMedium)
                Button(enabled = !doneToday, onClick = onDone, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Icon(Icons.Filled.CheckCircle, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                    Text(if (doneToday) "Faite aujourd'hui" else "Séance faite (+${Game.SESSION_XP} XP)")
                }
            }
        }
    }
}
