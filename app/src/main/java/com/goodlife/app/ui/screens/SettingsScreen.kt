@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package com.goodlife.app.ui.screens

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
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
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
import com.goodlife.app.data.Repo
import com.goodlife.app.security.AppLock
import com.goodlife.app.security.findFragmentActivity
import com.goodlife.app.sleep.SleepTracker
import com.goodlife.app.ui.SectionCard
import com.goodlife.app.ui.theme.THEME_COLORS
import com.goodlife.app.ui.theme.THEME_MODES
import com.goodlife.app.ui.theme.dynamicColorSupported

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val settings by Repo.settings.collectAsState()
    val context = LocalContext.current
    val uri = LocalUriHandler.current

    var keyDraft by remember(settings.apiKey) { mutableStateOf(settings.apiKey) }
    var showKey by remember { mutableStateOf(false) }
    var keySaved by remember { mutableStateOf(false) }
    var lockMessage by remember { mutableStateOf<String?>(null) }
    var confirmWipe by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") }
            Spacer(Modifier.width(4.dp))
            Text("Paramètres", style = MaterialTheme.typography.headlineMedium)
        }

        // ---- Apparence ----
        SectionCard(title = "Apparence", icon = Icons.Filled.Palette) {
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

        // ---- IA ----
        SectionCard(title = "Intelligence artificielle", icon = Icons.Filled.VpnKey) {
            Text(
                "Sans clé : ${com.goodlife.app.data.Settings.RELAY_DAILY_LIMIT} analyses IA gratuites par jour, offertes par GoodLife " +
                    "(Gemini 3.5 Flash-Lite). Restantes aujourd'hui : ${settings.relayRemainingToday()}.",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "Pour en faire plus, crée ta propre clé gratuite sur Google AI Studio et colle-la ici. " +
                    "Elle est chiffrée sur ton téléphone et n'est envoyée qu'à Google.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = { uri.openUri("https://aistudio.google.com/apikey") }) {
                Text("Obtenir une clé gratuite")
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
                }) { Text("Retirer ma clé (revenir aux analyses offertes)") }
            }
            // Choix du modèle seulement avec une clé perso ; sinon modèle unique imposé par le relais.
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

        // ---- Confidentialité ----
        SectionCard(title = "Confidentialité", icon = Icons.Filled.Lock) {
            Text(
                "• Profil, repas et sommeil : stockés uniquement sur ce téléphone, chiffrés (AES-256, Android Keystore).\n" +
                    "• Aucune sauvegarde cloud, aucun compte, aucune pub, aucun tracker.\n" +
                    "• Quand tu analyses une photo, seules la photo et tes allergies partent vers Google Gemini " +
                    "(directement avec ta clé, ou via le relais GoodLife sans clé, qui ne stocke rien). " +
                    "Les photos ne sont jamais enregistrées.",
                style = MaterialTheme.typography.bodyMedium
            )
            OutlinedButton(onClick = { confirmWipe = true }) {
                Text("Effacer toutes mes données", color = MaterialTheme.colorScheme.error)
            }
        }

        // ---- À propos ----
        SectionCard(title = "À propos", icon = Icons.Filled.Info) {
            Text("GoodLife v${BuildConfig.VERSION_NAME}", fontWeight = FontWeight.Medium)
            Text(
                "Projet développé avec l'assistance d'une IA (Claude, Anthropic). " +
                    "Les estimations caloriques sont indicatives et ne remplacent pas l'avis d'un professionnel de santé.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    if (confirmWipe) {
        AlertDialog(
            onDismissRequest = { confirmWipe = false },
            title = { Text("Tout effacer ?") },
            text = { Text("Profil, repas, sommeil, clé API et réglages seront supprimés définitivement de ce téléphone.") },
            confirmButton = {
                TextButton(onClick = {
                    SleepTracker.unsubscribe(context)
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
