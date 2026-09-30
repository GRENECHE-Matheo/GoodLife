package com.goodlife.app.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.goodlife.app.BuildConfig

private val REASONS = listOf(
    "Faux ou dangereux pour la santé",
    "Allergène non signalé",
    "Choquant ou offensant",
    "Autre"
)

/**
 * Mention « Généré par l'IA » + bouton « Signaler », sous chaque contenu produit par l'IA.
 * Le signalement prépare un e-mail (motif + texte signalé, jamais les données de santé) que l'utilisateur
 * relit et envoie lui-même : rien ne part automatiquement.
 */
@Composable
fun AiContentFooter(content: String, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Filled.AutoAwesome, null, Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(6.dp))
        Text(
            "Généré par l'IA · peut contenir des erreurs",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = { open = true }) {
            Icon(Icons.Outlined.Flag, null, Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("Signaler", style = MaterialTheme.typography.labelMedium)
        }
    }
    if (open) AiReportDialog(content, onDismiss = { open = false })
}

@Composable
private fun AiReportDialog(content: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var reason by remember { mutableStateOf(REASONS.first()) }
    var comment by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Outlined.Flag, null) },
        title = { Text("Signaler ce contenu") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                REASONS.forEach { r ->
                    Row(
                        Modifier.fillMaxWidth().selectable(reason == r, role = Role.RadioButton) { reason = r },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = reason == r, onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Text(r, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                OutlinedTextField(
                    comment, { comment = it.take(500) },
                    label = { Text("Précision (facultatif)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Un e-mail est préparé avec le motif et le texte signalé (sans tes données de santé). " +
                        "Tu le relis et l'envoies toi-même.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val body = buildString {
                    appendLine("Motif : $reason")
                    if (comment.isNotBlank()) appendLine("Précision : ${comment.trim()}")
                    appendLine()
                    appendLine("Contenu signalé :")
                    appendLine(content.take(2000))
                    appendLine()
                    append("GoodLife ${BuildConfig.VERSION_NAME} (${if (BuildConfig.SELF_UPDATE) "GitHub" else "Google Play"})")
                }
                val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"))
                    .putExtra(Intent.EXTRA_EMAIL, arrayOf(BuildConfig.CONTACT_EMAIL))
                    .putExtra(Intent.EXTRA_SUBJECT, "GoodLife – signalement d'un contenu IA")
                    .putExtra(Intent.EXTRA_TEXT, body)
                try {
                    context.startActivity(intent)
                    onDismiss()
                } catch (e: ActivityNotFoundException) {
                    error = "Aucune application e-mail trouvée. Tu peux écrire à ${BuildConfig.CONTACT_EMAIL}."
                }
            }) { Text("Préparer l'e-mail") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}
