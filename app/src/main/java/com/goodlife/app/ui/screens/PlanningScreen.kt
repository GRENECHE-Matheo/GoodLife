@file:OptIn(ExperimentalMaterial3Api::class)

package com.goodlife.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.goodlife.app.data.Meal
import com.goodlife.app.data.MealSlot
import com.goodlife.app.data.PlannedMeal
import com.goodlife.app.data.Repo
import com.goodlife.app.data.localDay
import com.goodlife.app.ui.ScreenTitle
import com.goodlife.app.ui.SectionCard

@Composable
fun PlanningScreen() {
    val plan by Repo.plan.collectAsState()
    val profile by Repo.profile.collectAsState()
    var weekOffset by rememberSaveable { mutableIntStateOf(0) }
    var selected by rememberSaveable { mutableStateOf(localDay(0)) }
    var addSlot by remember { mutableStateOf<MealSlot?>(null) }

    val days = (0..6).map { localDay(weekOffset * 7 + it) }
    val ofDay = plan.filter { it.date == selected }
    val total = ofDay.sumOf { it.kcal }
    val target = profile?.targetKcal ?: 0

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ScreenTitle("Emploi du temps", "Organise tes repas de la semaine")

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { weekOffset--; selected = localDay(weekOffset * 7) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Semaine précédente")
            }
            Row(
                Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                days.forEach { d ->
                    val count = plan.count { it.date == d }
                    FilterChip(
                        selected = selected == d,
                        onClick = { selected = d },
                        label = { Text(dayLabel(d) + if (count > 0) " · $count" else "") }
                    )
                }
            }
            IconButton(onClick = { weekOffset++; selected = localDay(weekOffset * 7) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Semaine suivante")
            }
        }

        SectionCard {
            Text(dayLabel(selected), style = MaterialTheme.typography.titleLarge)
            Text(
                if (target > 0) "Prévu : $total kcal sur $target kcal" else "Prévu : $total kcal",
                style = MaterialTheme.typography.bodyMedium,
                color = if (target > 0 && total > target) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        MealSlot.entries.forEach { slot ->
            val items = ofDay.filter { it.slot == slot }
            SectionCard(title = slot.label, icon = Icons.Filled.Restaurant) {
                if (items.isEmpty()) {
                    Text(
                        "Rien de prévu.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                items.forEachIndexed { i, m ->
                    if (i > 0) HorizontalDivider()
                    PlannedRow(m)
                }
                TextButton(onClick = { addSlot = slot }) {
                    Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Ajouter")
                }
            }
        }
        Text(
            "Astuce : sur l'accueil, les idées de repas de l'IA peuvent être ajoutées ici en un clic.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
    }

    addSlot?.let { s ->
        AddToPlanDialog(initialSlot = s, initialDate = selected, onDismiss = { addSlot = null })
    }
}

@Composable
private fun PlannedRow(m: PlannedMeal) {
    var expanded by remember { mutableStateOf(false) }
    var showRecipe by remember { mutableStateOf(false) }
    val aiOn = Repo.settings.collectAsState().value.aiEnabled

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    m.name, fontWeight = FontWeight.Medium,
                    textDecoration = if (m.done) TextDecoration.LineThrough else null
                )
                Text(
                    "${m.kcal} kcal" + if (m.done) " · mangé" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!m.done) {
                IconButton(onClick = {
                    Repo.addMeal(Meal(name = m.name, kcal = m.kcal, details = m.description, source = "planning"))
                    Repo.updatePlanned(m.copy(done = true))
                }) { Icon(Icons.Filled.Check, "Marquer comme mangé") }
            }
        }
        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (m.description.isNotBlank()) Text(m.description, style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (m.recipe != null || aiOn) {
                        AssistChip(onClick = { showRecipe = true }, label = { Text("Recette") })
                    }
                    AssistChip(
                        onClick = { Repo.deletePlanned(m.id) },
                        label = { Text("Retirer") },
                        leadingIcon = { Icon(Icons.Filled.Delete, null, Modifier.size(18.dp)) }
                    )
                }
            }
        }
    }

    if (showRecipe) {
        RecipeDialog(
            name = m.name, description = m.description, kcal = m.kcal, cached = m.recipe,
            onLoaded = { r -> Repo.updatePlanned(m.copy(recipe = r)) },
            onDismiss = { showRecipe = false }
        )
    }
}
