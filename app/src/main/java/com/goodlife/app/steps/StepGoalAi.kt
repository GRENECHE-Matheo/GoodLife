package com.goodlife.app.steps

import android.content.Context
import com.goodlife.app.ai.Gemini
import com.goodlife.app.data.Repo
import com.goodlife.app.data.localDay

/**
 * Objectif de pas conseillé par l'IA, recalculé une fois par jour (à la première ouverture de l'app, ou au relevé
 * des pas en arrière-plan) quand la personne a choisi « Conseil de l'IA ». L'IA voit les vrais pas des 7 derniers
 * jours et l'objectif d'hier ; l'app limite ensuite la variation (±15 % par jour) pour une progression réaliste.
 */
object StepGoalAi {
    @Volatile private var running = false

    suspend fun refreshIfNeeded(context: Context) {
        Repo.init(context)
        val s = Repo.settings.value
        val p = Repo.profile.value ?: return
        val today = localDay(0)
        if (running || !s.stepsEnabled || s.stepsGoalMode != "ia" || s.stepsGoalIaDay == today) return
        if (!Repo.aiAllowed() || s.apiKey.isBlank()) return
        running = true
        try {
            val days = Repo.steps.value.days
            val week = (1..7).mapNotNull { d -> days[localDay(-d)]?.let { localDay(-d) to it } }
            val (goal, why) = Gemini(s.apiKey, s.model).recommendSteps(p, Steps.weekAverage(), week, s.stepsGoalIa)
            Repo.updateSettings { it.copy(stepsGoalIa = goal, stepsGoalIaWhy = why, stepsGoalIaDay = today) }
        } catch (_: Exception) {
            // Pas de réseau ou clé refusée : on garde l'objectif d'hier et on réessaiera à la prochaine ouverture
        } finally {
            running = false
        }
    }
}
