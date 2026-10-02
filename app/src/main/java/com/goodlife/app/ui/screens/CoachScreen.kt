package com.goodlife.app.ui.screens

import com.goodlife.app.i18n.t
import com.goodlife.app.i18n.tp

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
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
private class CoachEntry(
    val message: ChatMessage,
    val meals: List<CoachMeal> = emptyList(),
    val shopping: List<com.goodlife.app.ai.ShopItem> = emptyList(),
    val fridgeAdd: List<com.goodlife.app.data.FridgeItem> = emptyList(),
    val fridgeRemove: List<Pair<String, Double>> = emptyList(),
    val sessions: List<com.goodlife.app.data.SportSession> = emptyList(),
    val removeDays: List<Int> = emptyList()
)

/**
 * La conversation reste en mémoire tant que l'app est ouverte (pour pouvoir revenir à l'accueil et y retourner),
 * mais n'est jamais enregistrée sur le téléphone.
 */
private object CoachSession {
    val entries = mutableStateListOf<CoachEntry>()
    val added = mutableStateMapOf<String, Boolean>()   // "message#repas" → ajouté au planning ; "message#courses" → liste
    var id: Long = 0L                                   // conversation en cours dans l'historique (0 = nouvelle)
    fun clear() { entries.clear(); added.clear(); id = 0L }

    /** Enregistre la conversation dans l'historique (chiffré, sur le téléphone), si l'historique est activé. */
    fun persist() {
        if (entries.isEmpty() || !Repo.settings.value.coachHistory) return
        if (id == 0L) id = System.currentTimeMillis()
        CoachHistory.save(id, entries.toList(), added.toMap())
    }

    fun open(c: CoachHistory.Conv) {
        val (list, done) = CoachHistory.entriesOf(c)
        entries.clear(); entries.addAll(list)
        added.clear(); added.putAll(done)
        id = c.id
    }
}

private const val MAX_TURNS = 30

internal const val COACH_RULES = """
Tu es « le chef », le coach bienveillant de l'app GoodLife : alimentation, cuisine, sport, pas, motivation.
Tu tutoies, tu es chaleureux, positif et concret. Tu t'appuies sur les chiffres de la personne donnés plus bas
(sans les réciter tous) pour personnaliser tes conseils. Jamais de culpabilisation ni de régime restrictif ;
ne pousse jamais à manger sous l'objectif calorique. Réponse courte (2 à 8 phrases, listes « • » permises).
Réponds UNIQUEMENT en JSON : {"reply": "ta réponse", "meals": [], "courses": [], "frigo_ajout": [], "frigo_retrait": [],
"programme_seances": [], "programme_retirer": []}
Si la personne envoie une photo (aliment, plat, étiquette, frigo, menu…), décris prudemment ce que tu vois et donne
un conseil adapté ; une estimation de calories reste approximative : dis-le, et ne prétends jamais en être sûr.
"courses" reste vide, SAUF si la personne demande une liste de courses (ou des repas pour plusieurs jours et une liste) :
chaque article {"nom": "...", "quantite": "...", "rayon": "Fruits et légumes|Viandes et poissons|Produits frais|Épicerie|Surgelés|Boulangerie|Autres"},
ingrédients regroupés et quantités additionnées, sans sel, poivre, huile ni eau.
"meals" reste vide, SAUF si la personne demande des repas à prévoir (idées pour un repas précis, menu de demain,
planning de la semaine…). Chaque repas : {"date": "AAAA-MM-JJ", "slot": "PETIT_DEJ|DEJEUNER|COLLATION|DINER",
"name": "nom court", "kcal": 0, "description": "ingrédients et quantités, en une ou deux phrases"}.
Dates à partir d'aujourd'hui (jamais un créneau déjà passé aujourd'hui), au plus 14 jours. Respecte les allergies,
les habitudes et l'objectif calorique réparti sur la journée, en tenant compte de ce qui est déjà mangé ou prévu.
Pour changer les repas d'un jour précis, propose les nouveaux repas de ce jour dans "meals" : ils remplaceront ceux du
même créneau quand la personne validera.
"frigo_ajout" reste vide, SAUF si la personne dit ce qu'elle a chez elle ou ce qu'elle vient d'acheter : chaque article
{"nom": "nom simple", "quantite": nombre, "unite": "pièce|g|kg|ml|L|boîte|paquet|bouteille", "rayon": "Fruits et légumes|Viandes et poissons|Produits frais|Épicerie|Surgelés|Boulangerie|Boissons|Autres"}.
"frigo_retrait" reste vide, SAUF si elle dit avoir fini, mangé ou jeté quelque chose de SON FRIGO (liste plus bas) :
{"nom": "nom EXACT de la liste du frigo", "quantite": nombre dans la même unité}.
"programme_seances" reste vide, SAUF si elle demande de modifier son programme sportif (remplacer un exercice, séance plus
facile, autre jour…) : donne chaque séance modifiée ou ajoutée en entier {"jour": 1-7 (1 = lundi), "titre": "...",
"minutes": 30, "echauffement": "...", "exercices": [{"nom": "...", "detail": "...", "repos": "...", "conseil": "..."}],
"retour_au_calme": "..."} ; elle remplace la séance de ce jour. "programme_retirer" : jours de séances à supprimer.
Respecte toujours ses limites et douleurs. Ne dis jamais que tu as déjà modifié le planning, le frigo, la liste ou le
programme : la personne valide elle-même chaque proposition avec un bouton.
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

    var askConsent by remember { mutableStateOf<String?>(null) }
    var showHistory by remember { mutableStateOf(false) }
    if (showHistory) {
        CoachHistoryScreen(onClose = { showHistory = false }, onOpen = { c -> CoachSession.open(c); error = null; showHistory = false })
        return
    }
    // Photo jointe au prochain message (aliment, plat, étiquette…) : envoyée à Gemini, jamais enregistrée
    var pendingPhoto by remember { mutableStateOf<ByteArray?>(null) }
    var photoMenu by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val shotFile = remember { java.io.File(java.io.File(context.cacheDir, "camera").apply { mkdirs() }, "coach.jpg") }
    val shotUri = remember { androidx.core.content.FileProvider.getUriForFile(context, context.packageName + ".camera", shotFile) }
    val takePhoto = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.TakePicture()) { ok ->
        if (ok) scope.launch {
            pendingPhoto = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { readPhoto(context, shotUri) }
            runCatching { shotFile.delete() }   // la photo n'est pas gardée sur le téléphone
        }
    }
    val askCamera = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) takePhoto.launch(shotUri) else error = t("Sans l'accès à la caméra, choisis plutôt une photo dans ta galerie.")
    }
    val pickPhoto = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { pendingPhoto = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { readPhoto(context, uri) } }
    }

    fun send(text: String) {
        val q = text.trim().take(600).ifEmpty { if (pendingPhoto != null) t("Qu'en penses-tu ?") else "" }
        if (q.isEmpty() || loading) return
        // Première fois : accord explicite pour envoyer le résumé de ses chiffres (données de santé)
        if (Repo.settings.value.coachConsentAt == 0L) { askConsent = q; return }
        if (entries.count { it.message.fromUser } >= MAX_TURNS) {
            error = t("On a beaucoup discuté ! Appuie sur « Nouvelle conversation » pour recommencer.")
            return
        }
        val photo = pendingPhoto
        entries.add(CoachEntry(ChatMessage(true, q, photo)))
        input = ""; pendingPhoto = null; error = null; loading = true
        scope.launch {
            try {
                val system = CHAT_RULES + "\n" + COACH_RULES + t("\nChiffres et contexte de la personne :\n") + Coach.aiContext()
                val r = Gemini(settings.apiKey, settings.model).coach(system, entries.map { it.message })
                entries.add(CoachEntry(ChatMessage(false, r.text), r.meals, r.shopping, r.fridgeAdd, r.fridgeRemove, r.sessions, r.removeDays))
                CoachSession.persist()
            } catch (e: Exception) {
                entries.removeAt(entries.lastIndex)
                input = q; pendingPhoto = photo
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
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, t("Retour")) }
                    ChefMascot(size = 40.dp, mood = if (loading) ChefMood.QUESTION else mood)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t("Ton coach"), style = MaterialTheme.typography.titleLarge)
                        Text(t("Alimentation, sport et motivation"), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { showHistory = true }) { Icon(Icons.Filled.History, t("Historique des conversations")) }
                    if (entries.isNotEmpty()) IconButton(onClick = { CoachSession.clear(); error = null }) {
                        Icon(Icons.Filled.RestartAlt, t("Nouvelle conversation"))
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
                                    if (aiReady) t("Pose-moi toutes tes questions : quoi manger, une idée de recette, un conseil sport, ton bilan de la semaine… Je connais tes chiffres. Dis-moi aussi ce que tu as dans ton frigo, ou ce que tu veux changer dans ton planning ou ton programme : je te le propose, tu valides d'un bouton.")
                                    else if ((profile?.age ?: 0) < 18) t("Salut l'ami ! 👋 Discuter avec moi utilise l'IA de Google, réservée aux 18 ans et plus. En attendant, je te laisse mes petits mots ici et dans tes notifications !")
                                    else t("Salut l'ami ! 👋 Pour discuter avec moi et débloquer les autres fonctions IA (photo de tes repas, planning de la semaine, programme sportif, idées avec ton frigo…), il te faut une clé Gemini : elle se crée gratuitement chez Google en deux minutes. Je t'explique juste en dessous !")
                                )
                            }
                        }
                    }
                    itemsIndexed(entries) { i, e ->
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = if (e.message.fromUser) Alignment.End else Alignment.Start) {
                            e.message.image?.let { bytes -> PhotoBubble(bytes) }
                            Bubble(e.message.fromUser, e.message.text)
                            if (!e.message.fromUser) {
                                if (e.meals.isNotEmpty()) MealProposals(i, e.meals)
                                if (e.shopping.isNotEmpty()) ShoppingProposal(i, e.shopping)
                                if (e.fridgeAdd.isNotEmpty() || e.fridgeRemove.isNotEmpty()) FridgeProposal(i, e.fridgeAdd, e.fridgeRemove)
                                if (e.sessions.isNotEmpty() || e.removeDays.isNotEmpty()) ProgramProposal(i, e.sessions, e.removeDays)
                                AiContentFooter(t("Coach GoodLife\n%1\$s", e.message.text), Modifier.widthIn(max = 340.dp))
                            }
                        }
                    }
                    if (loading) item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(t("Le chef réfléchit…"), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if (error != null) Text(
                    error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                if (!aiReady && (profile?.age ?: 0) >= 18) CoachKeySetup()
                if (aiReady) {
                    if (entries.isEmpty()) Row(
                        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            t("Que manger ce soir ?"), t("Prévois mes repas de demain"), t("Un conseil sport pour aujourd'hui"),
                            t("Comment s'est passée ma semaine ?"), t("Une idée de collation")
                        ).forEach { s -> AssistChip(onClick = { send(s) }, label = { Text(s) }) }
                    }
                    pendingPhoto?.let { bytes ->
                        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            PhotoBubble(bytes, size = 72)
                            TextButton(onClick = { pendingPhoto = null }) { Text(t("Retirer la photo")) }
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box {
                            IconButton(onClick = { photoMenu = true }) { Icon(Icons.Filled.AddPhotoAlternate, t("Envoyer une photo au chef")) }
                            androidx.compose.material3.DropdownMenu(expanded = photoMenu, onDismissRequest = { photoMenu = false }) {
                                androidx.compose.material3.DropdownMenuItem(text = { Text(t("Prendre une photo")) }, onClick = {
                                    photoMenu = false
                                    if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) takePhoto.launch(shotUri)
                                    else askCamera.launch(android.Manifest.permission.CAMERA)
                                })
                                androidx.compose.material3.DropdownMenuItem(text = { Text(t("Choisir dans la galerie")) }, onClick = {
                                    photoMenu = false
                                    pickPhoto.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly))
                                })
                            }
                        }
                        OutlinedTextField(
                            input, { input = it.take(600) },
                            placeholder = { Text(t("Écris au chef…")) },
                            modifier = Modifier.weight(1f),
                            maxLines = 4,
                            shape = RoundedCornerShape(24.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        FilledIconButton(enabled = (input.isNotBlank() || pendingPhoto != null) && !loading, onClick = { send(input) }) {
                            Icon(Icons.AutoMirrored.Filled.Send, t("Envoyer"))
                        }
                    }
                    askConsent?.let { q ->
                        CoachConsentDialog(
                            onAccept = {
                                Repo.updateSettings { it.copy(coachConsentAt = System.currentTimeMillis()) }
                                askConsent = null
                                send(q)
                            },
                            onDismiss = { askConsent = null; input = q }
                        )
                    }
                    // Une seule ligne (le détail complet a été accepté au premier message, et reste à un appui)
                    var privacyInfo by remember { mutableStateOf(false) }
                    Text(
                        t("Envoyé à Google Gemini avec ta clé · En savoir plus"),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                            .clickable { privacyInfo = true }
                    )
                    if (privacyInfo) androidx.compose.material3.AlertDialog(
                        onDismissRequest = { privacyInfo = false },
                        confirmButton = { androidx.compose.material3.TextButton(onClick = { privacyInfo = false }) { Text(t("OK")) } },
                        title = { Text(t("Ce que voit Google")) },
                        text = { Text(t("Tes questions, les photos que tu envoies et tes chiffres (profil, repas, pas, séries, sport, planning) sont envoyés à Google Gemini avec ta clé. Jamais ton prénom, ton sommeil ni tes positions GPS. L'historique reste chiffré sur ton téléphone, sans les photos.")) }
                    )
                }
            }
        }
    }
}

/** Ce que le coach envoie à Google, à accepter une fois avant la première question. */
@Composable
private fun CoachConsentDialog(onAccept: () -> Unit, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("Le chef a besoin de tes chiffres")) },
        text = {
            Text(
                t("Pour des conseils vraiment adaptés, chaque question est envoyée à Google Gemini (avec ta clé) avec : ton âge, sexe, poids, taille, activité, objectif, habitudes et allergies, tes repas et tes pas du jour, un résumé de tes 7 derniers jours (jours validés, score, calories, pas, sport, évolution du poids, série), ton programme sportif et les repas prévus au planning. Ce sont des données de santé.\n\nJamais ton prénom, ton sommeil ni tes positions GPS ; une photo n'est envoyée que si tu la joins toi-même à un message. Tu peux retirer cet accord en désactivant l'IA dans Paramètres.")
            )
        },
        confirmButton = { TextButton(onClick = onAccept) { Text(t("J'accepte")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Annuler")) } }
    )
}

/** Repas proposés par le coach : rien n'est ajouté au planning sans un appui sur « Ajouter ». */
@Composable
private fun MealProposals(entry: Int, meals: List<CoachMeal>) {
    val added = CoachSession.added
    val plan by Repo.plan.collectAsState()
    fun existing(m: CoachMeal) = plan.firstOrNull { it.date == m.date && it.slot == m.slot && !it.done }
    fun add(i: Int, m: CoachMeal) {
        val key = "$entry#$i"
        if (added[key] == true) return
        // Un repas déjà prévu sur ce créneau (pas encore mangé) est remplacé
        existing(m)?.let { Repo.deletePlanned(it.id) }
        Repo.addPlanned(PlannedMeal(
            id = System.currentTimeMillis() + i, date = m.date, slot = m.slot, name = m.name, kcal = m.kcal, description = m.description
        ))
        added[key] = true
        CoachSession.persist()
    }
    Column(Modifier.widthIn(max = 360.dp).padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        meals.forEachIndexed { i, m ->
            val done = added["$entry#$i"] == true
            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                Row(Modifier.fillMaxWidth().padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(m.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                        Text(t("%1\$s · %2\$s · %3\$s kcal", coachDayLabel(m.date), m.slot.label, m.kcal), style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer)
                        if (m.description.isNotBlank()) Text(m.description, style = MaterialTheme.typography.bodySmall,
                            maxLines = 3, overflow = TextOverflow.Ellipsis)
                        val old = if (done) null else existing(m)
                        if (old != null) Text(t("Remplace « %1\$s »", old.name), style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(enabled = !done, onClick = { add(i, m) }) {
                        Icon(if (done) Icons.Filled.Check else Icons.Filled.DateRange, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (done) t("Ajouté") else if (existing(m) != null) t("Remplacer") else t("Ajouter"))
                    }
                }
            }
        }
        val remaining = meals.indices.count { added["$entry#$it"] != true }
        if (meals.size > 1 && remaining > 0) FilledTonalButton(onClick = { meals.forEachIndexed { i, m -> add(i, m) } }) {
            Icon(Icons.Filled.DateRange, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(t("Tout valider dans le planning (%1\$s)", remaining))
        }
    }
}

private fun coachDayLabel(date: String): String = when (date) {
    localDay(0) -> t("Aujourd'hui")
    localDay(1) -> t("Demain")
    else -> runCatching {
        SimpleDateFormat("EEEE d MMM", com.goodlife.app.i18n.Lang.locale).format(SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date)!!)
            .replaceFirstChar { it.uppercase() }
    }.getOrDefault(date)
}

/** Photo jointe à un message (affichée en petit, jamais enregistrée). */
@Composable
private fun PhotoBubble(bytes: ByteArray, size: Int = 160) {
    val bmp = remember(bytes) { android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }
    if (bmp != null) androidx.compose.foundation.Image(
        bmp, t("Photo envoyée au chef"),
        Modifier.padding(bottom = 4.dp).size(size.dp).clip(RoundedCornerShape(16.dp)),
        contentScale = androidx.compose.ui.layout.ContentScale.Crop
    )
}

/** Liste de courses proposée par le coach : ajoutée à « Liste de courses » seulement sur appui. */
@Composable
private fun ShoppingProposal(entry: Int, items: List<com.goodlife.app.ai.ShopItem>) {
    val added = CoachSession.added
    val key = "$entry#courses"
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.widthIn(max = 360.dp).padding(top = 6.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(t("🛒 Liste de courses proposée (%1\$s articles)", items.size), style = MaterialTheme.typography.titleSmall)
            Text(items.take(6).joinToString(", ") { it.name } + if (items.size > 6) "…" else "",
                style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (added[key] == true) Text(t("Ajoutée à ta liste de courses ✓"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            else FilledTonalButton(onClick = { Shopping.add(items); added[key] = true; CoachSession.persist() }) { Text(t("Ajouter à ma liste de courses")) }
        }
    }
}

/**
 * Historique des conversations avec le coach : chiffré sur le téléphone (SecureStore), jamais envoyé ni sauvegardé
 * ailleurs. Les photos ne sont pas gardées (seulement une mention). Reprendre une conversation la renvoie à Gemini
 * avec la question suivante, pour garder le contexte. 30 conversations au plus, 60 messages chacune.
 */
private object CoachHistory {
    private const val KEY = "coach_history"
    private const val MAX_CONVS = 30
    private const val MAX_MESSAGES = 60

    data class Conv(val id: Long, val title: String, val updatedAt: Long, val json: org.json.JSONObject)

    @Synchronized
    fun list(): List<Conv> = runCatching {
        val a = org.json.JSONObject(Repo.getExtra(KEY) ?: "{}").optJSONArray("convs") ?: org.json.JSONArray()
        (0 until a.length()).map { i -> a.getJSONObject(i).let { Conv(it.optLong("id"), it.optString("title"), it.optLong("at"), it) } }
    }.getOrDefault(emptyList()).sortedByDescending { it.updatedAt }

    private fun write(convs: List<Conv>) {
        if (convs.isEmpty()) { Repo.putExtra(KEY, null); return }
        Repo.putExtra(KEY, org.json.JSONObject().put("convs", org.json.JSONArray().apply { convs.take(MAX_CONVS).forEach { put(it.json) } }).toString())
    }

    @Synchronized
    fun save(id: Long, entries: List<CoachEntry>, added: Map<String, Boolean>) {
        val msgs = org.json.JSONArray()
        entries.takeLast(MAX_MESSAGES).forEach { e ->
            msgs.put(org.json.JSONObject()
                .put("u", e.message.fromUser).put("t", e.message.text).put("p", e.message.image != null)
                .put("meals", org.json.JSONArray().apply { e.meals.forEach { m ->
                    put(org.json.JSONObject().put("d", m.date).put("s", m.slot.name).put("n", m.name).put("k", m.kcal).put("x", m.description)) } })
                .put("shop", org.json.JSONArray().apply { e.shopping.forEach { s ->
                    put(org.json.JSONObject().put("n", s.name).put("q", s.qty).put("r", s.aisle)) } })
                .put("fa", org.json.JSONArray().apply { e.fridgeAdd.forEach { f ->
                    put(org.json.JSONObject().put("n", f.name).put("q", f.qty).put("u", f.unit).put("r", f.aisle)) } })
                .put("fr", org.json.JSONArray().apply { e.fridgeRemove.forEach { (n, q) -> put(org.json.JSONObject().put("n", n).put("q", q)) } })
                .put("ps", org.json.JSONArray().apply { e.sessions.forEach { put(it.toJson()) } })
                .put("pr", org.json.JSONArray(e.removeDays)))
        }
        val title = entries.firstOrNull { it.message.fromUser }?.message?.text?.take(60) ?: t("Conversation")
        val json = org.json.JSONObject().put("id", id).put("title", title).put("at", System.currentTimeMillis())
            .put("msgs", msgs).put("added", org.json.JSONObject().apply { added.forEach { (k, v) -> if (v) put(k, true) } })
        write(listOf(Conv(id, title, System.currentTimeMillis(), json)) + list().filter { it.id != id })
    }

    fun entriesOf(c: Conv): Pair<List<CoachEntry>, Map<String, Boolean>> {
        val a = c.json.optJSONArray("msgs") ?: org.json.JSONArray()
        val list = (0 until a.length()).map { i ->
            val o = a.getJSONObject(i)
            val text = o.optString("t") + if (o.optBoolean("p")) "\n" + t("📷 (photo non conservée)") else ""
            val meals = o.optJSONArray("meals")?.let { m -> (0 until m.length()).map { j -> m.getJSONObject(j).let {
                CoachMeal(it.optString("d"), com.goodlife.app.data.MealSlot.entries.firstOrNull { s -> s.name == it.optString("s") } ?: com.goodlife.app.data.MealSlot.DEJEUNER,
                    it.optString("n"), it.optInt("k"), it.optString("x")) } } } ?: emptyList()
            val shop = o.optJSONArray("shop")?.let { m -> (0 until m.length()).map { j -> m.getJSONObject(j).let {
                com.goodlife.app.ai.ShopItem(it.optString("n"), it.optString("q"), it.optString("r")) } } } ?: emptyList()
            val fa = o.optJSONArray("fa")?.let { m -> (0 until m.length()).map { j -> m.getJSONObject(j).let {
                com.goodlife.app.data.FridgeItem(it.optString("n"), it.optDouble("q", 1.0), it.optString("u"), it.optString("r")) } } } ?: emptyList()
            val fr = o.optJSONArray("fr")?.let { m -> (0 until m.length()).map { j -> m.getJSONObject(j).let { it.optString("n") to it.optDouble("q", 0.0) } } } ?: emptyList()
            val ps = o.optJSONArray("ps")?.let { m -> (0 until m.length()).mapNotNull { j -> runCatching { com.goodlife.app.data.SportSession.fromJson(m.getJSONObject(j)) }.getOrNull() } } ?: emptyList()
            val pr = o.optJSONArray("pr")?.let { m -> (0 until m.length()).map { m.optInt(it) } } ?: emptyList()
            CoachEntry(ChatMessage(o.optBoolean("u"), text), meals, shop, fa, fr, ps, pr)
        }
        val added = c.json.optJSONObject("added")?.let { o -> o.keys().asSequence().associateWith { true } } ?: emptyMap()
        return list to added
    }

    @Synchronized fun delete(id: Long) = write(list().filter { it.id != id })
    @Synchronized fun clear() = Repo.putExtra(KEY, null)
}

/** Liste des conversations passées : toucher pour reprendre, poubelle pour supprimer. */
@Composable
private fun CoachHistoryScreen(onClose: () -> Unit, onOpen: (CoachHistory.Conv) -> Unit) {
    BackHandler(onBack = onClose)
    val settings by Repo.settings.collectAsState()
    var convs by remember { mutableStateOf(CoachHistory.list()) }
    var confirmClear by remember { mutableStateOf(false) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, t("Retour")) }
                    Text(t("Mes conversations"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(t("Garder l'historique"), style = MaterialTheme.typography.bodyLarge)
                        Text(t("Chiffré sur ton téléphone, jamais envoyé ailleurs, sans les photos. En reprenant une conversation, elle est renvoyée à Gemini pour que le chef se souvienne du contexte."),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    androidx.compose.material3.Switch(settings.coachHistory, { on -> Repo.updateSettings { it.copy(coachHistory = on) } })
                }
            }
            if (convs.isEmpty()) item {
                Text(t("Aucune conversation enregistrée pour l'instant."), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(convs.size) { i ->
                val c = convs[i]
                Surface(onClick = { onOpen(c) }, shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(c.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(com.goodlife.app.ui.formatDay(c.updatedAt) + " · " + com.goodlife.app.ui.formatTime(c.updatedAt) + " · " +
                                tp(c.json.optJSONArray("msgs")?.length() ?: 0, "%1\$s message", "%1\$s messages"),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { CoachHistory.delete(c.id); if (CoachSession.id == c.id) CoachSession.id = 0L; convs = CoachHistory.list() }) {
                            Icon(Icons.Filled.Delete, t("Supprimer"))
                        }
                    }
                }
            }
            if (convs.isNotEmpty()) item {
                TextButton(onClick = { confirmClear = true }) { Text(t("Tout effacer"), color = MaterialTheme.colorScheme.error) }
            }
        }
    }
    if (confirmClear) androidx.compose.material3.AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text(t("Effacer tout l'historique ?")) },
        text = { Text(t("Toutes les conversations enregistrées seront supprimées de ce téléphone.")) },
        confirmButton = { TextButton(onClick = { CoachHistory.clear(); CoachSession.id = 0L; convs = emptyList(); confirmClear = false }) { Text(t("Effacer")) } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(t("Annuler")) } }
    )
}

/** Le chef propose de mettre à jour « Mon frigo » : rien ne change avant l'appui sur le bouton. */
@Composable
private fun FridgeProposal(entry: Int, add: List<com.goodlife.app.data.FridgeItem>, remove: List<Pair<String, Double>>) {
    val added = CoachSession.added
    val key = "$entry#frigo"
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.widthIn(max = 360.dp).padding(top = 6.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(t("🧊 Mettre à jour ton frigo"), style = MaterialTheme.typography.titleSmall)
            if (add.isNotEmpty()) Text(t("Ajouter : %1\$s", add.joinToString(", ") { "${it.name} (${it.qtyText()})" }),
                style = MaterialTheme.typography.bodySmall, maxLines = 4, overflow = TextOverflow.Ellipsis)
            if (remove.isNotEmpty()) Text(t("Retirer : %1\$s", remove.joinToString(", ") { (n, q) -> "$n (${if (q % 1.0 == 0.0) q.toLong() else q})" }),
                style = MaterialTheme.typography.bodySmall, maxLines = 4, overflow = TextOverflow.Ellipsis)
            if (added[key] == true) Text(t("Frigo mis à jour ✓"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            else FilledTonalButton(onClick = {
                if (add.isNotEmpty()) com.goodlife.app.data.Fridge.add(add)
                if (remove.isNotEmpty()) com.goodlife.app.data.Fridge.remove(remove)
                added[key] = true; CoachSession.persist()
            }) { Text(t("Mettre à jour mon frigo")) }
        }
    }
}

/** Le chef propose de modifier le programme sportif : séances remplacées ou ajoutées, jours retirés. */
@Composable
private fun ProgramProposal(entry: Int, sessions: List<com.goodlife.app.data.SportSession>, removeDays: List<Int>) {
    val added = CoachSession.added
    val key = "$entry#programme"
    val days = listOf(t("Lundi"), t("Mardi"), t("Mercredi"), t("Jeudi"), t("Vendredi"), t("Samedi"), t("Dimanche"))
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.widthIn(max = 360.dp).padding(top = 6.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(t("🏋️ Modifier ton programme"), style = MaterialTheme.typography.titleSmall)
            sessions.forEach { s ->
                Text(t("%1\$s : %2\$s (%3\$s min)", days[s.day - 1], s.title, s.minutes), style = MaterialTheme.typography.labelLarge)
                Text(s.exercises.joinToString(", ") { "${it.name} ${it.detail}" }, style = MaterialTheme.typography.bodySmall,
                    maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            if (removeDays.isNotEmpty()) Text(t("Séances retirées : %1\$s", removeDays.joinToString(", ") { days[it - 1] }), style = MaterialTheme.typography.bodySmall)
            if (added[key] == true) Text(t("Programme mis à jour ✓"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            else FilledTonalButton(onClick = { Repo.applySessions(sessions, removeDays); added[key] = true; CoachSession.persist() }) {
                Text(t("Appliquer à mon programme"))
            }
        }
    }
}

/**
 * Pas encore de clé : le chef explique comment en créer une (gratuite, chez Google) et permet de l'enregistrer ici.
 * La personne accepte elle-même les conditions de Google en créant sa clé ; la clé reste chiffrée sur le téléphone.
 */
@Composable
private fun CoachKeySetup() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    val settings by Repo.settings.collectAsState()
    var key by remember { mutableStateOf("") }
    var consent by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(t("Activer le chef en 3 étapes"), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            if (!settings.aiEnabled) {
                Text(t("1. Autorise l'IA : tu choisis ce qui est envoyé à Google (seulement quand tu utilises une fonction IA)."), style = MaterialTheme.typography.bodySmall)
                FilledTonalButton(onClick = { consent = true }) { Text(t("Activer l'IA")) }
            } else {
                Text(t("1. IA activée ✓"), style = MaterialTheme.typography.bodySmall)
            }
            Text(t("2. Crée ta clé : connecte-toi avec ton compte Google, appuie sur « Create API key », puis copie la clé."), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { uri.openUri("https://aistudio.google.com/apikey") }) { Text(t("Créer ma clé chez Google")) }
            Text(t("3. Colle-la ici et enregistre :"), style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(
                key, { key = it.trim() }, singleLine = true,
                label = { Text(t("Clé API Gemini")) },
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                keyboardOptions = KEY_KEYBOARD,
                modifier = Modifier.fillMaxWidth()
            )
            Button(enabled = settings.aiEnabled && key.length >= 20, onClick = { saveApiKey(context, key); key = "" }) { Text(t("Enregistrer la clé")) }
            Text(
                t("Gratuit dans la limite offerte par Google ; en créant ta clé, tu acceptes ses conditions. Ta clé reste chiffrée sur ce téléphone et n'est envoyée qu'à Google."),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    if (consent) AiConsentDialog(onDismiss = { consent = false })
}
