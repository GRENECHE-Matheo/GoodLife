package com.goodlife.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.goodlife.app.BuildConfig

@Composable
private fun FlowLine(icon: ImageVector, title: String, body: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 2.dp).size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Ce qui quitte le téléphone, et vers qui. Utilisé au démarrage, pour l'IA et dans les Paramètres. */
@Composable
fun DataFlowSummary(showAi: Boolean = true) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            "Par défaut, rien ne quitte ton téléphone. Seules ces fonctions envoient des données, et uniquement quand tu les utilises :",
            style = MaterialTheme.typography.bodyMedium
        )
        if (showAi) {
            FlowLine(
                Icons.Filled.AutoAwesome, "IA (si tu l'actives) → Google Gemini",
                "Photo du repas et allergies pour l'analyse. Pour les conseils : âge, sexe, poids, taille, activité, " +
                    "objectif, habitudes, allergies et repas du jour (données de santé). Envoyé avec ta propre clé. " +
                    "Jamais ton prénom ni ton sommeil."
            )
        }
        FlowLine(
            Icons.Filled.QrCodeScanner, "Scan de code-barres → Open Food Facts",
            "Uniquement le numéro du code-barres. L'image est analysée sur le téléphone."
        )
        if (BuildConfig.SELF_UPDATE) FlowLine(
            Icons.Filled.SystemUpdate, "Mises à jour → GitHub",
            "Ton adresse IP, comme pour n'importe quel site, pour savoir s'il existe une nouvelle version."
        )
        FlowLine(
            Icons.Filled.Map, "Carte → OpenFreeMap et OpenStreetMap",
            "La zone affichée, et celle où tu cherches des clubs, des boucles ou un itinéraire. Ta position exacte et tes tracés GPS restent sur le téléphone."
        )
        FlowLine(
            Icons.Filled.Group, "Amis (si ton profil est public) → le téléphone de tes amis",
            "Seulement ton pseudo et ce que tu choisis (niveau, série, Nutridex), directement de téléphone à téléphone, sans serveur."
        )
        FlowLine(
            Icons.Filled.Backup, "Sauvegarde (si tu l'actives) → l'endroit que tu choisis",
            "Un fichier chiffré avec ton mot de passe, illisible sans lui (même pour Google Drive ou le développeur)."
        )
        FlowLine(
            Icons.Filled.Block, "Aucune revente",
            "Aucune donnée n'est vendue, louée ou utilisée pour de la publicité. GoodLife n'a pas de serveur : " +
                "le développeur ne reçoit rien."
        )
    }
}
