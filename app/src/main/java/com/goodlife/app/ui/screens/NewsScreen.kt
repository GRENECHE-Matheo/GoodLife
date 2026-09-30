package com.goodlife.app.ui.screens

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
    var feed by remember(today) { mutableStateOf(NewsFeed.cached()) }
    // Une fois par jour, les actus sont préparées à l'ouverture de l'accueil
    LaunchedEffect(today) { if (feed == null) feed = runCatching { NewsFeed.today() }.getOrNull() }
    val headline = feed?.let { f -> f.firstOrNull { it.kind == FeedItem.INSOLITE } ?: f.firstOrNull() }
    val unread = Repo.settings.collectAsState().value.lastNewsDay != today
    Surface(onClick = onOpen, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Newspaper, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (headline?.kind == FeedItem.INSOLITE) "Actu insolite du jour" else "Actus du jour",
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    headline?.title ?: "Alimentation, sport et insolite : les actus t'attendent",
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

    var feed by remember(today) { mutableStateOf(NewsFeed.cached()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    var askAbout by remember { mutableStateOf<FeedItem?>(null) }
    val uri = LocalUriHandler.current
    val aiReady = Repo.aiAllowed() && Repo.settings.collectAsState().value.apiKey.isNotBlank()

    LaunchedEffect(today, retry) {
        if (feed != null) return@LaunchedEffect
        loading = true; error = null
        try { feed = NewsFeed.today() } catch (e: Exception) { error = e.message } finally { loading = false }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        ScreenColumn {
            SubScreenHeader("Actus du jour", onBack)

            // ---- Vraies actus (flux RSS publics, sans clé) ----
            val odd = feed?.firstOrNull { it.kind == FeedItem.INSOLITE }
            if (odd != null) {
                Text("Actu insolite", style = MaterialTheme.typography.titleMedium)
                FeedCard(odd, highlight = true, onOpen = { runCatching { uri.openUri(odd.url) } }, onAsk = if (aiReady) ({ askAbout = odd }) else null)
            }
            Text("À la une", style = MaterialTheme.typography.titleMedium)
            when {
                loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Chargement des actus…", style = MaterialTheme.typography.bodyMedium)
                }
                error != null -> Column {
                    Text(error!!, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = { retry++ }) {
                        Icon(Icons.Filled.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Réessayer")
                    }
                }
                feed.isNullOrEmpty() -> Text(
                    "Pas de nouvelle actu sur l'alimentation ou le sport aujourd'hui. Reviens demain !",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                else -> feed!!.filter { it.kind != FeedItem.INSOLITE }.forEach { item ->
                    FeedCard(item, onOpen = { runCatching { uri.openUri(item.url) } }, onAsk = if (aiReady) ({ askAbout = item }) else null)
                }
            }

            // ---- Secours sans internet : anecdotes vérifiées de la banque intégrée (jamais deux fois la même) ----
            if (!loading && (error != null || feed.isNullOrEmpty())) OfflineAnecdotes(today)
            Text(
                "Actus trouvées chaque jour dans les flux publics de franceinfo, Sciences et Avenir, Futura, Anses, Inserm " +
                    "et Santé publique France (alimentation et sport uniquement). Seuls le titre et un court extrait sont " +
                    "affichés : l'article complet s'ouvre chez la source. Contenu d'information, pas un avis médical.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    askAbout?.let { item ->
        AiChatDialog(
            title = item.title,
            context = "Actualité publiée par ${item.source} : « ${item.title} ». Extrait : ${item.summary}\n" +
                "Tu ne connais que ce titre et cet extrait : ne prétends pas avoir lu l'article, et invite à le lire pour les détails.",
            suggestions = listOf("Explique-moi simplement", "Qu'est-ce que ça change pour moi ?", "Est-ce que c'est fiable ?"),
            onDismiss = { askAbout = null }
        )
    }
}

/** Sans internet : une anecdote insolite et une découverte vérifiées, jamais déjà lues. */
@Composable
private fun OfflineAnecdotes(today: String) {
    val news = remember(today) { NewsBank.today() }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Hors ligne : le saviez-vous ?", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
            SectionCard(container = MaterialTheme.colorScheme.secondaryContainer) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ChefMascot(size = 64.dp, mood = ChefMood.BRAVO)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Insolite", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        Text(news.insolite.title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
                Text(news.insolite.text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            news.discoveries.forEach { DiscoveryCard(it) }
            Text(
                "Anecdotes vérifiées intégrées à l'app (Anses, Programme national nutrition santé, OMS, faits établis), " +
                    "affichées seulement quand les actus ne peuvent pas être chargées.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
    }
}

@Composable
private fun FeedCard(item: FeedItem, onOpen: () -> Unit, onAsk: (() -> Unit)?, highlight: Boolean = false) {
    Surface(
        onClick = onOpen, shape = RoundedCornerShape(20.dp),
        color = if (highlight) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "${item.source} · ${SimpleDateFormat("d MMM", Locale.FRANCE).format(java.util.Date(item.time))}",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary
            )
            Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            if (item.summary.isNotBlank()) Text(
                item.summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(6.dp))
                Text("Lire l'article", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f))
                if (onAsk != null) TextButton(onClick = onAsk) { Text("Demander au chef") }
            }
        }
    }
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
