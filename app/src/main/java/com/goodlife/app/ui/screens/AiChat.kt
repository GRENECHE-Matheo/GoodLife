package com.goodlife.app.ui.screens

import com.goodlife.app.i18n.t

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.goodlife.app.ai.ChatMessage
import com.goodlife.app.ai.Gemini
import com.goodlife.app.data.Repo
import com.goodlife.app.ui.AiContentFooter
import com.goodlife.app.ui.ChefMascot
import com.goodlife.app.ui.ChefMood
import kotlinx.coroutines.launch

/** Règles communes à toutes les conversations (sécurité et sujet). */
internal const val CHAT_RULES = """
Tu es le coach de l'application Lifoody (bien-être, alimentation, sport). Réponds simplement, dans la langue de l'app,
en 2 à 6 phrases, avec des conseils concrets. Tu n'es pas un professionnel de santé : ne donne jamais de diagnostic,
de traitement ni de dose de médicament ; pour toute question médicale, grossesse, maladie ou trouble du comportement
alimentaire, conseille de consulter un médecin. Ne propose jamais de régime très restrictif. Reste sur le sujet
(le repas, la recette, la nutrition, la cuisine, le sport) et refuse poliment le reste.
"""

private const val MAX_TURNS = 20

/**
 * Conversation avec l'IA à propos d'un contexte précis (une photo analysée, une recette…).
 * Rien n'est conservé : la conversation est effacée à la fermeture.
 */
@Composable
fun AiChatDialog(
    title: String,
    context: String,
    image: ByteArray? = null,
    suggestions: List<String>,
    onDismiss: () -> Unit,
    webSearch: Boolean = false
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            AiChatContent(title, context, image, suggestions, onDismiss, webSearch)
        }
    }
}

/** Suggestions de recherche Google, affichées telles quelles avec la réponse (règles de la recherche Google de Gemini). */
@Composable
private fun SearchSuggestions(html: String) {
    androidx.compose.ui.viewinterop.AndroidView(
        factory = { ctx ->
            android.webkit.WebView(ctx).apply {
                settings.javaScriptEnabled = false
                // Aucun accès aux fichiers du téléphone ni aux contenus des autres apps (anciennes versions d'Android : activé par défaut)
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                webViewClient = object : android.webkit.WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: android.webkit.WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                        // Seuls les liens web s'ouvrent, dans le navigateur (jamais intent:, file:, content:…)
                        request?.url?.takeIf { it.scheme == "https" || it.scheme == "http" }?.let { u ->
                            runCatching { ctx.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, u).addCategory(android.content.Intent.CATEGORY_BROWSABLE).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        }
                        return true
                    }
                }
                loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
            }
        },
        modifier = Modifier.fillMaxWidth().height(72.dp)
    )
}

@Composable
private fun AiChatContent(title: String, context: String, image: ByteArray?, suggestions: List<String>, onClose: () -> Unit, webSearch: Boolean) {
    // Sources et suggestions Google de chaque réponse (seulement en mémoire, effacées à la fermeture)
    val grounded = remember { androidx.compose.runtime.mutableStateMapOf<Int, Gemini.Grounded>() }
    val uri = androidx.compose.ui.platform.LocalUriHandler.current
    val settings by Repo.settings.collectAsState()
    val profile by Repo.profile.collectAsState()
    val scope = rememberCoroutineScope()
    val messages = remember { mutableStateListOf<ChatMessage>() }
    var input by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val list = rememberLazyListState()

    val system = CHAT_RULES + t("\nContexte :\n") + context +
        t("\nAllergies de l'utilisateur : ") + (profile?.allergies?.ifBlank { null } ?: t("aucune connue")) +
        t("\nHabitudes : ") + (profile?.habits?.ifBlank { null } ?: t("non précisées"))

    fun send(text: String) {
        val q = text.trim().take(500)
        if (q.isEmpty() || loading) return
        if (messages.count { it.fromUser } >= MAX_TURNS) {
            error = t("Tu as posé beaucoup de questions : ferme et rouvre la conversation pour recommencer.")
            return
        }
        messages.add(ChatMessage(true, q))
        input = ""; error = null; loading = true
        scope.launch {
            try {
                if (webSearch) {
                    val g = Gemini(settings.apiKey, settings.model).chatWithSearch(system, messages.toList())
                    messages.add(ChatMessage(false, g.text))
                    grounded[messages.lastIndex] = g
                } else messages.add(ChatMessage(false, Gemini(settings.apiKey, settings.model).chat(system, image, messages.toList())))
            } catch (e: Exception) {
                messages.removeAt(messages.lastIndex)  // on retire la question pour pouvoir la reposer
                input = q
                error = e.message
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(messages.size, loading) {
        val count = messages.size + (if (loading) 1 else 0)
        if (count > 0) list.animateScrollToItem(count)
    }

    Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 640.dp).fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, t("Fermer")) }
                Column(Modifier.weight(1f)) {
                    Text(t("Demander à l'IA"), style = MaterialTheme.typography.titleMedium)
                    Text(title, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            LazyColumn(
                state = list,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Row(verticalAlignment = Alignment.Top) {
                        ChefMascot(size = 48.dp, mood = ChefMood.QUESTION)
                        Spacer(Modifier.width(8.dp))
                        Bubble(false, t("Pose-moi tes questions sur « %1\$s ». Je réponds en tenant compte de tes allergies et habitudes.", title))
                    }
                }
                itemsIndexed(messages) { mi, m ->
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (m.fromUser) Alignment.End else Alignment.Start) {
                        Bubble(m.fromUser, m.text)
                        if (!m.fromUser) grounded[mi]?.let { g ->
                            if (g.sources.isNotEmpty()) Column(Modifier.widthIn(max = 340.dp).padding(top = 4.dp)) {
                                Text(t("Sources consultées :"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                g.sources.forEach { (t, u) ->
                                    Text("• $t", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.clickable { runCatching { uri.openUri(u) } }.padding(vertical = 2.dp))
                                }
                            }
                            g.suggestionsHtml?.let { Box(Modifier.widthIn(max = 340.dp)) { SearchSuggestions(it) } }
                            if (!g.searched) Text((if (com.goodlife.app.ai.AiAccess.viaRelay) t("(Réponse sans recherche internet.)") else t("(Réponse sans recherche internet : ton modèle ou ta clé ne la permettent pas.)")),
                                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (!m.fromUser) AiContentFooter(t("Question sur « %1\$s »\n%2\$s", title, m.text), Modifier.widthIn(max = 340.dp))
                    }
                }
                if (loading) item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(t("L'IA réfléchit…"), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (error != null) Text(
                error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            if (messages.isEmpty()) Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                suggestions.forEach { s -> AssistChip(onClick = { send(s) }, label = { Text(s) }) }
            }
            Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    input, { input = it.take(500) },
                    placeholder = { Text(t("Ta question…")) },
                    modifier = Modifier.weight(1f),
                    maxLines = 4,
                    shape = RoundedCornerShape(24.dp)
                )
                Spacer(Modifier.width(8.dp))
                FilledIconButton(enabled = input.isNotBlank() && !loading, onClick = { send(input) }) {
                    Icon(Icons.AutoMirrored.Filled.Send, t("Envoyer"))
                }
            }
            Text(
                if (com.goodlife.app.ai.AiAccess.viaRelay) t("Les questions et le contexte sont envoyés à Google Gemini via le serveur de Lifoody, qui n'enregistre rien. Rien n'est gardé après fermeture.") else t("Les questions et le contexte sont envoyés à Google Gemini avec ta clé. Rien n'est gardé après fermeture."),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
            )
        }
    }
}

@Composable
internal fun Bubble(fromUser: Boolean, text: String) {
    Surface(
        shape = RoundedCornerShape(
            topStart = if (fromUser) 20.dp else 4.dp, topEnd = if (fromUser) 4.dp else 20.dp,
            bottomStart = 20.dp, bottomEnd = 20.dp
        ),
        color = if (fromUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.widthIn(max = 320.dp)
    ) {
        Text(
            text,
            color = if (fromUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        )
    }
}
