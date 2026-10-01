package com.goodlife.app.game

import com.goodlife.app.data.GameState
import com.goodlife.app.data.Goal
import com.goodlife.app.data.Meal
import com.goodlife.app.data.Profile
import com.goodlife.app.data.StepDay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

enum class DayStatus { REUSSI, RATE, RATTRAPE, VIDE }

data class DayResult(
    val date: String,
    val kcal: Int,
    val score: Int,        // 0..100 : score du jour (alimentation + pas)
    val status: DayStatus,
    val xp: Int,
    val streakAfter: Int,
    val foodScore: Int = score,
    val steps: Int = 0,
    val stepGoal: Int = 0  // 0 = pas de suivi des pas ce jour-là
)

data class LevelInfo(val level: Int, val title: String, val xpInLevel: Int, val xpForNext: Int, val totalXp: Int) {
    val progress: Float get() = if (xpForNext > 0) xpInLevel.toFloat() / xpForNext else 1f
}

data class GameSummary(
    val history: List<DayResult>,     // jours terminés (jusqu'à hier), du plus ancien au plus récent
    val today: DayResult,             // aujourd'hui, en cours
    val streak: Int,
    val bestStreak: Int,
    val level: LevelInfo,
    /** Longueur de la série cassée hier et encore récupérable aujourd'hui par le quiz (sinon 0). */
    val recoverableStreak: Int,
    val quizDoneToday: Boolean
)

/**
 * Règles (pensées pour la santé, et pour rester motivant) :
 * - Score alimentation 0..100 : 100 dans la zone idéale de l'objectif calorique, puis il baisse
 *   quand on s'en éloigne (trop OU trop peu : manger trop peu n'est jamais récompensé).
 * - Score pas 0..100 : part de l'objectif de pas atteinte.
 * - Score du jour = 60 % alimentation + 40 % pas (alimentation seule si le suivi des pas est coupé).
 * - Série validée dès 80/100 : pas besoin d'être parfait, mais il faut faire les deux.
 * - Un jour sans aucun repas enregistré n'est pas réussi.
 */
object Game {
    private val fmt get() = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    /** Score du jour à partir duquel la série continue. */
    const val STREAK_SCORE = 80

    /** XP sport : 15 par séance du programme, selon la durée pour une sortie ; au plus 40 par jour. */
    const val SPORT_XP_PER_DAY = 40
    const val SESSION_XP = 15

    /** Gels de série : 1 gagné tous les 7 jours de série, 2 au maximum. Un gel = quiz de 10 questions. */
    const val MAX_FREEZES = 2
    const val FREEZE_EVERY = 7
    const val FREEZE_QUESTIONS = 10

    /** Nouvel état des gels après une série de [streak] jours (null si rien ne change). */
    fun freezesAfter(g: GameState, streak: Int): GameState? {
        val palier = streak - streak % FREEZE_EVERY
        return when {
            g.freezeMark < 0 -> g.copy(freezeMark = palier, freezes = if (streak >= FREEZE_EVERY) maxOf(g.freezes, 1) else g.freezes)
            streak < g.freezeMark -> g.copy(freezeMark = palier)
            palier > g.freezeMark -> g.copy(
                freezes = minOf(MAX_FREEZES, g.freezes + (palier - g.freezeMark) / FREEZE_EVERY), freezeMark = palier
            )
            else -> null
        }
    }

    /** Une sortie GPS : 1 XP par tranche de 2 minutes en mouvement, au plus 30. */
    fun outingXp(movingMinutes: Long): Int = (movingMinutes / 2).toInt().coerceIn(0, 30)

    private data class Ideal(val min: Double, val max: Double)

    private fun ideal(goal: Goal) = when (goal) {
        Goal.PERTE -> Ideal(0.90, 1.00)
        Goal.MAINTIEN -> Ideal(0.95, 1.05)
        Goal.PRISE -> Ideal(1.00, 1.10)
    }

    fun rulesText(goal: Goal): String {
        val food = when (goal) {
            Goal.PERTE -> "reste juste sous ton objectif calorique (manger trop peu fait aussi baisser le score)"
            Goal.MAINTIEN -> "reste proche de ton objectif calorique"
            Goal.PRISE -> "atteins ton objectif calorique, sans trop le dépasser"
        }
        return "Score du jour = 60 % alimentation ($food) + 40 % pas si tu suis tes pas. " +
            "Ta série continue dès $STREAK_SCORE/100."
    }

    /** Score alimentation 0..100 pour une journée. */
    fun foodScore(kcal: Int, target: Int, goal: Goal): Int {
        if (kcal <= 0 || target <= 0) return 0
        val r = kcal.toDouble() / target
        val b = ideal(goal)
        val distance = when {
            r < b.min -> b.min - r
            r > b.max -> r - b.max
            else -> 0.0
        }
        return (100 - distance * 250).roundToInt().coerceIn(0, 100)
    }

    fun stepScore(steps: Int, goal: Int): Int =
        if (goal <= 0) 0 else (steps * 100.0 / goal).roundToInt().coerceIn(0, 100)

    /** Anciennes règles (avant la v0.7), gardées pour ne pas casser rétroactivement les séries existantes. */
    private fun legacyOk(kcal: Int, target: Int, goal: Goal): Boolean {
        if (kcal <= 0 || target <= 0) return false
        val r = kcal.toDouble() / target
        val (min, max) = when (goal) {
            Goal.PERTE -> 0.70 to 1.00
            Goal.MAINTIEN -> 0.90 to 1.10
            Goal.PRISE -> 1.00 to 1.30
        }
        return r >= min - 1e-9 && r <= max + 1e-9
    }

    /** Score du jour (0..100) et réussite. [step] = null si les pas n'étaient pas suivis ce jour-là. */
    fun evaluate(kcal: Int, target: Int, goal: Goal, step: StepDay?): Pair<Int, Boolean> {
        val food = foodScore(kcal, target, goal)
        val score = if (step == null || step.goal <= 0) food
                    else (food * 0.6 + stepScore(step.steps, step.goal) * 0.4).roundToInt()
        return score to (kcal > 0 && score >= STREAK_SCORE)
    }

    fun levelFor(totalXp: Int): LevelInfo {
        var level = 1
        while (threshold(level + 1) <= totalXp) level++
        val start = threshold(level)
        return LevelInfo(level, title(level), totalXp - start, threshold(level + 1) - start, totalXp)
    }

    /** XP cumulée nécessaire pour atteindre un niveau : 0, 100, 300, 600, 1000… */
    private fun threshold(level: Int): Int = 50 * level * (level - 1)

    fun title(level: Int): String = when (level) {
        1 -> "Commis"
        2 -> "Apprenti"
        3 -> "Cuisinier"
        4 -> "Chef de partie"
        5 -> "Sous-chef"
        6 -> "Chef"
        7 -> "Chef étoilé"
        8 -> "Chef deux étoiles"
        9 -> "Chef trois étoiles"
        else -> "Légende de la cuisine"
    }

    fun quizXp(correct: Int): Int = correct * 5

    fun summarize(
        meals: List<Meal>,
        profile: Profile,
        game: GameState,
        steps: Map<String, StepDay> = emptyMap(),
        now: Calendar = Calendar.getInstance(),
        newRulesFrom: String = ""   // AAAA-MM-JJ : avant ce jour, anciennes règles de validation
    ): GameSummary {
        val byDay = meals.groupBy { fmt.format(java.util.Date(it.timestamp)) }.mapValues { e -> e.value.sumOf { it.kcal } }
        val todayKey = fmt.format(now.time)
        val firstDay = byDay.keys.minOrNull()

        val history = mutableListOf<DayResult>()
        var streak = 0
        var best = 0
        if (firstDay != null && firstDay < todayKey) {
            val c = Calendar.getInstance().apply {
                time = fmt.parse(maxOf(firstDay, dayOffset(now, -365)))!!
            }
            while (true) {
                val key = fmt.format(c.time)
                if (key >= todayKey) break
                val kcal = byDay[key] ?: 0
                val step = steps[key]
                val (score, newOk) = evaluate(kcal, profile.targetKcal, profile.goal, step)
                val ok = if (newRulesFrom.isNotEmpty() && key < newRulesFrom) legacyOk(kcal, profile.targetKcal, profile.goal) else newOk
                val status = when {
                    ok -> DayStatus.REUSSI
                    key in game.recoveredDays -> DayStatus.RATTRAPE
                    kcal == 0 -> DayStatus.VIDE
                    else -> DayStatus.RATE
                }
                streak = if (status == DayStatus.REUSSI || status == DayStatus.RATTRAPE) streak + 1 else 0
                best = max(best, streak)
                val xp = when (status) {
                    DayStatus.REUSSI -> score / 2 + 20 + minOf(streak, 10) * 2
                    // Jamais moins qu'un jour raté : sauver sa série ne doit pas faire perdre d'XP
                    DayStatus.RATTRAPE -> maxOf(10, score / 4)
                    DayStatus.RATE -> score / 4
                    DayStatus.VIDE -> 0
                }
                history += DayResult(
                    key, kcal, score, status, xp, streak,
                    foodScore(kcal, profile.targetKcal, profile.goal), step?.steps ?: 0, step?.goal ?: 0
                )
                c.add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        val todayKcal = byDay[todayKey] ?: 0
        val todayStep = steps[todayKey]
        val (todayScore, todayOk) = evaluate(todayKcal, profile.targetKcal, profile.goal, todayStep)
        val today = DayResult(
            todayKey, todayKcal, todayScore,
            if (todayOk) DayStatus.REUSSI else if (todayKcal == 0) DayStatus.VIDE else DayStatus.RATE,
            0, streak,
            foodScore(todayKcal, profile.targetKcal, profile.goal), todayStep?.steps ?: 0, todayStep?.goal ?: 0
        )

        // Série cassée hier et récupérable aujourd'hui (une seule tentative de quiz par jour)
        val yesterday = history.lastOrNull()
        val quizDone = game.quizResults.containsKey(todayKey)
        val recoverable = if (
            yesterday != null && yesterday.date == dayOffset(now, -1) &&
            (yesterday.status == DayStatus.RATE || yesterday.status == DayStatus.VIDE) && !quizDone
        ) history.getOrNull(history.size - 2)?.streakAfter ?: 0 else 0

        val totalXp = history.sumOf { it.xp } + game.quizResults.values.sumOf { quizXp(it) } +
            game.sportXp.values.sumOf { it.coerceIn(0, SPORT_XP_PER_DAY) }
        return GameSummary(history, today, streak, best, levelFor(totalXp), recoverable, quizDone)
    }

    fun dayOffset(now: Calendar, offset: Int): String {
        val c = now.clone() as Calendar
        c.add(Calendar.DAY_OF_YEAR, offset)
        return fmt.format(c.time)
    }
}
