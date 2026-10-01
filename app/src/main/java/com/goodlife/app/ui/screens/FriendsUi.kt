@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.goodlife.app.ui.screens

import android.graphics.Bitmap
import android.graphics.Color as AColor
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Contactless
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.goodlife.app.data.Person
import com.goodlife.app.data.Repo
import com.goodlife.app.dex.DEX_NAME
import com.goodlife.app.dex.Nutridex
import com.goodlife.app.game.Game
import com.goodlife.app.game.Weekly
import com.goodlife.app.security.findFragmentActivity
import com.goodlife.app.social.CHEERS
import com.goodlife.app.social.Identity
import com.goodlife.app.social.Social
import com.goodlife.app.social.StreetPass
import com.goodlife.app.social.SyncEvent
import com.goodlife.app.social.TapSync
import com.goodlife.app.ui.ScreenColumn
import com.goodlife.app.ui.SectionCard
import com.goodlife.app.ui.Sfx
import com.goodlife.app.ui.Sounds
import com.goodlife.app.ui.SubScreenHeader
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.random.Random

private fun ago(ms: Long): String {
    val m = (System.currentTimeMillis() - ms) / 60_000
    return when {
        m < 1 -> "à l'instant"
        m < 60 -> "il y a $m min"
        m < 24 * 60 -> "il y a ${m / 60} h"
        else -> "il y a ${m / (24 * 60)} j"
    }
}

private fun resultText(e: SyncEvent): String = when (e.result) {
    Repo.Received.NEW_FRIEND -> "${e.pseudo} est maintenant ton ami !"
    Repo.Received.UPDATED -> "Carte de ${e.pseudo} mise à jour."
    Repo.Received.NEW_ENCOUNTER -> "Nouvelle rencontre : ${e.pseudo}"
    Repo.Received.SEEN_AGAIN -> "Tu as recroisé ${e.pseudo}."
    Repo.Received.IGNORED -> "Carte ignorée."
}

/** Carte d'accès depuis le profil. */
@Composable
fun FriendsCard(onOpen: () -> Unit) {
    val social by Repo.social.collectAsState()
    val friends = social.people.count { it.friend }
    val unread = social.cheersIn.count { !it.seen }
    Surface(onClick = onOpen, shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Group, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Amis", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                Text(
                    buildString {
                        append(if (friends == 0) "Ajoute des amis en collant vos téléphones" else "$friends ami${if (friends > 1) "s" else ""}")
                        if (unread > 0) append(" · $unread encouragement${if (unread > 1) "s" else ""}")
                    },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            if (unread > 0) Box(Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

@Composable
fun FriendsScreen(onBack: () -> Unit) {
    var screen by rememberSaveable { mutableStateOf("") }
    BackHandler { if (screen.isEmpty()) onBack() else screen = "" }
    when {
        screen == "tap" -> TapSyncScreen(onBack = { screen = "" })
        screen == "qr" -> QrScreen(onBack = { screen = "" })
        screen.startsWith("dex:") -> {
            val p = Repo.social.collectAsState().value.people.firstOrNull { it.id == screen.removePrefix("dex:") }
            if (p == null) LaunchedEffect(Unit) { screen = "" } else NutridexScreen(
                onBack = { screen = "friend:${p.id}" }, title = "$DEX_NAME de ${p.pseudo}", unlockedIds = p.dex ?: emptySet()
            )
        }
        screen.startsWith("friend:") -> FriendProfile(
            id = screen.removePrefix("friend:"),
            onBack = { screen = "" },
            onDex = { screen = "dex:$it" }
        )
        else -> FriendsHome(onBack, onTap = { screen = "tap" }, onQr = { screen = "qr" }, onPerson = { screen = "friend:$it" })
    }
}

@Composable
private fun FriendsHome(onBack: () -> Unit, onTap: () -> Unit, onQr: () -> Unit, onPerson: (String) -> Unit) {
    val settings by Repo.settings.collectAsState()
    val social by Repo.social.collectAsState()
    val context = LocalContext.current
    var pseudo by remember(settings.pseudo) { mutableStateOf(settings.pseudo) }
    var ranking by rememberSaveable { mutableStateOf("level") }
    var message by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    var pasteOpen by remember { mutableStateOf(false) }
    if (pasteOpen) PasteCodeDialog(onDismiss = { pasteOpen = false }) { result -> info = result; pasteOpen = false }
    val unreadCheers = remember { social.cheersIn.filter { !it.seen } }
    LaunchedEffect(Unit) { if (social.cheersIn.any { !it.seen }) Repo.markCheersSeen() }

    val askBt = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res.values.all { it }) {
            Repo.updateSettings { it.copy(streetPass = true) }
            StreetPass.sync(context)
        } else message = "StreetPass a besoin de l'autorisation « Appareils à proximité »."
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        ScreenColumn {
            SubScreenHeader("Amis", onBack)

            // ---- Mon profil public ----
            SectionCard(title = "Mon profil", icon = Icons.Filled.PhoneAndroid) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (settings.publicProfile) "Profil public" else "Profil privé", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (settings.publicProfile) "Tes amis reçoivent seulement ce que tu coches ci-dessous, quand vous vous synchronisez."
                            else "Personne ne reçoit rien. Tu peux quand même recevoir la carte des autres.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(settings.publicProfile, { on ->
                        Repo.updateSettings { it.copy(publicProfile = on, streetPass = if (on) it.streetPass else false) }
                        StreetPass.sync(context)
                    })
                }
                if (settings.publicProfile) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            pseudo, { pseudo = it.take(Identity.MAX_PSEUDO) },
                            label = { Text("Pseudo") }, singleLine = true, modifier = Modifier.weight(1f),
                            supportingText = { Text("Évite ton nom complet.") }
                        )
                        TextButton(
                            enabled = Identity.cleanPseudo(pseudo).length >= 2 && Identity.cleanPseudo(pseudo) != settings.pseudo,
                            onClick = { Repo.updateSettings { it.copy(pseudo = Identity.cleanPseudo(pseudo)) }; StreetPass.sync(context) }
                        ) { Text("OK") }
                    }
                    Text("Je partage :", style = MaterialTheme.typography.labelLarge)
                    ShareBox("Mon niveau", settings.shareLevel) { v -> Repo.updateSettings { it.copy(shareLevel = v) } }
                    ShareBox("Ma série", settings.shareStreak) { v -> Repo.updateSettings { it.copy(shareStreak = v) } }
                    ShareBox("Mon $DEX_NAME (sans mes photos)", settings.shareDex) { v -> Repo.updateSettings { it.copy(shareDex = v) } }
                    ShareBox("Mon défi de la semaine (jours validés, pas, XP)", settings.shareWeek) { v -> Repo.updateSettings { it.copy(shareWeek = v) } }
                    Text(
                        "Jamais partagé : ton poids, tes repas, ton sommeil, tes photos.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ---- Encouragements reçus ----
            if (unreadCheers.isNotEmpty()) SectionCard(title = "Encouragements", icon = Icons.Filled.Favorite,
                container = MaterialTheme.colorScheme.tertiaryContainer) {
                unreadCheers.forEach { c ->
                    val from = social.people.firstOrNull { it.id == c.from }?.pseudo ?: "Un ami"
                    Text("$from : ${CHEERS.getOrElse(c.message) { "" }}", style = MaterialTheme.typography.bodyLarge)
                }
            }

            // ---- Ajouter ----
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onTap, modifier = Modifier.weight(1f).height(56.dp)) {
                    Icon(Icons.Filled.Contactless, null); Spacer(Modifier.width(8.dp)); Text("Tap to Sync")
                }
                FilledTonalButton(onClick = onQr, modifier = Modifier.weight(1f).height(56.dp)) {
                    Icon(Icons.Filled.QrCode2, null); Spacer(Modifier.width(8.dp)); Text("QR code")
                }
            }
            // ---- À distance : la carte part par message, toujours sans serveur ----
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = {
                    val text = Social.shareText()
                    if (text == null) message = "Active « Profil public » et choisis un pseudo pour partager ta carte."
                    else runCatching {
                        context.startActivity(android.content.Intent.createChooser(
                            android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
                                .putExtra(android.content.Intent.EXTRA_TEXT, text), "Partager ma carte"))
                    }
                }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Share, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Partager ma carte")
                }
                OutlinedButton(onClick = { pasteOpen = true }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.ContentPaste, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Coller un code")
                }
            }
            Text(
                "Pas à côté ? Envoie ta carte par message (WhatsApp, SMS…) : ton ami l'ouvre avec GoodLife. " +
                    "Elle passe seulement par la messagerie que tu choisis, jamais par un serveur GoodLife.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (info != null) Text(info!!, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)

            // ---- Défi de la semaine ----
            WeeklyChallenge(social.people.filter { it.friend }, settings.pseudo)

            // ---- Classement entre amis ----
            val friends = social.people.filter { it.friend }
            SectionCard(title = "Classement entre amis", icon = Icons.Filled.EmojiEvents) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(ranking == "level", { ranking = "level" }, label = { Text("Niveau") })
                    FilterChip(ranking == "streak", { ranking = "streak" }, label = { Text("Série") })
                    FilterChip(ranking == "dex", { ranking = "dex" }, label = { Text(DEX_NAME) })
                }
                if (friends.isEmpty()) {
                    Text(
                        "Pas encore d'amis. Collez vos téléphones avec Tap to Sync ou scannez son QR code.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else Leaderboard(friends, ranking, onPerson)
            }

            // ---- StreetPass ----
            if (StreetPass.supported(context) && StreetPass.allowedForAge()) SectionCard(title = "StreetPass", icon = Icons.Filled.Sensors) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Croiser d'autres joueurs", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "En Bluetooth basse consommation, à quelques mètres. Une notification reste affichée " +
                                "tant que c'est actif. Nécessite un profil public.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.streetPass,
                        enabled = settings.publicProfile && settings.pseudo.isNotBlank(),
                        onCheckedChange = { on ->
                            if (on) {
                                val perms = StreetPass.permissions() +
                                    (if (Build.VERSION.SDK_INT >= 33) arrayOf(android.Manifest.permission.POST_NOTIFICATIONS) else emptyArray())
                                askBt.launch(perms)
                            } else {
                                Repo.updateSettings { it.copy(streetPass = false) }
                                StreetPass.sync(context)
                            }
                        }
                    )
                }
                val met = social.people.filter { !it.friend }.sortedByDescending { it.seenAt }
                if (met.isNotEmpty()) {
                    Text("Rencontres", style = MaterialTheme.typography.labelLarge)
                    met.take(30).forEachIndexed { i, p ->
                        if (i > 0) HorizontalDivider()
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f).clickable { onPerson(p.id) }) {
                                Text(p.pseudo, fontWeight = FontWeight.Medium)
                                Text(
                                    listOfNotNull(p.level?.let { "Niv. $it" }, "croisé ${p.encounters} fois", ago(p.seenAt)).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(onClick = { Repo.setFriend(p.id, true) }) { Icon(Icons.Filled.PersonAdd, "Ajouter en ami") }
                            TextButton(onClick = { Repo.blockPerson(p.id) }) { Icon(Icons.Filled.Block, "Masquer et bloquer") }
                        }
                    }
                }
            }
            if (message != null) Text(message!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            if (social.blocked.isNotEmpty()) {
                TextButton(onClick = { Repo.unblockAll() }) { Text("Débloquer les ${social.blocked.size} personne(s) bloquée(s)") }
            }
        }
    }
}

@Composable
private fun ShareBox(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(checked, role = Role.Checkbox, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Coller la carte reçue par message. */
@Composable
private fun PasteCodeDialog(onDismiss: () -> Unit, onDone: (String) -> Unit) {
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Coller un code d'ami") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Colle le message reçu (il contient un code qui commence par GL1:).", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(text, { text = it.take(3000); error = null }, minLines = 3, maxLines = 5, modifier = Modifier.fillMaxWidth())
                TextButton(onClick = { clipboard.getText()?.text?.let { text = it.take(3000) } }) {
                    Icon(Icons.Filled.ContentPaste, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Coller le presse-papiers")
                }
                if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val e = Social.receiveFromMessage(text)
                if (e == null) error = "Ce message ne contient pas de carte GoodLife valide (ou elle a été modifiée)."
                else onDone(resultText(e))
            }) { Text("Ajouter") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

/** Défi de la semaine : un objectif par semaine (jours validés, pas ou XP), entre amis. */
@Composable
private fun WeeklyChallenge(friends: List<Person>, myPseudo: String) {
    val meals = Repo.meals.collectAsState().value
    val game = Repo.game.collectAsState().value
    val steps = Repo.steps.collectAsState().value
    val week = Weekly.current()
    val kind = Weekly.kind(week)
    val mine = remember(meals, game, steps) { Weekly.myStats() }
    SectionCard(title = "Défi de la semaine", icon = Icons.Filled.Flag) {
        Text(kind.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        val left = Weekly.daysLeft()
        Text(
            "Encore $left jour${if (left > 1) "s" else ""} · les scores de tes amis arrivent à chaque échange de cartes (Tap to Sync, QR, StreetPass ou message).",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        data class R(val name: String, val value: Int, val me: Boolean)
        val synced = friends.filter { it.weekId == week }
        val rows = (synced.map { R(it.pseudo, Weekly.value(kind, it.weekDays, it.weekSteps, it.weekXp), false) } +
            listOfNotNull(mine?.let { R(myPseudo.ifBlank { "Moi" } + " (moi)", Weekly.value(kind, it.days, it.steps, it.xp), true) }))
            .sortedByDescending { it.value }
        rows.forEachIndexed { i, r ->
            if (i > 0) HorizontalDivider()
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(when (i) { 0 -> "🥇"; 1 -> "🥈"; 2 -> "🥉"; else -> "${i + 1}." }, modifier = Modifier.width(36.dp), textAlign = TextAlign.Center)
                Text(r.name, modifier = Modifier.weight(1f), fontWeight = if (r.me) FontWeight.Bold else FontWeight.Normal)
                Text("${com.goodlife.app.coach.Coach.fmt(r.value)} ${kind.unit}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
        val waiting = friends.filter { it.weekId != week }
        if (waiting.isNotEmpty()) Text(
            "Pas encore de nouvelles cette semaine : " + waiting.take(6).joinToString { it.pseudo } +
                (if (waiting.size > 6) "…" else "") + ". Échangez vos cartes pour voir leurs scores.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Classement : mes amis + moi, avec les infos qu'ils partagent. */
@Composable
private fun Leaderboard(friends: List<Person>, by: String, onPerson: (String) -> Unit) {
    val profile = Repo.profile.collectAsState().value
    val meals = Repo.meals.collectAsState().value
    val game = Repo.game.collectAsState().value
    val steps = Repo.steps.collectAsState().value
    val dex = Repo.dex.collectAsState().value
    val settings = Repo.settings.collectAsState().value
    val me = remember(meals, game, steps, profile) { profile?.let { Game.summarize(meals, it, game, steps.days, newRulesFrom = settings.scoreRulesFrom) } }

    data class Row3(val id: String?, val name: String, val value: Int?)
    val rows = (friends.map {
        Row3(it.id, it.pseudo, when (by) { "streak" -> it.streak; "dex" -> it.dex?.size; else -> it.level })
    } + Row3(null, settings.pseudo.ifBlank { "Moi" } + " (moi)", when (by) {
        "streak" -> me?.streak; "dex" -> dex.unlocked.size; else -> me?.level?.level
    })).sortedWith(compareByDescending<Row3> { it.value ?: -1 }.thenBy { it.name })

    rows.forEachIndexed { i, r ->
        if (i > 0) HorizontalDivider()
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                .then(if (r.id != null) Modifier.clickable { onPerson(r.id) } else Modifier)
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                when (i) { 0 -> "🥇"; 1 -> "🥈"; 2 -> "🥉"; else -> "${i + 1}." },
                modifier = Modifier.width(36.dp), textAlign = TextAlign.Center
            )
            Text(
                r.name, modifier = Modifier.weight(1f),
                fontWeight = if (r.id == null) FontWeight.Bold else FontWeight.Normal
            )
            Text(
                r.value?.let {
                    when (by) { "streak" -> "$it j"; "dex" -> "$it/${Nutridex.ENTRIES.size}"; else -> "Niv. $it" }
                } ?: "caché",
                style = MaterialTheme.typography.labelLarge,
                color = if (r.value == null) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary
            )
        }
    }
}

/** Fiche d'un ami ou d'une rencontre. */
@Composable
private fun FriendProfile(id: String, onBack: () -> Unit, onDex: (String) -> Unit) {
    val social by Repo.social.collectAsState()
    val p = social.people.firstOrNull { it.id == id }
    if (p == null) { LaunchedEffect(Unit) { onBack() }; return }
    var cheer by remember { mutableStateOf(false) }
    var confirmBlock by remember { mutableStateOf(false) }
    var info by remember { mutableStateOf<String?>(null) }
    val myDex = Repo.dex.collectAsState().value.unlocked.keys

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        ScreenColumn {
            SubScreenHeader(p.pseudo, onBack)
            SectionCard {
                Text(if (p.friend) "Ami" else "Rencontre StreetPass", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Stat("Niveau", p.level?.let { "$it · ${Game.title(it)}" } ?: "caché", Modifier.weight(1f))
                    Stat("Série", p.streak?.let { "$it j" } ?: "cachée", Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Stat("Record", p.bestStreak?.let { "$it j" } ?: "caché", Modifier.weight(1f))
                    Stat(DEX_NAME, p.dex?.let { "${it.size}/${Nutridex.ENTRIES.size}" } ?: "caché", Modifier.weight(1f))
                }
                Text(
                    "Dernière synchro ${ago(p.cardTime)} · ajouté par ${when (p.via) { "tap" -> "Tap to Sync"; "street" -> "StreetPass"; "code" -> "carte partagée"; else -> "QR code" }}. " +
                        "Ses infos se mettent à jour à chaque nouvelle synchro.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            p.dex?.let { theirs ->
                SectionCard(title = DEX_NAME) {
                    val both = theirs.intersect(myDex).size
                    val onlyThem = (theirs - myDex).size
                    Text("Vous avez $both aliment(s) en commun. ${p.pseudo} en a $onlyThem que tu n'as pas encore.", style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = { onDex(p.id) }) { Text("Voir son $DEX_NAME") }
                }
            }
            if (p.friend) {
                Button(onClick = { cheer = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Favorite, null); Spacer(Modifier.width(8.dp)); Text("Encourager")
                }
            } else {
                Button(onClick = { Repo.setFriend(p.id, true) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.PersonAdd, null); Spacer(Modifier.width(8.dp)); Text("Ajouter en ami")
                }
            }
            if (info != null) Text(info!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (p.friend) TextButton(onClick = { Repo.setFriend(p.id, false) }) { Text("Retirer des amis") }
                TextButton(onClick = { confirmBlock = true }) { Text("Bloquer", color = MaterialTheme.colorScheme.error) }
            }
        }
    }

    if (cheer) AlertDialog(
        onDismissRequest = { cheer = false },
        title = { Text("Encourager ${p.pseudo}") },
        text = {
            Column {
                Text(
                    "Il le recevra à votre prochaine synchro (Tap to Sync, QR code ou StreetPass). Un encouragement par jour.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                CHEERS.forEachIndexed { i, text ->
                    TextButton(onClick = {
                        info = if (Repo.sendCheer(p.id, i, Identity.myId())) "Encouragement prêt : « $text »"
                               else "Tu as déjà encouragé ${p.pseudo} aujourd'hui."
                        cheer = false
                    }) { Text(text) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { cheer = false }) { Text("Annuler") } }
    )
    if (confirmBlock) AlertDialog(
        onDismissRequest = { confirmBlock = false },
        title = { Text("Bloquer ${p.pseudo} ?") },
        text = { Text("Sa carte et ses encouragements seront ignorés. Tu pourras le débloquer depuis l'écran Amis.") },
        confirmButton = { TextButton(onClick = { Repo.blockPerson(p.id); confirmBlock = false; onBack() }) { Text("Bloquer", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmBlock = false }) { Text("Annuler") } }
    )
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

/**
 * Tap to Sync : l'écran reste ouvert sur les deux téléphones, on colle les dos. Les rôles NFC
 * (lecteur / carte) alternent au hasard pour que l'un finisse par lire l'autre.
 */
@Composable
private fun TapSyncScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = remember { context.findFragmentActivity() }
    val results = remember { mutableStateListOf<SyncEvent>() }
    val canShare = remember { Social.canShare() }
    val nfcOk = activity != null && TapSync.available(activity)
    val nfcOn = activity != null && TapSync.enabled(activity)

    DisposableEffect(Unit) {
        TapSync.active = true
        onDispose {
            TapSync.active = false
            activity?.let { TapSync.stopReader(it) }
        }
    }
    LaunchedEffect(nfcOn) {
        if (!nfcOn || activity == null) return@LaunchedEffect
        while (isActive) {
            TapSync.startReader(activity) {}
            delay(Random.nextLong(450, 900))
            if (!TapSync.busy) TapSync.stopReader(activity)
            delay(Random.nextLong(450, 900))
        }
    }
    LaunchedEffect(Unit) {
        Social.events.collect { e ->
            results.add(0, e)
            Sounds.play(Sfx.LEVEL_UP)
        }
    }
    val pulse = rememberInfiniteTransition(label = "tap")
    val scale by pulse.animateFloat(0.92f, 1.08f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "tapScale")

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        ScreenColumn {
            SubScreenHeader("Tap to Sync", onBack)
            Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.Contactless, null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(120.dp).graphicsLayer { scaleX = scale; scaleY = scale }
                )
            }
            Text(
                when {
                    !nfcOk -> "Ce téléphone n'a pas de NFC. Utilise le QR code à la place."
                    !nfcOn -> "Active le NFC dans les réglages rapides du téléphone, puis reviens ici."
                    else -> "Ouvre aussi cet écran sur le téléphone de ton ami, puis collez vos téléphones dos à dos quelques secondes."
                },
                style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
            )
            if (!canShare) Text(
                "Ton profil est privé (ou sans pseudo) : tu recevras sa carte, mais il ne recevra pas la tienne.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
            )
            results.forEach { e ->
                SectionCard(container = MaterialTheme.colorScheme.primaryContainer) {
                    Text(resultText(e), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

/** QR code : montrer le mien, ou scanner celui d'un ami avec le scanner de Google. */
@Composable
private fun QrScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val card = remember { Social.mySignedCard()?.let { Identity.toText(it) } }
    val qr = remember(card) { card?.let { qrBitmap(it, 720) } }
    var result by remember { mutableStateOf<String?>(null) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        ScreenColumn {
            SubScreenHeader("QR code", onBack)
            if (qr != null) SectionCard {
                Text("Fais scanner ce code par ton ami", style = MaterialTheme.typography.titleMedium)
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(androidx.compose.ui.graphics.Color.White).padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(qr.asImageBitmap(), "Mon QR code", Modifier.fillMaxWidth())
                }
                Text(
                    "Il contient seulement ce que tu partages, signé par ton téléphone. Montre-le seulement à des personnes que tu connais.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else SectionCard {
                Text(
                    "Ton profil est privé (ou sans pseudo) : active « Profil public » dans l'écran Amis pour avoir un QR code. " +
                        "Tu peux quand même scanner celui d'un ami.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Button(
                onClick = {
                    GmsBarcodeScanning.getClient(
                        context,
                        GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
                    ).startScan()
                        .addOnSuccessListener { b ->
                            val e = b.rawValue?.let { Social.receiveText(it, "qr") }
                            result = e?.let(::resultText) ?: "Ce QR code n'est pas une carte GoodLife valide."
                            if (e != null) Sounds.play(Sfx.LEVEL_UP)
                        }
                        .addOnFailureListener { result = "Scanner indisponible : ${it.message ?: "réessaie"}" }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Icon(Icons.Filled.QrCodeScanner, null); Spacer(Modifier.width(8.dp)); Text("Scanner le QR d'un ami")
            }
            if (result != null) SectionCard(container = MaterialTheme.colorScheme.primaryContainer) {
                Text(result!!, style = MaterialTheme.typography.titleMedium)
            }
            // Version de test uniquement : coller une carte à la main (l'émulateur ne peut pas scanner de QR)
            if (com.goodlife.app.BuildConfig.DEBUG) {
                var paste by remember { mutableStateOf("") }
                Text("Test : mon id ${Identity.myId()}", style = MaterialTheme.typography.labelSmall)
                OutlinedTextField(paste, { paste = it }, label = { Text("Carte (test)") }, modifier = Modifier.fillMaxWidth())
                TextButton(onClick = {
                    result = Social.receiveText(paste, "qr")?.let(::resultText) ?: "Carte refusée (invalide ou falsifiée)."
                }) { Text("Importer (test)") }
            }
        }
    }
}

private fun qrBitmap(text: String, size: Int): Bitmap? = runCatching {
    val m = QRCodeWriter().encode(
        text, BarcodeFormat.QR_CODE, size, size,
        mapOf(EncodeHintType.MARGIN to 1, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M)
    )
    Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565).apply {
        for (x in 0 until size) for (y in 0 until size) setPixel(x, y, if (m[x, y]) AColor.BLACK else AColor.WHITE)
    }
}.getOrNull()
