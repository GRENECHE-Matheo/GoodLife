package com.goodlife.app.ui.screens

import com.goodlife.app.i18n.t

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goodlife.app.data.Repo
import com.goodlife.app.dex.DEX_NAME
import com.goodlife.app.dex.DexCategory
import com.goodlife.app.dex.DexEntry
import com.goodlife.app.dex.Nutridex
import com.goodlife.app.ui.Sfx
import com.goodlife.app.ui.Sounds
import com.goodlife.app.ui.formatDay
import kotlinx.coroutines.delay

/**
 * Vignette d'une entrée : ta photo si tu l'as débloquée, sinon l'émoji ; en silhouette tant que
 * l'aliment n'est pas trouvé (comme un Pokédex).
 */
@Composable
fun DexTile(entry: DexEntry, unlocked: Boolean, photo: ByteArray?, size: Dp, modifier: Modifier = Modifier) {
    val bmp = remember(photo) { photo?.let { runCatching { BitmapFactory.decodeByteArray(it, 0, it.size) }.getOrNull() } }
    val silhouette = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
    Box(
        modifier.size(size).clip(RoundedCornerShape(size / 5))
            .background(if (unlocked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (unlocked && bmp != null) {
            Image(bmp.asImageBitmap(), entry.name, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Text(
                entry.emoji,
                fontSize = (size.value * 0.48f).sp,
                modifier = if (unlocked) Modifier else Modifier
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        drawRect(silhouette, blendMode = BlendMode.SrcIn)
                    }
            )
        }
    }
}

/** Accès depuis le profil : « Nutridex 12 / 150 ». */
@Composable
fun NutridexCard(onOpen: () -> Unit) {
    val dex by Repo.dex.collectAsState()
    val total = Nutridex.ENTRIES.size
    val found = dex.unlocked.size
    Surface(onClick = onOpen, shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Filled.MenuBook, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(DEX_NAME, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                Text(
                    t("%1\$s / %2\$s aliments découverts", found, total),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { found.toFloat() / total },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    strokeCap = StrokeCap.Round
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
        }
    }
}

/**
 * Le Nutridex en grille. [unlockedIds] / [title] permettent d'afficher celui d'un ami
 * (sans ses photos, qui ne sont jamais partagées).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NutridexScreen(onBack: () -> Unit, title: String = DEX_NAME, unlockedIds: Set<String>? = null) {
    BackHandler(onBack = onBack)
    val dex by Repo.dex.collectAsState()
    val mine = unlockedIds == null
    val unlocked = unlockedIds ?: dex.unlocked.keys
    var category by rememberSaveable { mutableStateOf<DexCategory?>(null) }
    var open by remember { mutableStateOf<DexEntry?>(null) }
    val entries = Nutridex.ENTRIES.filter { category == null || it.category == category }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(96.dp),
                modifier = Modifier.widthIn(max = 640.dp).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, t("Retour")) }
                            Spacer(Modifier.width(4.dp))
                            Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                            Text(
                                "${unlocked.size} / ${Nutridex.ENTRIES.size}",
                                style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (mine) Text(
                            t("Photographie tes repas (Scanner › Photo) : chaque aliment reconnu se débloque avec ta photo. Varie ton assiette pour tout découvrir !"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(category == null, { category = null }, label = { Text(t("Tous")) })
                            DexCategory.entries.forEach { c ->
                                val n = Nutridex.ENTRIES.count { it.category == c }
                                val got = Nutridex.ENTRIES.count { it.category == c && it.id in unlocked }
                                FilterChip(category == c, { category = c }, label = { Text("${c.label} $got/$n") })
                            }
                        }
                    }
                }
                items(entries, key = { it.id }) { e ->
                    val isUnlocked = e.id in unlocked
                    val photo = remember(e.id, isUnlocked, mine) { if (mine && isUnlocked) Repo.dexPhoto(e.id) else null }
                    Column(
                        Modifier.clip(RoundedCornerShape(16.dp)).clickable { open = e }.padding(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        DexTile(e, isUnlocked, photo, 88.dp)
                        Text(
                            "#%03d".format(e.number),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            e.name, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                            fontWeight = if (isUnlocked) FontWeight.Medium else FontWeight.Normal,
                            color = if (isUnlocked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }
    }

    open?.let { e ->
        val isUnlocked = e.id in unlocked
        AlertDialog(
            onDismissRequest = { open = null },
            title = { Text("#%03d · %s".format(e.number, e.name)) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    DexTile(e, isUnlocked, if (mine && isUnlocked) Repo.dexPhoto(e.id) else null, 200.dp)
                    Text(e.category.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(
                        when {
                            isUnlocked && mine -> t("Découvert le %1\$s.", formatDay(dex.unlocked[e.id] ?: 0L))
                            isUnlocked -> t("Déjà découvert.")
                            mine -> t("Pas encore découvert. Mets-en dans ton assiette et prends-la en photo !")
                            else -> t("Pas encore découvert.")
                        },
                        style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center
                    )
                }
            },
            confirmButton = { TextButton(onClick = { open = null }) { Text(t("Fermer")) } }
        )
    }
}

/** Bandeau « Nouveau dans le Nutridex ! » après une analyse photo, avec petit son et rebond. */
@Composable
fun DexUnlockedBanner(ids: List<String>, onOpen: () -> Unit) {
    val entries = ids.mapNotNull { Nutridex.byId(it) }
    if (entries.isEmpty()) return
    val pop = remember { Animatable(0.4f) }
    LaunchedEffect(ids) {
        delay(150)
        Sounds.play(Sfx.LEVEL_UP)
        pop.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 320f))
    }
    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier.fillMaxWidth().graphicsLayer { scaleX = pop.value; scaleY = pop.value }
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                if (entries.size == 1) t("Nouveau dans le %1\$s !", DEX_NAME) else t("%1\$s nouveautés dans le %2\$s !", entries.size, DEX_NAME),
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                entries.forEach { e ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        DexTile(e, true, Repo.dexPhoto(e.id), 64.dp)
                        Text(e.name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                }
            }
        }
    }
}
