package com.goodlife.app.coach

import com.goodlife.app.i18n.t
import com.goodlife.app.i18n.tp

import com.goodlife.app.data.Goal
import com.goodlife.app.data.MealSlot
import com.goodlife.app.data.Profile
import com.goodlife.app.data.Repo
import com.goodlife.app.data.localDay
import com.goodlife.app.game.DayResult
import com.goodlife.app.game.DayStatus
import com.goodlife.app.game.Game
import com.goodlife.app.game.GameSummary
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

/** Bilan d'une période (7 jours) : tout est calculé sur le téléphone. */
data class WeekStats(
    val trackedDays: Int,        // jours avec au moins un repas noté
    val validated: Int,          // jours réussis (ou rattrapés)
    val avgScore: Int,
    val avgKcal: Int,
    val steps: Int,
    val sessions: Int,           // séances du programme cochées
    val outings: Int,
    val outingKm: Double,
    val weightDelta: Double?,    // kg, null si pas assez de pesées
    val best: DayResult?
)

/** Le coach de GoodLife : statistiques, petits mots et contexte pour l'IA. Aucun réseau ici. */
object Coach {
    /** Paliers de série qui méritent des félicitations. */
    val MILESTONES = setOf(3, 5, 7, 10, 14, 21, 30, 45, 60, 75, 100, 150, 200, 250, 300, 365, 500, 730, 1000)

    fun summary(): GameSummary? {
        val p = Repo.profile.value ?: return null
        return Game.summarize(Repo.meals.value, p, Repo.game.value, Repo.steps.value.days, newRulesFrom = Repo.settings.value.scoreRulesFrom, foodOnlyFrom = Repo.settings.value.foodOnlyFrom, richFrom = Repo.settings.value.richScoreFrom)
    }

    /** Les 7 derniers jours, aujourd'hui compris (aujourd'hui compte comme un jour en cours). */
    fun week(s: GameSummary): WeekStats {
        val days = (s.history.takeLast(6) + s.today).filter { it.date >= localDay(-6) }
        val tracked = days.filter { it.kcal > 0 }
        val validated = days.count { it.status == DayStatus.REUSSI || it.status == DayStatus.RATTRAPE }
        val from = localDay(-6)
        val sessions = Repo.sport.value.done.count { it.substringBefore('#') >= from }
        val since = Repo.dayBounds(-6).first
        val outs = Repo.outings.value.filter { it.valid && it.start >= since }
        return WeekStats(
            trackedDays = tracked.size,
            validated = validated,
            avgScore = if (tracked.isEmpty()) 0 else tracked.map { it.score }.average().roundToInt(),
            avgKcal = if (tracked.isEmpty()) 0 else tracked.map { it.kcal }.average().roundToInt(),
            steps = days.sumOf { it.steps },
            sessions = sessions,
            outings = outs.size,
            outingKm = outs.sumOf { it.distanceM } / 1000.0,
            weightDelta = weightDelta(),
            best = tracked.maxByOrNull { it.score }
        )
    }

    /** Évolution du poids : dernière pesée de la semaine moins la dernière pesée d'avant (sur 30 jours). */
    fun weightDelta(): Double? {
        val w = Repo.game.value.weights
        val recent = w.lastOrNull { it.first >= localDay(-6) } ?: return null
        val before = w.lastOrNull { it.first < localDay(-6) && it.first >= localDay(-37) } ?: return null
        return ((recent.second - before.second) * 10).roundToInt() / 10.0
    }

    /** Une phrase positive sur le poids, seulement si elle va dans le sens de l'objectif (jamais culpabilisante). */
    fun weightLine(delta: Double?, goal: Goal): String? {
        if (delta == null) return null
        val d = String.format(com.goodlife.app.i18n.Lang.locale, "%+.1f kg", delta)
        return when (goal) {
            Goal.PERTE -> if (delta < 0) t("Poids : %1\$s, tu te rapproches de ton objectif 👏", d) else null
            Goal.PRISE -> if (delta > 0) t("Poids : %1\$s, tu te rapproches de ton objectif 👏", d) else null
            Goal.MAINTIEN -> if (abs(delta) <= 0.5) t("Poids stable (%1\$s) : parfait pour ton objectif.", d) else null
        }
    }

    fun dayName(date: String): String = runCatching {
        SimpleDateFormat("EEEE", com.goodlife.app.i18n.Lang.locale).format(SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(date)!!)
    }.getOrDefault(date)

    fun fmt(n: Int): String = String.format(com.goodlife.app.i18n.Lang.locale, "%,d", n).replace(' ', ' ').replace(' ', ' ')

    /** Tirage stable pour une journée (le même texte si la notification est recalculée). */
    fun <T> pick(list: List<T>, salt: Int): T {
        val day = (System.currentTimeMillis() / 86_400_000L).toInt()
        return list[Random(day * 31 + salt).nextInt(list.size)]
    }

    val ENCOURAGEMENTS = listOf(
        t("Chaque repas noté, c'est un pas de plus. Continue comme ça !"),
        t("Pas besoin d'être parfait, juste régulier. Tu gères."),
        t("Un bon repas ce midi, et l'après-midi sera plus facile."),
        t("Pense à boire un grand verre d'eau avec ton repas 💧"),
        t("Prends le temps de manger assis et sans écran : ton corps te dira quand il a assez."),
        t("Une assiette colorée, c'est souvent une assiette équilibrée 🌈"),
        t("Après le repas, 10 minutes de marche aident la digestion 🚶"),
        t("Tu fais déjà mieux qu'hier en y pensant. Bon appétit !"),
        t("Des légumes, une source de protéines, un féculent : le trio gagnant."),
        t("Le chef croit en toi. Bon appétit ! 👨‍🍳"),
        t("Écoute ta faim : on peut s'arrêter avant d'avoir fini son assiette."),
        t("Petit rappel : un fruit en dessert, c'est simple et bon 🍎")
    )

    /** Le petit mot du chef sur l'accueil, avec son humeur. Tout est calculé sur le téléphone. */
    fun homeMessage(s: GameSummary, p: Profile): Pair<com.goodlife.app.ui.ChefMood, String> {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val y = s.history.lastOrNull()?.takeIf { it.date == localDay(-1) }
        val name = p.name.ifBlank { null }
        val okYesterday = y != null && (y.status == DayStatus.REUSSI || y.status == DayStatus.RATTRAPE)
        return when {
            s.today.status == DayStatus.REUSSI -> com.goodlife.app.ui.ChefMood.BRAVO to
                pick(listOf(if (name != null) t("Journée validée (%1\$s/100) ! Bravo %2\$s 🎉", s.today.score, name) else t("Journée validée (%1\$s/100) ! Bravo 🎉", s.today.score),
                    t("%1\$s/100 aujourd'hui, c'est du beau travail !", s.today.score), t("Objectif du jour atteint. Le chef est ravi !")), 1)
            okYesterday && s.streak in MILESTONES -> com.goodlife.app.ui.ChefMood.BRAVO to
                t("%1\$s jours de série ! Continue, tu es sur une super lancée 🔥", s.streak)
            hour < 11 && y != null && y.kcal > 0 -> (if (okYesterday) com.goodlife.app.ui.ChefMood.CONTENT else com.goodlife.app.ui.ChefMood.QUESTION) to
                if (okYesterday) t("Hier : %1\$s/100, bravo ! On repart pour une belle journée ?", y.score)
                else t("Hier : %1\$s/100. Pas grave du tout : aujourd'hui est une nouvelle page.", y.score)
            s.today.kcal == 0 && hour >= 14 -> com.goodlife.app.ui.ChefMood.QUESTION to
                if (s.streak > 0) tp(s.streak, "Qu'as-tu mangé aujourd'hui ? Note-le pour garder ta série de %1\$s jour.", "Qu'as-tu mangé aujourd'hui ? Note-le pour garder ta série de %1\$s jours.")
                else t("Qu'as-tu mangé aujourd'hui ? Note tes repas pour lancer ta série !")
            s.today.kcal > 0 && s.today.stepGoal > 0 && s.today.steps < s.today.stepGoal / 2 && hour >= 16 -> com.goodlife.app.ui.ChefMood.CONTENT to
                t("Encore %1\$s pas pour ton objectif : une petite marche ce soir ?", fmt(s.today.stepGoal - s.today.steps))
            else -> com.goodlife.app.ui.ChefMood.CONTENT to pick(ENCOURAGEMENTS, 7)
        }
    }

    /**
     * Contexte envoyé à l'IA pour le coach (seulement si l'IA est activée). Chiffres utiles aux conseils, sans le prénom,
     * le sommeil, les photos ni les positions GPS.
     */
    fun aiContext(): String {
        val p = Repo.profile.value ?: return ""
        val s = summary() ?: return ""
        val w = week(s)
        val now = Calendar.getInstance()
        val today = Repo.mealsOfDay(Repo.meals.value)
        val sb = StringBuilder()
        sb.append(t("Aujourd'hui : ")).append(dayName(localDay(0))).append(' ').append(localDay(0))
            .append(t(", il est ")).append(now.get(Calendar.HOUR_OF_DAY)).append("h.\n")
        sb.append(t("Profil : %1\$s ans, %2\$s, %3\$s kg, %4\$s cm, activité %5\$s, ", p.age, p.sex.label, p.weightKg, p.heightCm.roundToInt(), p.activity.label))
            .append(t("objectif %1\$s. Objectif : %2\$s kcal/jour (protéines %3\$s g, glucides %4\$s g, lipides %5\$s g).\n", p.goal.label, p.targetKcal, p.proteinG, p.carbsG, p.fatG))
        sb.append("Habitudes : ${p.habits.ifBlank { "non précisées" }}. Allergies : ${p.allergies.ifBlank { "aucune connue" }}.\n")
        sb.append(t("Repas d'aujourd'hui : "))
        if (today.isEmpty()) sb.append(t("aucun pour l'instant")) else today.sortedBy { it.timestamp }.forEach {
            sb.append(t("%1\$s (%2\$s kcal, P%3\$s G%4\$s L%5\$s) ; ", it.name, it.kcal, it.proteinG.roundToInt(), it.carbsG.roundToInt(), it.fatG.roundToInt()))
        }
        sb.append(t("\nTotal du jour : %1\$s kcal, reste %2\$s kcal. Score du jour : %3\$s/100.\n", s.today.kcal, p.targetKcal - s.today.kcal, s.today.score))
        if (s.today.stepGoal > 0) sb.append(t("Pas aujourd'hui : %1\$s / objectif %2\$s.\n", s.today.steps, s.today.stepGoal))
        sb.append(t("7 derniers jours : %1\$s/7 jours validés, score moyen %2\$s, moyenne %3\$s kcal les jours notés, ", w.validated, w.avgScore, w.avgKcal))
            .append("${w.steps} pas au total, ${w.sessions} séance(s) de sport, ${w.outings} sortie(s) GPS (${String.format(Locale.US, "%.1f", w.outingKm)} km).\n")
        sb.append(t("Série en cours : %1\$s jours (record %2\$s). Niveau %3\$s.\n", s.streak, s.bestStreak, s.level.level))
        w.weightDelta?.let { sb.append("Évolution du poids sur la semaine : ${String.format(Locale.US, "%+.1f", it)} kg.\n") }
        Repo.sport.value.program?.let { pr ->
            sb.append(t("Programme sportif : but « %1\$s », niveau %2\$s, %3\$s jours/semaine, matériel : ", pr.goal, pr.level, pr.daysPerWeek))
                .append(pr.equipment.ifEmpty { listOf("aucun") }.joinToString()).append(t(". Séances : "))
                .append(pr.sessions.joinToString(" ; ") { t("jour %1\$s %2\$s (%3\$s min)", it.day, it.title, it.minutes) + " : " + it.exercises.joinToString(", ") { e -> "${e.name} ${e.detail}" } })
            if (pr.limits.isNotBlank()) sb.append(t(". Limites / douleurs : %1\$s", pr.limits))
            sb.append(".\n")
        }
        com.goodlife.app.data.Fridge.promptList().takeIf { it.isNotBlank() }?.let { sb.append(t("Son frigo : ")).append(it).append(".\n") }
        val upcoming = Repo.plan.value.filter { it.date >= localDay(0) && it.date <= localDay(7) && !it.done }
            .sortedWith(compareBy({ it.date }, { it.slot.ordinal }))
        if (upcoming.isNotEmpty()) {
            sb.append(t("Déjà prévu au planning : "))
            upcoming.take(20).forEach { sb.append(t("%1\$s %2\$s : %3\$s (%4\$s kcal) ; ", it.date, it.slot.label, it.name, it.kcal)) }
            sb.append('\n')
        }
        return sb.toString()
    }

    /** Créneau du repas en fonction de l'heure (pour le rappel de midi). */
    fun slotAt(hour: Int): MealSlot = when {
        hour < 10 -> MealSlot.PETIT_DEJ
        hour < 15 -> MealSlot.DEJEUNER
        hour < 18 -> MealSlot.COLLATION
        else -> MealSlot.DINER
    }
}
