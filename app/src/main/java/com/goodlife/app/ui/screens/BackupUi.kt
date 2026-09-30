package com.goodlife.app.ui.screens

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.goodlife.app.data.Backup
import com.goodlife.app.data.Repo
import com.goodlife.app.ui.SectionCard
import com.goodlife.app.ui.theme.successColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun displayName(context: Context, uri: Uri): String = runCatching {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }
}.getOrNull() ?: Backup.FILE_NAME

private fun formatBackupDate(ms: Long): String =
    SimpleDateFormat("EEEE d MMMM 'à' HH:mm", Locale.FRANCE).format(Date(ms))

/** Section « Sauvegarde » des Paramètres. */
@Composable
fun BackupSection() {
    val settings by Repo.settings.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var askPassword by remember { mutableStateOf(false) }
    var pendingKey by remember { mutableStateOf<Backup.DerivedKey?>(null) }
    var working by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var confirmOff by remember { mutableStateOf(false) }

    // 2e étape de l'activation : choisir où créer le fichier (Drive, Téléchargements, clé USB…)
    val createFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(Backup.MIME)) { uri ->
        val key = pendingKey
        pendingKey = null
        if (uri == null || key == null) { working = false; return@rememberLauncherForActivityResult }
        Backup.keepAccess(context, uri)
        val old = settings.backupUri
        if (old.isNotBlank() && old != uri.toString()) Backup.releaseAccess(context, Uri.parse(old))
        Repo.updateSettings {
            it.copy(backupUri = uri.toString(), backupKey = key.encode(), backupName = displayName(context, uri), lastBackupAt = 0L, backupError = "")
        }
        scope.launch {
            withContext(Dispatchers.IO) { Backup.autoBackup(context, force = true) }
            working = false
            message = Repo.settings.value.backupError.ifBlank { "Sauvegarde activée et première copie enregistrée." }
        }
    }

    SectionCard(title = "Sauvegarde chiffrée", icon = Icons.Filled.Backup) {
        if (settings.backupUri.isBlank()) {
            Text(
                "Pour ne rien perdre si tu changes de téléphone ou réinstalles l'app. GoodLife écrit une copie " +
                    "chiffrée de tes données dans le fichier de ton choix (Google Drive, Téléchargements…), puis la met à jour " +
                    "toute seule à chaque fois que tu quittes l'app après un changement.",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "Le fichier est illisible sans ton mot de passe, même pour Google ou pour le développeur. " +
                    "Mot de passe oublié = sauvegarde perdue : personne ne peut le retrouver.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FilledTonalButton(enabled = !working, onClick = { message = null; askPassword = true }) {
                if (working) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Icon(Icons.Filled.Backup, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Activer la sauvegarde")
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val ok = settings.backupError.isBlank() && settings.lastBackupAt > 0
                Icon(
                    if (ok) Icons.Filled.CheckCircle else Icons.Filled.Warning, null,
                    tint = if (ok) successColor else MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Sauvegarde automatique activée", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Fichier : ${settings.backupName.ifBlank { Backup.FILE_NAME }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        if (settings.lastBackupAt > 0) "Dernière copie : ${formatBackupDate(settings.lastBackupAt)}"
                        else "Pas encore de copie.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (settings.backupError.isNotBlank()) {
                Text(settings.backupError, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            FilledTonalButton(
                enabled = !working,
                onClick = {
                    working = true; message = null
                    scope.launch {
                        withContext(Dispatchers.IO) { Backup.autoBackup(context, force = true) }
                        working = false
                        message = Repo.settings.value.backupError.ifBlank { "Copie enregistrée." }
                    }
                }
            ) {
                if (working) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Icon(Icons.Filled.Backup, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Sauvegarder maintenant")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { message = null; askPassword = true }) { Text("Changer mot de passe / fichier") }
                TextButton(onClick = { confirmOff = true }) { Text("Désactiver", color = MaterialTheme.colorScheme.error) }
            }
        }
        RestoreButton(outlined = true)
        if (message != null) Text(message!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
    }

    if (askPassword) {
        NewPasswordDialog(
            onDismiss = { askPassword = false },
            onConfirm = { password ->
                askPassword = false
                working = true
                scope.launch {
                    pendingKey = withContext(Dispatchers.Default) { Backup.deriveKey(password) }
                    createFile.launch(Backup.FILE_NAME)
                }
            }
        )
    }

    if (confirmOff) {
        AlertDialog(
            onDismissRequest = { confirmOff = false },
            title = { Text("Désactiver la sauvegarde ?") },
            text = { Text("GoodLife arrête de mettre à jour le fichier. Le fichier déjà enregistré n'est pas supprimé : tu peux l'effacer toi-même si tu veux.") },
            confirmButton = {
                TextButton(onClick = {
                    Backup.releaseAccess(context, Uri.parse(settings.backupUri))
                    Repo.updateSettings { it.copy(backupUri = "", backupKey = "", backupName = "", lastBackupAt = 0L, backupError = "") }
                    confirmOff = false
                }) { Text("Désactiver") }
            },
            dismissButton = { TextButton(onClick = { confirmOff = false }) { Text("Annuler") } }
        )
    }
}

/** Choix d'un nouveau mot de passe (deux fois, 8 caractères minimum). */
@Composable
private fun NewPasswordDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var pw by remember { mutableStateOf("") }
    var pw2 by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    val longEnough = pw.length >= Backup.MIN_PASSWORD
    val same = pw == pw2
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Backup, null) },
        title = { Text("Mot de passe de la sauvegarde") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Il protège le fichier. Il sera demandé pour restaurer, par exemple sur un nouveau téléphone. " +
                        "Note-le dans un endroit sûr : sans lui, la sauvegarde est perdue.",
                    style = MaterialTheme.typography.bodyMedium
                )
                PasswordField(pw, { pw = it }, "Mot de passe", show) { show = !show }
                PasswordField(pw2, { pw2 = it }, "Confirmer", show) { show = !show }
                Text(
                    when {
                        pw.isNotEmpty() && !longEnough -> "Au moins ${Backup.MIN_PASSWORD} caractères."
                        pw2.isNotEmpty() && !same -> "Les deux mots de passe sont différents."
                        else -> "Ensuite, choisis où enregistrer le fichier (Google Drive conseillé)."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if ((pw.isNotEmpty() && !longEnough) || (pw2.isNotEmpty() && !same)) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(enabled = longEnough && same, onClick = { onConfirm(pw) }) { Text("Choisir le fichier") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}

@Composable
private fun PasswordField(value: String, onChange: (String) -> Unit, label: String, show: Boolean, onToggle: () -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            IconButton(onClick = onToggle) {
                Icon(if (show) Icons.Filled.VisibilityOff else Icons.Filled.Visibility, if (show) "Masquer" else "Afficher")
            }
        },
        modifier = Modifier.fillMaxWidth()
    )
}

/**
 * Bouton « Restaurer une sauvegarde » : choix du fichier, mot de passe, confirmation si des données
 * existent déjà, puis restauration. Propose de continuer la sauvegarde automatique dans le même fichier.
 */
@Composable
fun RestoreButton(outlined: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val hasData = Repo.profile.collectAsState().value != null
    var file by remember { mutableStateOf<Uri?>(null) }
    var pw by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    var keepAuto by remember { mutableStateOf(true) }
    var working by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var decrypted by remember { mutableStateOf<Pair<String, Backup.DerivedKey>?>(null) }
    var done by remember { mutableStateOf(false) }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { file = uri; pw = ""; error = null }
    }

    fun apply(result: Pair<String, Backup.DerivedKey>, uri: Uri) {
        runCatching { Repo.restore(result.first) }
            .onSuccess {
                if (keepAuto) {
                    Backup.keepAccess(context, uri)
                    Repo.updateSettings {
                        it.copy(
                            backupUri = uri.toString(), backupKey = result.second.encode(),
                            backupName = displayName(context, uri), lastBackupAt = System.currentTimeMillis(), backupError = ""
                        )
                    }
                }
                file = null; decrypted = null; done = true
            }
            .onFailure { error = it.message ?: "Restauration impossible." }
    }

    val label = "Restaurer une sauvegarde"
    val onClick = { done = false; pick.launch(arrayOf("*/*")) }
    if (outlined) OutlinedButton(onClick = onClick, modifier = modifier) {
        Icon(Icons.Filled.Restore, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(label)
    } else FilledTonalButton(onClick = onClick, modifier = modifier) {
        Icon(Icons.Filled.Restore, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text(label)
    }
    if (done && hasData) {
        Text("Données restaurées.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
    }

    val uri = file
    if (uri != null && decrypted == null) {
        AlertDialog(
            onDismissRequest = { if (!working) file = null },
            icon = { Icon(Icons.Filled.Restore, null) },
            title = { Text("Restaurer") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Fichier : ${displayName(context, uri)}", style = MaterialTheme.typography.bodyMedium)
                    PasswordField(pw, { pw = it; error = null }, "Mot de passe de la sauvegarde", show) { show = !show }
                    Row(
                        Modifier.fillMaxWidth().toggleable(keepAuto, role = Role.Checkbox) { keepAuto = it },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = keepAuto, onCheckedChange = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Continuer la sauvegarde automatique dans ce fichier", style = MaterialTheme.typography.bodyMedium)
                    }
                    if (working) Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Déchiffrement…", style = MaterialTheme.typography.bodySmall)
                    }
                    if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(enabled = pw.isNotEmpty() && !working, onClick = {
                    working = true; error = null
                    scope.launch {
                        val result = withContext(Dispatchers.Default) {
                            runCatching { Backup.decrypt(Backup.read(context, uri), pw) }
                        }
                        working = false
                        result.onSuccess { r -> if (hasData) decrypted = r else apply(r, uri) }
                            .onFailure { error = it.message ?: "Fichier illisible." }
                    }
                }) { Text("Restaurer") }
            },
            dismissButton = { TextButton(enabled = !working, onClick = { file = null }) { Text("Annuler") } }
        )
    }

    // Des données existent déjà sur ce téléphone : on confirme avant de les remplacer
    val ready = decrypted
    if (uri != null && ready != null) {
        AlertDialog(
            onDismissRequest = { decrypted = null; file = null },
            icon = { Icon(Icons.Filled.Warning, null) },
            title = { Text("Remplacer les données actuelles ?") },
            text = {
                Text(
                    "Le profil, les repas, le sommeil, le planning et la progression de ce téléphone seront remplacés " +
                        "par ceux de la sauvegarde. Tes réglages de sécurité, l'IA et ta clé restent comme ils sont."
                )
            },
            confirmButton = { TextButton(onClick = { apply(ready, uri) }) { Text("Remplacer", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { decrypted = null; file = null }) { Text("Annuler") } }
        )
    }
}
