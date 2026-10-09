package com.goodlife.app.ui.screens

import com.goodlife.app.i18n.t

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Alignment
import com.goodlife.app.ui.ChefMascot
import com.goodlife.app.ui.ChefMood
import com.goodlife.app.ui.SlideSwitch
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.goodlife.app.data.Repo
import com.goodlife.app.ui.SectionCard

fun enableAi() = Repo.updateSettings {
    it.copy(aiEnabled = true, aiConsentAsked = true, aiConsentAt = System.currentTimeMillis())
}

fun disableAi() = Repo.updateSettings { it.copy(aiEnabled = false, aiConsentAsked = true, coachConsentAt = 0L) }

/** Explication honnête de ce que l'activation de l'IA implique. */
@Composable
fun AiConsentText() {
    if (com.goodlife.app.ai.AiAccess.viaRelay) { AiConsentTextRelay(); return }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            t("Les fonctions IA (analyse des photos, objectif calorique, idées de repas, recettes, coach) utilisent Google Gemini avec ta propre clé API, que tu crées toi-même chez Google (Google AI Studio). En la créant, tu acceptes les conditions de Google (18 ans minimum) ; l'éventuelle facturation se fait entre toi et Google. Si tu actives l'IA :"),
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            t("• La photo du repas et tes allergies sont envoyées à Google pour l'analyse.\n• Pour les recommandations : âge, sexe, poids, taille, activité, objectif, habitudes, allergies et repas du jour. Pour le coach, en plus : tes chiffres des 7 derniers jours (scores, calories, pas, séances, sorties, évolution du poids) et ton planning. Ce sont des données de santé.\n• Elles partent directement de ton téléphone vers Google, sous ton propre compte Google.\n• Google peut les conserver temporairement et les traiter hors de l'UE. Selon ses conditions, pour les utilisateurs situés dans l'UE, Google ne s'en sert pas pour améliorer ses produits.\n• Les réponses de l'IA sont des estimations et peuvent être fausses : vérifie-les.\n• Ton prénom, ton sommeil et tes positions GPS ne sont jamais envoyés."),
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            t("Ces données ne sont ni vendues ni utilisées pour de la publicité par Lifoody. Réservé aux 18 ans et plus. Tu peux désactiver l'IA à tout moment dans Paramètres. Sans IA, tout le reste fonctionne : saisie manuelle, scan de code-barres, planning, sommeil, quiz."),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Version Google Play : l'IA passe par le relais Lifoody (Fanix Studio), qui transmet à Google Gemini avec sa propre clé
 * sans rien enregistrer du contenu. Consentement explicite (données de santé, RGPD art. 9.2.a).
 */
@Composable
private fun AiConsentTextRelay() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            t("Les fonctions IA (analyse des photos, objectif calorique, idées de repas, recettes, coach) utilisent Google Gemini, via le service IA de Lifoody édité par Fanix Studio. Aucune clé à créer : 3 essais gratuits, puis l'abonnement Lifoody Premium. Si tu actives l'IA :"),
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            t("• La photo du repas et tes allergies sont envoyées pour l'analyse.\n• Pour les recommandations : âge, sexe, poids, taille, activité, objectif, habitudes, allergies et repas du jour. Pour le coach, en plus : tes chiffres des 7 derniers jours (scores, calories, pas, séances, sorties, évolution du poids) et ton planning. Ce sont des données de santé.\n• Elles passent par le serveur de Lifoody (hébergé par Cloudflare), qui les transmet aussitôt à Google sans les enregistrer : ni photo, ni message, ni réponse n'y sont gardés. Il ne garde que des compteurs d'utilisation, liés à un identifiant aléatoire.\n• Google les traite pour répondre et peut les conserver temporairement (par exemple pour détecter les abus), parfois hors de l'UE. Avec l'offre payante de l'API Gemini utilisée par Lifoody, Google ne s'en sert pas pour améliorer ses produits.\n• Les réponses de l'IA sont des estimations et peuvent être fausses : vérifie-les.\n• Ton prénom, ton sommeil et tes positions GPS ne sont jamais envoyés."),
            style = MaterialTheme.typography.bodyMedium
        )
        Text(
            t("Ces données ne sont ni vendues ni utilisées pour de la publicité. Réservé aux 18 ans et plus. Tu peux désactiver l'IA à tout moment dans Paramètres. Sans IA, tout le reste fonctionne : saisie manuelle, scan de code-barres, planning, sommeil, quiz."),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Écran affiché une fois après le profil (et aux utilisateurs qui mettent à jour). */
@Composable
fun AiChoiceScreen() {
    val profile by Repo.profile.collectAsState()
    val adult = (profile?.age ?: 0) >= 18
    var showPolicy by remember { mutableStateOf(false) }
    SlideSwitch(showPolicy) { open ->
    if (open) {
        Surface(Modifier.fillMaxSize().safeDrawingPadding(), color = MaterialTheme.colorScheme.background) {
            PrivacyScreen(onBack = { showPolicy = false })
        }
    } else Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = 640.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ChefMascot(size = 96.dp, mood = ChefMood.QUESTION)
            Text(t("Activer l'IA ?"), style = MaterialTheme.typography.headlineMedium)
            SectionCard(title = t("Ce que ça implique"), icon = Icons.Filled.AutoAwesome) {
                AiConsentText()
                TextButton(onClick = { showPolicy = true }) { Text(t("Lire la politique de confidentialité")) }
            }
            if (adult) {
                Button(onClick = { enableAi() }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text(t("J'accepte, activer l'IA"))
                }
            } else {
                Text(
                    t("Les fonctions IA sont réservées aux 18 ans et plus. Tu peux utiliser tout le reste de l'app."),
                    color = MaterialTheme.colorScheme.error
                )
            }
            OutlinedButton(onClick = { disableAi() }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(t("Continuer sans IA"))
            }
        }
        }
    }
    }
}

/** Dialogue de consentement utilisé depuis les Paramètres ou l'écran Scanner. */
@Composable
fun AiConsentDialog(onDismiss: () -> Unit, onAccepted: () -> Unit = {}) {
    val profile by Repo.profile.collectAsState()
    val adult = (profile?.age ?: 0) >= 18
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("Activer l'IA ?")) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                AiConsentText()
                if (!adult) {
                    Text(
                        t("\nRéservé aux 18 ans et plus : l'IA ne peut pas être activée avec l'âge de ton profil."),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            if (adult) {
                TextButton(onClick = { enableAi(); onAccepted(); onDismiss() }) { Text(t("J'accepte")) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Annuler")) } }
    )
}
