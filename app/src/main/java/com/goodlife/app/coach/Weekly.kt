package com.goodlife.app.coach

import com.goodlife.app.i18n.t
import com.goodlife.app.i18n.tp

import com.goodlife.app.data.Goal
import com.goodlife.app.data.MealSlot
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Bilan de la semaine : d'abord une réussite, puis quelques chiffres comparés à la médiane des 4 semaines d'avant
 * (moins trompeur que la seule semaine précédente), un lien observé dans les données, les constats du coach
 * et 2 ou 3 défis à choisir (Michie 2009 : retour + révision de l'objectif ; Gollwitzer 2006 : plans « quand…, je… »).
 * Jamais de rouge : une hausse utile est verte, le reste est neutre.
 */
data class Metric(
    val id: String,
    val emoji: String,
    val label: String,
    val value: String,
    val before: String?,         // médiane des semaines d'avant (null = pas assez d'historique)
    val trend: Int,              // 1 = en hausse, -1 = en baisse, 0 = stable
    val good: Boolean            // la tendance va dans le bon sens (affichée en vert)
)

data class WeekReport(
    val win: String,
    val metrics: List<Metric>,
    val link: String?,
    val findings: List<Insight>,
    val proposals: List<Challenge>,
    val lastChallenge: String?,
    val firstWeek: Boolean
)

/** Défi choisi par la personne, à partir du jour [start] (7 jours). */
data class ActiveChallenge(val id: String, val title: String, val target: Int, val plan: String, val start: String)

object Weekly {

    /** Les 7 jours de la semaine k (0 = les 7 derniers jours, aujourd'hui compris ; 1 = la semaine d'avant…). */
    private fun week(days: List<String>, k: Int): List<String> {
        val end = days.size - 7 * k
        val start = end - 7
        return if (start < 0) emptyList() else days.subList(start, end)
    }

    fun build(input: CoachInput, insights: List<Insight>, challenge: ActiveChallenge?): WeekReport {
        val f = CoachFacts(input)
        val cur = week(input.days, 0)
        val prev = (1..4).map { week(input.days, it) }.filter { it.isNotEmpty() }
        val metrics = mutableListOf<Metric>()

        fun tracked(w: List<String>) = w.count { f.mealsOn(it).isNotEmpty() }.toDouble()
        val prevTracked = prev.map { tracked(it) }.filter { it > 0 }
        val firstWeek = prevTracked.isEmpty()
        metrics += metric("track", "🍽️", t("Jours suivis"), tracked(cur), prevTracked, higherIsBetter = true) { tp(it.roundToInt(), "%1\$s jour sur 7", "%1\$s jours sur 7") }

        if (input.stepsEnabled) {
            fun avgSteps(w: List<String>) = w.mapNotNull { input.steps[it]?.takeIf { s -> s >= 1000 } }.average().takeUnless { it.isNaN() } ?: 0.0
            val p = prev.map { avgSteps(it) }.filter { it > 0 }
            val c = avgSteps(cur)
            if (c > 0) metrics += metric("steps", "👟", t("Pas par jour"), c, p, higherIsBetter = true) { num(it.roundToInt()) }
        }
        fun moves(w: List<String>) = w.sumOf { (input.sessions[it] ?: 0) + (input.outings[it] ?: 0) }.toDouble()
        val cm = moves(cur)
        val pm = prev.map { moves(it) }
        if (cm > 0 || pm.any { it > 0 }) metrics += metric("move", "🏃", t("Séances et sorties"), cm, pm.filter { it > 0 }, higherIsBetter = true) { num(it.roundToInt()) }

        fun proteinKg(w: List<String>): Double {
            val d = w.filter { f.complete(it) }
            return if (d.size < 3 || input.weightKg <= 0) 0.0 else d.map { f.protein(it) }.average() / input.weightKg
        }
        val cp = proteinKg(cur)
        if (cp > 0) metrics += metric("protein", "🥚", t("Protéines (g par kilo)"), cp, prev.map { proteinKg(it) }.filter { it > 0 }, higherIsBetter = cp < 1.6) { dec(it) }

        fun sleepAvg(w: List<String>) = input.sleep.filter { it.wakeDay in w && it.minutes in 120..960 }.takeIf { it.size >= 3 }?.map { it.minutes.toDouble() }?.average() ?: 0.0
        val cs = sleepAvg(cur)
        if (cs > 0) metrics += metric("sleep", "😴", t("Sommeil par nuit"), cs, prev.map { sleepAvg(it) }.filter { it > 0 }, higherIsBetter = cs < 9 * 60) {
            val m = it.roundToInt(); if (com.goodlife.app.i18n.Lang.en) "${m / 60}h${String.format("%02d", m % 60)}" else "${m / 60} h ${String.format("%02d", m % 60)}"
        }
        fun waterAvg(w: List<String>) = w.mapNotNull { input.water[it]?.takeIf { v -> v > 0 } }.takeIf { it.size >= 3 }?.average() ?: 0.0
        val cw = waterAvg(cur)
        if (cw > 0) metrics += metric("water", "💧", t("Eau par jour"), cw, prev.map { waterAvg(it) }.filter { it > 0 }, higherIsBetter = cw < input.waterGoal) { num(it.roundToInt()) + " ml" }

        // Poids : tendance seulement, jamais avant 18 ans ni en cas d'alerte
        val rate = f.weightRate
        if (rate != null && !f.minor && !f.care) {
            val good = when (input.goal) { Goal.PERTE -> rate < -0.1; Goal.PRISE -> rate > 0.1; Goal.MAINTIEN -> abs(rate) <= 0.2 }
            metrics += Metric("weight", "⚖️", t("Poids (tendance)"), String.format(com.goodlife.app.i18n.Lang.locale, "%+.1f kg", rate) + t(" par semaine"), null,
                if (rate > 0.1) 1 else if (rate < -0.1) -1 else 0, good)
        }

        val last = challenge?.let { measure(input, it) }
        // Défi encore en cours (moins de 7 jours) : on montre où on en est, sans verdict
        val running = challenge != null && input.days.indexOf(challenge.start).let { it < 0 || it > input.days.size - 7 }
        val lastText = if (challenge != null && last != null && running)
            t("Ton défi « %1\$s » est en cours : %2\$s sur %3\$s pour l'instant.", challenge.title, last, challenge.target)
        else if (challenge != null && last != null)
            t("Ton défi « %1\$s » : %2\$s sur %3\$s.", challenge.title, last, challenge.target) + " " +
                if (last >= challenge.target) t("Réussi, bravo ! 🎉") else if (last * 2 >= challenge.target) t("Pas loin, c'est déjà un vrai progrès.") else t("Pas grave : on peut le garder ou en choisir un plus facile.")
        else null

        return WeekReport(
            win = win(f, metrics, !running && last != null && challenge != null && last >= challenge.target, cur),
            metrics = metrics,
            link = link(input, f),
            findings = insights.filter { it.kind != InsightKind.WIN && it.kind != InsightKind.CARE }.take(3),
            proposals = proposals(f, insights),
            lastChallenge = lastText,
            firstWeek = firstWeek
        )
    }

    private fun metric(id: String, emoji: String, label: String, value: Double, before: List<Double>, higherIsBetter: Boolean, fmt: (Double) -> String): Metric {
        val ref = if (before.isEmpty()) null else median(before)
        val trend = when {
            ref == null || ref == 0.0 -> 0
            value > ref * 1.1 -> 1
            value < ref * 0.9 -> -1
            else -> 0
        }
        return Metric(id, emoji, label, fmt(value), ref?.let(fmt), trend, if (higherIsBetter) trend > 0 else trend < 0)
    }

    /** La réussite mise en avant, en premier (toujours quelque chose de vrai et de positif). */
    private fun win(f: CoachFacts, metrics: List<Metric>, challengeOk: Boolean, cur: List<String>): String {
        val up = metrics.firstOrNull { it.good && it.trend != 0 }
        return when {
            challengeOk -> t("Défi de la semaine relevé. Tu tiens tes engagements, c'est ce qui fait la différence.")
            up != null && up.id == "steps" -> t("Tu as plus bougé que d'habitude : %1\$s pas par jour, contre %2\$s avant.", up.value, up.before ?: "")
            up != null && up.id == "track" -> t("Tu as noté tes repas plus souvent que d'habitude : %1\$s.", up.value)
            up != null && up.id == "move" -> t("Plus de séances et de sorties que d'habitude cette semaine. Bien joué !")
            up != null && up.id == "sleep" -> t("Tu as mieux dormi que d'habitude : %1\$s par nuit.", up.value)
            f.input.programDays > 0 && f.sessionsThisWeek >= f.input.programDays -> t("Toutes tes séances de la semaine sont faites. Bravo !")
            cur.count { f.mealsOn(it).isNotEmpty() } >= 5 -> t("Tu as noté tes repas presque tous les jours : c'est la base de tout, bravo.")
            cur.any { f.mealsOn(it).isNotEmpty() } -> tp(cur.count { f.mealsOn(it).isNotEmpty() }, "Tu as noté tes repas %1\$s jour cette semaine : chaque jour noté m'aide à mieux te conseiller.", "Tu as noté tes repas %1\$s jours cette semaine : chaque jour noté m'aide à mieux te conseiller.")
            else -> t("Une nouvelle semaine commence : c'est le bon moment pour repartir.")
        }
    }

    /** Un lien observé sur 4 semaines (seulement s'il est net, et présenté comme une observation, pas une règle). */
    fun link(input: CoachInput, f: CoachFacts = CoachFacts(input)): String? {
        val days = input.days.takeLast(29).dropLast(1)
        val nights = input.sleep.filter { it.minutes in 120..960 }.associateBy { it.wakeDay }
        // Sommeil → pas du jour même (le jour du réveil)
        if (input.stepsEnabled) {
            val good = days.filter { (nights[it]?.minutes ?: 0) >= 420 }.mapNotNull { input.steps[it]?.takeIf { s -> s >= 1000 }?.toDouble() }
            val bad = days.filter { n -> nights[n]?.let { it.minutes < 420 } == true }.mapNotNull { input.steps[it]?.takeIf { s -> s >= 1000 }?.toDouble() }
            if (good.size >= 3 && bad.size >= 3) {
                val diff = (good.average() - bad.average()).roundToInt()
                if (diff >= 800 && diff >= bad.average() * 0.15) return t("Sur 4 semaines, les jours qui suivent une nuit de 7 h ou plus, tu fais en moyenne %1\$s pas de plus.", num(diff)) + " " + t("C'est une observation sur tes données, pas une règle.")
            }
        }
        // Nuit courte → on mange plus le lendemain (Al Khatib 2016 : c'est fréquent)
        val goodK = days.filter { (nights[it]?.minutes ?: 0) >= 420 && f.complete(it) }.map { f.kcal(it).toDouble() }
        val badK = days.filter { d -> nights[d]?.let { it.minutes < 360 } == true && f.complete(d) }.map { f.kcal(it).toDouble() }
        if (goodK.size >= 3 && badK.size >= 3) {
            val diff = (badK.average() - goodK.average()).roundToInt()
            if (diff >= 150 && diff >= goodK.average() * 0.1) return t("Après une nuit de moins de 6 h, tu manges en moyenne %1\$s kcal de plus dans la journée. C'est très courant : une nuit courte peut donner plus faim.", num(diff)) + " " + t("C'est une observation sur tes données, pas une règle.")
        }
        // Bouger → humeur
        val active = days.filter { (input.sessions[it] ?: 0) + (input.outings[it] ?: 0) > 0 || (input.steps[it] ?: 0) >= 8000 }.mapNotNull { input.moods[it]?.toDouble() }
        val calm = days.filter { (input.sessions[it] ?: 0) + (input.outings[it] ?: 0) == 0 && (input.steps[it] ?: 0) < 8000 }.mapNotNull { input.moods[it]?.toDouble() }
        if (active.size >= 3 && calm.size >= 3 && active.average() - calm.average() >= 0.5)
            return t("Les jours où tu bouges, ton humeur notée est meilleure : %1\$s sur 5, contre %2\$s les autres jours.", dec(active.average()), dec(calm.average())) + " " + t("C'est une observation sur tes données, pas une règle.")
        return null
    }

    /** 2 ou 3 défis taillés pour la personne, tirés des constats, puis des défis simples par défaut. */
    private fun proposals(f: CoachFacts, insights: List<Insight>): List<Challenge> {
        val list = insights.mapNotNull { it.challenge }.toMutableList()
        if (f.trackedPast7 < 5) list += Challenge("track", t("Noter mes repas 5 jours sur 7"), 5, listOf(
            t("Quand je m'assieds pour manger, je prends la photo avant la première bouchée."),
            t("Le soir, avant de dormir, je vérifie que mes repas du jour sont notés.")
        ))
        if (f.input.stepsEnabled && f.adaptiveSteps > 0) list += Challenge("steps:${f.adaptiveSteps}", t("Faire %1\$s pas 4 jours sur 7", num(f.adaptiveSteps)), 4, listOf(
            t("Quand je finis de déjeuner, je marche 10 minutes."),
            t("Quand je rentre le soir, je fais le tour du quartier avant de m'asseoir."),
            t("Je descends un arrêt plus tôt ou je prends les escaliers.")
        ))
        list += Challenge("water", t("Atteindre mon objectif d'eau 4 jours sur 7"), 4, listOf(
            t("Quand je m'installe pour travailler, je remplis ma gourde."),
            t("À chaque repas, je bois un grand verre d'eau.")
        ))
        list += Challenge("veggies", t("Des légumes à 2 repas par jour, 5 jours sur 7"), 5, listOf(
            t("Au déjeuner et au dîner, je remplis la moitié de l'assiette de légumes."),
            t("Je garde des légumes surgelés pour les soirs pressés.")
        ))
        return list.distinctBy { it.id.substringBefore(':') }.take(3)
    }

    /** Résultat d'un défi sur ses 7 jours (null = on ne peut pas le mesurer, ou il n'a pas encore commencé). */
    fun measure(input: CoachInput, ch: ActiveChallenge): Int? {
        val f = CoachFacts(input)
        val i = input.days.indexOf(ch.start)
        if (i < 0) return null
        val days = input.days.subList(i, minOf(i + 7, input.days.size))
        val kind = ch.id.substringBefore(':')
        val param = ch.id.substringAfter(':', "").toIntOrNull() ?: 0
        return when (kind) {
            "track" -> days.count { f.complete(it) }
            "steps" -> days.count { (input.steps[it] ?: 0) >= param }
            "move" -> days.sumOf { (input.sessions[it] ?: 0) + (input.outings[it] ?: 0) }
            "protein" -> days.count { f.protein(it) >= param }
            "water" -> days.count { (input.water[it] ?: 0) >= input.waterGoal }
            "sleep" -> days.count { d -> input.sleep.any { it.wakeDay == d && it.minutes >= f.sleepNeedMin } }
            "breakfast" -> days.count { d -> f.mealsOn(d).any { it.slot == MealSlot.PETIT_DEJ } }
            "carbs" -> null
            else -> null   // « légumes » : pas mesurable sur une photo, la personne le valide elle-même
        }
    }
}
