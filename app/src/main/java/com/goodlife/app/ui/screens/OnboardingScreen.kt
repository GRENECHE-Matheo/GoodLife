package com.goodlife.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.goodlife.app.ai.Nutrition
import com.goodlife.app.data.Profile
import com.goodlife.app.data.Repo
import com.goodlife.app.ui.SectionCard

@Composable
fun OnboardingScreen() {
    var accepted by remember { mutableStateOf(false) }
    var showPolicy by remember { mutableStateOf(false) }
    var mustAccept by remember { mutableStateOf(false) }
    if (showPolicy) {
        PrivacyScreen(onBack = { showPolicy = false })
        return
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Bienvenue sur GoodLife", style = MaterialTheme.typography.headlineLarge)
            Text(
                "Quelques infos pour calculer tes besoins et adapter les idées de repas.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SectionCard(title = "Tes données restent sur ce téléphone", icon = Icons.Filled.Lock) {
                Text(
                    "Ton poids, tes repas, tes allergies ou ton sommeil sont des données de santé. GoodLife les garde " +
                        "chiffrées sur ce téléphone, sans compte ni cloud. Rien n'est envoyé à qui que ce soit, " +
                        "sauf si tu actives plus tard les fonctions IA (on te demandera ton accord séparément).",
                    style = MaterialTheme.typography.bodyMedium
                )
                TextButton(onClick = { showPolicy = true }) { Text("Lire la politique de confidentialité") }
                Row(
                    Modifier.fillMaxWidth().toggleable(value = accepted, onValueChange = { accepted = it; mustAccept = false }, role = Role.Checkbox),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = accepted, onCheckedChange = null)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "J'accepte que mes données de santé soient traitées sur ce téléphone pour le suivi GoodLife.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (mustAccept) {
                    Text("Coche cette case pour continuer.", color = MaterialTheme.colorScheme.error)
                }
            }
            ProfileForm(initial = Profile(), saveLabel = "Commencer") { p ->
                if (!accepted) {
                    mustAccept = true
                } else {
                    Repo.updateSettings { it.copy(privacyAcceptedAt = System.currentTimeMillis()) }
                    Repo.saveProfile(Nutrition.formulaTarget(p))
                }
            }
        }
    }
}
