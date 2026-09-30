package com.goodlife.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.goodlife.app.ai.Gemini
import com.goodlife.app.data.Meal
import com.goodlife.app.data.MealSuggestion
import com.goodlife.app.data.Repo
import com.goodlife.app.ui.CalorieRing
import com.goodlife.app.ui.MacroBar
import com.goodlife.app.ui.ScreenTitle
import com.goodlife.app.ui.SectionCard
import com.goodlife.app.ui.WeekBars
import com.goodlife.app.ui.formatDay
import com.goodlife.app.ui.formatTime
import com.goodlife.app.ui.theme.GoogleGreen
import com.goodlife.app.ui.theme.GoogleRed
import com.goodlife.app.ui.theme.GoogleYellow
import com.goodlife.app.ui.toNumber
import kotlinx.coroutines.launch
import java.util.Calendar

@Composable
fun HomeScreen(onScan: () -> Unit) {
    val profile by Repo.profile.collectAsState()
    val meals by Repo.meals.collectAsState()
    val settings by Repo.settings.collectAsState()
    val scope = rememberCoroutineScope()
    val p = profile ?: return

    val today = Repo.mealsOfDay(meals)
    val eaten = today.sumOf { it.kcal }
    val remaining = p.targetKcal - eaten

    var showAdd by remember { mutableStateOf(false) }
    var suggestions by remember { mutableStateOf<List<MealSuggestion>>(emptyList()) }
    var sugLoading by remember { mutableStateOf(false) }
    var sugError by remember { mutableStateOf<String?>(null) }

    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val hello = if (hour < 18) "Bonjour" else "Bonsoir"

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ScreenTitle(
            if (p.name.isBlank()) hello else "$hello ${p.name}",
            formatDay(System.currentTimeMillis()).replaceFirstChar { it.uppercase() }
        )

        SectionCard {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CalorieRing(consumed = eaten, target = p.targetKcal)
            }
            Text(
                if (remaining >= 0) "Il te reste $remaining kcal aujourd'hui"
                else "Objectif dépassé de ${-remaining} kcal",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            MacroBar("Protéines", today.sumOf { it.proteinG }, p.proteinG, GoogleRed)
            MacroBar("Glucides", today.sumOf { it.carbsG }, p.carbsG, GoogleYellow)
            MacroBar("Lipides", today.sumOf { it.fatG }, p.fatG, GoogleGreen)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onScan, modifier = Modifier.weight(1f).height(52.dp)) {
                Icon(Icons.Filled.PhotoCamera, null)
                Spacer(Modifier.width(8.dp))
                Text("Scanner")
            }
            FilledTonalButton(onClick = { showAdd = true }, modifier = Modifier.weight(1f).height(52.dp)) {
                Icon(Icons.Filled.Add, null)
                Spacer(Modifier.width(8.dp))
                Text("Saisir")
            }
        }

        SectionCard(title = "Repas du jour", icon = Icons.Filled.Restaurant) {
            if (today.isEmpty()) {
                Text(
                    "Aucun repas enregistré. Prends ton assiette en photo pour commencer.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            today.forEachIndexed { i, m ->
                if (i > 0) HorizontalDivider()
                MealRow(m) { Repo.deleteMeal(m.id) }
            }
        }

        SectionCard(title = "Idées de repas", icon = Icons.Filled.AutoAwesome) {
            Text(
                "Suggestions adaptées à ce qu'il te reste, à tes habitudes et à tes allergies.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            suggestions.forEach { s ->
                Column(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(s.name, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        Text("${s.kcal} kcal", color = MaterialTheme.colorScheme.primary)
                    }
                    if (s.moment.isNotBlank()) {
                        Text(s.moment.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelMedium)
                    }
                    Text(s.description, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        s.why, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (sugError != null) Text(sugError!!, color = MaterialTheme.colorScheme.error)
            FilledTonalButton(
                enabled = !sugLoading,
                onClick = {
                    sugLoading = true; sugError = null
                    scope.launch {
                        try {
                            suggestions = Gemini(settings.apiKey, settings.model).suggestMeals(p, today)
                        } catch (e: Exception) {
                            sugError = e.message
                        } finally {
                            sugLoading = false
                        }
                    }
                }
            ) {
                if (sugLoading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Icon(Icons.Filled.AutoAwesome, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (suggestions.isEmpty()) "Proposer des repas" else "Autres idées")
            }
        }

        SectionCard(title = "7 derniers jours", icon = Icons.Filled.BarChart) {
            val days = (-6..0).toList()
            val values = days.map { d -> Repo.mealsOfDay(meals, d).sumOf { it.kcal }.toFloat() }
            val labels = days.map { d ->
                formatDay(Repo.dayBounds(d).first).take(3).replaceFirstChar { it.uppercase() }
            }
            WeekBars(values, p.targetKcal.toFloat(), labels, MaterialTheme.colorScheme.primary)
            Text(
                "Moyenne : ${values.average().toInt()} kcal/jour · la ligne jaune = ton objectif",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(8.dp))
    }

    if (showAdd) AddMealDialog(onDismiss = { showAdd = false }) { Repo.addMeal(it); showAdd = false }
}

@Composable
private fun MealRow(m: Meal, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(m.name, fontWeight = FontWeight.Medium)
            Text(
                "${formatTime(m.timestamp)} · ${if (m.source == "photo") "photo IA" else "saisie"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text("${m.kcal} kcal", style = MaterialTheme.typography.titleSmall)
        IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Supprimer") }
    }
}

@Composable
private fun AddMealDialog(onDismiss: () -> Unit, onAdd: (Meal) -> Unit) {
    var name by remember { mutableStateOf("") }
    var kcal by remember { mutableStateOf("") }
    val k = kcal.toNumber()?.toInt()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajouter un repas") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nom") }, singleLine = true)
                OutlinedTextField(
                    kcal, { kcal = it }, label = { Text("Calories (kcal)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && k != null && k in 0..5000,
                onClick = { onAdd(Meal(name = name.trim(), kcal = k ?: 0, source = "manuel")) }
            ) { Text("Ajouter") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}
