@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.goodlife.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.goodlife.app.ai.Gemini
import com.goodlife.app.data.Meal
import com.goodlife.app.data.MealSlot
import com.goodlife.app.data.PlannedMeal
import com.goodlife.app.data.Repo
import com.goodlife.app.data.localDay
import com.goodlife.app.ui.AiContentFooter
import com.goodlife.app.ui.Motion
import com.goodlife.app.ui.ScreenColumn
import com.goodlife.app.ui.ScreenTitle
import com.goodlife.app.ui.SectionCard
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private val ISO = SimpleDateFormat("yyyy-MM-dd", Locale.US)

/** Lundi de la semaine courante, décalé de [weeks] semaines. */
private fun weekStart(weeks: Int): Calendar = Calendar.getInstance().apply {
    firstDayOfWeek = Calendar.MONDAY
    set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
    if (after(Calendar.getInstance())) add(Calendar.DAY_OF_YEAR, -7)
    add(Calendar.DAY_OF_YEAR, weeks * 7)
}

private fun weekDays(weeks: Int): List<String> {
    val c = weekStart(weeks)
    return (0..6).map { ISO.format(c.time).also { c.add(Calendar.DAY_OF_YEAR, 1) } }
}

private fun parse(d: String) = runCatching { ISO.parse(d) }.getOrNull()
private fun fmt(d: String, pattern: String) = parse(d)?.let { SimpleDateFormat(pattern, Locale.FRANCE).format(it) } ?: d
fun euros(v: Double): String = String.format(Locale.FRANCE, "%.2f €", v)

@Composable
fun PlanningScreen() {
    val plan by Repo.plan.collectAsState()
    val profile by Repo.profile.collectAsState()
    val settings by Repo.settings.collectAsState()
    var week by rememberSaveable { mutableIntStateOf(0) }
    var selected by rememberSaveable { mutableStateOf(localDay(0)) }
    var addSlot by remember { mutableStateOf<MealSlot?>(null) }
    var generate by remember { mutableStateOf(false) }

    val days = weekDays(week)
    val target = profile?.targetKcal ?: 0

    ScreenColumn {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { ScreenTitle("Planning", "Tes repas de la semaine") }
            if (settings.aiEnabled) {
                FilledTonalButton(onClick = { generate = true }) {
                    Icon(Icons.Filled.AutoAwesome, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Ma semaine")
                }
            }
        }

        // Semaine : « 29 sept. – 5 oct. » avec flèches
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { week--; selected = weekDays(week).first() }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Semaine précédente")
            }
            Text(
                (if (week == 0) "Cette semaine · " else "") + "${fmt(days.first(), "d MMM")} – ${fmt(days.last(), "d MMM")}",
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { week++; selected = weekDays(week).first() }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Semaine suivante")
            }
        }

        // 7 pastilles de jour sur une ligne, un point sous les jours déjà planifiés
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            days.forEach { d ->
                val isSel = d == selected
                val isToday = d == localDay(0)
                val has = plan.any { it.date == d }
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(16.dp))
                        .background(
                            when {
                                isSel -> MaterialTheme.colorScheme.primary
                                isToday -> MaterialTheme.colorScheme.primaryContainer
                                else -> MaterialTheme.colorScheme.surfaceContainerLow
                            }
                        )
                        .clickable { selected = d }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val fg = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                    Text(fmt(d, "EEE").take(3).replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelSmall, color = fg)
                    Text(fmt(d, "d"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, color = fg)
                    Box(
                        Modifier.size(5.dp).clip(CircleShape).background(
                            if (has) (if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary)
                            else androidx.compose.ui.graphics.Color.Transparent
                        )
                    )
                }
            }
        }

        // Le jour choisi, en fondu
        AnimatedContent(targetState = selected, transitionSpec = { Motion.fadeThrough() }, label = "day") { day ->
            val ofDay = plan.filter { it.date == day }
            val total = ofDay.sumOf { it.kcal }
            val cost = ofDay.sumOf { it.costEur }
            SectionCard {
                Text(fmt(day, "EEEE d MMMM").replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (target > 0) "$total / $target kcal prévues" else "$total kcal prévues",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (target > 0 && total > target) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    if (cost > 0) Text("≈ ${euros(cost)}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
                if (target > 0) {
                    LinearProgressIndicator(
                        progress = { (total.toFloat() / target).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        strokeCap = StrokeCap.Round
                    )
                }
                MealSlot.entries.forEachIndexed { i, slot ->
                    if (i > 0) HorizontalDivider()
                    val items = ofDay.filter { it.slot == slot }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(slot.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                        IconButton(onClick = { addSlot = slot }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Filled.Add, "Ajouter ${slot.label.lowercase()}", Modifier.size(20.dp))
                        }
                    }
                    if (items.isEmpty()) {
                        Text("—", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    }
                    items.forEach { PlannedRow(it) }
                }
            }
        }
        Text(
            "Astuce : touche un repas pour voir sa description, sa recette ou le retirer ; ✓ l'ajoute à ton journal.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
    }

    addSlot?.let { s ->
        AddToPlanDialog(initialSlot = s, initialDate = selected, onDismiss = { addSlot = null })
    }
    if (generate) WeekPlanDialog(startDate = days.first(), onDismiss = { generate = false }, onDone = { selected = it })
}

@Composable
private fun PlannedRow(m: PlannedMeal) {
    var expanded by remember { mutableStateOf(false) }
    var showRecipe by remember { mutableStateOf(false) }
    val aiOn = Repo.settings.collectAsState().value.aiEnabled

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { expanded = !expanded }.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    m.name, fontWeight = FontWeight.Medium,
                    textDecoration = if (m.done) TextDecoration.LineThrough else null
                )
                Text(
                    listOfNotNull(
                        "${m.kcal} kcal",
                        m.costEur.takeIf { it > 0 }?.let { "≈ ${euros(it)}" },
                        "mangé".takeIf { m.done }
                    ).joinToString(" · "),
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
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 6.dp)) {
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

/** Génère la semaine avec l'IA : budget, nombre de personnes, repas à prévoir. */
@Composable
private fun WeekPlanDialog(startDate: String, onDismiss: () -> Unit, onDone: (String) -> Unit) {
    val settings by Repo.settings.collectAsState()
    val profile by Repo.profile.collectAsState()
    val scope = rememberCoroutineScope()
    var budget by rememberSaveable { mutableStateOf("50") }
    var people by rememberSaveable { mutableIntStateOf(1) }
    var slots by remember { mutableStateOf(setOf(MealSlot.DEJEUNER, MealSlot.DINER)) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<Pair<List<PlannedMeal>, String>?>(null) }
    val b = budget.toIntOrNull()

    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        icon = { Icon(Icons.Filled.AutoAwesome, null) },
        title = { Text(if (result == null) "Planifier ma semaine" else "Ta semaine est prête") },
        text = {
            val r = result
            if (r == null) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "À partir du ${fmt(startDate, "EEEE d MMMM")}. L'IA vise ton budget avec les prix moyens en " +
                        "supermarché en France : ce sont des estimations.",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    budget, { budget = it.filter(Char::isDigit).take(4) },
                    label = { Text("Budget de la semaine (€)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Personnes", modifier = Modifier.weight(1f))
                    IconButton(enabled = people > 1, onClick = { people-- }) { Icon(Icons.Filled.Remove, "Moins") }
                    Text("$people", style = MaterialTheme.typography.titleMedium)
                    IconButton(enabled = people < 8, onClick = { people++ }) { Icon(Icons.Filled.Add, "Plus") }
                }
                Text("Repas à prévoir", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MealSlot.entries.forEach { s ->
                        FilterChip(
                            selected = s in slots,
                            onClick = { slots = if (s in slots) slots - s else slots + s },
                            label = { Text(s.label) }
                        )
                    }
                }
                if (loading) Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("L'IA prépare ta semaine…", style = MaterialTheme.typography.bodySmall)
                }
                if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            } else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val total = r.first.sumOf { it.costEur }
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text("≈ ${euros(total)} pour ${r.first.size} repas", style = MaterialTheme.typography.titleMedium)
                        Text("Budget : ${b ?: 0} € · $people personne(s)", style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (r.second.isNotBlank()) Text(r.second, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Les repas déjà prévus (non mangés) sur ces créneaux seront remplacés.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                AiContentFooter(r.first.joinToString("\n") { "${it.date} ${it.slot.label} : ${it.name} (${it.costEur} €)" })
            }
        },
        confirmButton = {
            val r = result
            if (r == null) {
                TextButton(
                    enabled = !loading && b != null && b in 5..2000 && slots.isNotEmpty() && profile != null,
                    onClick = {
                        loading = true; error = null
                        scope.launch {
                            try {
                                result = Gemini(settings.apiKey, settings.model)
                                    .planWeek(profile!!, startDate, b!!, people, MealSlot.entries.filter { it in slots })
                            } catch (e: Exception) {
                                error = e.message
                            } finally {
                                loading = false
                            }
                        }
                    }
                ) { Text("Générer") }
            } else {
                TextButton(onClick = {
                    Repo.addPlannedWeek(r.first, r.first.map { it.date }.toSet() + weekDaysFrom(startDate), slots)
                    onDone(startDate)
                    onDismiss()
                }) { Text("Ajouter au planning") }
            }
        },
        dismissButton = {
            val r = result
            if (r != null) TextButton(onClick = { result = null }) { Text("Refaire") }
            else TextButton(enabled = !loading, onClick = onDismiss) { Text("Annuler") }
        }
    )
}

private fun weekDaysFrom(start: String): Set<String> {
    val c = Calendar.getInstance().apply { time = parse(start) ?: time }
    return (0..6).map { ISO.format(c.time).also { c.add(Calendar.DAY_OF_YEAR, 1) } }.toSet()
}
