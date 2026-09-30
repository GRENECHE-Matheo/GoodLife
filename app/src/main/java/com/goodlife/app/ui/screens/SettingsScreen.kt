@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package com.goodlife.app.ui.screens

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
import kotlinx.coroutines.launch
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

    var keyDraft by remember(settings.apiKey) { mutableStateOf(settings.apiKey) }
    var showKey by remember { mutableStateOf(false) }
    var keySaved by remember { mutableStateOf(false) }
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
                "Données exportées."
            }.getOrElse { "Export impossible : ${it.message}" }
        }
    }


    ScreenColumn {
        SubScreenHeader("Paramètres", onBack)

        // ---- Apparence ----
        SectionCard(title = "Apparence et sons", icon = Icons.Filled.Palette) {
            Text("Thème", style = MaterialTheme.typography.labelLarge)
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
            Text("Couleur", style = MaterialTheme.typography.labelLarge)
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
                    "« Couleurs du téléphone » reprend les couleurs de ton fond d'écran (Material You).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            SettingSwitch(
                title = "Sons",
                subtitle = "Petits sons pendant le quiz et quand l'XP monte. Suivent le volume multimédia.",
                checked = settings.sounds,
                onChange = { v ->
                    Repo.updateSettings { it.copy(sounds = v) }
                    if (v) Sounds.play(Sfx.CORRECT)
                }
            )
        }

        // ---- Sécurité ----
        SectionCard(title = "Sécurité", icon = Icons.Filled.Fingerprint) {
            SettingSwitch(
                title = "Verrouiller avec l'empreinte",
                subtitle = "Empreinte, visage ou code du téléphone à l'ouverture et après 1 min en arrière-plan.",
                checked = settings.appLock,
                onChange = { wanted ->
                    lockMessage = null
                    val activity = context.findFragmentActivity()
                    when {
                        activity == null -> lockMessage = "Verrouillage indisponible."
                        wanted && !AppLock.isAvailable(context) ->
                            lockMessage = "Ajoute d'abord une empreinte ou un code de verrouillage dans les réglages du téléphone."
                        else -> AppLock.authenticate(
                            activity,
                            if (wanted) "Activer le verrouillage" else "Désactiver le verrouillage",
                            onSuccess = { Repo.updateSettings { it.copy(appLock = wanted) } },
                            onError = { lockMessage = it }
                        )
                    }
                }
            )
            SettingSwitch(
                title = "Bloquer les captures d'écran",
                subtitle = "Empêche captures et enregistrement d'écran, masque l'aperçu dans les apps récentes.",
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
        SectionCard(title = "Intelligence artificielle", icon = Icons.Filled.VpnKey) {
            SettingSwitch(
                title = "Fonctions IA (Google Gemini)",
                subtitle = if (settings.aiEnabled) "Activées. Pour chaque demande, la photo et les infos nécessaires sont envoyées à Google avec ta clé."
                           else "Désactivées. Aucune donnée n'est envoyée à Google.",
                checked = settings.aiEnabled,
                onChange = { on -> if (on) askAiConsent = true else disableAi() }
            )
            if (settings.aiEnabled) {
                Text(
                    "Chaque utilisateur utilise sa propre clé Gemini, créée chez Google (tu acceptes alors ses conditions ; " +
                        "l'éventuelle facturation se fait entre toi et Google). Elle est chiffrée sur ce téléphone, " +
                        "conservée lors des mises à jour de l'app, et n'est envoyée qu'à Google avec tes demandes.",
                    style = MaterialTheme.typography.bodyMedium
                )
                TextButton(onClick = { uri.openUri("https://aistudio.google.com/apikey") }) {
                    Text("Créer ma clé chez Google")
                }
                OutlinedTextField(
                    value = keyDraft,
                    onValueChange = { keyDraft = it.trim(); keySaved = false },
                    label = { Text("Clé API Gemini") },
                    singleLine = true,
                    visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showKey = !showKey }) {
                            Icon(if (showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, "Afficher")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalButton(onClick = {
                        Repo.updateSettings { it.copy(apiKey = keyDraft) }
                        keySaved = true
                    }) { Text("Enregistrer la clé") }
                    if (keySaved) {
                        Spacer(Modifier.width(12.dp))
                        Text("Enregistrée", color = MaterialTheme.colorScheme.primary)
                    }
                }
                if (settings.apiKey.isNotBlank()) {
                    TextButton(onClick = {
                        keyDraft = ""
                        Repo.updateSettings { it.copy(apiKey = "") }
                    }) { Text("Retirer ma clé") }
                }
                // Choix du modèle : uniquement quand une clé personnelle est enregistrée.
                if (settings.apiKey.isNotBlank()) {
                    Text("Modèle (avec ta clé)", style = MaterialTheme.typography.labelLarge)
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
        if (BuildConfig.SELF_UPDATE) SectionCard(title = "Mises à jour", icon = Icons.Filled.SystemUpdate) {
            SettingSwitch(
                title = "Me prévenir des nouvelles versions",
                subtitle = "À chaque ouverture de l'app (au plus toutes les 30 min), vérifie les versions publiées sur GitHub.",
                checked = settings.checkUpdates,
                onChange = { v -> Repo.updateSettings { it.copy(checkUpdates = v) } }
            )
            val update = Updater.availableUpdate()
            Text(
                if (update != null) "Nouvelle version ${update.tag} disponible (tu as la ${BuildConfig.VERSION_NAME})."
                else "Tu as la version ${BuildConfig.VERSION_NAME}.",
                style = MaterialTheme.typography.bodyMedium
            )
            OutlinedButton(
                enabled = !checking,
                onClick = {
                    checking = true; updateMessage = null
                    scope.launch {
                        val err = Updater.check(force = true)
                        updateMessage = err?.let { "Vérification impossible : $it" }
                            ?: if (Updater.availableUpdate() == null) "Tu as la dernière version." else null
                        checking = false
                    }
                }
            ) { Text(if (checking) "Vérification…" else "Vérifier les mises à jour") }
            if (update != null) UpdatePanel(update, showDismiss = false)
            if (updateMessage != null) Text(updateMessage!!, style = MaterialTheme.typography.bodySmall)
        }

        // ---- Confidentialité ----
        SectionCard(title = "Ce qui quitte ton téléphone", icon = Icons.Filled.Lock) {
            DataFlowSummary()
            Text(
                if (settings.aiEnabled) "IA : activée." else "IA : désactivée, rien n'est envoyé à Google.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
            TextButton(onClick = onOpenPolicy) { Text("Lire la politique de confidentialité") }
            OutlinedButton(onClick = { exportLauncher.launch("goodlife-export.json") }) {
                Text("Exporter mes données (JSON)")
            }
            if (exportMessage != null) {
                Text(exportMessage!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            OutlinedButton(onClick = { confirmWipe = true }) {
                Text("Effacer toutes mes données", color = MaterialTheme.colorScheme.error)
            }
        }

        // ---- À propos ----
        SectionCard(title = "À propos", icon = Icons.Filled.Info) {
            Text("GoodLife v${BuildConfig.VERSION_NAME}", fontWeight = FontWeight.Medium)
            Text(
                "Projet développé avec l'assistance d'une IA (Claude, Anthropic). " +
                    "GoodLife est une app de bien-être, pas un dispositif médical : elle ne diagnostique, ne traite " +
                    "ni ne prévient aucune maladie. Les estimations sont indicatives et ne remplacent pas l'avis " +
                    "d'un professionnel de santé.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = { uri.openUri("mailto:${BuildConfig.CONTACT_EMAIL}") }) {
                Text("Contact : ${BuildConfig.CONTACT_EMAIL}")
            }
        }
    }

    if (askAiConsent) AiConsentDialog(onDismiss = { askAiConsent = false })

    if (confirmWipe) {
        AlertDialog(
            onDismissRequest = { confirmWipe = false },
            title = { Text("Tout effacer ?") },
            text = { Text("Profil, repas, sommeil, clé API et réglages seront supprimés définitivement de ce téléphone. Un fichier de sauvegarde déjà enregistré ailleurs n'est pas effacé.") },
            confirmButton = {
                TextButton(onClick = {
                    SleepTracker.unsubscribe(context)
                    if (settings.backupUri.isNotBlank()) Backup.releaseAccess(context, android.net.Uri.parse(settings.backupUri))
                    Repo.wipeAll()
                    confirmWipe = false
                    onBack()
                }) { Text("Effacer", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmWipe = false }) { Text("Annuler") } }
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
