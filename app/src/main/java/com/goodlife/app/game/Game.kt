package com.goodlife.app.game

import com.goodlife.app.data.GameState
import com.goodlife.app.data.Goal
import com.goodlife.app.data.Meal
import com.goodlife.app.data.Profile
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

enum class DayStatus { REUSSI, RATE, RATTRAPE, VIDE }

data class DayResult(
    val date: String,
    val kcal: Int,
    val score: Int,        // 0..100 : proximité avec l'objectif
    val status: DayStatus,
    val xp: Int,
    val streakAfter: Int
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
 * Règles (pensées pour la santé) :
 * - Perte de poids : réussi entre 70 % et 100 % de l'objectif. Dépasser casse la série, mais manger
 *   beaucoup trop peu ne compte pas non plus comme une réussite.
 * - Prise de poids : réussi entre 100 % et 130 % de l'objectif ; pas assez casse la série.
 * - Maintien : réussi entre 90 % et 110 %.
 * - Un jour sans aucun repas enregistré n'est pas réussi.
 */
object Game {
    private val fmt get() = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    private data class Bands(val okMin: Double, val okMax: Double, val idealMin: Double, val idealMax: Double)

    private fun bands(goal: Goal) = when (goal) {
        Goal.PERTE -> Bands(0.70, 1.00, 0.90, 1.00)
        Goal.MAINTIEN -> Bands(0.90, 1.10, 0.95, 1.05)
        Goal.PRISE -> Bands(1.00, 1.30, 1.00, 1.10)
    }

    fun rulesText(goal: Goal): String = when (goal) {
        Goal.PERTE -> "Objectif perte : reste sous ton objectif calorique, sans descendre sous 70 % (manger trop peu ne compte pas)."
        Goal.MAINTIEN -> "Objectif maintien : reste à ±10 % de ton objectif calorique."
        Goal.PRISE -> "Objectif prise : atteins au moins ton objectif calorique (jusqu'à +30 %)."
    }

    /** Score 0..100 et réussite pour une journée. */
    fun evaluate(kcal: Int, target: Int, goal: Goal): Pair<Int, Boolean> {
        if (kcal <= 0 || target <= 0) return 0 to false
        val r = kcal.toDouble() / target
        val b = bands(goal)
        val distance = when {
            r < b.idealMin -> b.idealMin - r
            r > b.idealMax -> r - b.idealMax
            else -> 0.0
        }
        val score = (100 - distance * 250).roundToInt().coerceIn(0, 100)
        return score to (r >= b.okMin - 1e-9 && r <= b.okMax + 1e-9)
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

    fun summarize(meals: List<Meal>, profile: Profile, game: GameState, now: Calendar = Calendar.getInstance()): GameSummary {
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
                val (score, ok) = evaluate(kcal, profile.targetKcal, profile.goal)
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
                    DayStatus.RATTRAPE -> 10
                    DayStatus.RATE -> score / 4
                    DayStatus.VIDE -> 0
                }
                history += DayResult(key, kcal, score, status, xp, streak)
                c.add(Calendar.DAY_OF_YEAR, 1)
            }
        }

        val todayKcal = byDay[todayKey] ?: 0
        val (todayScore, todayOk) = evaluate(todayKcal, profile.targetKcal, profile.goal)
        val today = DayResult(
            todayKey, todayKcal, todayScore,
            if (todayOk) DayStatus.REUSSI else if (todayKcal == 0) DayStatus.VIDE else DayStatus.RATE,
            0, streak
        )

        // Série cassée hier et récupérable aujourd'hui (une seule tentative de quiz par jour)
        val yesterday = history.lastOrNull()
        val quizDone = game.quizResults.containsKey(todayKey)
        val recoverable = if (
            yesterday != null && yesterday.date == dayOffset(now, -1) &&
            (yesterday.status == DayStatus.RATE || yesterday.status == DayStatus.VIDE) && !quizDone
        ) history.getOrNull(history.size - 2)?.streakAfter ?: 0 else 0

        val totalXp = history.sumOf { it.xp } + game.quizResults.values.sumOf { quizXp(it) }
        return GameSummary(history, today, streak, best, levelFor(totalXp), recoverable, quizDone)
    }

    fun dayOffset(now: Calendar, offset: Int): String {
        val c = now.clone() as Calendar
        c.add(Calendar.DAY_OF_YEAR, offset)
        return fmt.format(c.time)
    }
}
