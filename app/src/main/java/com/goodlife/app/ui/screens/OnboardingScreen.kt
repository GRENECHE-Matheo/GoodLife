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
                SectionCard(title = "Tes données", icon = Icons.Filled.Lock) {
                    DataFlowSummary()
                    TextButton(onClick = onOpenPolicy) { Text("Politique de confidentialité complète") }
                }
                ProfileForm(initial = Profile(), saveLabel = "Commencer") { p ->
                    Repo.updateSettings { it.copy(privacyAcceptedAt = System.currentTimeMillis()) }
                    Repo.saveProfile(Nutrition.formulaTarget(p))
                }
            }
        }
    }
}
