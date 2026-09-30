package com.goodlife.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.goodlife.app.data.Repo
import com.goodlife.app.net.AvailableUpdate
import com.goodlife.app.net.UpdateInstaller
import kotlinx.coroutines.launch

/** Bloc « nouvelle version » : téléchargement vérifié puis installation, en un bouton. */
@Composable
fun UpdatePanel(update: AvailableUpdate, showDismiss: Boolean) {
    val context = LocalContext.current
    val uri = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    var progress by remember { mutableStateOf<Float?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Tes données et ta clé sont conservées. Android te demandera de confirmer l'installation.",
            style = MaterialTheme.typography.bodyMedium
        )
        val p = progress
        if (p != null) {
            LinearProgressIndicator(
                progress = { p.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                strokeCap = StrokeCap.Round
            )
        }
        if (status != null) Text(status!!, style = MaterialTheme.typography.bodySmall)
        if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (update.apkUrl.isNotBlank()) {
                Button(
                    enabled = progress == null,
                    onClick = {
                        error = null
                        if (!UpdateInstaller.canInstall(context)) {
                            status = "Autorise GoodLife à installer ses mises à jour, reviens ici puis appuie à nouveau sur « Installer »."
                            UpdateInstaller.openInstallPermission(context)
                            return@Button
                        }
                        progress = 0f
                        status = "Téléchargement de ${update.tag}…"
                        scope.launch {
                            try {
                                val apk = UpdateInstaller.download(context, update) { f -> progress = f }
                                status = "Vérifié (source officielle, signature identique). Installation…"
                                UpdateInstaller.install(context, apk)
                            } catch (e: Exception) {
                                error = e.message ?: "Mise à jour impossible."
                                status = null
                            } finally {
                                progress = null
                            }
                        }
                    }
                ) { Text("Installer ${update.tag}") }
            }
            TextButton(onClick = { uri.openUri(update.pageUrl) }) { Text("Voir sur GitHub") }
            if (showDismiss) {
                TextButton(onClick = { Repo.updateSettings { it.copy(dismissedTag = update.tag) } }) { Text("Plus tard") }
            }
        }
    }
}
