package com.goodlife.app.ui.screens

import com.goodlife.app.i18n.t

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
    t("Qui est responsable ?") to
        t("GoodLife est un projet personnel développé par Mathéo Greneche, avec l'assistance d'une IA. L'app n'a pas de serveur et n'envoie aucune donnée à son développeur. Contact (questions, exercice de tes droits, signalements) : matheo.greneche0@gmail.com."),
    t("Qui peut utiliser GoodLife ?") to
        t("L'app est réservée aux personnes de 15 ans et plus (âge à partir duquel on peut consentir seul au traitement de ses données en France). Les fonctions IA sont réservées aux 18 ans et plus. L'objectif « Perdre du poids » n'est pas proposé avant 18 ans, ni quand l'IMC est déjà inférieur à 18,5. Les croisements (rencontres avec des inconnus) sont réservés aux 18 ans et plus."),
    t("Ce qui reste sur ton téléphone") to
        t("Ton profil (âge, sexe, poids, taille, activité, objectif, habitudes, allergies), ta photo de profil, tes repas, ton emploi du temps de repas, ton sommeil, tes pas, tes pesées, ta progression (séries, niveaux, quiz), ton Nutridex et ses photos, la liste de tes amis et rencontres, ton programme sportif, tes activités GPS (tracés), les actus déjà lues, les questions de quiz déjà posées, l'eau bue, ton humeur et ton énergie du jour, ta liste de courses, tes missions et badges, et tes réglages. Certaines de ces informations sont des données de santé. Elles sont chiffrées sur le téléphone (AES-256, Android Keystore), sans compte, et aucune copie n'est faite sans ton accord. Elles ne sont envoyées nulle part tant que l'IA et la sauvegarde sont désactivées. Base légale : ton consentement explicite (case à cocher au premier lancement). Durée : tant que l'app est installée (repas : 1 an, emploi du temps : 3 mois)."),
    t("Ce qui est envoyé si tu actives l'IA") to
        t("L'IA est désactivée par défaut, demande un consentement séparé (réservé aux 18 ans et plus) et ta propre clé API Gemini, que tu crées toi-même chez Google. Si tu l'actives, seules les données nécessaires à chaque demande sont envoyées :\n• Analyse de photo : la photo du repas et tes allergies.\n• Objectif calorique : âge, sexe, poids, taille, activité, objectif, habitudes, allergies.\n• Idées de repas : ton objectif, les repas du jour, tes habitudes et allergies.\n• Recette : le nom du plat, tes habitudes et allergies.\n• Planning de la semaine : ton objectif, tes habitudes et allergies, ton budget et le nombre de personnes.\n• Objectif de pas (si tu choisis « Conseil de l'IA ») : âge, sexe, activité, objectif, tes pas et objectifs des 7 derniers jours. Envoyé une fois par jour, automatiquement (à la première ouverture de l'app ou au relevé des pas), tant que ce mode est choisi.\n• Liste de courses (si tu la prépares avec l'IA) : les repas prévus (noms, descriptions, ingrédients des recettes) et le nombre de personnes.\n• « J'ai ça dans mon frigo » : la photo de ton frigo ou de tes placards (si tu en prends une) et ce que tu écris, avec tes allergies, habitudes, objectif et calories restantes. La photo est supprimée du téléphone juste après l'analyse.\n• Programme sportif : âge, sexe, poids, taille, activité, but, niveau, matériel, et les envies et douleurs ou limites que tu écris (données de santé).\n• Questions à l'IA (sur une photo, une recette, ton programme ou une actu) : tes questions, le contexte concerné (photo et analyse, recette, programme, ou titre, extrait et lien de l'actu), tes allergies et habitudes. Pour une actu, l'IA peut faire des recherches Google (outil de recherche de Gemini) pour savoir de quoi parle l'article : Google reçoit alors les recherches que l'IA formule ; les sources et les suggestions de recherche de Google sont affichées avec la réponse. La conversation n'est pas gardée après fermeture.\n• Coach (« Parler au chef ») : tes questions, et pour personnaliser ses conseils : âge, sexe, poids, taille, activité, objectif calorique et macros, habitudes, allergies, repas du jour, pas du jour, et un résumé des 7 derniers jours (jours validés, score moyen, calories moyennes, total de pas, séances de sport, nombre et distance des sorties, évolution du poids, série en cours), ton programme sportif et les repas déjà prévus au planning pour les 7 prochains jours. Ces envois demandent ton accord, une fois, avant ta première question au coach (retiré si tu désactives l'IA). Les repas qu'il propose ne sont ajoutés au planning que si tu appuies sur « Ajouter ». La conversation reste en mémoire tant que l'app est ouverte, sans être enregistrée.\nNe sont jamais envoyés : ton prénom, ton sommeil, tes photos (sauf celle que tu fais analyser), tes positions GPS, ton historique complet. Les réponses de l'IA sont des estimations et peuvent contenir des erreurs ; elles sont signalées comme générées par l'IA."),
    t("À qui ces données sont envoyées") to
        t("• Google LLC (API Gemini), directement depuis ton téléphone, avec ta propre clé API et donc sous ton propre compte Google : en créant ta clé, tu acceptes toi-même les conditions de Google, et l'éventuelle facturation se fait entre toi et Google. Google traite ces données selon les conditions de l'API Gemini ; elles peuvent être conservées temporairement par Google (par exemple pour détecter les abus) et traitées hors de l'Union européenne. Selon ces conditions (version du 28 avril 2026), pour les utilisateurs situés dans l'Espace économique européen, Google n'utilise pas les demandes ni les réponses pour améliorer ses produits, même sur son offre sans frais. Google est une entreprise américaine adhérente au cadre de protection des données UE–États-Unis (Data Privacy Framework).\n• GoodLife n'a aucun serveur : le développeur ne reçoit et ne voit aucune de tes données."),
    t("Ta clé API") to
        t("Ta clé Gemini est chiffrée sur le téléphone (Android Keystore), conservée lors des mises à jour de l'app et envoyée uniquement à Google, dans l'en-tête des requêtes. Elle n'apparaît dans aucune exportation ni sauvegarde."),
    t("Scan de code-barres (sans IA)") to
        t("Le code-barres est lu sur le téléphone avec ML Kit (modèle intégré, aucune image envoyée). Seul le numéro du code-barres est envoyé à Open Food Facts (association française, base de données ouverte) pour obtenir les valeurs nutritionnelles. Open Food Facts voit ton adresse IP, comme n'importe quel site web."),
    t("Les pas") to
        t("Si tu actives le suivi des pas, ils sont comptés sur le téléphone : par le capteur de pas (relevé toutes les 15 minutes, lecture d'un simple compteur) ou, si tu le choisis, en lecture seule dans Health Connect (pas enregistrés par Samsung Health, Google Fit, une montre…). GoodLife ne lit que le nombre de pas, rien d'autre, et ne les envoie nulle part, sauf ta moyenne de pas si tu demandes un objectif à l'IA. Tu peux couper le suivi dans Paramètres › Pas."),
    t("Activités GPS (course, marche, vélo)") to
        t("Ta position n'est utilisée que pendant une activité que tu lances toi-même (autorisation « pendant l'utilisation »). Tant qu'elle est en cours, une notification l'indique et le suivi continue écran éteint. Le tracé, le temps, la distance, la vitesse et le dénivelé sont enregistrés chiffrés sur ton téléphone, jamais envoyés, et inclus dans ta sauvegarde chiffrée si tu l'as activée. Tu peux supprimer une activité à tout moment. Pas de localisation en arrière-plan."),
    t("Carte et clubs") to
        t("Le fond de carte vient d'OpenFreeMap (données © contributeurs OpenStreetMap, © OpenMapTiles) : comme pour toute appli de carte, le serveur voit ton adresse IP et la zone affichée. Quand tu cherches des clubs, des boucles ou un itinéraire vers une destination, les coordonnées de la zone concernée sont envoyées à l'API Overpass d'OpenStreetMap (serveur public géré par une association allemande) pour récupérer les chemins ou les clubs ; le calcul de l'itinéraire se fait ensuite sur ton téléphone. Les cartes hors ligne sont téléchargées depuis OpenFreeMap pour la seule zone que tu choisis, et tu peux les supprimer à tout moment. Les informations des clubs (tarifs, horaires, site) viennent d'OpenStreetMap et peuvent être incomplètes : vérifie-les auprès du club."),
    t("Notifications du coach") to
        t("Désactivées tant que tu ne les as pas acceptées (à l'inscription ou dans Paramètres › Coach et notifications). Le bilan du matin, le mot de midi, le rappel du soir et le bilan de la semaine sont préparés sur ton téléphone, sans réseau ni IA et sans aucun serveur. Sur l'écran verrouillé, seul « Un message du chef » s'affiche, sans tes chiffres ; si le verrouillage par empreinte est activé, les chiffres ne s'affichent nulle part dans les notifications. Tu peux couper chaque notification à tout moment."),
    t("Actus du jour") to
        t("Tu choisis tes thèmes (alimentation, sport, santé et bien-être, insolite, anecdote du jour), ou aucun : les actus sont alors désactivées. Une fois par jour (à l'ouverture de l'accueil ou des actus), GoodLife lit les flux RSS publics de franceinfo, Sciences et Avenir, Futura, de l'Anses et de Santé publique France pour choisir des actus sur tes thèmes. Ces sites voient ton adresse IP, comme pour n'importe quel site ; aucune autre donnée ne leur est envoyée. Seul le titre est affiché (avec un court extrait pour l'Anses et Santé publique France) ; l'article complet s'ouvre chez la source, dans ton navigateur, seulement si tu le touches. La liste des actus déjà montrées reste chiffrée sur ton téléphone pour ne jamais te remontrer la même. Sans connexion, l'app affiche à la place des anecdotes vérifiées intégrées.\n• Résumé du chef (si l'IA est activée) : pour un article de l'Anses ou de Santé publique France, l'app lit la page publique de l'article et envoie son titre et son texte à Google Gemini, avec ta clé, pour le résumer. Aucune donnée te concernant n'est envoyée. Le résumé n'est pas gardé."),
    t("Widgets") to
        t("Si tu ajoutes un widget GoodLife sur ton écran d'accueil, il affiche le chef, ta série et, pour le grand widget, ton score du jour, tes calories, tes pas et un petit mot. Tout est calculé sur le téléphone. Les widgets sont visibles par toute personne qui voit ton écran d'accueil : si le verrouillage par empreinte est activé, ils n'affichent aucun chiffre de santé."),
    t("Le Nutridex") to
        t("Quand l'IA reconnaît un aliment sur une photo, il se débloque dans ton Nutridex avec une petite vignette de ta photo. Ces vignettes sont chiffrées sur ton téléphone et ne sont jamais partagées : tes amis ne voient, si tu l'autorises, que la liste des aliments découverts."),
    t("Amis : Tap to Sync, QR code et croisements") to
        t("Ton profil est privé par défaut. Si tu le rends public, tu choisis un pseudo et ce que tu partages (niveau, série, liste du Nutridex, bilan de la semaine pour le défi entre amis : jours validés, total de pas et XP de la semaine). Ces informations forment une « carte » signée par ton téléphone, transmise directement à l'autre téléphone, sans aucun serveur : en collant les téléphones (NFC), en scannant ton QR code, avec les croisements, ou par un message que tu envoies toi-même (« Partager ma carte ») : la carte passe alors par la messagerie que tu choisis (WhatsApp, SMS…), selon ses propres conditions, jamais par un serveur GoodLife. Ne sont jamais partagés : ton poids, tes repas, ton sommeil, tes photos, ton âge.\n• Croisements (désactivés par défaut, 18 ans et plus, Android 12+) : tant qu'ils sont actifs, une notification discrète l'indique (Android l'impose) et les téléphones GoodLife à quelques mètres peuvent lire ta carte en Bluetooth. Une notification te prévient quand tu croises quelqu'un pour la première fois ou que tu le recroises. L'app ne demande pas la localisation. Tu peux masquer et bloquer une personne.\n• Les cartes que tu reçois sont gardées chiffrées sur ton téléphone ; retire ou bloque une personne pour effacer la sienne.\n• Les encouragements sont des messages tout prêts, sans texte libre. Quand une carte reçue contient un encouragement pour toi, une notification te prévient (préparée sur ton téléphone, sans serveur).\nBase légale : ton consentement (activation du profil public). Tu peux le retirer à tout moment en le rendant privé."),
    t(PRIVACY_GITHUB_ONLY) to
        t("Dans la version téléchargée depuis GitHub, si l'option est activée (Paramètres › Mises à jour), l'app demande à GitHub, au plus toutes les 30 minutes, quelle est la dernière version publiée ; si tu appuies sur « Installer », elle télécharge l'APK depuis la page officielle du projet. GitHub voit alors ton adresse IP ; aucune autre donnée n'est envoyée. Avant l'installation, l'app vérifie l'empreinte du fichier et qu'il est signé avec la même clé que l'app installée, puis Android te demande de confirmer. Rien n'est installé sans ton accord. La version Google Play n'a pas cette fonction : ses mises à jour passent par le Play Store."),
    t("Sauvegarde chiffrée (si tu l'actives)") to
        t("Désactivée par défaut. Si tu l'actives (Paramètres › Sauvegarde chiffrée), GoodLife écrit une copie de tes données dans le fichier que tu choisis (par exemple sur Google Drive ou dans Téléchargements), puis la met à jour quand tu quittes l'app après un changement. Le fichier est chiffré (AES-256-GCM) avec une clé tirée de ton mot de passe (PBKDF2, 310 000 itérations) : sans le mot de passe, il est illisible, y compris pour le service qui le stocke et pour le développeur. Le mot de passe n'est jamais enregistré ; seule la clé qui en est tirée est gardée, chiffrée par l'Android Keystore, pour refaire la copie automatiquement. La sauvegarde ne contient ni ta clé API, ni ton consentement IA, ni tes réglages de sécurité. Si tu choisis un service en ligne, c'est lui qui stocke le fichier chiffré, selon ses propres conditions. Mot de passe oublié = sauvegarde perdue."),
    t("Signaler un contenu de l'IA") to
        t("Sous chaque réponse de l'IA, un bouton « Signaler » te permet de prévenir le développeur d'un contenu faux, dangereux ou choquant. Rien n'est envoyé automatiquement : l'app prépare un e-mail que tu peux relire et modifier avant de l'envoyer toi-même depuis ta messagerie. Il contient le motif choisi et le texte signalé, jamais tes données de santé. Cet e-mail est utilisé uniquement pour traiter le signalement, puis supprimé."),
    t("Retirer ton consentement") to
        t("Tu peux désactiver l'IA à tout moment dans Paramètres › Intelligence artificielle. Plus rien n'est alors envoyé. Ce retrait ne remet pas en cause les demandes faites avant. Pour retirer ton accord au traitement de tes données de santé, efface tes données (Paramètres) ou désinstalle l'app."),
    t("Le sommeil") to
        t("La détection automatique utilise la Sleep API des services Google Play, calculée sur le téléphone. GoodLife ne reçoit que les heures de coucher et de réveil, stockées localement et jamais envoyées."),
    t("Tes droits (RGPD)") to
        t("Accès et portabilité : Paramètres › Exporter mes données. Rectification : Profil › Modifier mes infos. Effacement : Paramètres › Effacer toutes mes données (ou désinstaller l'app) ; un fichier de sauvegarde que tu as créé est à supprimer toi-même là où tu l'as rangé. Retrait du consentement : interrupteur IA, profil privé (Amis). Comme GoodLife n'a pas de serveur, toutes tes données sont sur ton téléphone (et dans ta sauvegarde chiffrée, si tu en as créé une). Pour toute question : matheo.greneche0@gmail.com. Tu peux aussi adresser une réclamation à la CNIL (cnil.fr)."),
    t("Ce que GoodLife ne fait pas") to
        t("Aucune donnée n'est vendue, louée ou partagée à des fins publicitaires. Pas de publicité, pas de traceur, pas de profilage marketing. GoodLife n'a pas de serveur : le développeur ne reçoit aucune de tes données."),
    t("Important") to
        t("GoodLife est une application de bien-être. Ce n'est pas un dispositif médical : elle ne permet pas de diagnostiquer, traiter, guérir ou prévenir une maladie. Les calories, objectifs et conseils (calculés ou donnés par l'IA) sont des estimations indicatives. Demande l'avis d'un médecin ou d'un professionnel de santé avant de changer ton alimentation, surtout en cas de maladie, de grossesse ou de troubles du comportement alimentaire.")
)

@Composable
fun PrivacyScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        ScreenColumn {
            SubScreenHeader(t("Confidentialité"), onBack)
            Text(
                t("Version 0.9.6 · mise à jour le 1er octobre 2026"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            PRIVACY_SECTIONS.filter { BuildConfig.SELF_UPDATE || it.first != t(PRIVACY_GITHUB_ONLY) }.forEach { (title, body) ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                    Text(body, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
