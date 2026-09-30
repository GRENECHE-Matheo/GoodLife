package com.goodlife.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.goodlife.app.ai.Nutrition
import com.goodlife.app.data.Profile
import com.goodlife.app.data.Repo
import com.goodlife.app.ui.ChefMascot
import com.goodlife.app.ui.ChefMood
import com.goodlife.app.ui.SectionCard
import com.goodlife.app.ui.SlideSwitch

@Composable
fun OnboardingScreen() {
    var showPolicy by rememberSaveable { mutableStateOf(false) }
    SlideSwitch(showPolicy) { open ->
        if (open) {
            Surface(Modifier.fillMaxSize().safeDrawingPadding(), color = MaterialTheme.colorScheme.background) {
                PrivacyScreen(onBack = { showPolicy = false })
            }
        } else {
            OnboardingContent(onOpenPolicy = { showPolicy = true })
        }
    }
}

@Composable
private fun OnboardingContent(onOpenPolicy: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 640.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ChefMascot(size = 96.dp, mood = ChefMood.BRAVO)
                Text("Bienvenue sur GoodLife", style = MaterialTheme.typography.headlineLarge)
                Text(
                    "Quelques infos pour calculer tes besoins et adapter les idées de repas.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "Réservé aux ${Nutrition.MIN_AGE} ans et plus. GoodLife est une app de bien-être, pas un dispositif " +
                        "médical : elle ne diagnostique, ne traite ni ne prévient aucune maladie. Demande l'avis d'un " +
                        "professionnel de santé avant de changer ton alimentation.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SectionCard(title = "Tes données", icon = Icons.Filled.Lock) {
                    DataFlowSummary()
                    TextButton(onClick = onOpenPolicy) { Text("Politique de confidentialité complète") }
                }
                SectionCard(title = "Tu changes de téléphone ?", icon = Icons.Filled.Restore) {
                    Text(
                        "Si tu as une sauvegarde GoodLife (fichier .goodlife), restaure-la avec son mot de passe : " +
                            "tu retrouves ton profil, tes repas, ton sommeil et ta progression.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    RestoreButton(outlined = false)
                }
                ProfileForm(
                    initial = Profile(), saveLabel = "Commencer",
                    consentText = "J'accepte que GoodLife enregistre sur ce téléphone mes données de santé " +
                        "(poids, taille, repas, sommeil, allergies) pour calculer mes besoins. " +
                        "Je peux retirer cet accord en effaçant mes données (Paramètres)."
                ) { p ->
                    Repo.updateSettings { it.copy(privacyAcceptedAt = System.currentTimeMillis()) }
                    Repo.saveProfile(Nutrition.formulaTarget(p))
                }
            }
        }
    }
}
