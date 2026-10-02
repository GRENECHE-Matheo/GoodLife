@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package com.goodlife.app.ui.screens

import com.goodlife.app.i18n.t

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.goodlife.app.ai.Gemini
import com.goodlife.app.data.MealSlot
import com.goodlife.app.data.PlannedMeal
import com.goodlife.app.data.Recipe
import com.goodlife.app.data.Repo
import com.goodlife.app.data.localDay
import com.goodlife.app.ui.toNumber
import com.goodlife.app.ui.AiContentFooter
import java.text.SimpleDateFormat
import java.util.Locale

/** Libellé court d'un jour AAAA-MM-JJ, ex. « Aujourd'hui », « Demain », « jeu. 2 oct. ». */
fun dayLabel(date: String): String = when (date) {
    localDay(0) -> t("Aujourd'hui")
    localDay(1) -> t("Demain")
    else -> runCatching {
        val d = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date)!!
        SimpleDateFormat("EEE d MMM", com.goodlife.app.i18n.Lang.locale).format(d)
    }.getOrDefault(date)
}

/** Choisir un jour (7 prochains jours) et un créneau pour ajouter un repas à l'emploi du temps. */
@Composable
fun AddToPlanDialog(
    initialName: String = "",
    initialKcal: Int? = null,
    initialSlot: MealSlot = MealSlot.DEJEUNER,
    initialDate: String = localDay(0),
    description: String = "",
    recipe: Recipe? = null,
    editableName: Boolean = true,
    onDismiss: () -> Unit,
    onAdded: () -> Unit = {},
    editing: PlannedMeal? = null   // repas déjà prévu à modifier
) {
    var name by remember { mutableStateOf(initialName) }
    var kcal by remember { mutableStateOf(initialKcal?.toString() ?: "") }
    var slot by remember { mutableStateOf(initialSlot) }
    var date by remember { mutableStateOf(initialDate) }
    val days = (0..6).map { localDay(it) }.let { if (initialDate in it) it else listOf(initialDate) + it }
    val k = kcal.toNumber()?.toInt()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editing != null) t("Modifier le repas") else t("Ajouter à l'emploi du temps")) },
        text = {
            Column(
                Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (editableName) {
                    OutlinedTextField(name, { name = it }, label = { Text(t("Repas")) }, singleLine = true)
                } else {
                    Text(name, style = MaterialTheme.typography.titleMedium)
                }
                OutlinedTextField(
                    kcal, { kcal = it }, label = { Text(t("Calories (kcal)")) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Text(t("Jour"), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    days.forEach { d ->
                        FilterChip(selected = date == d, onClick = { date = d }, label = { Text(dayLabel(d)) })
                    }
                }
                Text(t("Moment"), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MealSlot.entries.forEach { s ->
                        FilterChip(selected = slot == s, onClick = { slot = s }, label = { Text(s.label) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && (k ?: 0) in 0..5000,
                onClick = {
                    if (editing != null) Repo.updatePlanned(editing.copy(
                        date = date, slot = slot, name = name.trim(), kcal = k ?: 0,
                        // Nouveau plat : l'ancienne recette ne correspond plus
                        recipe = if (name.trim() == editing.name) editing.recipe else null
                    ))
                    else Repo.addPlanned(
                        PlannedMeal(
                            date = date, slot = slot, name = name.trim(), kcal = k ?: 0,
                            description = description, recipe = recipe
                        )
                    )
                    onAdded()
                    onDismiss()
                }
            ) { Text(if (editing != null) t("Enregistrer") else t("Ajouter")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Annuler")) } }
    )
}

/**
 * Affiche une recette. Si elle n'est pas encore connue, elle est demandée à l'IA (1 analyse),
 * puis transmise via [onLoaded] pour être gardée en mémoire et ne pas la redemander.
 */
@Composable
fun RecipeDialog(
    name: String,
    description: String,
    kcal: Int,
    cached: Recipe?,
    onLoaded: (Recipe) -> Unit,
    onDismiss: () -> Unit
) {
    val settings by Repo.settings.collectAsState()
    val profile by Repo.profile.collectAsState()
    var recipe by remember { mutableStateOf(cached) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableStateOf(0) }
    var chat by remember { mutableStateOf(false) }

    LaunchedEffect(attempt) {
        if (recipe == null) {
            error = null
            try {
                val r = Gemini(settings.apiKey, settings.model).recipe(profile, name, description, kcal)
                recipe = r
                onLoaded(r)
            } catch (e: Exception) {
                error = e.message ?: t("Recette indisponible.")
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(name) },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 500.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val r = recipe
                when {
                    r != null -> {
                        Text(
                            t("%1\$s portion(s) · %2\$s min · ~%3\$s kcal/portion", r.servings, r.minutes, r.kcalPerServing) +
                                (if (r.costEur > 0) " · " + t("≈ %1\$s en tout", euros(r.costEur)) else ""),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(t("Ingrédients"), fontWeight = FontWeight.Medium)
                        r.ingredients.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                        if (r.costEur > 0) Text(t("Prix : estimations avec les prix moyens actuels en supermarché en France."),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(t("Préparation"), fontWeight = FontWeight.Medium)
                        r.steps.forEachIndexed { i, step ->
                            Text("${i + 1}. $step", style = MaterialTheme.typography.bodyMedium)
                        }
                        if (r.tip.isNotBlank()) {
                            Text(r.tip, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                        }
                        AiContentFooter(
                            t("Recette : %1\$s\n", name) + r.ingredients.joinToString("\n") + "\n" +
                                r.steps.joinToString("\n") + "\n" + r.tip
                        )
                    }
                    error != null -> Text(error!!, color = MaterialTheme.colorScheme.error)
                    else -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(t("Recette en préparation…"))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(t("Fermer")) } },
        dismissButton = {
            if (error != null) TextButton(onClick = { attempt++ }) { Text(t("Réessayer")) }
            else if (recipe != null) TextButton(onClick = { chat = true }) { Text(t("Poser une question")) }
        }
    )
    val r = recipe
    if (chat && r != null) {
        AiChatDialog(
            title = name,
            context = "Recette de « $name » (${r.servings} portion(s), ${r.minutes} min, ~${r.kcalPerServing} kcal/portion). Ingrédients : ${r.ingredients.joinToString("; ")}. Étapes : ${r.steps.joinToString(" / ")}. Astuce : ${r.tip}",
            suggestions = listOf(t("Par quoi remplacer un ingrédient ?"), t("Version végétarienne ?"), t("Pour 4 personnes ?"), t("Plus rapide ?")),
            onDismiss = { chat = false }
        )
    }
}
