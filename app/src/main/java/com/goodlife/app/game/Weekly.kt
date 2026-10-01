package com.goodlife.app.game

import com.goodlife.app.data.Repo
import com.goodlife.app.social.WeekStats
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Défi de la semaine entre amis (lundi → dimanche). Chaque semaine a son défi : jours validés, pas ou XP,
 * à tour de rôle. Mes chiffres viennent du jeu (mêmes règles que les séries) ; ceux des amis, de leur
 * dernière carte reçue cette semaine.
 */
object Weekly {
    enum class Kind(val title: String, val unit: String) {
        DAYS("Le plus de jours validés", "j"),
        STEPS("Le plus de pas", "pas"),
        XP("Le plus d'XP gagnée", "XP")
    }

    /** Numéro de semaine (lundi = début), compté depuis 1970, en heure locale. */
    fun weekOf(c: Calendar = Calendar.getInstance()): Int {
        val epochDay = (c.timeInMillis + c.get(Calendar.ZONE_OFFSET) + c.get(Calendar.DST_OFFSET)) / 86_400_000L
        return ((epochDay + 3) / 7).toInt()   // le 1er janvier 1970 était un jeudi
    }

    fun current(): Int = weekOf()

    fun kind(week: Int = current()): Kind = Kind.entries[week % Kind.entries.size]

    /** Jours restants dans la semaine, aujourd'hui compris (7 le lundi, 1 le dimanche). */
    fun daysLeft(): Int {
        val dow = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)   // dimanche = 1
        return if (dow == Calendar.SUNDAY) 1 else 9 - dow
    }

    /** Lundi de la semaine en cours (AAAA-MM-JJ). */
    fun monday(): String {
        val c = Calendar.getInstance()
        while (c.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) c.add(Calendar.DAY_OF_YEAR, -1)
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(c.time)
    }

    /** Mon bilan de la semaine, calculé avec les mêmes règles que le score et la série. */
    fun myStats(): WeekStats? {
        val p = Repo.profile.value ?: return null
        val game = Repo.game.value
        val s = Game.summarize(Repo.meals.value, p, game, Repo.steps.value.days, newRulesFrom = Repo.settings.value.scoreRulesFrom)
        val from = monday()
        val past = s.history.filter { it.date >= from }
        val days = past.count { it.status == DayStatus.REUSSI || it.status == DayStatus.RATTRAPE } +
            (if (s.today.status == DayStatus.REUSSI) 1 else 0)
        val steps = Repo.steps.value.days.filterKeys { it >= from }.values.sumOf { it.steps }
        val xp = past.sumOf { it.xp } +
            game.quizResults.filterKeys { it >= from }.values.sumOf { Game.quizXp(it) } +
            game.sportXp.filterKeys { it >= from }.values.sumOf { it.coerceIn(0, Game.SPORT_XP_PER_DAY) }
        return WeekStats(current(), days, steps, xp)
    }

    fun value(kind: Kind, days: Int, steps: Int, xp: Int): Int = when (kind) {
        Kind.DAYS -> days; Kind.STEPS -> steps; Kind.XP -> xp
    }
}
