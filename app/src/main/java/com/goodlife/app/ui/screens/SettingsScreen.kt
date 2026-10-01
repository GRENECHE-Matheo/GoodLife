@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package com.goodlife.app.ui.screens

import com.goodlife.app.i18n.t

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.goodlife.app.BuildConfig
import com.goodlife.app.ai.Gemini
import com.goodlife.app.data.Backup
import com.goodlife.app.data.Repo
import com.goodlife.app.ui.Sfx
import com.goodlife.app.ui.Sounds
import com.goodlife.app.net.Updater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.goodlife.app.security.AppLock
import com.goodlife.app.security.findFragmentActivity
import com.goodlife.app.sleep.SleepTracker
import com.goodlife.app.ui.ScreenColumn
import com.goodlife.app.ui.SubScreenHeader
import com.goodlife.app.ui.SectionCard
import com.goodlife.app.ui.SlideSwitch
import com.goodlife.app.ui.theme.THEME_COLORS
import com.goodlife.app.ui.theme.THEME_MODES
import com.goodlife.app.ui.theme.dynamicColorSupported

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    var showPolicy by rememberSaveable { mutableStateOf(false) }
    SlideSwitch(showPolicy) { open ->
        if (open) PrivacyScreen(onBack = { showPolicy = false })
        else SettingsContent(onBack, onOpenPolicy = { showPolicy = true })
    }
}

@Composable
private fun SettingsContent(onBack: () -> Unit, onOpenPolicy: () -> Unit) {
    val settings by Repo.settings.collectAsState()
    val context = LocalContext.current
    val uri = LocalUriHandler.current

    // La clé enregistrée n'est jamais réaffichée : le champ sert seulement à en saisir une nouvelle
    var keyDraft by remember { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }
    var keySaved by remember { mutableStateOf(false) }
    var replacingKey by remember { mutableStateOf(false) }
    var lockMessage by remember { mutableStateOf<String?>(null) }
    var confirmWipe by remember { mutableStateOf(false) }
    var askAiConsent by remember { mutableStateOf(false) }
    var exportMessage by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    var updateMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            exportMessage = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { it.write(Repo.exportJson().toByteArray()) }
                t("Données exportées.")
            }.getOrElse { t("Export impossible : %1\$s", it.message) }
        }
    }
    // Export protégé : même format que la sauvegarde chiffrée (restaurable), clé tirée du mot de passe
    var exportKey by remember { mutableStateOf<com.goodlife.app.data.Backup.DerivedKey?>(null) }
    var exportDialog by remember { mutableStateOf(false) }
    val protectedExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(com.goodlife.app.data.Backup.MIME)
    ) { uri ->
        val k = exportKey
        exportKey = null
        if (uri != null && k != null) {
            exportMessage = runCatching {
                com.goodlife.app.data.Backup.write(context, uri, com.goodlife.app.data.Backup.encrypt(Repo.backupJson(), k))
                t("Données exportées, protégées par ton mot de passe.")
            }.getOrElse { t("Export impossible : %1\$s", it.message) }
        }
    }
    if (exportDialog) ExportDialog(
        onDismiss = { exportDialog = false },
        onProtected = { password ->
            exportDialog = false
            scope.launch {
                exportKey = withContext(Dispatchers.Default) { com.goodlife.app.data.Backup.deriveKey(password) }
                protectedExportLauncher.launch("GoodLife-export.goodlife")
            }
        },
        onPlain = { exportDialog = false; exportLauncher.launch("goodlife-export.json") }
    )


    ScreenColumn {
        SubScreenHeader(t("Paramètres"), onBack)

        // ---- Apparence ----
        SectionCard(title = t("Apparence et sons"), icon = Icons.Filled.Palette) {
            Text(t("Thème"), style = MaterialTheme.typography.labelLarge)
            THEME_MODES.forEach { (id, label) ->
                Row(
                    Modifier.fillMaxWidth().selectable(
                        selected = settings.themeMode == id,
                        onClick = { Repo.updateSettings { it.copy(themeMode = id) } },
                        role = Role.RadioButton
                    ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = settings.themeMode == id, onClick = null)
                    Spacer(Modifier.width(12.dp))
                    Text(label)
                }
            }
            Text(t("Couleur"), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                THEME_COLORS.filter { it.first != "auto" || dynamicColorSupported }.forEach { (id, label) ->
                    FilterChip(
                        selected = settings.themeColor == id ||
                            (settings.themeColor == "auto" && !dynamicColorSupported && id == "blue"),
                        onClick = { Repo.updateSettings { it.copy(themeColor = id) } },
                        label = { Text(label) }
                    )
                }
            }
            if (dynamicColorSupported) {
                Text(
                    t("« Couleurs du téléphone » reprend les couleurs de ton fond d'écran (Material You)."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(t("Langue"), style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("system" to t("Langue du téléphone"), "fr" to "Français", "en" to "English").forEach { (id, label) ->
                    FilterChip(
                        selected = settings.language == id,
                        onClick = {
                            if (settings.language != id) {
                                Repo.updateSettings { it.copy(language = id) }
                                restartApp(context)
                            }
                        },
                        label = { Text(label) }
                    )
                }
            }
            SettingSwitch(
                title = t("Sons"),
                subtitle = t("Petits sons pendant le quiz et quand l'XP monte. Suivent le volume multimédia."),
                checked = settings.sounds,
                onChange = { v ->
                    Repo.updateSettings { it.copy(sounds = v) }
                    if (v) Sounds.play(Sfx.CORRECT)
                }
            )
        }

        // ---- Sécurité ----
        SectionCard(title = t("Coach et notifications"), icon = Icons.Filled.Notifications) {
            Text(
                t("Messages préparés sur ton téléphone (sans réseau ni IA), jamais plus d'un à la fois. Sur l'écran verrouillé, seul « Un message du chef » s'affiche."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            NotifSettingsBlock(settings)
            SettingSwitch(
                t("Rappel d'hydratation"),
                t("Vers 15 h 30, seulement si tu as bu moins de la moitié de ton objectif d'eau."),
                settings.notifWater
            ) { on -> Repo.updateSettings { it.copy(notifWater = on) }; com.goodlife.app.coach.CoachNotifier.schedule(context) }
            Text(t("Objectif d'eau par jour"), style = MaterialTheme.typography.labelLarge)
            val aiWater = Repo.aiAllowed() && settings.apiKey.isNotBlank()
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (aiWater) androidx.compose.material3.FilterChip(settings.waterGoalMode == "ia",
                    { Repo.updateSettings { it.copy(waterGoalMode = "ia", waterGoalIaDay = "") }
                      scope.launch { com.goodlife.app.ai.WaterGoalAi.refreshIfNeeded(context) } },
                    label = { Text(t("Conseil de l'IA")) })
                listOf(1500 to t("1,5 L"), 2000 to t("2 L"), 2500 to t("2,5 L")).forEach { (ml, label) ->
                    androidx.compose.material3.FilterChip((!aiWater || settings.waterGoalMode == "fixed") && settings.waterGoalMl == ml,
                        { Repo.updateSettings { it.copy(waterGoalMl = ml, waterGoalMode = "fixed") } }, label = { Text(label) })
                }
            }
            Text(
                if (aiWater && settings.waterGoalMode == "ia")
                    t("Chaque jour, l'IA calcule ton objectif selon tes besoins (âge, sexe, poids, activité, apport visé) et ton activité d'hier (pas, sport).") +
                        (if (settings.waterGoalIa > 0) t(" Aujourd'hui : %1\$s ml.", settings.waterGoalIa) else "")
                else t("Repère pour un adulte : environ 1,5 L de boissons par jour, plus s'il fait chaud ou si tu fais du sport."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        SectionCard(title = t("Sécurité"), icon = Icons.Filled.Fingerprint) {
            SettingSwitch(
                title = t("Verrouiller avec l'empreinte"),
                subtitle = t("Empreinte, visage ou code du téléphone à l'ouverture et après 1 min en arrière-plan."),
                checked = settings.appLock,
                onChange = { wanted ->
                    lockMessage = null
                    val activity = context.findFragmentActivity()
                    when {
                        activity == null -> lockMessage = t("Verrouillage indisponible.")
                        wanted && !AppLock.isAvailable(context) ->
                            lockMessage = t("Ajoute d'abord une empreinte ou un code de verrouillage dans les réglages du téléphone.")
                        else -> AppLock.authenticate(
                            activity,
                            if (wanted) t("Activer le verrouillage") else t("Désactiver le verrouillage"),
                            onSuccess = { Repo.updateSettings { it.copy(appLock = wanted) } },
                            onError = { lockMessage = it }
                        )
                    }
                }
            )
            SettingSwitch(
                title = t("Bloquer les captures d'écran"),
                subtitle = t("Empêche captures et enregistrement d'écran, masque l'aperçu dans les apps récentes."),
                checked = settings.blockScreenshots,
                onChange = { v -> Repo.updateSettings { it.copy(blockScreenshots = v) } }
            )
            if (lockMessage != null) Text(lockMessage!!, color = MaterialTheme.colorScheme.error)
        }

        // ---- Pas ----
        StepsSettingsSection()

        // ---- Sauvegarde ----
        BackupSection()

        // ---- IA ----
        SectionCard(title = t("Intelligence artificielle"), icon = Icons.Filled.VpnKey) {
            SettingSwitch(
                title = t("Fonctions IA (Google Gemini)"),
                subtitle = if (settings.aiEnabled) t("Activées. Pour chaque demande, la photo et les infos nécessaires sont envoyées à Google avec ta clé.")
                           else t("Désactivées. Aucune donnée n'est envoyée à Google."),
                checked = settings.aiEnabled,
                onChange = { on -> if (on) askAiConsent = true else disableAi() }
            )
            if (settings.aiEnabled) {
                Text(
                    t("Chaque utilisateur utilise sa propre clé Gemini, créée chez Google (tu acceptes alors ses conditions ; l'éventuelle facturation se fait entre toi et Google). Elle est chiffrée sur ce téléphone, conservée lors des mises à jour de l'app, et n'est envoyée qu'à Google avec tes demandes."),
                    style = MaterialTheme.typography.bodyMedium
                )
                TextButton(onClick = { uri.openUri("https://aistudio.google.com/apikey") }) {
                    Text(t("Créer ma clé chez Google"))
                }
                if (settings.apiKey.isNotBlank() && !replacingKey) {
                    // Clé déjà enregistrée : on ne montre que sa fin, pour la reconnaître
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Lock, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(if (keySaved) t("Clé enregistrée") else t("Clé enregistrée et chiffrée"), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                t("Elle n'est jamais réaffichée, même en partie : personne ne peut la lire dans l'app."),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { replacingKey = true; keyDraft = ""; showKey = false; keySaved = false }) {
                            Text(t("Remplacer la clé"))
                        }
                        TextButton(onClick = { Repo.updateSettings { it.copy(apiKey = "") }; keySaved = false }) {
                            Text(t("Retirer ma clé"), color = MaterialTheme.colorScheme.error)
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = keyDraft,
                        onValueChange = { keyDraft = it.trim(); keySaved = false },
                        label = { Text(if (replacingKey) t("Nouvelle clé API Gemini") else t("Clé API Gemini")) },
                        singleLine = true,
                        keyboardOptions = KEY_KEYBOARD,
                        visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            // L'œil ne montre que ce qui est en train d'être tapé, jamais la clé enregistrée
                            IconButton(onClick = { showKey = !showKey }) {
                                Icon(if (showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, if (showKey) t("Masquer") else t("Afficher"))
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        FilledTonalButton(enabled = keyDraft.length >= 20, onClick = {
                            saveApiKey(context, keyDraft)
                            keyDraft = ""; showKey = false; keySaved = true; replacingKey = false
                        }) { Text(t("Enregistrer la clé")) }
                        if (replacingKey) TextButton(onClick = { replacingKey = false; keyDraft = "" }) { Text(t("Annuler")) }
                    }
                }
                // Choix du modèle : uniquement quand une clé personnelle est enregistrée.
                if (settings.apiKey.isNotBlank()) {
                    Text(t("Modèle (avec ta clé)"), style = MaterialTheme.typography.labelLarge)
                    Gemini.KNOWN_MODELS.forEach { (id, desc) ->
                        Row(
                            Modifier.fillMaxWidth().selectable(
                                selected = settings.model == id,
                                onClick = { Repo.updateSettings { it.copy(model = id) } },
                                role = Role.RadioButton
                            ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = settings.model == id, onClick = null)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(id)
                                Text(
                                    desc, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // ---- Mises à jour (version GitHub uniquement ; sur Google Play, c'est le Play Store) ----
        if (BuildConfig.SELF_UPDATE) SectionCard(title = t("Mises à jour"), icon = Icons.Filled.SystemUpdate) {
            SettingSwitch(
                title = t("Me prévenir des nouvelles versions"),
                subtitle = t("À chaque ouverture de l'app, vérifie s'il existe une nouvelle version sur GitHub (une seule petite requête, rien en arrière-plan)."),
                checked = settings.checkUpdates,
                onChange = { v -> Repo.updateSettings { it.copy(checkUpdates = v) } }
            )
            val update = Updater.availableUpdate()
            Text(
                if (update != null) t("Nouvelle version %1\$s disponible (tu as la %2\$s).", update.tag, BuildConfig.VERSION_NAME)
                else t("Tu as la version %1\$s.", BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodyMedium
            )
            OutlinedButton(
                enabled = !checking,
                onClick = {
                    checking = true; updateMessage = null
                    scope.launch {
                        val err = Updater.check(force = true)
                        updateMessage = err?.let { t("Vérification impossible : %1\$s", it) }
                            ?: if (Updater.availableUpdate() == null) t("Tu as la dernière version.") else null
                        checking = false
                    }
                }
            ) { Text(if (checking) t("Vérification…") else t("Vérifier les mises à jour")) }
            if (update != null) UpdatePanel(update, showDismiss = false)
            if (updateMessage != null) Text(updateMessage!!, style = MaterialTheme.typography.bodySmall)
        }

        // ---- Confidentialité ----
        SectionCard(title = t("Ce qui quitte ton téléphone"), icon = Icons.Filled.Lock) {
            DataFlowSummary()
            Text(
                if (settings.aiEnabled) t("IA : activée.") else t("IA : désactivée, rien n'est envoyé à Google."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
            TextButton(onClick = onOpenPolicy) { Text(t("Lire la politique de confidentialité")) }
            OutlinedButton(onClick = { exportDialog = true }) {
                Text(t("Exporter mes données"))
            }
            if (exportMessage != null) {
                Text(exportMessage!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            OutlinedButton(onClick = { confirmWipe = true }) {
                Text(t("Effacer toutes mes données"), color = MaterialTheme.colorScheme.error)
            }
        }

        // ---- À propos ----
        SectionCard(title = t("À propos"), icon = Icons.Filled.Info) {
            Text(t("GoodLife v%1\$s", BuildConfig.VERSION_NAME), fontWeight = FontWeight.Medium)
            Text(
                t("Projet développé avec l'assistance d'une IA (Claude, Anthropic). GoodLife est une app de bien-être, pas un dispositif médical : elle ne diagnostique, ne traite ni ne prévient aucune maladie. Les estimations sont indicatives et ne remplacent pas l'avis d'un professionnel de santé."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                t("Données nutritionnelles : Anses, table de composition nutritionnelle des aliments Ciqual 2025 (mise à jour du 19/11/2025, Licence Ouverte Etalab 2.0) ; Open Food Facts (licence ODbL)."),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = { uri.openUri("mailto:${BuildConfig.CONTACT_EMAIL}") }) {
                Text(t("Contact : %1\$s", BuildConfig.CONTACT_EMAIL))
            }
        }
    }

    if (askAiConsent) AiConsentDialog(onDismiss = { askAiConsent = false })

    if (confirmWipe) {
        AlertDialog(
            onDismissRequest = { confirmWipe = false },
            title = { Text(t("Tout effacer ?")) },
            text = { Text(t("Profil, repas, sommeil, clé API et réglages seront supprimés définitivement de ce téléphone. Un fichier de sauvegarde déjà enregistré ailleurs n'est pas effacé.")) },
            confirmButton = {
                TextButton(onClick = {
                    SleepTracker.unsubscribe(context)
                    if (settings.backupUri.isNotBlank()) Backup.releaseAccess(context, android.net.Uri.parse(settings.backupUri))
                    Repo.wipeAll()
                    confirmWipe = false
                    onBack()
                }) { Text(t("Effacer"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmWipe = false }) { Text(t("Annuler")) } }
        )
    }
}

@Composable
private fun SettingSwitch(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** Redémarre l'app pour appliquer une nouvelle langue partout (textes, notifications, widgets). */
private fun restartApp(context: android.content.Context) {
    val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
    } ?: return
    Repo.flush()
    context.startActivity(intent)
    (context as? android.app.Activity)?.finishAffinity()
    Runtime.getRuntime().exit(0)
}

/** Export : protégé par mot de passe (recommandé, restaurable) ou fichier lisible (JSON, pour la portabilité RGPD). */
@Composable
private fun ExportDialog(onDismiss: () -> Unit, onProtected: (String) -> Unit, onPlain: () -> Unit) {
    var pw by remember { mutableStateOf("") }
    var pw2 by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    val ok = pw.length >= com.goodlife.app.data.Backup.MIN_PASSWORD && pw == pw2
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("Exporter mes données")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(t("Le fichier contient tes données de santé (repas, poids, sommeil…). Protège-le par un mot de passe : sans lui, personne ne peut le lire. Tu pourras aussi le restaurer dans GoodLife."),
                    style = MaterialTheme.typography.bodyMedium)
                PasswordField(pw, { pw = it }, t("Mot de passe"), show) { show = !show }
                PasswordField(pw2, { pw2 = it }, t("Confirmer"), show) { show = !show }
                Text(
                    when {
                        pw.isNotEmpty() && pw.length < com.goodlife.app.data.Backup.MIN_PASSWORD -> t("Au moins %1\$s caractères.", com.goodlife.app.data.Backup.MIN_PASSWORD)
                        pw2.isNotEmpty() && pw != pw2 -> t("Les deux mots de passe sont différents.")
                        else -> t("Ta clé API n'est jamais incluse dans l'export.")
                    },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = onPlain) { Text(t("Exporter sans mot de passe (fichier lisible)")) }
            }
        },
        confirmButton = { TextButton(enabled = ok, onClick = { onProtected(pw) }) { Text(t("Exporter")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Annuler")) } }
    )
}
