package com.goodlife.app.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.goodlife.app.data.Meal
import com.goodlife.app.data.Repo
import kotlin.math.roundToInt

/**
 * « Refaire un repas » : favoris (⭐) puis repas fréquents, rajoutés en un appui avec exactement les valeurs
 * enregistrées la dernière fois (aucun nouveau calcul).
 */
@Composable
fun QuickMeals() {
    val meals by Repo.meals.collectAsState()
    val favs by Repo.favMeals.collectAsState()
    val frequent = remember(meals, favs) { Repo.frequentMeals() }
    if (favs.isEmpty() && frequent.isEmpty()) return
    var confirm by remember { mutableStateOf<Meal?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Refaire un repas", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            favs.forEach { m -> AssistChip(onClick = { confirm = m }, label = { Text("⭐ ${m.name} · ${m.kcal} kcal", maxLines = 1, overflow = TextOverflow.Ellipsis) }) }
            frequent.forEach { m -> AssistChip(onClick = { confirm = m }, label = { Text("${m.name} · ${m.kcal} kcal", maxLines = 1, overflow = TextOverflow.Ellipsis) }) }
        }
    }
    confirm?.let { m ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(m.name) },
            text = {
                Text(
                    "${m.kcal} kcal · protéines ${m.proteinG.roundToInt()} g · glucides ${m.carbsG.roundToInt()} g · lipides ${m.fatG.roundToInt()} g\n" +
                        "Mêmes valeurs que la dernière fois. Ajouter ce repas maintenant ?",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val now = System.currentTimeMillis()
                    Repo.addMeal(m.copy(id = now, timestamp = now, source = "refait"))
                    confirm = null
                }) { Text("Ajouter") }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Annuler") } }
        )
    }
}

