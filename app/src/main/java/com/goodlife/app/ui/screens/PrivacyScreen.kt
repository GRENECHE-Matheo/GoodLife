package com.goodlife.app.ui.screens

import com.goodlife.app.ui.ScreenColumn
import com.goodlife.app.ui.SubScreenHeader
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
import com.goodlife.app.BuildConfig

/** Titre de la section propre à la version GitHub (masquée dans la version Google Play). */
const val PRIVACY_GITHUB_ONLY = "Vérification des mises à jour (version GitHub uniquement)"

/** Texte de la politique de confidentialité (même contenu que PRIVACY.md dans le dépôt). */
val PRIVACY_SECTIONS: List<Pair<String, String>> = listOf(
    "Qui est responsable ?" to
        "GoodLife est un projet personnel développé par Mathéo Greneche, avec l'assistance d'une IA. " +
        "L'app n'a pas de serveur et n'envoie aucune donnée à son développeur. " +
        "Contact (questions, exercice de tes droits, signalements) : matheo.greneche0@gmail.com.",
    "Qui peut utiliser GoodLife ?" to
        "L'app est réservée aux personnes de 15 ans et plus (âge à partir duquel on peut consentir seul au traitement " +
        "de ses données en France). Les fonctions IA sont réservées aux 18 ans et plus. L'objectif « Perdre du poids » " +
        "n'est pas proposé avant 18 ans, ni quand l'IMC est déjà inférieur à 18,5.",
    "Ce qui reste sur ton téléphone" to
        "Ton profil (âge, sexe, poids, taille, activité, objectif, habitudes, allergies), ta photo de profil, tes repas, " +
        "ton emploi du temps de repas, ton sommeil, tes pesées, ta progression (séries, niveaux, quiz) et tes réglages. " +
        "Certaines de ces informations sont des données de santé. " +
        "Elles sont chiffrées sur le téléphone (AES-256, Android Keystore), sans compte, et aucune copie n'est faite sans ton accord. " +
        "Elles ne sont envoyées nulle part tant que l'IA et la sauvegarde sont désactivées. Base légale : ton consentement " +
        "explicite (case à cocher au premier lancement). Durée : tant que l'app est installée (repas : 1 an, emploi du temps : 3 mois).",
    "Ce qui est envoyé si tu actives l'IA" to
        "L'IA est désactivée par défaut, demande un consentement séparé (réservé aux 18 ans et plus) " +
        "et ta propre clé API Gemini, que tu crées toi-même chez Google. " +
        "Si tu l'actives, seules les données nécessaires à chaque demande sont envoyées :\n" +
        "• Analyse de photo : la photo du repas et tes allergies.\n" +
        "• Objectif calorique : âge, sexe, poids, taille, activité, objectif, habitudes, allergies.\n" +
        "• Idées de repas : ton objectif, les repas du jour, tes habitudes et allergies.\n" +
        "• Recette : le nom du plat, tes habitudes et allergies.\n" +
        "Ne sont jamais envoyés : ton prénom, ton sommeil, ton historique complet. " +
        "Les réponses de l'IA sont des estimations et peuvent contenir des erreurs ; elles sont signalées comme générées par l'IA.",
    "À qui ces données sont envoyées" to
        "• Google LLC (API Gemini), directement depuis ton téléphone, avec ta propre clé API et donc sous ton propre " +
        "compte Google : en créant ta clé, tu acceptes toi-même les conditions de Google, et l'éventuelle facturation " +
        "se fait entre toi et Google. Google traite ces données selon les conditions de l'API Gemini ; elles peuvent être " +
        "conservées temporairement par Google (par exemple pour détecter les abus) et traitées hors de l'Union européenne. " +
        "Selon ces conditions (version du 28 avril 2026), pour les utilisateurs situés dans l'Espace économique européen, " +
        "Google n'utilise pas les demandes ni les réponses pour améliorer ses produits, même sur son offre sans frais. " +
        "Google est une entreprise américaine adhérente au cadre de protection des données UE–États-Unis " +
        "(Data Privacy Framework).\n" +
        "• GoodLife n'a aucun serveur : le développeur ne reçoit et ne voit aucune de tes données.",
    "Ta clé API" to
        "Ta clé Gemini est chiffrée sur le téléphone (Android Keystore), conservée lors des mises à jour de l'app " +
        "et envoyée uniquement à Google, dans l'en-tête des requêtes. Elle n'apparaît dans aucune exportation ni sauvegarde.",
    "Scan de code-barres (sans IA)" to
        "Le code-barres est lu sur le téléphone avec ML Kit (modèle intégré, aucune image envoyée). Seul le numéro du " +
        "code-barres est envoyé à Open Food Facts (association française, base de données ouverte) pour obtenir les " +
        "valeurs nutritionnelles. Open Food Facts voit ton adresse IP, comme n'importe quel site web.",
    PRIVACY_GITHUB_ONLY to
        "Dans la version téléchargée depuis GitHub, si l'option est activée (Paramètres › Mises à jour), l'app demande à " +
        "GitHub, au plus toutes les 30 minutes, quelle est la dernière version publiée ; si tu appuies sur « Installer », " +
        "elle télécharge l'APK depuis la page officielle du projet. GitHub voit alors ton adresse IP ; aucune autre donnée " +
        "n'est envoyée. Avant l'installation, l'app vérifie l'empreinte du fichier et qu'il est signé avec la même clé que " +
        "l'app installée, puis Android te demande de confirmer. Rien n'est installé sans ton accord. " +
        "La version Google Play n'a pas cette fonction : ses mises à jour passent par le Play Store.",
    "Sauvegarde chiffrée (si tu l'actives)" to
        "Désactivée par défaut. Si tu l'actives (Paramètres › Sauvegarde chiffrée), GoodLife écrit une copie de tes données " +
        "dans le fichier que tu choisis (par exemple sur Google Drive ou dans Téléchargements), puis la met à jour quand tu " +
        "quittes l'app après un changement. Le fichier est chiffré (AES-256-GCM) avec une clé tirée de ton mot de passe " +
        "(PBKDF2, 310 000 itérations) : sans le mot de passe, il est illisible, y compris pour le service qui le stocke " +
        "et pour le développeur. Le mot de passe n'est jamais enregistré ; seule la clé qui en est tirée est gardée, chiffrée " +
        "par l'Android Keystore, pour refaire la copie automatiquement. La sauvegarde ne contient ni ta clé API, ni ton " +
        "consentement IA, ni tes réglages de sécurité. Si tu choisis un service en ligne, c'est lui qui stocke le fichier " +
        "chiffré, selon ses propres conditions. Mot de passe oublié = sauvegarde perdue.",
    "Signaler un contenu de l'IA" to
        "Sous chaque réponse de l'IA, un bouton « Signaler » te permet de prévenir le développeur d'un contenu faux, " +
        "dangereux ou choquant. Rien n'est envoyé automatiquement : l'app prépare un e-mail que tu peux relire et modifier " +
        "avant de l'envoyer toi-même depuis ta messagerie. Il contient le motif choisi et le texte signalé, jamais tes " +
        "données de santé. Cet e-mail est utilisé uniquement pour traiter le signalement, puis supprimé.",
    "Retirer ton consentement" to
        "Tu peux désactiver l'IA à tout moment dans Paramètres › Intelligence artificielle. " +
        "Plus rien n'est alors envoyé. Ce retrait ne remet pas en cause les demandes faites avant. " +
        "Pour retirer ton accord au traitement de tes données de santé, efface tes données (Paramètres) ou désinstalle l'app.",
    "Le sommeil" to
        "La détection automatique utilise la Sleep API des services Google Play, calculée sur le téléphone. " +
        "GoodLife ne reçoit que les heures de coucher et de réveil, stockées localement et jamais envoyées.",
    "Tes droits (RGPD)" to
        "Accès et portabilité : Paramètres › Exporter mes données. Rectification : Profil › Modifier mes infos. " +
        "Effacement : Paramètres › Effacer toutes mes données (ou désinstaller l'app) ; un fichier de sauvegarde que tu as créé " +
        "est à supprimer toi-même là où tu l'as rangé. " +
        "Retrait du consentement : interrupteur IA. Comme GoodLife n'a pas de serveur, toutes tes données sont sur ton téléphone " +
        "(et dans ta sauvegarde chiffrée, si tu en as créé une). Pour toute question : matheo.greneche0@gmail.com. " +
        "Tu peux aussi adresser une réclamation à la CNIL (cnil.fr).",
    "Ce que GoodLife ne fait pas" to
        "Aucune donnée n'est vendue, louée ou partagée à des fins publicitaires. Pas de publicité, pas de traceur, " +
        "pas de profilage marketing. GoodLife n'a pas de serveur : le développeur ne reçoit aucune de tes données.",
    "Important" to
        "GoodLife est une application de bien-être. Ce n'est pas un dispositif médical : elle ne permet pas de diagnostiquer, " +
        "traiter, guérir ou prévenir une maladie. Les calories, objectifs et conseils (calculés ou donnés par l'IA) sont des " +
        "estimations indicatives. Demande l'avis d'un médecin ou d'un professionnel de santé avant de changer ton " +
        "alimentation, surtout en cas de maladie, de grossesse ou de troubles du comportement alimentaire."
)

@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        ScreenColumn {
            SubScreenHeader("Confidentialité", onBack)
            Text(
                "Version 0.6 · mise à jour le 30 septembre 2026",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            PRIVACY_SECTIONS.filter { BuildConfig.SELF_UPDATE || it.first != PRIVACY_GITHUB_ONLY }.forEach { (title, body) ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                    Text(body, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
