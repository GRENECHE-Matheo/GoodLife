package com.goodlife.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.goodlife.app.ai.ChatMessage
import com.goodlife.app.ai.CoachMeal
import com.goodlife.app.ai.Gemini
import com.goodlife.app.coach.Coach
import com.goodlife.app.data.PlannedMeal
import com.goodlife.app.data.Repo
import com.goodlife.app.data.localDay
import com.goodlife.app.ui.AiContentFooter
import com.goodlife.app.ui.ChefMascot
import com.goodlife.app.ui.ChefMood
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

/** Un message de la conversation avec le coach, et les repas qu'il propose d'ajouter au planning. */
private class CoachEntry(val message: ChatMessage, val meals: List<CoachMeal> = emptyList())

/**
 * La conversation reste en mémoire tant que l'app est ouverte (pour pouvoir revenir à l'accueil et y retourner),
 * mais n'est jamais enregistrée sur le téléphone.
 */
private object CoachSession {
    val entries = mutableStateListOf<CoachEntry>()
    val added = mutableStateMapOf<String, Boolean>()   // "message#repas" → ajouté au planning
    fun clear() { entries.clear(); added.clear() }
}

private const val MAX_TURNS = 30

private const val COACH_RULES = """
Tu es « le chef », le coach bienveillant de l'app GoodLife : alimentation, cuisine, sport, pas, motivation.
Tu tutoies, tu es chaleureux, positif et concret. Tu t'appuies sur les chiffres de la personne donnés plus bas
(sans les réciter tous) pour personnaliser tes conseils. Jamais de culpabilisation ni de régime restrictif ;
ne pousse jamais à manger sous l'objectif calorique. Réponse courte (2 à 8 phrases, listes « • » permises).
Réponds UNIQUEMENT en JSON : {"reply": "ta réponse", "meals": []}
"meals" reste vide, SAUF si la personne demande des repas à prévoir (idées pour un repas précis, menu de demain,
planning de la semaine…). Chaque repas : {"date": "AAAA-MM-JJ", "slot": "PETIT_DEJ|DEJEUNER|COLLATION|DINER",
"name": "nom court", "kcal": 0, "description": "ingrédients et quantités, en une ou deux phrases"}.
Dates à partir d'aujourd'hui (jamais un créneau déjà passé aujourd'hui), au plus 14 jours. Respecte les allergies,
les habitudes et l'objectif calorique réparti sur la journée, en tenant compte de ce qui est déjà mangé ou prévu.
Ne dis jamais que tu as ajouté les repas au planning : la personne les ajoute elle-même avec un bouton si elle le veut.
"""

@Composable
fun CoachScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val settings by Repo.settings.collectAsState()
    val profile by Repo.profile.collectAsState()
    val scope = rememberCoroutineScope()
    val entries = CoachSession.entries
    var input by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val list = rememberLazyListState()
    val aiReady = Repo.aiAllowed() && settings.apiKey.isNotBlank()
    val summary = remember { Coach.summary() }
    val (mood, dailyWord) = remember(summary, profile) {
        if (summary != null && profile != null) Coach.homeMessage(summary, profile!!) else ChefMood.CONTENT to ""
    }

    fun send(text: String) {
        val q = text.trim().take(600)
        if (q.isEmpty() || loading) return
        if (entries.count { it.message.fromUser } >= MAX_TURNS) {
            error = "On a beaucoup discuté ! Appuie sur « Nouvelle conversation » pour recommencer."
            return
        }
        entries.add(CoachEntry(ChatMessage(true, q)))
        input = ""; error = null; loading = true
        scope.launch {
            try {
                val system = CHAT_RULES + "\n" + COACH_RULES + "\nChiffres et contexte de la personne :\n" + Coach.aiContext()
                val r = Gemini(settings.apiKey, settings.model).coach(system, entries.map { it.message })
                entries.add(CoachEntry(ChatMessage(false, r.text), r.meals))
            } catch (e: Exception) {
                entries.removeAt(entries.lastIndex)
                input = q
                error = e.message
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(entries.size, loading) {
        val count = entries.size + 1 + (if (loading) 1 else 0)
        list.animateScrollToItem(count - 1)
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize().imePadding(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 640.dp).fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") }
                    ChefMascot(size = 40.dp, mood = if (loading) ChefMood.QUESTION else mood)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Ton coach", style = MaterialTheme.typography.titleLarge)
                        Text("Alimentation, sport et motivation", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (entries.isNotEmpty()) IconButton(onClick = { CoachSession.clear(); error = null }) {
                        Icon(Icons.Filled.RestartAlt, "Nouvelle conversation")
                    }
                }
                LazyColumn(
                    state = list,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Row(verticalAlignment = Alignment.Top) {
                            ChefMascot(size = 48.dp, mood = mood)
                            Spacer(Modifier.width(8.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (dailyWord.isNotBlank()) Bubble(false, dailyWord)
                                Bubble(false,
                                    if (aiReady) "Pose-moi toutes tes questions : quoi manger, une idée de recette, un conseil sport, " +
                                        "ton bilan de la semaine… Je connais tes chiffres, et si je te propose des repas, tu pourras " +
                                        "les ajouter à ton planning en un geste."
                                    else "Pour discuter avec moi, active l'IA et ajoute ta clé Gemini dans Profil › Paramètres › " +
                                        "Intelligence artificielle (18 ans et plus). En attendant, je te laisse mes petits mots ici et dans tes notifications !"
                                )
                            }
                        }
                    }
                    itemsIndexed(entries) { i, e ->
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = if (e.message.fromUser) Alignment.End else Alignment.Start) {
                            Bubble(e.message.fromUser, e.message.text)
                            if (!e.message.fromUser) {
                                if (e.meals.isNotEmpty()) MealProposals(i, e.meals)
                                AiContentFooter("Coach GoodLife\n${e.message.text}", Modifier.widthIn(max = 340.dp))
                            }
                        }
                    }
                    if (loading) item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Le chef réfléchit…", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if (error != null) Text(
                    error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                if (aiReady) {
                    if (entries.isEmpty()) Row(
                        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "Que manger ce soir ?", "Prévois mes repas de demain", "Un conseil sport pour aujourd'hui",
                            "Comment s'est passée ma semaine ?", "Une idée de collation"
                        ).forEach { s -> AssistChip(onClick = { send(s) }, label = { Text(s) }) }
                    }
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            input, { input = it.take(600) },
                            placeholder = { Text("Écris au chef…") },
                            modifier = Modifier.weight(1f),
                            maxLines = 4,
                            shape = RoundedCornerShape(24.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        FilledIconButton(enabled = input.isNotBlank() && !loading, onClick = { send(input) }) {
                            Icon(Icons.AutoMirrored.Filled.Send, "Envoyer")
                        }
                    }
                    Text(
                        "Tes questions et tes chiffres (profil, repas, pas, séries, sport, planning) sont envoyés à Google Gemini " +
                            "avec ta clé. Jamais ton prénom, ton sommeil ni tes positions GPS. Rien n'est gardé après la fermeture de l'app.",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                    )
                }
            }
        }
    }
}

/** Repas proposés par le coach : rien n'est ajouté au planning sans un appui sur « Ajouter ». */
@Composable
private fun MealProposals(entry: Int, meals: List<CoachMeal>) {
    val added = CoachSession.added
    fun add(i: Int, m: CoachMeal) {
        val key = "$entry#$i"
        if (added[key] == true) return
        Repo.addPlanned(PlannedMeal(
            id = System.currentTimeMillis() + i, date = m.date, slot = m.slot, name = m.name, kcal = m.kcal, description = m.description
        ))
        added[key] = true
    }
    Column(Modifier.widthIn(max = 360.dp).padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        meals.forEachIndexed { i, m ->
            val done = added["$entry#$i"] == true
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                Row(Modifier.fillMaxWidth().padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(m.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                        Text("${coachDayLabel(m.date)} · ${m.slot.label} · ${m.kcal} kcal", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer)
                        if (m.description.isNotBlank()) Text(m.description, style = MaterialTheme.typography.bodySmall,
                            maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                    TextButton(enabled = !done, onClick = { add(i, m) }) {
                        Icon(if (done) Icons.Filled.Check else Icons.Filled.DateRange, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (done) "Ajouté" else "Ajouter")
                    }
                }
            }
        }
        val remaining = meals.indices.count { added["$entry#$it"] != true }
        if (meals.size > 1 && remaining > 0) FilledTonalButton(onClick = { meals.forEachIndexed { i, m -> add(i, m) } }) {
            Icon(Icons.Filled.DateRange, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Tout ajouter au planning ($remaining)")
        }
    }
}

private fun coachDayLabel(date: String): String = when (date) {
    localDay(0) -> "Aujourd'hui"
    localDay(1) -> "Demain"
    else -> runCatching {
        SimpleDateFormat("EEEE d MMM", Locale.FRANCE).format(SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date)!!)
            .replaceFirstChar { it.uppercase() }
    }.getOrDefault(date)
}
