package com.goodlife.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.goodlife.app.ai.Gemini
import com.goodlife.app.data.Meal
import com.goodlife.app.data.MealSlot
import com.goodlife.app.data.MealSuggestion
import com.goodlife.app.data.Recipe
import com.goodlife.app.data.Repo
import com.goodlife.app.net.Updater
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
    var expandedIdx by remember { mutableIntStateOf(-1) }
    var planFor by remember { mutableStateOf<MealSuggestion?>(null) }
    var recipeFor by remember { mutableStateOf<MealSuggestion?>(null) }
    val recipes = remember { mutableStateMapOf<String, Recipe>() }

    val uri = LocalUriHandler.current
    LaunchedEffect(Unit) { Updater.check() }

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

        val update = if (settings.checkUpdates) Updater.availableUpdate() else null
        if (update != null && update.tag != settings.dismissedTag) {
            SectionCard(
                title = "Nouvelle version ${update.tag} disponible",
                icon = Icons.Filled.SystemUpdate,
                container = MaterialTheme.colorScheme.primaryContainer
            ) {
                UpdatePanel(update, showDismiss = true)
            }
        }

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
            if (!settings.aiEnabled) {
                Text(
                    "Les idées de repas utilisent l'IA (désactivée). Tu peux l'activer dans Paramètres.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    "Selon ce qu'il te reste, tes habitudes et tes allergies. Touche une idée pour les détails.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                suggestions.forEachIndexed { i, s ->
                    if (i > 0) HorizontalDivider()
                    SuggestionRow(
                        s = s,
                        expanded = expandedIdx == i,
                        onToggle = { expandedIdx = if (expandedIdx == i) -1 else i },
                        onPlan = { planFor = s },
                        onRecipe = { recipeFor = s }
                    )
                }
                if (sugError != null) Text(sugError!!, color = MaterialTheme.colorScheme.error)
                FilledTonalButton(
                    enabled = !sugLoading,
                    onClick = {
                        sugLoading = true; sugError = null
                        scope.launch {
                            try {
                                suggestions = Gemini(settings.apiKey, settings.model).suggestMeals(p, today)
                                expandedIdx = -1
                                recipes.clear()
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

    planFor?.let { s ->
        AddToPlanDialog(
            initialName = s.name, initialKcal = s.kcal, initialSlot = MealSlot.guess(s.moment),
            description = s.description, recipe = recipes[s.name], editableName = false,
            onDismiss = { planFor = null }
        )
    }
    recipeFor?.let { s ->
        RecipeDialog(
            name = s.name, description = s.description, kcal = s.kcal, cached = recipes[s.name],
            onLoaded = { recipes[s.name] = it }, onDismiss = { recipeFor = null }
        )
    }

    if (showAdd) AddMealDialog(onDismiss = { showAdd = false }) { Repo.addMeal(it); showAdd = false }
}

@Composable
private fun SuggestionRow(
    s: MealSuggestion,
    expanded: Boolean,
    onToggle: () -> Unit,
    onPlan: () -> Unit,
    onRecipe: () -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(s.name, fontWeight = FontWeight.Medium)
                Text(
                    listOf(s.moment.replaceFirstChar { it.uppercase() }, s.summary).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text("${s.kcal} kcal", color = MaterialTheme.colorScheme.primary)
            Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null)
        }
        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                Text(s.description, style = MaterialTheme.typography.bodyMedium)
                if (s.why.isNotBlank()) {
                    Text(
                        s.why, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = onPlan) {
                        Icon(Icons.Filled.DateRange, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Planifier")
                    }
                    OutlinedButton(onClick = onRecipe) {
                        Icon(Icons.Filled.Restaurant, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Recette")
                    }
                }
            }
        }
    }
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
