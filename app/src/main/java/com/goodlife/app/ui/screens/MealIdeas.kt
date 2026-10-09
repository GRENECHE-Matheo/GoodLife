package com.goodlife.app.ui.screens

import com.goodlife.app.i18n.t

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.goodlife.app.ai.Gemini
import com.goodlife.app.data.MealSlot
import com.goodlife.app.data.MealSuggestion
import com.goodlife.app.data.Recipe
import com.goodlife.app.data.Repo
import com.goodlife.app.ui.AiContentFooter
import com.goodlife.app.ui.SectionCard
import kotlinx.coroutines.launch

/**
 * Idées de repas (onglet Repas › Idées) : selon ce qu'il reste à manger aujourd'hui, les habitudes et les allergies.
 * Chaque idée se planifie ou donne sa recette. Avant, ce bloc était tout en bas de l'accueil.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun MealIdeasCard() {
    val profile by Repo.profile.collectAsState()
    val meals by Repo.meals.collectAsState()
    val settings by Repo.settings.collectAsState()
    val scope = rememberCoroutineScope()
    val p = profile ?: return
    val today = Repo.mealsOfDay(meals)

    var suggestions by remember { mutableStateOf<List<MealSuggestion>>(emptyList()) }
    var sugLoading by remember { mutableStateOf(false) }
    var sugError by remember { mutableStateOf<String?>(null) }
    var expandedIdx by remember { mutableIntStateOf(-1) }
    var sugSlot by remember { mutableStateOf<MealSlot?>(null) }
    var planFor by remember { mutableStateOf<MealSuggestion?>(null) }
    var recipeFor by remember { mutableStateOf<MealSuggestion?>(null) }
    val recipes = remember { mutableStateMapOf<String, Recipe>() }

    SectionCard(title = t("Idées de repas"), icon = Icons.Filled.AutoAwesome) {
        if (!settings.aiEnabled) {
            Text(
                t("Les idées de repas utilisent l'IA (désactivée). Tu peux l'activer dans Paramètres."),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(
                t("Selon ce qu'il te reste, tes habitudes et tes allergies. Touche une idée pour les détails."),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // Pour quel repas ?
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = sugSlot == null, onClick = { sugSlot = null }, label = { Text(t("Toute la journée")) })
                MealSlot.entries.forEach { slot ->
                    FilterChip(selected = sugSlot == slot, onClick = { sugSlot = slot }, label = { Text(slot.label) })
                }
            }
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
            // « Régénérer » remplace la liste ; « Plus d'idées » en ajoute d'autres, différentes
            fun ask(append: Boolean) {
                sugLoading = true; sugError = null
                scope.launch {
                    try {
                        val fresh = Gemini(settings.apiKey, settings.model)
                            .suggestMeals(p, today, sugSlot, avoid = suggestions.map { it.name })
                        suggestions = if (append) suggestions + fresh else fresh
                        if (!append) { expandedIdx = -1; recipes.clear() }
                    } catch (e: Exception) {
                        sugError = e.message
                    } finally {
                        sugLoading = false
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FilledTonalButton(enabled = !sugLoading, onClick = { ask(append = false) }) {
                    if (sugLoading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Icon(if (suggestions.isEmpty()) Icons.Filled.AutoAwesome else Icons.Filled.Refresh, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (suggestions.isEmpty()) t("Proposer des repas") else t("Régénérer"))
                }
                if (suggestions.isNotEmpty()) {
                    TextButton(enabled = !sugLoading, onClick = { ask(append = true) }) {
                        Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(t("Plus d'idées"))
                    }
                }
            }
        }
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
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
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
        AnimatedVisibility(visible = expanded, enter = androidx.compose.animation.expandVertically(expandFrom = Alignment.Top) + androidx.compose.animation.fadeIn(), exit = androidx.compose.animation.shrinkVertically(shrinkTowards = Alignment.Top)) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                Text(s.description, style = MaterialTheme.typography.bodyMedium)
                if (s.why.isNotBlank()) {
                    Text(
                        s.why, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                AiContentFooter(t("Idée de repas : %1\$s (%2\$s kcal)\n%3\$s\n%4\$s", s.name, s.kcal, s.description, s.why))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilledTonalButton(onClick = onPlan) {
                        Icon(Icons.Filled.DateRange, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(t("Planifier"))
                    }
                    OutlinedButton(onClick = onRecipe) {
                        Icon(Icons.Filled.Restaurant, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(t("Recette"))
                    }
                }
            }
        }
    }
}
