package com.goodlife.app.ui.screens

import com.goodlife.app.i18n.t

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
import androidx.compose.material.icons.filled.Newspaper
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

/** Une question simple et sa réponse (pour « En bref »). */
@Composable
private fun QA(q: String, a: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(q, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
        Text(a, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * La confidentialité en mots simples, pour tout le monde (avant le détail technique). Les réponses doivent rester
 * exactes : si une nouvelle donnée quitte le téléphone, mettre à jour ce texte, DataFlowSummary et PRIVACY.md.
 */
@Composable
fun PlainPrivacy() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        QA(t("Quelqu'un peut-il voir mes données ?"),
            t("Non. Tes repas, ton poids, ton sommeil et le reste sont enregistrés seulement sur ton téléphone, verrouillés (chiffrés). Il n'y a pas de compte en ligne : ni le développeur, ni tes amis, ni une autre personne ne peut les lire."))
        if (com.goodlife.app.ai.AiAccess.viaRelay) QA(t("Et si j'utilise l'IA ?"),
            t("C'est toi qui choisis de l'activer. Quand tu prends un repas en photo ou que tu parles au chef, la photo ou la question passe par le serveur de Lifoody, qui la transmet à Google (Gemini) pour avoir la réponse, sans rien garder. Elle ne sert pas à entraîner l'IA. Ton prénom, ton sommeil et tes trajets ne sont jamais envoyés."))
        else QA(t("Et si j'utilise l'IA ?"),
            t("C'est toi qui choisis de l'activer. Quand tu prends un repas en photo ou que tu parles au chef, la photo ou la question part chez Google (Gemini), avec ta propre clé, le temps d'avoir la réponse. Elle ne sert pas à entraîner l'IA. Ton prénom, ton sommeil et tes trajets ne sont jamais envoyés."))
        QA(t("Mes amis voient quoi ?"),
            t("Seulement ce que tu choisis de partager (pseudo, niveau, série…), et seulement si tu rends ton profil public. Jamais tes repas, ton poids ni ton sommeil."))
        QA(t("Le chef qui m'envoie des messages, il me surveille ?"),
            t("Non : ses messages sont calculés par l'app, sur ton téléphone, sans internet. Ce qu'il apprend (tes heures de repas…) reste chez toi, et tu peux l'effacer dans Paramètres › Le coach."))
        QA(t("Et si je perds mon téléphone ?"),
            t("Sans sauvegarde, tes données sont perdues avec lui. Avec la sauvegarde, tu les retrouves grâce à ton mot de passe, que personne d'autre ne connaît."))
        QA(t("Je peux tout effacer ?"),
            t("Oui, quand tu veux : Paramètres › Mes données et confidentialité. Rien n'est vendu, et il n'y a pas de publicité."))
    }
}

/** Ce qui quitte le téléphone, et vers qui. Utilisé au démarrage, pour l'IA et dans les Paramètres. */
@Composable
fun DataFlowSummary(showAi: Boolean = true) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            t("Seules ces fonctions contactent internet. Les actus et la vérification des mises à jour sont actives par défaut (tu peux les couper) ; tout le reste, uniquement quand tu l'utilises :"),
            style = MaterialTheme.typography.bodyMedium
        )
        if (showAi) {
            if (com.goodlife.app.ai.AiAccess.viaRelay) FlowLine(
                Icons.Filled.AutoAwesome, t("IA (si tu l'actives) → serveur de Lifoody → Google Gemini"),
                t("Photo du repas (ou du frigo, du ticket de caisse, ou celle que tu joins au coach) et allergies pour l'analyse. Pour les conseils : âge, sexe, poids, taille, activité, objectif, habitudes, allergies et repas du jour (données de santé) ; pour le coach, aussi tes chiffres de la semaine (scores, pas, sport, poids), ton planning, ton programme, le contenu de « Mes provisions » et, si tu demandes « Le chef t'explique », les chiffres de ton bilan de la semaine (dont l'eau). Le serveur de Lifoody (Fanix Studio, hébergé par Cloudflare) vérifie ton abonnement et transmet à Google sans rien enregistrer ; il ne garde que des compteurs d'utilisation. Jamais ton prénom, ton sommeil ni tes positions.")
            ) else FlowLine(
                Icons.Filled.AutoAwesome, t("IA (si tu l'actives) → Google Gemini"),
                t("Photo du repas (ou du frigo, du ticket de caisse, ou celle que tu joins au coach) et allergies pour l'analyse. Pour les conseils : âge, sexe, poids, taille, activité, objectif, habitudes, allergies et repas du jour (données de santé) ; pour le coach, aussi tes chiffres de la semaine (scores, pas, sport, poids), ton planning, ton programme, le contenu de « Mes provisions » et, si tu demandes « Le chef t'explique », les chiffres de ton bilan de la semaine (dont l'eau). Envoyé avec ta propre clé. Jamais ton prénom, ton sommeil ni tes positions.")
            )
        }
        FlowLine(
            Icons.Filled.QrCodeScanner, t("Scan de code-barres → Open Food Facts"),
            t("Uniquement le numéro du code-barres. L'image est analysée sur le téléphone.")
        )
        if (BuildConfig.SELF_UPDATE) FlowLine(
            Icons.Filled.SystemUpdate, t("Mises à jour → GitHub"),
            t("Ton adresse IP, comme pour n'importe quel site, pour savoir s'il existe une nouvelle version.")
        )
        FlowLine(
            Icons.Filled.Map, t("Carte → OpenFreeMap et OpenStreetMap"),
            t("La zone affichée, et celle où tu prépares une boucle ou un itinéraire, cherches des clubs ou télécharges une carte hors ligne ; pour le dénivelé, les tuiles d'altitude de cette zone (Terrain Tiles, Amazon) ; pour la recherche d'un lieu, le texte tapé (Nominatim). Ta position exacte et tes tracés GPS restent sur le téléphone.")
        )
        FlowLine(
            Icons.Filled.Newspaper, t("Actus du jour → sites d'actualité"),
            t("Une fois par jour, l'app lit les flux publics de franceinfo, Sciences et Avenir, Futura, Anses et Santé publique France. Ces sites voient ton adresse IP, rien d'autre. Un article ne s'ouvre chez eux que si tu le touches. Avec l'IA, le « Résumé du chef » d'un article public envoie seulement le texte de cet article à Gemini.")
        )
        FlowLine(
            Icons.Filled.Group, t("Amis (si ton profil est public) → le téléphone de tes amis"),
            t("Seulement ton pseudo et ce que tu choisis (niveau, série, Nutridex, bilan de la semaine), de téléphone à téléphone, ou par un message que tu envoies toi-même (avec un lien vers une page d'invitation sur GitHub Pages, qui ne reçoit jamais ta carte). Jamais par un serveur Lifoody.")
        )
        FlowLine(
            Icons.Filled.Backup, t("Sauvegarde (si tu l'actives) → l'endroit que tu choisis"),
            t("Un fichier chiffré avec ton mot de passe, illisible sans lui (même pour Google Drive ou le développeur).")
        )
        FlowLine(
            Icons.Filled.Block, t("Aucune revente"),
            if (com.goodlife.app.ai.AiAccess.viaRelay) t("Aucune donnée n'est vendue, louée ou utilisée pour de la publicité. Le serveur IA de Lifoody ne garde ni photo, ni message, ni donnée de santé : seulement des compteurs d'utilisation.")
            else t("Aucune donnée n'est vendue, louée ou utilisée pour de la publicité. Lifoody n'a pas de serveur : le développeur ne reçoit rien.")
        )
    }
}
