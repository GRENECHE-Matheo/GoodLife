package com.goodlife.app.coach

import com.goodlife.app.data.Goal
import com.goodlife.app.data.MealSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InsightsTest {
    private val days = (1..35).map { String.format("2026-09-%02d", it).let { d -> if (it > 30) String.format("2026-10-%02d", it - 30) else d } }
    private val past = days.dropLast(1).takeLast(7)

    /** Une journée type : petit-déjeuner, déjeuner et dîner, avec les macros données (par jour). */
    private fun day(d: String, kcal: Int, p: Double, c: Double, f: Double, breakfast: Boolean = true): List<MealFact> {
        val parts = if (breakfast) listOf(8 * 60 to 0.25, 12 * 60 + 30 to 0.4, 19 * 60 + 45 to 0.35) else listOf(12 * 60 + 30 to 0.55, 19 * 60 + 45 to 0.45)
        return parts.map { (m, k) -> MealFact(d, m, (kcal * k).toInt(), p * k, c * k, f * k) }
    }

    private fun input(meals: List<MealFact>, goal: Goal = Goal.MAINTIEN, target: Int = 2400, age: Int = 30, weight: Double = 75.0, height: Double = 178.0,
                      sessions: Map<String, Int> = emptyMap(), steps: Map<String, Int> = emptyMap(), skipped: Set<String> = emptySet(),
                      weights: List<Pair<String, Double>> = emptyList(), female: Boolean = false, tone: Tone = Tone.EQUILIBRE) = CoachInput(
        days = days, nowMinute = 18 * 60, dayOfWeek = 5, age = age, female = female, weightKg = weight, heightCm = height,
        activityFactor = 1.55, goal = goal, targetKcal = target, meals = meals, steps = steps, stepsEnabled = steps.isNotEmpty(),
        sessions = sessions, skipped = skipped, weights = weights, tone = tone
    )

    private fun ids(i: CoachInput) = Insights.all(i).map { it.id }

    @Test fun pasDeConseilSansAssezDeJours() {
        val meals = past.take(3).flatMap { day(it, 2400, 40.0, 300.0, 80.0) }
        assertFalse(ids(input(meals)).any { it.startsWith("food.") })
    }

    @Test fun caloriesBonnesMaisProteinesBasses() {
        // 2 400 kcal (l'objectif), seulement 40 g de protéines pour 75 kg : le souci n'est pas les calories
        val meals = past.flatMap { day(it, 2400, 40.0, 330.0, 85.0) }
        val all = Insights.all(input(meals))
        val prot = all.first { it.id == "food.protein" }
        assertTrue(prot.text.contains("calories"))
        assertNotNull(prot.challenge)
        assertFalse(all.any { it.id == "food.ok" })
    }

    @Test fun peuDeGlucidesSeulementSiSportif() {
        val meals = past.flatMap { day(it, 2400, 140.0, 150.0, 140.0) }   // ~25 % de glucides
        assertFalse(ids(input(meals)).contains("food.carbs"))
        val sport = past.take(4).associateWith { 1 }
        assertTrue(ids(input(meals, sessions = sport)).contains("food.carbs"))
    }

    @Test fun assietteEquilibreeFelicitee() {
        val meals = past.flatMap { day(it, 2400, 110.0, 300.0, 85.0) }
        assertTrue(ids(input(meals)).contains("food.ok"))
    }

    @Test fun jamaisDeDeficitPousseAvant18Ans() {
        val meals = past.flatMap { day(it, 2800, 90.0, 380.0, 100.0) }
        assertFalse(ids(input(meals, goal = Goal.PERTE, target = 2000, age = 16)).contains("food.kcal.high"))
        assertTrue(ids(input(meals, goal = Goal.PERTE, target = 2000, age = 30)).contains("food.kcal.high"))
    }

    @Test fun apportsTresBasDeclenchentLeGardeFou() {
        val meals = past.flatMap { day(it, 700, 30.0, 90.0, 20.0) }
        val all = Insights.all(input(meals, goal = Goal.PERTE, target = 2000, female = true, weight = 60.0, height = 165.0))
        assertEquals("care.low", all.first().id)
        // Et aucun conseil pour manger moins ni félicitations
        assertFalse(all.any { it.id == "food.kcal.high" || it.id == "food.ok" || it.id == "weight.good" })
    }

    @Test fun gardeFouEnPause() {
        val meals = past.flatMap { day(it, 700, 30.0, 90.0, 20.0) }
        val i = input(meals).copy(careSnoozed = true)
        assertFalse(ids(i).contains("care.low"))
    }

    @Test fun jamaisRienMangeDeclencheLeGardeFou() {
        // Aucun repas noté, et « pas mangé » midi et soir deux jours de suite : 0 kcal, mais le chef s'inquiète
        val skips = listOf(days[days.size - 2], days.last()).flatMap { d -> listOf("$d#DEJEUNER", "$d#DINER") }.toSet()
        val all = Insights.all(input(emptyList(), skipped = skips))
        assertEquals("care.skip", all.first().id)
        assertFalse(all.any { it.id.startsWith("rhythm.skip") })
        // Un seul repas sauté : pas d'inquiétude
        assertFalse(ids(input(emptyList(), skipped = setOf("${days.last()}#DEJEUNER"))).contains("care.skip"))
    }

    @Test fun perteTropRapide() {
        val w = listOf(days[13] to 80.0, days[20] to 78.4, days[27] to 76.8, days[33] to 75.4)
        val meals = past.flatMap { day(it, 1900, 100.0, 220.0, 60.0) }
        assertTrue(ids(input(meals, goal = Goal.PERTE, target = 1900, weights = w, weight = 75.4)).contains("care.fast"))
    }

    @Test fun repasSautesPlusieursFois() {
        val meals = past.flatMap { day(it, 2400, 110.0, 300.0, 85.0) }
        val skips = past.take(3).map { "$it#${MealSlot.DEJEUNER.name}" }.toSet()
        val i = input(meals, goal = Goal.PRISE, skipped = skips)
        val skip = Insights.all(i).first { it.id == "rhythm.skip.DEJEUNER" }
        assertTrue(skip.text.contains("3"))
        // Jeûne déclaré : plus de remarque
        assertFalse(ids(i.copy(fasting = setOf(MealSlot.DEJEUNER))).contains("rhythm.skip.DEJEUNER"))
    }

    @Test fun petitDejeunerSauteEstUneQuestionPasUneMorale() {
        val meals = past.flatMap { day(it, 2400, 110.0, 300.0, 85.0, breakfast = false) }
        val b = Insights.all(input(meals)).first { it.id == "rhythm.breakfast" }
        assertEquals(InsightKind.ASK, b.kind)
        assertTrue(b.text.contains("Pas d'obligation"))
    }

    @Test fun pasEnBaisseParRapportASaPropreMoyenne() {
        val steps = days.dropLast(4).associateWith { 9000 } + days.dropLast(1).takeLast(3).associateWith { 3500 }
        assertTrue(ids(input(emptyList(), steps = steps)).contains("move.drop"))
        // Un seul mauvais jour ne suffit pas
        val one = days.dropLast(2).associateWith { 9000 } + mapOf(days[days.size - 2] to 3000)
        assertFalse(ids(input(emptyList(), steps = one)).contains("move.drop"))
    }

    @Test fun objectifDePasAdaptatif() {
        val steps = days.dropLast(1).takeLast(9).mapIndexed { i, d -> d to 4000 + i * 500 }.toMap()   // 4 000 à 8 000
        val f = CoachFacts(input(emptyList(), steps = steps))
        assertEquals(6500, f.adaptiveSteps)                // 4e meilleur jour
        val hard = CoachFacts(input(emptyList(), steps = steps, tone = Tone.EXIGEANT))
        assertEquals(7000, hard.adaptiveSteps)             // 3e meilleur jour
    }

    @Test fun heureHabituelleDesRepas() {
        val meals = days.dropLast(1).takeLast(14).flatMap { day(it, 2000, 90.0, 250.0, 70.0) }
        val f = CoachFacts(input(meals))
        assertEquals(12 * 60 + 30, f.usualTime(MealSlot.DEJEUNER))
        assertNull(CoachFacts(input(meals.take(6))).usualTime(MealSlot.DEJEUNER))
    }

    @Test fun bilanSansRougeEtAvecUneReussite() {
        val meals = days.flatMap { day(it, 2400, 110.0, 300.0, 85.0) }
        val i = input(meals)
        val r = Weekly.build(i, Insights.all(i), null)
        assertTrue(r.win.isNotBlank())
        assertTrue(r.metrics.any { it.id == "track" })
        assertTrue(r.proposals.size in 1..3)
        assertEquals(r.proposals.size, r.proposals.map { it.id.substringBefore(':') }.distinct().size)
    }

    @Test fun mesureDuDefi() {
        val steps = days.associateWith { 8000 }
        val i = input(emptyList(), steps = steps)
        assertEquals(7, Weekly.measure(i, ActiveChallenge("steps:7000", "", 4, "", days[20])))
        assertNull(Weekly.measure(i, ActiveChallenge("veggies", "", 5, "", days[20])))
    }

    @Test fun creneauxDesRepas() {
        assertEquals(MealSlot.PETIT_DEJ, slotOf(7 * 60))
        assertEquals(MealSlot.DEJEUNER, slotOf(13 * 60))
        assertEquals(MealSlot.COLLATION, slotOf(16 * 60))
        assertEquals(MealSlot.DINER, slotOf(20 * 60))
        assertEquals(MealSlot.DINER, slotOf(60))
    }
}
