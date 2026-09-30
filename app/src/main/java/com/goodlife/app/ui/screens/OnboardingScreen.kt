package com.goodlife.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.goodlife.app.ai.Nutrition
import com.goodlife.app.data.Profile
import com.goodlife.app.data.Repo
import com.goodlife.app.ui.SectionCard

@Composable
fun OnboardingScreen() {
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
                    "Profil, repas et sommeil sont chiffrés localement. Rien n'est sauvegardé dans le cloud.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            ProfileForm(initial = Profile(), saveLabel = "Commencer") { p ->
                Repo.saveProfile(Nutrition.formulaTarget(p))
            }
        }
    }
}
