@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.goodlife.app.ui.screens

import com.goodlife.app.i18n.t

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.goodlife.app.coach.CoachBrain
import com.goodlife.app.coach.CoachStore
import com.goodlife.app.coach.clock
import com.goodlife.app.coach.slotNoun
import com.goodlife.app.coach.slotOf
import com.goodlife.app.data.MealSlot
import com.goodlife.app.data.Repo
import com.goodlife.app.data.localDay
import com.goodlife.app.ui.ChefMascot
import com.goodlife.app.ui.ChefMood
import com.goodlife.app.ui.SectionCard

/** « Aujourd'hui » ou « Hier ». */
private fun dayWord(day: String): String = when (day) {
    localDay(0) -> t("Aujourd'hui")
    localDay(-1) -> t("Hier")
    else -> day
}

/** « Hier · dîner (19:50) » pour l'heure d'un repas noté après coup. */
fun mealWhenLabel(ms: Long): String {
    val minute = CoachBrain.minuteOf(ms)
    return dayWord(CoachBrain.dayOf(ms)) + " · " + slotNoun(slotOf(minute)) + " (" + clock(minute) + ")"
}

/**
 * Repas probablement oubliés (hier et aujourd'hui) : « Ajouter » (rangé à l'heure habituelle de ce repas) ou
 * « Pas mangé ». Un oubli fausse les calories, le score et les conseils du coach : on propose de le rattraper.
 */
@Composable
fun CatchUpCard() {
    val meals by Repo.meals.collectAsState()
    val mem by CoachStore.mem.collectAsState()
    val notice by CoachStore.notice.collectAsState()
    val items = remember(meals, mem.skipped, mem.catchupDismissed, mem.fasting) { CoachBrain.forgotten() }

    // Confirmation après un repas noté après coup (il n'apparaît pas dans « Repas du jour » s'il est d'hier)
    notice?.let { ms ->
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(t("✓ Repas ajouté : %1\$s", mealWhenLabel(ms)), style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f).padding(vertical = 10.dp))
                IconButton(onClick = { CoachStore.notice.value = null }) { Icon(Icons.Filled.Close, t("Fermer")) }
            }
        }
    }
    if (items.isEmpty()) return

    SectionCard(container = MaterialTheme.colorScheme.secondaryContainer) {
        Row(verticalAlignment = Alignment.Top) {
            ChefMascot(size = 48.dp, mood = ChefMood.QUESTION)
            Spacer(Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (items.size == 1) t("Un repas oublié ?") else t("Des repas oubliés ?"), style = MaterialTheme.typography.titleMedium)
                Text(t("Je ne vois pas ces repas. Si tu les as oubliés, ajoute-les : tes calories, ton score et mes conseils resteront justes. Sinon, dis-moi que tu n'as pas mangé."),
                    style = MaterialTheme.typography.bodySmall)
            }
        }
        items.forEach { (day, slot) ->
            HorizontalDivider()
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(dayWord(day) + " · " + slot.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                TextButton(onClick = { CoachStore.skip(day, slot) }) { Text(t("Pas mangé")) }
                Button(onClick = { CoachPanels.check.value = MealCheckReq(slot, day) }) { Text(t("Ajouter")) }
            }
        }
        TextButton(onClick = { CoachStore.dismissCatchup(items) }) { Text(t("Ne plus demander pour ces repas")) }
    }
}

/**
 * « C'était quand ? » dans Ajouter : pour un repas oublié, on choisit le moment (aujourd'hui ou hier) ; le prochain
 * repas ajouté prend cette heure, puis on revient à « maintenant ».
 */
@Composable
fun MealTimeChip() {
    val mem by CoachStore.mem.collectAsState()
    val now = System.currentTimeMillis()
    val active = mem.backdate > 0 && now - mem.backdateSetAt in 0..30 * 60_000L
    var open by remember { mutableStateOf(false) }
    fun clear() = CoachStore.update { it.copy(backdate = 0L, backdateSetAt = 0L) }
    AssistChip(
        onClick = { open = true },
        label = { Text(if (active) t("Repas de : %1\$s", mealWhenLabel(mem.backdate)) else t("Repas de maintenant · un oubli ?")) },
        leadingIcon = { Icon(Icons.Filled.Schedule, null, Modifier.size(18.dp)) },
        trailingIcon = if (active) ({ Icon(Icons.Filled.Close, t("Revenir à maintenant"), Modifier.size(18.dp)) }) else null
    )
    if (!open) return
    AlertDialog(
        onDismissRequest = { open = false },
        title = { Text(t("C'était quand ?")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(t("Tu as oublié de noter un repas ? Choisis quand tu l'as mangé : il sera rangé à la bonne heure, et ta journée restera juste."),
                    style = MaterialTheme.typography.bodyMedium)
                FilterChip(selected = !active, onClick = { clear(); open = false }, label = { Text(t("Maintenant")) })
                listOf(0, -1).forEach { offset ->
                    val day = localDay(offset)
                    Text(dayWord(day), style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(MealSlot.PETIT_DEJ, MealSlot.DEJEUNER, MealSlot.COLLATION, MealSlot.DINER).forEach { slot ->
                            val at = CoachBrain.usualTimeOn(slot, day)
                            FilterChip(
                                selected = active && mem.backdate == at,
                                enabled = at <= now,   // pas de repas dans le futur
                                onClick = { CoachStore.backdateNext(at); open = false },
                                label = { Text(slot.label + " · " + clock(CoachBrain.minuteOf(at))) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { open = false }) { Text(t("Fermer")) } }
    )
}
