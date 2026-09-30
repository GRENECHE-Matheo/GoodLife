@file:OptIn(ExperimentalMaterial3Api::class)

package com.goodlife.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.goodlife.app.ai.Gemini
import com.goodlife.app.ai.Nutrition
import com.goodlife.app.data.Repo
import com.goodlife.app.ui.ScreenTitle
import com.goodlife.app.ui.SectionCard
import kotlinx.coroutines.launch

@Composable
fun ProfileScreen() {
    var showSettings by rememberSaveable { mutableStateOf(false) }
    if (showSettings) {
        BackHandler { showSettings = false }
        SettingsScreen(onBack = { showSettings = false })
    } else {
        ProfileContent(onOpenSettings = { showSettings = true })
    }
}

@Composable
private fun ProfileContent(onOpenSettings: () -> Unit) {
    val profile by Repo.profile.collectAsState()
    val settings by Repo.settings.collectAsState()
    val scope = rememberCoroutineScope()
    val p = profile ?: return

    var editing by remember { mutableStateOf(false) }
    var aiLoading by remember { mutableStateOf(false) }
    var aiError by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                ScreenTitle("Profil", "IMC ${"%.1f".format(Nutrition.bmi(p))}")
            }
            FilledTonalIconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, "Paramètres") }
        }

        SectionCard(title = "Objectif quotidien", icon = Icons.Filled.AutoAwesome) {
            Text("${p.targetKcal} kcal", style = MaterialTheme.typography.displaySmall)
            Text(
                "Protéines ${p.proteinG} g · Glucides ${p.carbsG} g · Lipides ${p.fatG} g",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                (if (p.targetSource == "ia") "Recommandé par l'IA : " else "") + p.targetExplanation,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (aiError != null) Text(aiError!!, color = MaterialTheme.colorScheme.error)
            FilledTonalButton(
                enabled = !aiLoading,
                onClick = {
                    aiLoading = true; aiError = null
                    scope.launch {
                        try {
                            Repo.saveProfile(Gemini(settings.apiKey, settings.model).recommendTarget(p))
                        } catch (e: Exception) {
                            aiError = e.message
                        } finally {
                            aiLoading = false
                        }
                    }
                }
            ) {
                if (aiLoading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Icon(Icons.Filled.AutoAwesome, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Calculer avec l'IA")
            }
        }

        SectionCard(title = "Mes infos", icon = Icons.Filled.Person) {
            if (editing) {
                ProfileForm(initial = p, saveLabel = "Enregistrer") { updated ->
                    Repo.saveProfile(Nutrition.formulaTarget(updated))
                    editing = false
                }
                TextButton(onClick = { editing = false }) { Text("Annuler") }
            } else {
                InfoLine("Prénom", p.name.ifBlank { "—" })
                InfoLine("Âge", "${p.age} ans")
                InfoLine("Sexe", p.sex.label)
                InfoLine("Poids", "${p.weightKg} kg")
                InfoLine("Taille", "${p.heightCm} cm")
                InfoLine("Activité", p.activity.label)
                InfoLine("Objectif", p.goal.label)
                InfoLine("Habitudes", p.habits.ifBlank { "—" })
                InfoLine("Allergies", p.allergies.ifBlank { "—" })
                OutlinedButton(onClick = { editing = true }) { Text("Modifier mes infos") }
            }
        }

        // Accès bien visible aux paramètres
        Card(onClick = onOpenSettings, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Settings, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text("Paramètres", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Thème, couleurs, empreinte, captures d'écran, clé IA",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            label, Modifier.width(96.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
