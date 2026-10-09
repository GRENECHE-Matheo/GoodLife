package com.goodlife.app.ui.screens

import com.goodlife.app.i18n.t

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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.filled.Replay
import com.goodlife.app.data.Repo
import kotlin.math.roundToInt

/**
 * « Refaire un repas » : favoris (⭐) puis repas fréquents, rajoutés en un appui avec exactement les valeurs
 * enregistrées la dernière fois (aucun nouveau calcul).
 */
@Composable
fun QuickMeals(vertical: Boolean = false, onAdded: () -> Unit = {}) {
    val meals by Repo.meals.collectAsState()
    val favs by Repo.favMeals.collectAsState()
    val frequent = remember(meals, favs) { Repo.frequentMeals() }
    var confirm by remember { mutableStateOf<Meal?>(null) }
    if (vertical) {
        // Onglet Ajouter › Refaire : une vraie liste, et une explication quand elle est vide
        com.goodlife.app.ui.SectionCard(title = t("Refaire un repas"), icon = androidx.compose.material.icons.Icons.Filled.Replay) {
            if (favs.isEmpty() && frequent.isEmpty()) Text(
                t("Rien pour l'instant. Touche l'étoile ⭐ d'un repas dans « Repas du jour » pour le garder en favori : il apparaîtra ici. Les repas que tu manges souvent s'y ajoutent tout seuls."),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            (favs.map { it to true } + frequent.map { it to false }).forEachIndexed { i, (m, fav) ->
                if (i > 0) androidx.compose.material3.HorizontalDivider()
                Row(
                    Modifier.fillMaxWidth().clickable { confirm = m }.padding(vertical = 10.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Text(if (fav) "⭐" else "🔁", modifier = Modifier.padding(end = 12.dp))
                    Text(m.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(t("%1\$s kcal", m.kcal), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    } else if (favs.isNotEmpty() || frequent.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(t("Refaire un repas"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            favs.forEach { m -> AssistChip(onClick = { confirm = m }, label = { Text(t("⭐ %1\$s · %2\$s kcal", m.name, m.kcal), maxLines = 1, overflow = TextOverflow.Ellipsis) }) }
            frequent.forEach { m -> AssistChip(onClick = { confirm = m }, label = { Text(t("%1\$s · %2\$s kcal", m.name, m.kcal), maxLines = 1, overflow = TextOverflow.Ellipsis) }) }
        }
    }
    confirm?.let { m ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(m.name) },
            text = {
                Text(
                    t("%1\$s kcal · protéines %2\$s g · glucides %3\$s g · lipides %4\$s g\nMêmes valeurs que la dernière fois. Ajouter ce repas maintenant ?", m.kcal, m.proteinG.roundToInt(), m.carbsG.roundToInt(), m.fatG.roundToInt()),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val now = System.currentTimeMillis()
                    Repo.addMeal(m.copy(id = now, timestamp = now, source = "refait"))
                    confirm = null
                    onAdded()
                }) { Text(t("Ajouter")) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(t("Annuler")) } }
        )
    }
}

