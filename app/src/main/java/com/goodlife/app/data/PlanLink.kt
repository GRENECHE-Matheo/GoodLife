package com.goodlife.app.data

import java.text.Normalizer
import java.util.Calendar

/**
 * Lien entre un repas photographié et le planning du jour :
 * - rien de prévu à ce créneau → le repas est ajouté au planning, déjà coché « mangé » ;
 * - le repas prévu ressemble à celui photographié → il est coché « mangé » ;
 * - un autre repas était prévu → on demande « Remplacer le repas prévu ? » (voir [replace]).
 */
object PlanLink {

    /** Créneau du repas d'après l'heure (de 4 h à 10 h 30 petit-déjeuner, puis déjeuner jusqu'à 15 h, collation jusqu'à 18 h, puis dîner, y compris après minuit). */
    fun slotNow(cal: Calendar = Calendar.getInstance()): MealSlot {
        val minutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        return when {
            minutes < 4 * 60 -> MealSlot.DINER
            minutes < 10 * 60 + 30 -> MealSlot.PETIT_DEJ
            minutes < 15 * 60 -> MealSlot.DEJEUNER
            minutes < 18 * 60 -> MealSlot.COLLATION
            else -> MealSlot.DINER
        }
    }

    private fun words(s: String): Set<String> {
        val plain = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
        return plain.split(Regex("[^a-z]+"))
            .filter { it.length >= 3 && it !in STOP }
            .map { it.removeSuffix("s").removeSuffix("x") }
            .toSet()
    }

    private val STOP = setOf("avec", "aux", "des", "les", "une", "sans", "sur", "pour", "par", "fait", "maison", "assiette", "portion", "plat", "bol")

    /** Deux noms de repas qui désignent sans doute la même chose (un mot important en commun). */
    fun similar(a: String, b: String): Boolean {
        val wa = words(a); val wb = words(b)
        if (wa.isEmpty() || wb.isEmpty()) return false
        return wa.intersect(wb).isNotEmpty()
    }

    /**
     * À appeler après l'ajout d'un repas photographié. Gère seul les cas simples et renvoie le repas prévu
     * à proposer de remplacer (repas différent au même créneau), ou null.
     */
    fun afterMeal(meal: Meal, slot: MealSlot = slotNow()): PlannedMeal? {
        // Après minuit (avant 4 h), c'est encore le dîner de la veille
        val today = localDay(if (Calendar.getInstance().get(Calendar.HOUR_OF_DAY) < 4) -1 else 0)
        val atSlot = Repo.plan.value.filter { it.date == today && it.slot == slot }
        val pending = atSlot.filter { !it.done }
        val match = pending.firstOrNull { similar(it.name, meal.name) }
        return when {
            match != null -> { Repo.updatePlanned(match.copy(done = true)); null }
            pending.isNotEmpty() -> pending.first()
            // Déjà un repas mangé à ce créneau (dessert, deuxième assiette…) : on ne double pas le planning
            atSlot.isNotEmpty() -> null
            else -> {
                Repo.addPlanned(PlannedMeal(date = today, slot = slot, name = meal.name, kcal = meal.kcal, description = meal.details.take(300), done = true))
                null
            }
        }
    }

    /** « Remplacer le repas prévu » : le planning garde ce qui a vraiment été mangé. */
    fun replace(planned: PlannedMeal, meal: Meal) {
        Repo.updatePlanned(planned.copy(name = meal.name, kcal = meal.kcal, description = meal.details.take(300), recipe = null, costEur = 0.0, done = true))
    }
}
