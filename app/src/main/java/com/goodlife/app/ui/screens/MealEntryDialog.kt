package com.goodlife.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.goodlife.app.data.Meal
import com.goodlife.app.data.Repo
import com.goodlife.app.food.Ciqual
import com.goodlife.app.food.CiqualFood
import com.goodlife.app.ui.toNumber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Saisie d'un repas : par défaut « aliment + grammes » avec la table Ciqual (hors ligne, sans IA),
 * ou directement en calories.
 */
@Composable
fun AddMealDialog(onDismiss: () -> Unit, onAdd: (Meal) -> Unit) {
    val context = LocalContext.current
    var byFood by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<CiqualFood>>(emptyList()) }
    var chosen by remember { mutableStateOf<CiqualFood?>(null) }
    var grams by remember { mutableStateOf("100") }
    var name by remember { mutableStateOf("") }
    var kcal by remember { mutableStateOf("") }
    var count by remember { mutableStateOf("1") }
    val n = count.toNumber()?.toInt()?.takeIf { it in 1..50 }

    // Recherche avec un petit délai pour ne pas relancer à chaque lettre
    LaunchedEffect(query) {
        if (chosen != null) return@LaunchedEffect
        delay(200)
        results = withContext(Dispatchers.Default) { Ciqual.search(context, query) }
    }

    val g = grams.toNumber()
    val food = chosen
    val computed = if (food != null && g != null && n != null) (food.kcal * g / 100 * n).roundToInt() else null
    val k = kcal.toNumber()?.toInt()
    val total = if (k != null && n != null) k * n else null
    val canAdd = if (byFood) food != null && g != null && n != null && g > 0 && g <= 3000 && (computed ?: 0) <= Repo.MAX_MEAL_KCAL
                 else name.isNotBlank() && total != null && total in 0..Repo.MAX_MEAL_KCAL

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajouter un repas") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(byFood, { byFood = true }, label = { Text("Aliment + grammes") })
                    FilterChip(!byFood, { byFood = false }, label = { Text("Calories") })
                }
                if (byFood) {
                    if (food == null) {
                        OutlinedTextField(
                            query, { query = it }, label = { Text("Aliment (ex. riz cuit, pomme)") },
                            singleLine = true, modifier = Modifier.fillMaxWidth()
                        )
                        Column(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
                            results.forEachIndexed { i, f ->
                                if (i > 0) HorizontalDivider()
                                Row(
                                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                                        .clickable { chosen = f; name = f.name }.padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(f.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text("${f.kcal.roundToInt()} kcal", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            if (query.length >= 2 && results.isEmpty()) Text(
                                "Aucun aliment trouvé. Essaie un autre mot, ou le mode « Calories ».",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                Text(food.name, fontWeight = FontWeight.Medium)
                                Text(
                                    "Pour 100 g : ${food.kcal.roundToInt()} kcal · P ${"%.1f".format(food.protein)} g · " +
                                        "G ${"%.1f".format(food.carbs)} g · L ${"%.1f".format(food.fat)} g",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                TextButton(onClick = { chosen = null }) { Text("Changer d'aliment") }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                count, { count = it.take(2) }, label = { Text("Nombre") }, singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                grams, { grams = it.take(6) }, label = { Text("Grammes (pour 1)") }, singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(2f)
                            )
                        }
                        Text(
                            if (computed != null) "= $computed kcal" else "Indique un nombre et une quantité en grammes.",
                            style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(Ciqual.SOURCE, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    OutlinedTextField(name, { name = it }, label = { Text("Nom (ex. Banane)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            count, { count = it.take(2) }, label = { Text("Nombre") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            kcal, { kcal = it }, label = { Text("kcal pour 1") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(2f)
                        )
                    }
                    if (total != null) Text("= $total kcal", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        },
        confirmButton = {
            TextButton(enabled = canAdd, onClick = {
                if (byFood && food != null && g != null && n != null) {
                    onAdd(
                        Meal(
                            name = com.goodlife.app.ai.mealName(n, "${food.name} (${g.roundToInt()} g)").take(80),
                            kcal = computed ?: 0,
                            proteinG = food.protein * g / 100 * n, carbsG = food.carbs * g / 100 * n, fatG = food.fat * g / 100 * n,
                            details = "$n × ${g.roundToInt()} g · table Ciqual 2025 (Anses)",
                            source = "ciqual"
                        )
                    )
                } else {
                    onAdd(Meal(name = com.goodlife.app.ai.mealName(n ?: 1, name.trim()).take(80), kcal = total ?: 0, source = "manuel"))
                }
            }) { Text("Ajouter") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } }
    )
}
