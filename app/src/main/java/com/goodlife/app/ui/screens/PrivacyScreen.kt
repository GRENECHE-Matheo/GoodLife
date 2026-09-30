package com.goodlife.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Texte de la politique de confidentialité (même contenu que PRIVACY.md dans le dépôt). */
val PRIVACY_SECTIONS: List<Pair<String, String>> = listOf(
    "Qui est responsable ?" to
        "GoodLife est un projet personnel développé par Mathéo Greneche, avec l'assistance d'une IA. " +
        "L'app n'a pas de serveur et n'envoie aucune donnée à son développeur. " +
        "Pour toute question : github.com/GRENECHE-Matheo.",
    "Ce qui reste sur ton téléphone" to
        "Ton profil (âge, sexe, poids, taille, activité, objectif, habitudes, allergies), ta photo de profil, tes repas, " +
        "ton emploi du temps de repas, ton sommeil, tes pesées, ta progression (séries, niveaux, quiz) et tes réglages. Certaines de ces informations sont des données de santé. " +
        "Elles sont chiffrées sur le téléphone (AES-256, Android Keystore), sans compte ni sauvegarde cloud, " +
        "et ne sont envoyées nulle part tant que l'IA est désactivée. Base légale : ton consentement explicite, " +
        "donné au premier lancement. Durée : tant que l'app est installée (repas : 1 an, emploi du temps : 3 mois).",
    "Ce qui est envoyé si tu actives l'IA" to
        "L'IA est désactivée par défaut, demande un consentement séparé (réservé aux 18 ans et plus) " +
        "et ta propre clé Gemini gratuite. " +
        "Si tu l'actives, seules les données nécessaires à chaque demande sont envoyées :\n" +
        "• Analyse de photo : la photo du repas et tes allergies.\n" +
        "• Objectif calorique : âge, sexe, poids, taille, activité, objectif, habitudes, allergies.\n" +
        "• Idées de repas : ton objectif, les repas du jour, tes habitudes et allergies.\n" +
        "• Recette : le nom du plat, tes habitudes et allergies.\n" +
        "Ne sont jamais envoyés : ton prénom, ton sommeil, ton historique complet.",
    "À qui ces données sont envoyées" to
        "• Google LLC (API Gemini), directement depuis ton téléphone, avec ta propre clé API et donc sous ton propre " +
        "compte Google. Google traite ces données selon les conditions de l'API Gemini ; elles peuvent être conservées " +
        "temporairement par Google (par exemple pour détecter les abus) et traitées hors de l'Union européenne. " +
        "Google est une entreprise américaine adhérente au cadre de protection des données UE–États-Unis " +
        "(Data Privacy Framework).\n" +
        "• GoodLife n'a aucun serveur : le développeur ne reçoit et ne voit aucune de tes données.",
    "Ta clé API" to
        "Ta clé Gemini est chiffrée sur le téléphone (Android Keystore), conservée lors des mises à jour de l'app " +
        "et envoyée uniquement à Google, dans l'en-tête des requêtes. Elle n'apparaît dans aucune exportation.",
    "Scan de code-barres (sans IA)" to
        "Le code-barres est lu sur le téléphone avec ML Kit (modèle intégré, aucune image envoyée). Seul le numéro du " +
        "code-barres est envoyé à Open Food Facts (association française, base de données ouverte) pour obtenir les " +
        "valeurs nutritionnelles. Open Food Facts voit ton adresse IP, comme n'importe quel site web.",
    "Vérification des mises à jour" to
        "Si l'option est activée (Paramètres › Mises à jour), l'app demande à GitHub, au plus toutes les 30 minutes, " +
        "quelle est la dernière version publiée ; si tu appuies sur « Installer », elle télécharge l'APK depuis la page " +
        "officielle du projet. GitHub voit alors ton adresse IP ; aucune autre donnée n'est envoyée. Avant l'installation, " +
        "l'app vérifie l'empreinte du fichier et qu'il est signé avec la même clé que l'app installée, puis Android te " +
        "demande de confirmer. Rien n'est installé sans ton accord.",
    "Retirer ton consentement" to
        "Tu peux désactiver l'IA à tout moment dans Paramètres › Intelligence artificielle. " +
        "Plus rien n'est alors envoyé. Ce retrait ne remet pas en cause les demandes faites avant.",
    "Le sommeil" to
        "La détection automatique utilise la Sleep API des services Google Play, calculée sur le téléphone. " +
        "GoodLife ne reçoit que les heures de coucher et de réveil, stockées localement et jamais envoyées.",
    "Tes droits (RGPD)" to
        "Accès et portabilité : Paramètres › Exporter mes données. Rectification : Profil › Modifier mes infos. " +
        "Effacement : Paramètres › Effacer toutes mes données (ou désinstaller l'app). " +
        "Retrait du consentement : interrupteur IA. Comme GoodLife n'a pas de serveur, toutes tes données sont sur ton téléphone. " +
        "Tu peux aussi adresser une réclamation à la CNIL (cnil.fr).",
    "Ce que GoodLife ne fait pas" to
        "Pas de publicité, pas de revente de données, pas de traceur, pas de profilage marketing.",
    "Important" to
        "Les calories et conseils donnés par l'IA sont des estimations indicatives. GoodLife n'est pas un dispositif " +
        "médical et ne remplace pas l'avis d'un professionnel de santé."
)

@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") }
                Spacer(Modifier.width(4.dp))
                Text("Confidentialité", style = MaterialTheme.typography.headlineSmall)
            }
            Text(
                "Version 0.4 · mise à jour le 30 septembre 2026",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            PRIVACY_SECTIONS.forEach { (title, body) ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                    Text(body, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
