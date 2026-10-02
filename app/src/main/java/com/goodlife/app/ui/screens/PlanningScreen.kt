@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.goodlife.app.ui.screens

import androidx.compose.material.icons.filled.Edit
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.filled.Close
import com.goodlife.app.i18n.t

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
import androidx.compose.material3.OutlinedButton
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
private fun fmt(d: String, pattern: String) = parse(d)?.let { SimpleDateFormat(pattern, com.goodlife.app.i18n.Lang.locale).format(it) } ?: d
fun euros(v: Double): String = String.format(com.goodlife.app.i18n.Lang.locale, "%.2f €", v)

@Composable
fun PlanningScreen() {
    val plan by Repo.plan.collectAsState()
    val profile by Repo.profile.collectAsState()
    val settings by Repo.settings.collectAsState()
    var week by rememberSaveable { mutableIntStateOf(0) }
    var selected by rememberSaveable { mutableStateOf(localDay(0)) }
    var addSlot by remember { mutableStateOf<MealSlot?>(null) }
    var generate by remember { mutableStateOf(false) }
    var redoDay by remember { mutableStateOf<String?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    var shopping by remember { mutableStateOf(false) }
    var fridge by remember { mutableStateOf(false) }

    val days = weekDays(week)
    val target = profile?.targetKcal ?: 0

    ScreenColumn {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { ScreenTitle(t("Planning"), t("Tes repas de la semaine")) }
            if (settings.aiEnabled) {
                FilledTonalButton(onClick = { generate = true }) {
                    Icon(Icons.Filled.AutoAwesome, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(t("Ma semaine"))
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { shopping = true }) { Text(t("🛒 Liste de courses")) }
            OutlinedButton(onClick = { fridge = true }) { Text(t("🧊 Mon frigo")) }
        }

        // Semaine : « 29 sept. – 5 oct. » avec flèches
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { week--; selected = weekDays(week).first() }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, t("Semaine précédente"))
            }
            Text(
                (if (week == 0) t("Cette semaine · ") else "") + "${fmt(days.first(), "d MMM")} – ${fmt(days.last(), "d MMM")}",
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { week++; selected = weekDays(week).first() }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, t("Semaine suivante"))
            }
        }
        val clearable = plan.count { it.date in days && it.date >= localDay(0) && !it.done }
        if (clearable > 0) TextButton(onClick = { confirmClear = true }, modifier = Modifier.align(Alignment.End)) {
            Icon(Icons.Filled.Delete, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
            Text(t("Vider la semaine (%1\$s repas)", clearable))
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(fmt(day, "EEEE d MMMM").replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    if (settings.aiEnabled && day >= localDay(0)) TextButton(onClick = { redoDay = day }) {
                        Icon(Icons.Filled.AutoAwesome, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp))
                        Text(if (ofDay.any { !it.done }) t("Changer ce jour") else t("Prévoir ce jour"))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (target > 0) t("%1\$s / %2\$s kcal prévues", total, target) else t("%1\$s kcal prévues", total),
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
                            Icon(Icons.Filled.Add, t("Ajouter %1\$s", slot.label.lowercase()), Modifier.size(20.dp))
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
            t("Astuce : touche un repas pour voir sa description, sa recette ou le retirer ; ✓ l'ajoute à ton journal."),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
    }

    addSlot?.let { s ->
        AddToPlanDialog(initialSlot = s, initialDate = selected, onDismiss = { addSlot = null })
    }
    if (generate) WeekPlanDialog(startDate = maxOf(days.first(), localDay(0)), onDismiss = { generate = false }, onDone = { selected = it })
    redoDay?.let { d -> WeekPlanDialog(startDate = d, days = 1, onDismiss = { redoDay = null }, onDone = { selected = it }) }
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text(t("Vider la semaine ?")) },
        text = { Text(t("Les repas prévus à partir d'aujourd'hui et pas encore mangés seront retirés. Ton journal ne change pas.")) },
        confirmButton = { TextButton(onClick = { Repo.clearPlanned(days.filter { it >= localDay(0) }.toSet()); confirmClear = false }) { Text(t("Vider")) } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(t("Annuler")) } }
    )
    if (shopping) androidx.compose.ui.window.Dialog(onDismissRequest = { shopping = false },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) { ShoppingScreen(onBack = { shopping = false }) }
    if (fridge) FridgeDialog(onDismiss = { fridge = false })
}

@Composable
private fun PlannedRow(m: PlannedMeal) {
    var expanded by remember { mutableStateOf(false) }
    var showRecipe by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    val aiOn = Repo.settings.collectAsState().value.aiEnabled
    if (editing) AddToPlanDialog(
        initialName = m.name, initialKcal = m.kcal, initialSlot = m.slot, initialDate = m.date,
        description = m.description, recipe = m.recipe, editing = m, onDismiss = { editing = false }
    )

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
                        t("mangé").takeIf { m.done }
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!m.done) {
                IconButton(onClick = {
                    Repo.addMeal(Meal(name = m.name, kcal = m.kcal, details = m.description, source = "planning"))
                    Repo.updatePlanned(m.copy(done = true))
                }) { Icon(Icons.Filled.Check, t("Marquer comme mangé")) }
            }
        }
        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                if (m.description.isNotBlank()) Text(m.description, style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (m.recipe != null || aiOn) {
                        AssistChip(onClick = { showRecipe = true }, label = { Text(t("Recette")) })
                    }
                    if (!m.done) AssistChip(
                        onClick = { editing = true },
                        label = { Text(t("Modifier")) },
                        leadingIcon = { Icon(Icons.Filled.Edit, null, Modifier.size(18.dp)) }
                    )
                    AssistChip(
                        onClick = { Repo.deletePlanned(m.id) },
                        label = { Text(t("Retirer")) },
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
private fun WeekPlanDialog(startDate: String, onDismiss: () -> Unit, onDone: (String) -> Unit, days: Int = 7) {
    val settings by Repo.settings.collectAsState()
    val profile by Repo.profile.collectAsState()
    val scope = rememberCoroutineScope()
    var budget by rememberSaveable { mutableStateOf(if (days == 1) "10" else "50") }
    var people by rememberSaveable { mutableIntStateOf(1) }
    var slots by remember { mutableStateOf(setOf(MealSlot.DEJEUNER, MealSlot.DINER)) }
    var notes by rememberSaveable { mutableStateOf(Repo.settings.value.planNotes) }
    var withShopping by rememberSaveable { mutableStateOf(Repo.settings.value.planShopping) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<Pair<List<PlannedMeal>, String>?>(null) }
    val b = budget.toIntOrNull()

    fun accept(r: Pair<List<PlannedMeal>, String>) {
        Repo.addPlannedWeek(r.first, r.first.map { it.date }.toSet() + weekDaysFrom(startDate, days), slots)
        // Liste de courses remplie toute seule à partir de la semaine (en arrière-plan, même si l'écran se ferme)
        if (withShopping) {
            val meals = r.first
            val key = settings.apiKey; val model = settings.model; val n = people
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                val items = runCatching { Gemini(key, model).shoppingList(meals, n) }.getOrNull()
                if (!items.isNullOrEmpty()) Shopping.add(items)
            }
        }
        onDone(startDate)
        onDismiss()
    }

    // Résultat : aperçu en plein écran, repas par repas, avant de valider
    result?.let { r ->
        PlanPreview(
            r, days, people, b ?: 0,
            onRemove = { id -> result = r.copy(first = r.first.filterNot { it.id == id }) },
            onRedo = { result = null },
            onDismiss = onDismiss,
            onAccept = { accept(r) }
        )
        return
    }

    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        icon = { Icon(Icons.Filled.AutoAwesome, null) },
        title = {
            Text(when {
                days == 1 && result == null -> t("Changer le %1\$s", fmt(startDate, "EEEE d MMMM"))
                days == 1 -> t("Ta journée est prête")
                result == null -> t("Planifier ma semaine")
                else -> t("Ta semaine est prête")
            })
        },
        text = {
            val r = result
            if (r == null) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (days == 1) t("L'IA vise ton budget avec les prix moyens actuels en supermarché en France : ce sont des estimations.")
                    else t("À partir du %1\$s. L'IA vise ton budget avec les prix moyens actuels en supermarché en France : ce sont des estimations.", fmt(startDate, "EEEE d MMMM")),
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    budget, { budget = it.filter(Char::isDigit).take(4) },
                    label = { Text(if (days == 1) t("Budget de la journée (€)") else t("Budget de la semaine (€)")) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(t("Personnes"), modifier = Modifier.weight(1f))
                    IconButton(enabled = people > 1, onClick = { people-- }) { Icon(Icons.Filled.Remove, t("Moins")) }
                    Text("$people", style = MaterialTheme.typography.titleMedium)
                    IconButton(enabled = people < 8, onClick = { people++ }) { Icon(Icons.Filled.Add, t("Plus")) }
                }
                Text(t("Repas à prévoir"), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MealSlot.entries.forEach { s ->
                        FilterChip(
                            selected = s in slots,
                            onClick = { slots = if (s in slots) slots - s else slots + s },
                            label = { Text(s.label) }
                        )
                    }
                }
                OutlinedTextField(
                    notes, { notes = it.take(400) },
                    label = { Text(t("Précisions (facultatif)")) },
                    placeholder = { Text(t("Ex : Léa n'aime pas les champignons, repas rapides le soir, végétarien le lundi…")) },
                    minLines = 2, maxLines = 4, modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(withShopping, { withShopping = it })
                    Text(t("Préparer aussi ma liste de courses"), style = MaterialTheme.typography.bodyMedium)
                }
                if (loading) Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(if (days == 1) t("L'IA prépare ta journée…") else t("L'IA prépare ta semaine…"), style = MaterialTheme.typography.bodySmall)
                }
                if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            } else Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val total = r.first.sumOf { it.costEur }
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                    Column(Modifier.fillMaxWidth().padding(12.dp)) {
                        Text(t("≈ %1\$s pour %2\$s repas", euros(total), r.first.size), style = MaterialTheme.typography.titleMedium)
                        Text(t("Budget : %1\$s € · %2\$s personne(s)", b ?: 0, people), style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (r.second.isNotBlank()) Text(r.second, style = MaterialTheme.typography.bodyMedium)
                // Aperçu repas par repas : on voit tout avant de valider, et on peut retirer ce qui ne plaît pas
                r.first.groupBy { it.date }.toSortedMap().forEach { (d, list) ->
                    Text(fmt(d, "EEEE d MMMM").replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary)
                    list.sortedBy { it.slot.ordinal }.forEach { m ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(m.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text(listOfNotNull(m.slot.label, "${m.kcal} kcal", m.costEur.takeIf { it > 0 }?.let { "≈ ${euros(it)}" }).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { result = r.copy(first = r.first.filterNot { it.id == m.id }) }) {
                                Icon(Icons.Filled.Close, t("Retirer ce repas"))
                            }
                        }
                    }
                }
                Text(
                    t("Les repas déjà prévus (non mangés) sur ces créneaux seront remplacés."),
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
                        // Les précisions sont retenues pour la prochaine fois (chiffrées sur le téléphone)
                        Repo.updateSettings { it.copy(planNotes = notes.trim(), planShopping = withShopping) }
                        scope.launch {
                            try {
                                result = Gemini(settings.apiKey, settings.model)
                                    .planWeek(profile!!, startDate, b!!, people, MealSlot.entries.filter { it in slots }, notes.trim(), days)
                            } catch (e: Exception) {
                                error = e.message
                            } finally {
                                loading = false
                            }
                        }
                    }
                ) { Text(t("Générer")) }
            } else {
                TextButton(enabled = r.first.isNotEmpty(), onClick = {
                    Repo.addPlannedWeek(r.first, r.first.map { it.date }.toSet() + weekDaysFrom(startDate, days), slots)
                    // Liste de courses remplie toute seule à partir de la semaine (en arrière-plan, même si le dialogue se ferme)
                    if (withShopping) {
                        val meals = r.first
                        val key = settings.apiKey; val model = settings.model; val n = people
                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                            val items = runCatching { Gemini(key, model).shoppingList(meals, n) }.getOrNull()
                            if (!items.isNullOrEmpty()) Shopping.add(items)
                        }
                    }
                    onDone(startDate)
                    onDismiss()
                }) { Text(t("Ajouter au planning")) }
            }
        },
        dismissButton = {
            val r = result
            if (r != null) TextButton(onClick = { result = null }) { Text(t("Refaire")) }
            else TextButton(enabled = !loading, onClick = onDismiss) { Text(t("Annuler")) }
        }
    )
}

private fun weekDaysFrom(start: String, days: Int = 7): Set<String> {
    val c = Calendar.getInstance().apply { time = parse(start) ?: time }
    return (0 until days).map { ISO.format(c.time).also { c.add(Calendar.DAY_OF_YEAR, 1) } }.toSet()
}

/**
 * Aperçu d'un planning proposé par l'IA, en plein écran : chaque jour en carte, chaque repas avec son moment, ses
 * calories et son prix, et une croix pour le retirer. Rien n'est ajouté avant « Ajouter au planning ».
 */
@Composable
private fun PlanPreview(
    r: Pair<List<PlannedMeal>, String>, days: Int, people: Int, budget: Int,
    onRemove: (Long) -> Unit, onRedo: () -> Unit, onDismiss: () -> Unit, onAccept: () -> Unit
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, t("Fermer")) }
                    Text(if (days == 1) t("Ta journée est prête") else t("Ta semaine est prête"), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                }
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val total = r.first.sumOf { it.costEur }
                    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                        Column(Modifier.fillMaxWidth().padding(14.dp)) {
                            Text(t("≈ %1\$s pour %2\$s repas", euros(total), r.first.size), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text(t("Budget : %1\$s € · %2\$s personne(s)", budget, people), style = MaterialTheme.typography.bodySmall)
                            if (r.second.isNotBlank()) Text(r.second, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                    Text(t("Touche ✕ pour retirer un repas qui ne te plaît pas. Les repas déjà prévus (non mangés) sur ces créneaux seront remplacés."),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    r.first.groupBy { it.date }.toSortedMap().forEach { (d, list) ->
                        SectionCard(title = fmt(d, "EEEE d MMMM").replaceFirstChar { it.uppercase() }) {
                            list.sortedBy { it.slot.ordinal }.forEachIndexed { i, m ->
                                if (i > 0) HorizontalDivider()
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
                                        Text(m.slot.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                        Text(m.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                                        Text(listOfNotNull("${m.kcal} kcal", m.costEur.takeIf { it > 0 }?.let { "≈ ${euros(it)}" }).joinToString(" · "),
                                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        if (m.description.isNotBlank()) Text(m.description, style = MaterialTheme.typography.bodySmall, maxLines = 2,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                    }
                                    IconButton(onClick = { onRemove(m.id) }) { Icon(Icons.Filled.Close, t("Retirer ce repas")) }
                                }
                            }
                        }
                    }
                    AiContentFooter(r.first.joinToString("\n") { "${it.date} ${it.slot.label} : ${it.name} (${it.costEur} €)" })
                    Spacer(Modifier.height(8.dp))
                }
                Surface(tonalElevation = 3.dp) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = onRedo, modifier = Modifier.weight(1f)) { Text(t("Refaire")) }
                        Button(enabled = r.first.isNotEmpty(), onClick = onAccept, modifier = Modifier.weight(1f)) { Text(t("Ajouter au planning")) }
                    }
                }
            }
        }
    }
}
