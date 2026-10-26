package com.goodlife.app

import android.content.Context
import com.goodlife.app.coach.CoachNotifier
import com.goodlife.app.data.Meal
import com.goodlife.app.data.MealSlot
import com.goodlife.app.data.Repo
import com.goodlife.app.data.SleepSession
import com.goodlife.app.data.localDay

/**
 * Version de développement seulement (absente de l'app publiée) : remplit l'app avec 3 semaines de données fictives
 * pour tester le coach, et déclenche ses notifications à la demande.
 *   adb shell am start -n com.goodlife.app.debug/com.goodlife.app.MainActivity --es goodlife_demo seed
 *   … --es goodlife_demo meal | move | weekly | morning
 */
object DemoData {
    private const val DAY = 86_400_000L

    @JvmStatic
    fun run(context: Context, what: String) {
        when (what) {
            "seed" -> seed()
            "meal" -> CoachNotifier.fire(context, CoachNotifier.Kind.MEAL, MealSlot.DEJEUNER, force = true)
            "move" -> CoachNotifier.fire(context, CoachNotifier.Kind.MOVE, force = true)
            "weekly" -> CoachNotifier.fire(context, CoachNotifier.Kind.WEEKLY)
            "morning" -> CoachNotifier.fire(context, CoachNotifier.Kind.MORNING)
            "tour" -> { com.goodlife.app.social.AppNav.request.value = "home:"; com.goodlife.app.ui.Tour.start() }
        }
    }

    /** Profil type : calories dans l'objectif, protéines basses, déjeuner parfois sauté, pas en baisse depuis 3 jours. */
    private fun seed() {
        val p = Repo.profile.value ?: return
        val target = p.targetKcal.takeIf { it > 0 } ?: 2400
        val start = Repo.dayBounds(-21).first
        Repo.meals.value.filter { it.timestamp >= start && it.source == "demo" }.forEach { Repo.deleteMeal(it.id) }
        var id = System.currentTimeMillis()
        for (d in 21 downTo 1) {
            val dayStart = Repo.dayBounds(-d).first
            fun meal(h: Int, m: Int, name: String, share: Double, protein: Double) {
                val kcal = (target * share).toInt()
                Repo.addMeal(Meal(id = id++, timestamp = dayStart + (h * 60 + m) * 60_000L, name = name, kcal = kcal,
                    proteinG = protein, carbsG = kcal * 0.52 / 4, fatG = kcal * 0.33 / 9, source = "demo"))
            }
            meal(8, 5 + d % 3 * 5, "Tartines et café", 0.22, 8.0)
            if (d % 4 != 1) meal(12, 30 + d % 2 * 10, "Pâtes à la tomate", 0.40, 18.0)
            meal(19, 50, "Riz et légumes", 0.38, 20.0)
            Repo.updateSteps { s -> s.copy(days = s.days + (localDay(-d) to com.goodlife.app.data.StepDay(if (d <= 3) 3200 else 8800 + d * 37, 7000))) }
            val bed = dayStart - 60 * 60_000L   // 23 h la veille
            Repo.addSleep(SleepSession(id = id++, start = bed, end = bed + (if (d % 3 == 0) 6 * 60 else 7 * 60 + 20) * 60_000L, source = "demo"))
        }
        Repo.updateGame { g -> g.copy(weights = (g.weights.filter { it.first < localDay(-21) } +
            listOf(localDay(-20) to p.weightKg + 0.6, localDay(-13) to p.weightKg + 0.3, localDay(-6) to p.weightKg + 0.1, localDay(-1) to p.weightKg)).sortedBy { it.first }) }
    }
}
