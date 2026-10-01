package com.goodlife.app.ai

import android.content.Context
import com.goodlife.app.data.Repo
import com.goodlife.app.data.localDay

/**
 * Objectif d'eau conseillé par l'IA, recalculé une fois par jour (à la première ouverture de l'app, ou au relevé
 * des pas en arrière-plan) quand l'IA est activée et que la personne n'a pas choisi un objectif fixe.
 * L'IA tient compte des besoins (âge, sexe, poids, activité, apport calorique visé) et de l'activité réelle d'hier
 * (pas, séances, sorties) ; l'app borne ensuite le résultat (1 à 4 L, ±25 % d'un jour à l'autre).
 */
object WaterGoalAi {
    @Volatile private var running = false

    suspend fun refreshIfNeeded(context: Context) {
        Repo.init(context)
        val s = Repo.settings.value
        val p = Repo.profile.value ?: return
        val today = localDay(0)
        if (running || s.waterGoalMode != "ia" || s.waterGoalIaDay == today) return
        if (!Repo.aiAllowed() || s.apiKey.isBlank()) return
        running = true
        try {
            val yesterday = localDay(-1)
            val steps = Repo.steps.value.days[yesterday]?.steps ?: 0
            val (dayStart, dayEnd) = Repo.dayBounds(-1)
            val outings = Repo.outings.value.filter { it.start in dayStart until dayEnd }
            val sessions = Repo.sport.value.done.count { it.startsWith(yesterday) }
            val (ml, why) = Gemini(s.apiKey, s.model).recommendWater(
                p, stepsYesterday = steps, sessionsYesterday = sessions,
                outingMinutesYesterday = (outings.sumOf { it.movingMs } / 60_000).toInt(), previous = s.waterGoalIa
            )
            Repo.updateSettings { it.copy(waterGoalIa = ml, waterGoalIaWhy = why, waterGoalIaDay = today) }
        } catch (_: Exception) {
            // Pas de réseau ou clé refusée : on garde l'objectif d'hier et on réessaiera à la prochaine ouverture
        } finally {
            running = false
        }
    }
}
