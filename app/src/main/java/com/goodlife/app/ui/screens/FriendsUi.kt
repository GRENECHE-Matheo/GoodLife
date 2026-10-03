@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.goodlife.app.ui.screens

import com.goodlife.app.i18n.t
import com.goodlife.app.i18n.tp

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
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Close
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
        m < 1 -> t("à l'instant")
        m < 60 -> t("il y a %1\$s min", m)
        m < 24 * 60 -> t("il y a %1\$s h", m / 60)
        else -> t("il y a %1\$s j", m / (24 * 60))
    }
}

private fun resultText(e: SyncEvent): String = when (e.result) {
    Repo.Received.NEW_FRIEND -> t("%1\$s est maintenant ton ami !", e.pseudo)
    Repo.Received.UPDATED -> t("Carte de %1\$s mise à jour.", e.pseudo)
    Repo.Received.NEW_ENCOUNTER -> t("Nouvelle rencontre : %1\$s", e.pseudo)
    Repo.Received.SEEN_AGAIN -> t("Tu as recroisé %1\$s.", e.pseudo)
    Repo.Received.IGNORED -> t("Carte ignorée.")
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
                Text(t("Amis"), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                Text(
                    buildString {
                        append(if (friends == 0) t("Ajoute des amis en collant vos téléphones") else tp(friends, "%1\$s ami", "%1\$s amis"))
                        if (unread > 0) append(" · " + tp(unread, "%1\$s encouragement", "%1\$s encouragements"))
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
    var ranking by rememberSaveable { mutableStateOf("level") }
    var message by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    var pasteOpen by remember { mutableStateOf(false) }
    if (pasteOpen) PasteCodeDialog(onDismiss = { pasteOpen = false }) { result -> info = result; pasteOpen = false }
    val unreadCheers = remember { social.cheersIn.filter { !it.seen } }
    LaunchedEffect(Unit) {
        if (social.cheersIn.any { !it.seen }) Repo.markCheersSeen()
        com.goodlife.app.social.SocialNotifier.cancelCheers(context)
    }

    val askBt = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (StreetPass.hasPermissions(context)) {
            Repo.updateSettings { it.copy(streetPass = true) }
            StreetPass.sync(context)
        } else message = t("Les croisements ont besoin de l'autorisation « Appareils à proximité ».")
    }
    val friends = social.people.filter { it.friend }
    val met = social.people.filter { !it.friend }.sortedByDescending { it.seenAt }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        ScreenColumn {
            SubScreenHeader(t("Amis"), onBack)

            // ---- Encouragements reçus ----
            if (unreadCheers.isNotEmpty()) SectionCard(title = t("Encouragements"), icon = Icons.Filled.Favorite,
                container = MaterialTheme.colorScheme.tertiaryContainer) {
                unreadCheers.forEach { c ->
                    val from = social.people.firstOrNull { it.id == c.from }?.pseudo ?: t("Un ami")
                    Text("$from : ${CHEERS.getOrElse(c.message) { "" }}", style = MaterialTheme.typography.bodyLarge)
                }
            }

            // ---- Rencontres à accepter (croisements) ----
            if (met.isNotEmpty()) SectionCard(title = t("Personnes croisées · %1\$s", met.size), icon = Icons.Filled.Sensors) {
                Text(
                    t("Ajoute-les en ami pour les suivre dans ton classement, ou ignore-les."),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                met.take(20).forEach { p ->
                    PersonCard(
                        name = p.pseudo,
                        details = listOfNotNull(p.level?.let { t("Niv. %1\$s", it) }, tp(p.encounters, "croisé une fois", "croisé %1\$s fois"), ago(p.seenAt)).joinToString(" · "),
                        onClick = { onPerson(p.id) }
                    ) {
                        FilledTonalButton(onClick = { Repo.setFriend(p.id, true); info = t("%1\$s est maintenant ton ami !", p.pseudo) }) {
                            Icon(Icons.Filled.PersonAdd, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text(t("Ajouter"))
                        }
                        IconButton(onClick = { Repo.removePerson(p.id) }) { Icon(Icons.Filled.Close, t("Ignorer")) }
                    }
                }
            }

            // ---- Ajouter un ami ----
            SectionCard(title = t("Ajouter un ami"), icon = Icons.Filled.PersonAdd) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AddTile(Icons.Filled.Contactless, t("Tap to Sync"), t("Collez vos téléphones"), Modifier.weight(1f), primary = true, onClick = onTap)
                    AddTile(Icons.Filled.QrCode2, t("QR code"), t("Montre ou scanne"), Modifier.weight(1f), primary = true, onClick = onQr)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AddTile(Icons.Filled.Share, t("Partager ma carte"), t("Par message"), Modifier.weight(1f)) {
                        val text = Social.shareText()
                        if (text == null) message = t("Active « Profil public » et choisis un pseudo pour partager ta carte.")
                        else runCatching {
                            context.startActivity(android.content.Intent.createChooser(
                                android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
                                    .putExtra(android.content.Intent.EXTRA_TEXT, text), t("Partager ma carte")))
                        }
                    }
                    AddTile(Icons.Filled.ContentPaste, t("Coller un code"), t("Reçu par message"), Modifier.weight(1f)) { pasteOpen = true }
                }
                if (!Social.canShare()) Text(
                    t("Pour que tes amis te voient aussi, rends ton profil public (en bas de l'écran)."),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (info != null) Text(info!!, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)

            // ---- Mes amis (et classement) ----
            SectionCard(title = if (friends.isEmpty()) t("Mes amis") else t("Mes amis · %1\$s", friends.size), icon = Icons.Filled.Group) {
                if (friends.isEmpty()) {
                    Text(
                        t("Pas encore d'amis. Collez vos téléphones avec Tap to Sync ou scannez son QR code."),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(ranking == "level", { ranking = "level" }, label = { Text(t("Niveau")) })
                        FilterChip(ranking == "streak", { ranking = "streak" }, label = { Text(t("Série")) })
                        FilterChip(ranking == "dex", { ranking = "dex" }, label = { Text(DEX_NAME) })
                    }
                    Leaderboard(friends, ranking, onPerson)
                }
            }

            // ---- Défi de la semaine ----
            if (friends.isNotEmpty()) WeeklyChallenge(friends, settings.pseudo)

            // ---- Croisements ----
            if (StreetPass.supported(context) && StreetPass.allowedForAge()) SectionCard(title = t("Croisements"), icon = Icons.Filled.Sensors) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(t("Croiser d'autres joueurs"), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            t("À quelques mètres d'un joueur GoodLife, vos téléphones échangent vos cartes en Bluetooth. Une notification à chaque nouvelle rencontre. Profil public requis. Toute personne à proximité peut lire ta carte (pseudo, niveau…) et la reconnaître plus tard."),
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
                if (settings.streetPass) Text(
                    t("Pour garder le Bluetooth actif en arrière-plan, Android impose une notification : elle est réduite au minimum (silencieuse, sans icône en haut de l'écran). Sur Android 13 et plus, tu peux la balayer pour la masquer."),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ---- Mon profil d'ami ----
            MyFriendProfile()

            if (message != null) Text(message!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            if (social.blocked.isNotEmpty()) {
                TextButton(onClick = { Repo.unblockAll() }) { Text(t("Débloquer les %1\$s personne(s) bloquée(s)", social.blocked.size)) }
            }
        }
    }
}

/** Grande tuile d'action (ajouter un ami). */
@Composable
private fun AddTile(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, modifier: Modifier, primary: Boolean = false, onClick: () -> Unit) {
    Surface(
        onClick = onClick, modifier = modifier, shape = RoundedCornerShape(20.dp),
        color = if (primary) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, null, tint = if (primary) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1,
                color = if (primary) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, maxLines = 1,
                color = if (primary) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Carte d'une personne : avatar, pseudo, infos, et des actions à droite. */
@Composable
private fun PersonCard(
    name: String, details: String, onClick: (() -> Unit)?, leading: String? = null, highlight: Boolean = false,
    trailing: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit
) {
    Surface(
        onClick = { onClick?.invoke() }, enabled = onClick != null, shape = RoundedCornerShape(18.dp),
        color = if (highlight) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (leading != null) Text(leading, modifier = Modifier.width(30.dp), textAlign = TextAlign.Center)
            com.goodlife.app.ui.Avatar(null, name, 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = if (highlight) FontWeight.Bold else FontWeight.Medium, maxLines = 1)
                if (details.isNotBlank()) Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            trailing()
        }
    }
}

/** Mon profil d'ami : replié une fois prêt (pseudo choisi), avec un bouton « Modifier ». */
@Composable
private fun MyFriendProfile() {
    val settings by Repo.settings.collectAsState()
    val avatar by Repo.avatar.collectAsState()
    val context = LocalContext.current
    val ready = settings.publicProfile && settings.pseudo.isNotBlank()
    var editing by rememberSaveable { mutableStateOf(false) }
    var pseudo by remember(settings.pseudo) { mutableStateOf(settings.pseudo) }
    SectionCard(title = t("Mon profil d'ami"), icon = Icons.Filled.PhoneAndroid) {
        if (ready && !editing) {
            val shared = listOfNotNull(
                if (settings.shareLevel) t("niveau") else null, if (settings.shareStreak) t("série") else null,
                if (settings.shareDex) DEX_NAME else null, if (settings.shareWeek) t("défi de la semaine") else null
            ).joinToString(", ").ifEmpty { t("rien d'autre que ton pseudo") }
            Row(verticalAlignment = Alignment.CenterVertically) {
                com.goodlife.app.ui.Avatar(avatar, settings.pseudo, 44.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(settings.pseudo, style = MaterialTheme.typography.titleMedium)
                    Text(t("Profil public · tu partages : %1\$s", shared), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { editing = true }) {
                    Icon(Icons.Filled.Edit, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text(t("Modifier"))
                }
            }
            return@SectionCard
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (settings.publicProfile) t("Profil public") else t("Profil privé"), style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (settings.publicProfile) t("Tes amis reçoivent seulement ce que tu coches ci-dessous, quand vous vous synchronisez.")
                    else t("Personne ne reçoit rien. Tu peux quand même recevoir la carte des autres."),
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
                    label = { Text(t("Pseudo")) }, singleLine = true, modifier = Modifier.weight(1f),
                    supportingText = { Text(t("Évite ton nom complet.")) }
                )
                TextButton(
                    enabled = Identity.cleanPseudo(pseudo).length >= 2 && Identity.cleanPseudo(pseudo) != settings.pseudo,
                    onClick = { Repo.updateSettings { it.copy(pseudo = Identity.cleanPseudo(pseudo)) }; StreetPass.sync(context) }
                ) { Text("OK") }
            }
            Text(t("Je partage :"), style = MaterialTheme.typography.labelLarge)
            ShareBox(t("Mon niveau"), settings.shareLevel) { v -> Repo.updateSettings { it.copy(shareLevel = v) } }
            ShareBox(t("Ma série"), settings.shareStreak) { v -> Repo.updateSettings { it.copy(shareStreak = v) } }
            ShareBox(t("Mon %1\$s (sans mes photos)", DEX_NAME), settings.shareDex) { v -> Repo.updateSettings { it.copy(shareDex = v) } }
            ShareBox(t("Mon défi de la semaine (jours validés, pas, XP)"), settings.shareWeek) { v -> Repo.updateSettings { it.copy(shareWeek = v) } }
            Text(
                t("Jamais partagé : ton poids, tes repas, ton sommeil, tes photos."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (ready) Button(onClick = { editing = false }) { Text(t("Terminé")) }
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
        title = { Text(t("Coller un code d'ami")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(t("Colle le message reçu (il contient un code qui commence par GL1:)."), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(text, { text = it.take(3000); error = null }, minLines = 3, maxLines = 5, modifier = Modifier.fillMaxWidth())
                TextButton(onClick = { clipboard.getText()?.text?.let { text = it.take(3000) } }) {
                    Icon(Icons.Filled.ContentPaste, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(t("Coller le presse-papiers"))
                }
                if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val e = Social.receiveFromMessage(text)
                if (e == null) error = t("Ce message ne contient pas de carte GoodLife valide (ou elle a été modifiée).")
                else onDone(resultText(e))
            }) { Text(t("Ajouter")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Annuler")) } }
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
    SectionCard(title = t("Défi de la semaine"), icon = Icons.Filled.Flag) {
        Text(kind.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        val left = Weekly.daysLeft()
        Text(
            tp(left, "Encore %1\$s jour", "Encore %1\$s jours") + t(" · les scores de tes amis arrivent à chaque échange de cartes (Tap to Sync, QR, croisement ou message)."),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        data class R(val name: String, val value: Int, val me: Boolean)
        val synced = friends.filter { it.weekId == week }
        val rows = (synced.map { R(it.pseudo, Weekly.value(kind, it.weekDays, it.weekSteps, it.weekXp), false) } +
            listOfNotNull(mine?.let { R(myPseudo.ifBlank { t("Moi") } + t(" (moi)"), Weekly.value(kind, it.days, it.steps, it.xp), true) }))
            .sortedByDescending { it.value }
        rows.forEachIndexed { i, r ->
            if (i > 0) HorizontalDivider()
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(when (i) { 0 -> "🥇"; 1 -> "🥈"; 2 -> "🥉"; else -> "${i + 1}." }, modifier = Modifier.width(36.dp), textAlign = TextAlign.Center)
                Text(r.name, modifier = Modifier.weight(1f), fontWeight = if (r.me) FontWeight.Bold else FontWeight.Normal)
                Text("${com.goodlife.app.coach.Coach.fmt(r.value)} ${t(kind.unit)}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
        val waiting = friends.filter { it.weekId != week }
        if (waiting.isNotEmpty()) Text(
            t("Pas encore de nouvelles cette semaine : ") + waiting.take(6).joinToString { it.pseudo } +
                (if (waiting.size > 6) "…" else "") + t(". Échangez vos cartes pour voir leurs scores."),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Classement : mes amis + moi, en cartes, avec les infos qu'ils partagent. */
@Composable
private fun Leaderboard(friends: List<Person>, by: String, onPerson: (String) -> Unit) {
    val profile = Repo.profile.collectAsState().value
    val meals = Repo.meals.collectAsState().value
    val game = Repo.game.collectAsState().value
    val steps = Repo.steps.collectAsState().value
    val dex = Repo.dex.collectAsState().value
    val settings = Repo.settings.collectAsState().value
    val me = remember(meals, game, steps, profile) { profile?.let { Game.summarize(meals, it, game, steps.days, newRulesFrom = settings.scoreRulesFrom, foodOnlyFrom = settings.foodOnlyFrom, richFrom = settings.richScoreFrom) } }

    data class Row3(val id: String?, val name: String, val level: Int?, val streak: Int?, val dex: Int?) {
        fun value(by: String): Int? = when (by) { "streak" -> this.streak; "dex" -> this.dex; else -> this.level }
    }
    val rows = (friends.map { Row3(it.id, it.pseudo, it.level, it.streak, it.dex?.size) } +
        Row3(null, settings.pseudo.ifBlank { t("Moi") } + t(" (moi)"), me?.level?.level, me?.streak, dex.unlocked.size))
        .sortedWith(compareByDescending<Row3> { it.value(by) ?: -1 }.thenBy { it.name })

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEachIndexed { i, r ->
            val details = listOfNotNull(
                r.level?.let { t("Niv. %1\$s", it) }, r.streak?.let { "🔥 $it" }, r.dex?.let { "📖 $it" }
            ).joinToString(" · ").ifEmpty { t("infos cachées") }
            PersonCard(
                name = r.name, details = details, onClick = r.id?.let { id -> { onPerson(id) } },
                leading = when (i) { 0 -> "🥇"; 1 -> "🥈"; 2 -> "🥉"; else -> "${i + 1}." }, highlight = r.id == null
            ) {
                val v = r.value(by)
                Text(
                    v?.let { when (by) { "streak" -> t("%1\$s j", it); "dex" -> "$it/${Nutridex.ENTRIES.size}"; else -> t("Niv. %1\$s", it) } } ?: t("caché"),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (v == null) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary
                )
                if (r.id != null) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.outline)
            }
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
                Text(if (p.friend) t("Ami") else t("Personne croisée"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Stat(t("Niveau"), p.level?.let { "$it · ${Game.title(it)}" } ?: t("caché"), Modifier.weight(1f))
                    Stat(t("Série"), p.streak?.let { "$it j" } ?: t("cachée"), Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Stat(t("Record"), p.bestStreak?.let { "$it j" } ?: t("caché"), Modifier.weight(1f))
                    Stat(DEX_NAME, p.dex?.let { "${it.size}/${Nutridex.ENTRIES.size}" } ?: t("caché"), Modifier.weight(1f))
                }
                Text(
                    t("Dernière synchro %1\$s · ajouté par %2\$s. Ses infos se mettent à jour à chaque nouvelle synchro.", ago(p.cardTime),
                        when (p.via) { "tap" -> "Tap to Sync"; "street" -> t("croisement"); "code" -> t("carte partagée"); else -> "QR code" }),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            p.dex?.let { theirs ->
                SectionCard(title = DEX_NAME) {
                    val both = theirs.intersect(myDex).size
                    val onlyThem = (theirs - myDex).size
                    Text(t("Vous avez %1\$s aliment(s) en commun. %2\$s en a %3\$s que tu n'as pas encore.", both, p.pseudo, onlyThem), style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = { onDex(p.id) }) { Text(t("Voir son %1\$s", DEX_NAME)) }
                }
            }
            if (p.friend) {
                Button(onClick = { cheer = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Favorite, null); Spacer(Modifier.width(8.dp)); Text(t("Encourager"))
                }
            } else {
                Button(onClick = { Repo.setFriend(p.id, true) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.PersonAdd, null); Spacer(Modifier.width(8.dp)); Text(t("Ajouter en ami"))
                }
            }
            if (info != null) Text(info!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (p.friend) TextButton(onClick = { Repo.setFriend(p.id, false) }) { Text(t("Retirer des amis")) }
                TextButton(onClick = { confirmBlock = true }) { Text(t("Bloquer"), color = MaterialTheme.colorScheme.error) }
            }
        }
    }

    if (cheer) AlertDialog(
        onDismissRequest = { cheer = false },
        title = { Text(t("Encourager %1\$s", p.pseudo)) },
        text = {
            Column {
                Text(
                    t("Il le recevra à votre prochaine synchro (Tap to Sync, QR code, croisement ou message). Un encouragement par jour."),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                CHEERS.forEachIndexed { i, text ->
                    TextButton(onClick = {
                        info = if (Repo.sendCheer(p.id, i, Identity.myId())) t("Encouragement prêt : « %1\$s »", text)
                               else t("Tu as déjà encouragé %1\$s aujourd'hui.", p.pseudo)
                        cheer = false
                    }) { Text(text) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { cheer = false }) { Text(t("Annuler")) } }
    )
    if (confirmBlock) AlertDialog(
        onDismissRequest = { confirmBlock = false },
        title = { Text(t("Bloquer %1\$s ?", p.pseudo)) },
        text = { Text(t("Sa carte et ses encouragements seront ignorés. Tu pourras le débloquer depuis l'écran Amis.")) },
        confirmButton = { TextButton(onClick = { Repo.blockPerson(p.id); confirmBlock = false; onBack() }) { Text(t("Bloquer"), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmBlock = false }) { Text(t("Annuler")) } }
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
            SubScreenHeader(t("Tap to Sync"), onBack)
            Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.Contactless, null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(120.dp).graphicsLayer { scaleX = scale; scaleY = scale }
                )
            }
            Text(
                when {
                    !nfcOk -> t("Ce téléphone n'a pas de NFC. Utilise le QR code à la place.")
                    !nfcOn -> t("Active le NFC dans les réglages rapides du téléphone, puis reviens ici.")
                    else -> t("Ouvre aussi cet écran sur le téléphone de ton ami, puis collez vos téléphones dos à dos quelques secondes.")
                },
                style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
            )
            if (!canShare) Text(
                t("Ton profil est privé (ou sans pseudo) : tu recevras sa carte, mais il ne recevra pas la tienne."),
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
            SubScreenHeader(t("QR code"), onBack)
            if (qr != null) SectionCard {
                Text(t("Fais scanner ce code par ton ami"), style = MaterialTheme.typography.titleMedium)
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(androidx.compose.ui.graphics.Color.White).padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Image(qr.asImageBitmap(), t("Mon QR code"), Modifier.fillMaxWidth())
                }
                Text(
                    t("Il contient seulement ce que tu partages, signé par ton téléphone. Montre-le seulement à des personnes que tu connais."),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else SectionCard {
                Text(
                    t("Ton profil est privé (ou sans pseudo) : active « Profil public » dans l'écran Amis pour avoir un QR code. Tu peux quand même scanner celui d'un ami."),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Button(
                onClick = {
                    // Téléphones sans services Google (Huawei…) : pas de plantage, on propose le lien d'invitation à la place
                    runCatching {
                        GmsBarcodeScanning.getClient(
                            context,
                            GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
                        ).startScan()
                            .addOnSuccessListener { b ->
                                val e = b.rawValue?.let { Social.receiveText(it, "qr") }
                                result = e?.let(::resultText) ?: t("Ce QR code n'est pas une carte GoodLife valide.")
                                if (e != null) Sounds.play(Sfx.LEVEL_UP)
                            }
                            .addOnFailureListener { result = t("Scanner indisponible : %1\$s", it.message ?: t("réessaie")) }
                    }.onFailure { result = t("Le scanner de QR code a besoin des services Google. Demande plutôt à ton ami de t'envoyer son lien d'invitation.") }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Icon(Icons.Filled.QrCodeScanner, null); Spacer(Modifier.width(8.dp)); Text(t("Scanner le QR d'un ami"))
            }
            if (result != null) SectionCard(container = MaterialTheme.colorScheme.primaryContainer) {
                Text(result!!, style = MaterialTheme.typography.titleMedium)
            }
            // Version de test uniquement : coller une carte à la main (l'émulateur ne peut pas scanner de QR)
            if (com.goodlife.app.BuildConfig.DEBUG) {
                var paste by remember { mutableStateOf("") }
                Text(t("Test : mon id %1\$s", Identity.myId()), style = MaterialTheme.typography.labelSmall)
                OutlinedTextField(paste, { paste = it }, label = { Text(t("Carte (test)")) }, modifier = Modifier.fillMaxWidth())
                TextButton(onClick = {
                    result = Social.receiveText(paste, "qr")?.let(::resultText) ?: t("Carte refusée (invalide ou falsifiée).")
                }) { Text(t("Importer (test)")) }
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
