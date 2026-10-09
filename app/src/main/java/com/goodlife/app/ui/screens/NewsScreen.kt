@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.goodlife.app.ui.screens

import com.goodlife.app.i18n.t

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.goodlife.app.data.Repo
import com.goodlife.app.data.localDay
import com.goodlife.app.news.Anecdote
import com.goodlife.app.news.FeedItem
import com.goodlife.app.news.NewsBank
import com.goodlife.app.news.NewsFeed
import com.goodlife.app.ui.ChefMascot
import com.goodlife.app.ui.ChefMood
import com.goodlife.app.ui.ScreenColumn
import com.goodlife.app.ui.SectionCard
import com.goodlife.app.ui.SubScreenHeader
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Locale

/** Jour courant (AAAA-MM-JJ) qui change tout seul à minuit, même si l'app reste ouverte. */
@Composable
fun rememberToday(): String {
    var day by remember { mutableStateOf(localDay(0)) }
    LaunchedEffect(day) {
        val end = Repo.dayBounds(0).second
        delay((end - System.currentTimeMillis()).coerceAtLeast(0L) + 1_000L)
        day = localDay(0)
    }
    return day
}

/** Petite carte de l'accueil : les vraies actus du jour, avec un point tant qu'elles n'ont pas été lues. */
@Composable
fun NewsTeaser(onOpen: () -> Unit) {
    val today = rememberToday()
    val themes = Repo.settings.collectAsState().value.newsThemes
    if (themes.isBlank()) return   // aucun thème choisi : pas d'actus
    var feed by remember(today, themes) { mutableStateOf(NewsFeed.cached()) }
    // Une fois par jour, les actus sont préparées à l'ouverture de l'accueil
    LaunchedEffect(today, themes) { if (feed == null) feed = runCatching { NewsFeed.today() }.getOrNull() }
    val anecdote = if ("anecdote" in themes) remember(today) { NewsBank.today().insolite } else null
    val headline = feed?.let { f -> f.firstOrNull { it.kind == FeedItem.INSOLITE } ?: f.firstOrNull() }
    val unread = Repo.settings.collectAsState().value.lastNewsDay != today
    Surface(onClick = onOpen, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Newspaper, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        headline?.kind == FeedItem.INSOLITE -> t("Actu insolite du jour")
                        headline != null -> t("Actus du jour")
                        anecdote != null -> t("Le saviez-vous ?")
                        else -> t("Actus du jour")
                    },
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    headline?.title ?: anecdote?.title ?: t("Les actus du jour t'attendent"),
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            if (unread) Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

@Composable
fun NewsScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val today = rememberToday()
    LaunchedEffect(today) { Repo.updateSettings { it.copy(lastNewsDay = today) } }

    val themes = Repo.settings.collectAsState().value.newsThemes
    val chosen = themes.split(",").filter { it.isNotBlank() }.toSet()
    var feed by remember(today, themes) { mutableStateOf(NewsFeed.cached()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    var askAbout by remember { mutableStateOf<FeedItem?>(null) }
    var summarize by remember { mutableStateOf<FeedItem?>(null) }
    val uri = LocalUriHandler.current
    val aiReady = Repo.aiAllowed() && com.goodlife.app.ai.AiAccess.ready(Repo.settings.collectAsState().value)

    LaunchedEffect(today, retry, themes) {
        if (feed != null) return@LaunchedEffect
        loading = true; error = null
        try { feed = NewsFeed.today() } catch (e: Exception) { error = e.message } finally { loading = false }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        ScreenColumn {
            SubScreenHeader(t("Actus du jour"), onBack)
            // ---- Mes thèmes (0, 1 ou plusieurs) ----
            Text(t("Mes thèmes"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NEWS_THEMES.forEach { (id, label) ->
                    androidx.compose.material3.FilterChip(
                        selected = id in chosen,
                        onClick = {
                            val next = if (id in chosen) chosen - id else chosen + id
                            Repo.updateSettings { it.copy(newsThemes = NEWS_THEMES.map { t -> t.first }.filter { t -> t in next }.joinToString(",")) }
                        },
                        label = { Text(label) }
                    )
                }
            }
            if (chosen.isEmpty()) Text(
                t("Aucun thème choisi : les actus sont désactivées et n'apparaissent plus sur l'accueil. Choisis un thème pour les retrouver."),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if ("anecdote" in chosen) DailyAnecdote(today)

            // ---- Vraies actus (flux RSS publics, sans clé) ----
            val odd = feed?.firstOrNull { it.kind == FeedItem.INSOLITE }
            if (odd != null) {
                Text(t("Actu insolite"), style = MaterialTheme.typography.titleMedium)
                FeedCard(odd, highlight = true, onOpen = { runCatching { uri.openUri(odd.url) } },
                    onAsk = if (aiReady) ({ askAbout = odd }) else null,
                    onSummary = if (aiReady && odd.public) ({ summarize = odd }) else null)
            }
            val wantsNews = chosen.any { it in setOf("food", "sport", "health", "insolite") }
            if (wantsNews) Text(t("À la une"), style = MaterialTheme.typography.titleMedium)
            if (wantsNews) when {
                loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(t("Chargement des actus…"), style = MaterialTheme.typography.bodyMedium)
                }
                error != null -> Column {
                    Text(error!!, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { retry++ }) {
                        Icon(Icons.Filled.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(t("Réessayer"))
                    }
                }
                feed.isNullOrEmpty() -> Text(
                    t("Pas de nouvelle actu sur tes thèmes aujourd'hui. Reviens demain !"),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                else -> feed!!.filter { it.kind != FeedItem.INSOLITE }.forEach { item ->
                    FeedCard(item, onOpen = { runCatching { uri.openUri(item.url) } },
                        onAsk = if (aiReady && !item.public) ({ askAbout = item }) else null,
                        onSummary = if (aiReady && item.public) ({ summarize = item }) else null)
                }
            }

            // ---- Secours sans internet : anecdotes vérifiées de la banque intégrée (jamais deux fois la même) ----
            if (wantsNews && "anecdote" !in chosen && !loading && error != null) OfflineAnecdotes(today)
            Text(
                t("Actus trouvées chaque jour dans les flux publics de franceinfo, Sciences et Avenir, Futura, de l'Anses et de Santé publique France, selon tes thèmes. Seuls le titre (et, pour les organismes publics, un court extrait) sont affichés : l'article complet s'ouvre chez la source. Les articles appartiennent à leurs éditeurs. Contenu d'information, pas un avis médical."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    summarize?.let { item -> ArticleSummaryDialog(item, onOpen = { runCatching { uri.openUri(item.url) } }, onDismiss = { summarize = null }) }
    askAbout?.let { item ->
        AiChatDialog(
            title = item.title,
            context = t("Actualité publiée par %1\$s : « %2\$s ».", item.source, item.title) +
                (if (item.summary.isNotBlank()) t(" Extrait : %1\$s", item.summary) else "") + t(" Lien : %1\$s\nUtilise la recherche Google pour retrouver cet article et d'autres sources fiables, afin de savoir précisément de quoi il parle. Explique avec tes propres mots (sans recopier l'article), distingue bien les faits des avis, et invite à lire l'article complet chez la source.", item.url),
            suggestions = listOf(t("De quoi parle cet article ?"), t("Qu'est-ce que ça change pour moi ?"), t("Est-ce que c'est fiable ?")),
            onDismiss = { askAbout = null },
            webSearch = true
        )
    }
}

/** Thèmes d'actus proposés (l'anecdote vient de la banque intégrée, vérifiée). */
val NEWS_THEMES = listOf(
    "food" to t("🥗 Alimentation"), "sport" to t("🏃 Sport"), "health" to t("🧘 Santé et bien-être"),
    "insolite" to t("😮 Insolite"), "anecdote" to t("💡 Anecdote du jour")
)

/** L'anecdote du jour : un fait vrai et vérifié sur la nourriture, jamais deux fois le même. */
@Composable
private fun DailyAnecdote(today: String) {
    val news = remember(today) { NewsBank.today() }
    SectionCard(container = MaterialTheme.colorScheme.secondaryContainer) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ChefMascot(size = 56.dp, mood = ChefMood.CLIN)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(t("Le saviez-vous ?"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
                Text(news.insolite.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
        Text(news.insolite.text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

/** Sans internet : une anecdote insolite et une découverte vérifiées, jamais déjà lues. */
@Composable
private fun OfflineAnecdotes(today: String) {
    val news = remember(today) { NewsBank.today() }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(t("Hors ligne : le saviez-vous ?"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
            SectionCard(container = MaterialTheme.colorScheme.secondaryContainer) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ChefMascot(size = 64.dp, mood = ChefMood.BRAVO)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(t("Insolite"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        Text(news.insolite.title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
                Text(news.insolite.text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            news.discoveries.forEach { DiscoveryCard(it) }
            Text(
                t("Anecdotes vérifiées intégrées à l'app (Anses, Programme national nutrition santé, OMS, faits établis), affichées seulement quand les actus ne peuvent pas être chargées."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
    }
}

@Composable
private fun FeedCard(
    item: FeedItem, onOpen: () -> Unit, onAsk: (() -> Unit)?, highlight: Boolean = false, onSummary: (() -> Unit)? = null
) {
    Surface(
        onClick = onOpen, shape = RoundedCornerShape(20.dp),
        color = if (highlight) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                if (item.time == NewsFeed.NO_DATE) item.source
                else "${item.source} · ${SimpleDateFormat("d MMM", com.goodlife.app.i18n.Lang.locale).format(java.util.Date(item.time))}",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary
            )
            Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            if (item.summary.isNotBlank()) Text(
                item.summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
                Text(t("Lire l'article"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f))
                if (onSummary != null) TextButton(onClick = onSummary) { Text(t("Résumé du chef")) }
                else if (onAsk != null) TextButton(onClick = onAsk) { Text(t("Demander au chef")) }
            }
        }
    }
}

/**
 * Résumé IA d'un article d'organisme public (réutilisation d'informations publiques : source et date citées,
 * sens respecté, l'article reste la référence). Rien n'est gardé.
 */
@Composable
private fun ArticleSummaryDialog(item: FeedItem, onOpen: () -> Unit, onDismiss: () -> Unit) {
    val settings = Repo.settings.collectAsState().value
    var points by remember { mutableStateOf<List<String>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(item.url) {
        try {
            val text = NewsFeed.articleText(item.url)
            points = com.goodlife.app.ai.Gemini(settings.apiKey, settings.model).summarizeArticle(item.source, item.title, text)
        } catch (e: Exception) {
            error = e.message
        }
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("Résumé du chef")) },
        text = {
            Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(item.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                when {
                    error != null -> Text(error!!, color = MaterialTheme.colorScheme.error)
                    points == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(t("Le chef lit l'article…"))
                    }
                    else -> {
                        points!!.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                        Text(
                            t("Résumé généré par l'IA à partir de l'article de %1\$s", item.source) +
                                (if (item.time != NewsFeed.NO_DATE) t(" du %1\$s", SimpleDateFormat("d MMMM yyyy", com.goodlife.app.i18n.Lang.locale).format(java.util.Date(item.time))) else "") +
                                t(". Il peut contenir des erreurs : l'article original fait foi."),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        com.goodlife.app.ui.AiContentFooter(t("Résumé d'article (%1\$s) : %2\$s\n", item.source, item.title) + points!!.joinToString("\n"))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onOpen) { Text(t("Lire l'article")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Fermer")) } }
    )
}

@Composable
private fun DiscoveryCard(a: Anecdote) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Lightbulb, null, tint = MaterialTheme.colorScheme.tertiary)
            Spacer(Modifier.width(10.dp))
            Text(a.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        }
        Text(a.text, style = MaterialTheme.typography.bodyMedium)
    }
}
