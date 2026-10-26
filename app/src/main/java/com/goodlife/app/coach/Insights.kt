package com.goodlife.app.coach

import com.goodlife.app.i18n.t
import com.goodlife.app.i18n.tp

import com.goodlife.app.data.Goal
import com.goodlife.app.data.MealSlot
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Le « cerveau » du coach : il lit les chiffres de la personne et en tire des constats (protéines basses, pas en baisse,
 * repas souvent sautés…). Tout est calculé sur le téléphone, sans réseau ni IA, à partir de repères publics
 * (ANSES 2016 et 2024, Santé publique France 2019, EFSA 2010-2012, OMS 2020, ACSM 2016, NHS ; pas : Ding 2025 et
 * objectif adaptatif d'Adams 2016 ; sommeil : AASM ; repères de sécurité : HAS 2010, Levinson 2017).
 *
 * Règles communes (issues de la recherche) :
 * - jamais de remarque sur un seul jour : moyennes sur 7 jours, et seulement à partir de 4 jours bien renseignés ;
 * - les calories estimées par photo sont approximatives (souvent sous-estimées) : on parle de tendances ;
 * - jamais de félicitations pour avoir mangé moins ; si un signal d'alerte apparaît, le coach arrête de parler de déficit ;
 * - on parle du comportement, pas de la personne ; on propose, on n'ordonne pas.
 * Ce fichier n'utilise rien d'Android : il est testé par des tests unitaires.
 */

/** Ton du coach, choisi par la personne (Paramètres › Le coach). */
enum class Tone {
    DOUX, EQUILIBRE, EXIGEANT;

    companion object {
        fun of(s: String): Tone = entries.firstOrNull { it.name == s } ?: EQUILIBRE
    }
}

/** Un repas, réduit à ce dont le coach a besoin. [minute] = minute de la journée (0 à 1439). */
data class MealFact(val day: String, val minute: Int, val kcal: Int, val p: Double, val c: Double, val f: Double) {
    val slot: MealSlot get() = slotOf(minute)
}

/** Une nuit : jour du réveil, durée (min) et heure du coucher (minute de la journée). */
data class SleepFact(val wakeDay: String, val minutes: Int, val bedMinute: Int)

/** Tout ce que le coach regarde. [days] = les 35 derniers jours, du plus ancien à aujourd'hui (aujourd'hui en dernier). */
data class CoachInput(
    val days: List<String>,
    val nowMinute: Int,
    val dayOfWeek: Int,                 // 1 = lundi … 7 = dimanche
    val age: Int,
    val female: Boolean,
    val weightKg: Double,
    val heightCm: Double,
    val activityFactor: Double,
    val goal: Goal,
    val targetKcal: Int,
    val meals: List<MealFact>,
    val steps: Map<String, Int> = emptyMap(),
    val stepsEnabled: Boolean = false,
    val sleep: List<SleepFact> = emptyList(),
    val water: Map<String, Int> = emptyMap(),
    val waterGoal: Int = 1500,
    val sessions: Map<String, Int> = emptyMap(),      // jour → séances du programme faites
    val programDays: Int = 0,                          // séances prévues par semaine (0 = pas de programme)
    val programDaysOfWeek: Set<Int> = emptySet(),
    val outings: Map<String, Int> = emptyMap(),       // jour → sorties GPS
    val weights: List<Pair<String, Double>> = emptyList(),
    val moods: Map<String, Int> = emptyMap(),         // jour → humeur de 1 à 5
    val skipped: Set<String> = emptySet(),            // « jour#CRÉNEAU » : la personne a répondu « pas mangé »
    val fasting: Set<MealSlot> = emptySet(),          // créneaux sautés volontairement (jeûne, horaires décalés)
    val careSnoozed: Boolean = false,
    val tone: Tone = Tone.EQUILIBRE
) {
    val today: String get() = days.last()
}

enum class Area { FOOD, RHYTHM, MOVE, SLEEP, WATER, WEIGHT, TRACK, CARE }

/** WIN = une réussite, TIP = un conseil, ASK = une question, CARE = le chef s'inquiète (bienveillant, sans chiffres). */
enum class InsightKind { WIN, TIP, ASK, CARE }

/** Un défi d'une semaine, proposé dans le bilan et choisi par la personne. [plans] = phrases « quand…, je… » au choix. */
data class Challenge(val id: String, val title: String, val target: Int, val plans: List<String>)

/** Un constat du coach. [action] = destination dans l'app (AppNav), [challenge] = le défi qu'il propose pour la semaine. */
data class Insight(
    val id: String,
    val area: Area,
    val kind: InsightKind,
    val priority: Int,
    val title: String,
    val text: String,
    val action: String = "",
    val actionLabel: String = "",
    val challenge: Challenge? = null
)

/** Créneau d'un repas d'après son heure (même découpage que le planning). */
fun slotOf(minute: Int): MealSlot = when {
    minute < 4 * 60 -> MealSlot.DINER
    minute < 10 * 60 + 30 -> MealSlot.PETIT_DEJ
    minute < 15 * 60 -> MealSlot.DEJEUNER
    minute < 18 * 60 -> MealSlot.COLLATION
    else -> MealSlot.DINER
}

/** « 12 h 30 » / « 12:30 ». */
fun clock(minute: Int): String {
    val m = ((minute % 1440) + 1440) % 1440
    return if (com.goodlife.app.i18n.Lang.en) String.format(java.util.Locale.UK, "%d:%02d", m / 60, m % 60)
    else if (m % 60 == 0) "${m / 60} h" else String.format(java.util.Locale.FRANCE, "%d h %02d", m / 60, m % 60)
}

internal fun num(n: Int): String = String.format(com.goodlife.app.i18n.Lang.locale, "%,d", n).replace(' ', ' ').replace(' ', ' ')
internal fun dec(x: Double): String = String.format(com.goodlife.app.i18n.Lang.locale, "%.1f", x)

internal fun median(xs: List<Double>): Double {
    if (xs.isEmpty()) return 0.0
    val s = xs.sorted()
    return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
}

/** Les mots du créneau : « petit-déjeuner », « ce matin »… */
fun slotNoun(slot: MealSlot): String = when (slot) {
    MealSlot.PETIT_DEJ -> t("petit-déjeuner")
    MealSlot.DEJEUNER -> t("déjeuner")
    MealSlot.COLLATION -> t("goûter")
    MealSlot.DINER -> t("dîner")
}

fun slotMoment(slot: MealSlot): String = when (slot) {
    MealSlot.PETIT_DEJ -> t("ce matin")
    MealSlot.DEJEUNER -> t("ce midi")
    MealSlot.COLLATION -> t("cet après-midi")
    MealSlot.DINER -> t("ce soir")
}

/** Calculs communs aux constats, au bilan de la semaine et aux rappels. */
class CoachFacts(val input: CoachInput) {
    private val byDay: Map<String, List<MealFact>> = input.meals.groupBy { it.day }

    /** Les 7 jours complets avant aujourd'hui (aujourd'hui n'est pas fini). */
    val past7: List<String> = input.days.dropLast(1).takeLast(7)

    fun mealsOn(day: String): List<MealFact> = byDay[day].orEmpty()

    /** Repas principaux notés ce jour-là (petit-déjeuner, déjeuner, dîner). */
    fun mainSlots(day: String): Set<MealSlot> = mealsOn(day).map { it.slot }.filter { it != MealSlot.COLLATION }.toSet()

    fun kcal(day: String): Int = mealsOn(day).sumOf { it.kcal }
    fun protein(day: String): Double = mealsOn(day).sumOf { it.p }

    /** Journée « bien renseignée » : au moins 2 repas principaux notés (sinon les moyennes ne veulent rien dire). */
    fun complete(day: String): Boolean = mainSlots(day).size >= 2

    val completePast7: List<String> = past7.filter { complete(it) }
    val trackedPast7: Int = past7.count { mealsOn(it).isNotEmpty() }

    /** Métabolisme de base (Mifflin-St Jeor) et dépense estimée. */
    val bmr: Double = 10 * input.weightKg + 6.25 * input.heightCm - 5 * input.age + if (input.female) -161 else 5
    val tdee: Double = bmr * input.activityFactor
    val bmi: Double = if (input.heightCm > 0) input.weightKg / ((input.heightCm / 100) * (input.heightCm / 100)) else 22.0
    val minor: Boolean = input.age < 18

    /** Plancher sous lequel le coach ne propose jamais d'objectif (repère prudent, pas une recommandation officielle). */
    val kcalFloor: Int = maxOf(if (input.female) 1200 else 1500, bmr.roundToInt())

    // ---------- Moyennes alimentaires (jours bien renseignés des 7 derniers jours) ----------
    val enoughFood: Boolean = completePast7.size >= 4
    val avgKcal: Double = completePast7.map { kcal(it).toDouble() }.average().takeUnless { it.isNaN() } ?: 0.0
    val avgP: Double = completePast7.map { protein(it) }.average().takeUnless { it.isNaN() } ?: 0.0
    val avgC: Double = completePast7.map { d -> mealsOn(d).sumOf { it.c } }.average().takeUnless { it.isNaN() } ?: 0.0
    val avgF: Double = completePast7.map { d -> mealsOn(d).sumOf { it.f } }.average().takeUnless { it.isNaN() } ?: 0.0
    val proteinPerKg: Double = if (input.weightKg > 0) avgP / input.weightKg else 0.0
    private val macroKcal: Double = avgP * 4 + avgC * 4 + avgF * 9
    val carbPct: Double = if (macroKcal > 0) avgC * 4 / macroKcal * 100 else 0.0
    val fatPct: Double = if (macroKcal > 0) avgF * 9 / macroKcal * 100 else 0.0

    // ---------- Activité ----------
    val activeDaysPast7: Int = past7.count { (input.sessions[it] ?: 0) + (input.outings[it] ?: 0) > 0 }
    val sporty: Boolean = activeDaysPast7 >= 2 || input.activityFactor >= 1.7

    /** Séances du programme faites depuis lundi. */
    val sessionsThisWeek: Int = input.days.takeLast(input.dayOfWeek).sumOf { input.sessions[it] ?: 0 }

    private fun stepsOn(day: String): Int = input.steps[day] ?: 0
    /** Jours « portés » : au moins 1 000 pas (sinon le téléphone est sûrement resté sur la table). */
    private fun validSteps(days: List<String>): List<Double> = days.map { stepsOn(it) }.filter { it >= 1000 }.map { it.toDouble() }
    val stepsHistory: List<Double> = validSteps(input.days.dropLast(1).takeLast(28))
    val stepsUsual: Int = median(stepsHistory).roundToInt()
    val steps3: Int = median(validSteps(input.days.dropLast(1).takeLast(3))).roundToInt()
    val stepsAvg7: Int = validSteps(past7).average().takeUnless { it.isNaN() }?.roundToInt() ?: 0

    /** Objectif adaptatif : le 4e meilleur jour des 9 derniers (60e percentile), arrondi à 250 (Adams et al., JMIR 2016). */
    val adaptiveSteps: Int = run {
        val last9 = validSteps(input.days.dropLast(1).takeLast(9)).sortedDescending()
        if (last9.size < 5) 0 else {
            val rank = when (input.tone) { Tone.EXIGEANT -> 2; else -> 3 }   // 3e (exigeant) ou 4e meilleur jour
            (((last9[minOf(rank, last9.size - 1)] / 250).roundToInt() * 250).coerceAtLeast(4000))
        }
    }

    // ---------- Sommeil ----------
    private val nights7: List<SleepFact> = input.sleep.filter { it.wakeDay in past7 || it.wakeDay == input.today }
        .filter { it.minutes in 120..960 }
    val enoughSleep: Boolean = nights7.size >= 4
    val sleepAvgMin: Int = nights7.map { it.minutes }.average().takeUnless { it.isNaN() }?.roundToInt() ?: 0
    val sleepNeedMin: Int = when { input.age < 18 -> 8 * 60; else -> 7 * 60 }

    /** Heure de coucher rapportée à midi (pour que 23 h 30 et 0 h 30 soient proches). */
    private fun bedFromNoon(m: Int): Int = ((m - 720) % 1440 + 1440) % 1440
    val usualBed: Int? = input.sleep.filter { it.wakeDay in input.days.takeLast(14) && it.minutes in 120..960 }
        .takeIf { it.size >= 4 }?.let { l -> (median(l.map { bedFromNoon(it.bedMinute).toDouble() }).roundToInt() + 720) % 1440 }
    val bedSpread: Int = nights7.map { bedFromNoon(it.bedMinute).toDouble() }.let { xs ->
        if (xs.size < 4) 0 else { val m = xs.average(); sqrt(xs.sumOf { (it - m) * (it - m) } / xs.size).roundToInt() }
    }

    // ---------- Eau ----------
    private val waterDays: List<Int> = past7.mapNotNull { input.water[it]?.takeIf { w -> w > 0 } }
    val waterAvg: Int = if (waterDays.size >= 4) waterDays.average().roundToInt() else -1

    // ---------- Poids : pente (kg par semaine) sur 3 semaines, par régression ----------
    private fun dayIndex(d: String): Int = input.days.indexOf(d)
    val weightRate: Double? = run {
        val pts = input.weights.mapNotNull { (d, kg) -> dayIndex(d).takeIf { it >= input.days.size - 21 }?.let { it.toDouble() to kg } }
        if (pts.size < 3 || pts.maxOf { it.first } - pts.minOf { it.first } < 10) null else {
            val mx = pts.map { it.first }.average(); val my = pts.map { it.second }.average()
            val den = pts.sumOf { (it.first - mx) * (it.first - mx) }
            if (den == 0.0) null else pts.sumOf { (it.first - mx) * (it.second - my) } / den * 7
        }
    }

    // ---------- Rythme des repas ----------
    /** Heure habituelle d'un repas : médiane du premier repas de ce créneau sur 14 jours (au moins 4 jours). */
    fun usualTime(slot: MealSlot): Int? {
        val times = input.days.dropLast(1).takeLast(14).mapNotNull { d -> mealsOn(d).filter { it.slot == slot }.minOfOrNull { it.minute } }
        return if (times.size >= 4) median(times.map { it.toDouble() }).roundToInt() else null
    }

    /** Jours (sur les 7 derniers) où ce créneau n'a rien, alors que d'autres repas ont été notés. */
    fun missedSlot(slot: MealSlot): Int = past7.count { d -> mealsOn(d).isNotEmpty() && mealsOn(d).none { it.slot == slot } }
    fun skippedSlot(slot: MealSlot): Int = past7.count { "$it#${slot.name}" in input.skipped }

    // ---------- Signaux d'alerte (troubles alimentaires, perte trop rapide) ----------
    val lowDays: Int = completePast7.count { kcal(it) < 0.5 * tdee }
    /** 3 jours bien notés sous la moitié des besoins, ou les 3 derniers jours tous notés sous la moitié de l'objectif. */
    val careLow: Boolean = lowDays >= 3 ||
        (input.targetKcal > 0 && past7.takeLast(3).let { l -> l.size == 3 && l.all { kcal(it) in 1 until input.targetKcal / 2 } })
    val careFast: Boolean = (weightRate ?: 0.0) < -1.0
    val careBmi: Boolean = bmi < 18.5 && input.goal == Goal.PERTE
    val care: Boolean = careLow || careFast || careBmi
}

object Insights {

    /** Tous les constats du moment, du plus important au moins important. */
    fun all(input: CoachInput): List<Insight> {
        val f = CoachFacts(input)
        val list = mutableListOf<Insight>()
        care(f, list)
        food(f, list)
        rhythm(f, list)
        move(f, list)
        sleep(f, list)
        water(f, list)
        weight(f, list)
        tracking(f, list)
        return list.sortedByDescending { it.priority }
    }

    // ---------- Garde-fous : le chef s'inquiète, sans diagnostic ni chiffres ----------
    private fun care(f: CoachFacts, out: MutableList<Insight>) {
        if (f.input.careSnoozed) return
        val help = if (f.input.age <= 25) t("Fil Santé Jeunes répond gratuitement et anonymement au 0 800 235 236 (12-25 ans).")
                   else t("Ton médecin peut t'aider, et la ligne Anorexie Boulimie Info Écoute répond au 09 69 325 900 (appel non surtaxé).")
        when {
            f.careLow -> out += Insight("care.low", Area.CARE, InsightKind.CARE, 100, t("Le chef prend de tes nouvelles"),
                t("Ces derniers jours, tes repas notés sont très en dessous de tes besoins estimés. Peut-être que tu n'as pas tout noté, et c'est très bien ainsi. Mais si manger est compliqué en ce moment, en parler à un proche ou à un médecin aide vraiment.") + " " + help)
            f.careFast -> out += Insight("care.fast", Area.CARE, InsightKind.CARE, 95, t("Tu perds du poids vite"),
                t("Ces trois dernières semaines, ton poids baisse de plus d'1 kg par semaine. Les repères conseillent 0,5 à 1 kg au plus, pour garder ton muscle et ton énergie. Mange un peu plus, et parles-en à ton médecin si ça continue.") + " " + help)
            f.careBmi -> out += Insight("care.bmi", Area.CARE, InsightKind.CARE, 90, t("Ton objectif mérite un avis"),
                t("D'après ta taille et ton poids, tu es déjà sous la corpulence habituelle. Le chef ne t'aidera pas à perdre davantage : parles-en plutôt à ton médecin. On peut travailler ensemble l'énergie, le sport et le sommeil.") + " " + help,
                action = "moi:objective", actionLabel = t("Changer d'objectif"))
        }
    }

    // ---------- Alimentation : protéines, glucides, lipides, calories ----------
    private fun food(f: CoachFacts, out: MutableList<Insight>) {
        if (!f.enoughFood) return
        val i = f.input
        val n = f.completePast7.size
        val kcalOk = i.targetKcal > 0 && abs(f.avgKcal - i.targetKcal) <= 0.12 * i.targetKcal
        // « Le souci, ce n'est pas les calories mais… » : on le dit quand les calories sont bonnes et qu'une macro coince
        val lead = if (kcalOk && !f.care) t("Côté calories, tu es dans ton objectif : le souci n'est pas là. ") else ""
        val muscle = i.goal == Goal.PRISE || (i.programDays > 0 && f.sporty)
        val protTarget = when {
            f.minor -> 0.85
            muscle -> 1.6
            i.goal == Goal.PERTE && f.sporty -> 1.6
            else -> 0.83
        }
        val protMin = if (protTarget > 1.0) protTarget * 0.8 else 0.8
        val protLow = f.proteinPerKg < protMin
        if (protLow) {
            val why = when {
                muscle && !f.minor -> t("Pour prendre du muscle, le repère est d'environ 1,6 g par kilo de poids et par jour.")
                i.goal == Goal.PERTE && f.sporty -> t("En perdant du poids, assez de protéines aide à garder ton muscle (repère : 1,6 g par kilo quand on fait du sport).")
                else -> t("Le repère de base est d'environ 0,8 g par kilo de poids et par jour (ANSES).")
            }
            val goalG = (protTarget * i.weightKg).roundToInt()
            out += Insight("food.protein", Area.FOOD, InsightKind.TIP, if (kcalOk) 80 else 70, t("Il te manque des protéines"),
                lead + t("Sur tes %1\$s derniers jours, tu manges en moyenne %2\$s g de protéines par jour (%3\$s g par kilo).", n, f.avgP.roundToInt(), dec(f.proteinPerKg)) + " " + why + " " +
                    t("Une source à chaque repas suffit souvent : œufs, yaourt ou fromage blanc, poisson, viande, légumineuses, tofu."),
                challenge = Challenge("protein:$goalG", t("Atteindre %1\$s g de protéines 4 jours sur 7", goalG), 4, listOf(
                    t("Au petit-déjeuner, j'ajoute un yaourt, du fromage blanc ou un œuf."),
                    t("À chaque repas, je mets une source de protéines dans l'assiette."),
                    t("Pour le goûter, je prends un produit laitier ou une poignée d'amandes.")
                )))
        }
        val carbLow = f.carbPct < 40 && f.sporty
        if (carbLow) {
            val perte = if (i.goal == Goal.PERTE) " " + t("Pour perdre du poids, c'est surtout le total sur la durée qui compte : les glucides n'empêchent pas de maigrir.") else ""
            out += Insight("food.carbs", Area.FOOD, InsightKind.TIP, if (kcalOk) 78 else 66, t("Peu de glucides pour quelqu'un qui bouge"),
                lead + t("Les glucides font %1\$s %% de tes calories, alors que tu as bougé %2\$s jours cette semaine. Les repères conseillent 40 à 55 %%. Sans assez de glucides, l'énergie peut manquer pendant l'effort.", f.carbPct.roundToInt(), f.activeDaysPast7) + perte + " " +
                    t("Un féculent au repas d'avant le sport aide : riz, pâtes, pain complet, pommes de terre."),
                challenge = Challenge("carbs", t("Un féculent au repas avant chaque séance"), maxOf(2, f.activeDaysPast7), listOf(
                    t("Le repas d'avant le sport, j'ajoute une portion de riz, de pâtes ou de pain.")
                )))
        } else if (f.carbPct in 0.1..30.0) {
            out += Insight("food.carbs.info", Area.FOOD, InsightKind.TIP, 30, t("Très peu de glucides"),
                t("Les glucides ne font que %1\$s %% de tes calories. Si c'est un choix, pas de souci. Sinon, les repères conseillent 40 à 55 %%, surtout avec des féculents complets, des fruits et des légumineuses.", f.carbPct.roundToInt()))
        }
        if (f.fatPct in 0.1..20.0) out += Insight("food.fat.low", Area.FOOD, InsightKind.TIP, 55, t("Très peu de matières grasses"),
            lead + t("Les lipides ne font que %1\$s %% de tes calories. En dessous de 20 %%, on manque de certaines vitamines (A, D, E, K) et d'acides gras essentiels. Un filet d'huile de colza ou d'olive, des noix ou un poisson gras suffisent.", f.fatPct.roundToInt()))
        else if (f.fatPct > 42) out += Insight("food.fat.high", Area.FOOD, InsightKind.TIP, 50, t("Beaucoup de gras cette semaine"),
            lead + t("Les lipides font %1\$s %% de tes calories ; les repères conseillent 35 à 40 %%. Pas besoin de tout changer : un peu moins de fritures, de charcuterie ou de fromage, et des huiles végétales plutôt que du beurre.", f.fatPct.roundToInt()))

        // Calories : jamais de déficit à pousser si un signal d'alerte existe ou avant 18 ans
        val gap = (f.avgKcal - i.targetKcal).roundToInt()
        if (i.targetKcal > 0 && !f.care) when (i.goal) {
            Goal.PERTE -> if (!f.minor && gap > 0.12 * i.targetKcal) out += Insight("food.kcal.high", Area.FOOD, InsightKind.TIP, 60, t("Un peu au-dessus de ton objectif"),
                t("En moyenne %1\$s kcal par jour, environ %2\$s de plus que ton objectif. Pas besoin de tout changer : une portion un peu plus petite au dîner, ou moins de boissons sucrées, suffit souvent.", num(f.avgKcal.roundToInt()), num(gap)))
            Goal.PRISE -> if (-gap > 0.12 * i.targetKcal) out += Insight("food.kcal.low", Area.FOOD, InsightKind.TIP, 75, t("Pas assez pour prendre du poids"),
                t("En moyenne %1\$s kcal par jour, il manque environ %2\$s kcal pour ton objectif. Une collation en plus suffit souvent : yaourt et fruits secs, tartine au beurre de cacahuète, banane et lait.", num(f.avgKcal.roundToInt()), num(-gap)))
            Goal.MAINTIEN -> Unit
        }
        if (kcalOk && !protLow && !carbLow && !f.care) out += Insight("food.ok", Area.FOOD, InsightKind.WIN, 40, t("Assiette équilibrée cette semaine"),
            t("Calories dans ton objectif et assez de protéines (%1\$s g par kilo) sur tes %2\$s derniers jours. C'est exactement ce qu'il faut, continue.", dec(f.proteinPerKg), n))
    }

    // ---------- Rythme : repas sautés, petit-déjeuner, dîner tardif ----------
    private fun rhythm(f: CoachFacts, out: MutableList<Insight>) {
        val i = f.input
        for (slot in listOf(MealSlot.DEJEUNER, MealSlot.DINER)) {
            if (slot in i.fasting) continue
            val n = f.skippedSlot(slot)
            if (n < 3) continue
            val extra = when {
                f.care -> ""
                i.goal == Goal.PRISE -> " " + t("Avec ton objectif de prise de poids, chaque repas sauté complique les choses.")
                f.sporty -> " " + t("Avec le sport que tu fais, sauter des repas peut te laisser sans énergie.")
                else -> ""
            }
            out += Insight("rhythm.skip.${slot.name}", Area.RHYTHM, InsightKind.ASK, 72, t("Des repas sautés"),
                t("Tu as sauté le %1\$s %2\$s fois cette semaine.", slotNoun(slot), n) + extra + " " +
                    t("C'est voulu, ou c'est le rythme ? Si c'est le temps qui manque, je peux te proposer des idées rapides."),
                action = "repas:idees", actionLabel = t("Idées rapides"))
        }
        if (MealSlot.PETIT_DEJ !in i.fasting && f.trackedPast7 >= 5) {
            val n = f.missedSlot(MealSlot.PETIT_DEJ)
            if (n >= 4) {
                val txt = if (i.goal == Goal.PRISE && !f.care)
                    t("Le matin, rien n'est noté %1\$s jours sur 7. Pour prendre du poids, un petit-déjeuner aide beaucoup à atteindre ton objectif. Si tu n'as pas faim, même un verre de lait et une banane comptent.", n)
                else t("Le matin, rien n'est noté %1\$s jours sur 7. Pas d'obligation : si tu n'as pas faim le matin, c'est tout à fait possible. C'est voulu, ou tu oublies de le noter ?", n)
                out += Insight("rhythm.breakfast", Area.RHYTHM, InsightKind.ASK, if (i.goal == Goal.PRISE) 62 else 35, t("Et le petit-déjeuner ?"), txt,
                    action = "coach:fast:PETIT_DEJ", actionLabel = t("C'est voulu"),
                    challenge = if (i.goal == Goal.PRISE) Challenge("breakfast", t("Un petit-déjeuner 5 jours sur 7"), 5, listOf(
                        t("Le soir, je prépare mon petit-déjeuner pour ne pas le sauter."),
                        t("Si je n'ai pas faim, je prends au moins un fruit et un laitage.")
                    )) else null)
            }
        }
        // Dîner tardif : moins de 2 h avant le coucher habituel, 4 soirs sur 7 (ANSES 2024)
        val bed = f.usualBed
        if (bed != null) {
            val late = f.past7.count { d ->
                val dinner = f.mealsOn(d).filter { it.slot == MealSlot.DINER && it.minute >= 18 * 60 }.maxOfOrNull { it.minute } ?: return@count false
                val b = if (bed < 12 * 60) bed + 1440 else bed
                b - dinner in 0 until 120
            }
            if (late >= 4) out += Insight("rhythm.late", Area.RHYTHM, InsightKind.TIP, 45, t("Des dîners tardifs"),
                t("Tu dînes souvent moins de 2 h avant de te coucher (%1\$s soirs sur 7). L'ANSES conseille un dîner plutôt léger et un peu plus tôt : ça peut aider à mieux dormir.", late))
        }
    }

    // ---------- Activité : pas, séances du programme ----------
    private fun move(f: CoachFacts, out: MutableList<Insight>) {
        val i = f.input
        if (i.stepsEnabled && f.stepsHistory.size >= 14 && f.stepsUsual > 0) {
            val drop = f.steps3 in 1 until (0.7 * f.stepsUsual).roundToInt()
            if (drop) out += Insight("move.drop", Area.MOVE, InsightKind.TIP, 68, t("Tu bouges moins que d'habitude"),
                when (i.tone) {
                    Tone.EXIGEANT -> t("3 jours sous ton rythme habituel : %1\$s pas par jour contre %2\$s d'habitude. Allez, 15 minutes de marche aujourd'hui et c'est reparti.", num(f.steps3), num(f.stepsUsual))
                    else -> t("Ces 3 derniers jours, tu as fait %1\$s pas par jour, contre %2\$s d'habitude. Une marche de 15 minutes aujourd'hui, ça te dit ?", num(f.steps3), num(f.stepsUsual))
                },
                action = "forme:carte", actionLabel = t("Partir marcher"),
                challenge = stepsChallenge(f))
            val prev = median(f.stepsHistory.dropLast(7)).roundToInt()
            if (!drop && f.stepsAvg7 > 0 && prev > 0 && f.stepsAvg7 > prev * 1.15) out += Insight("move.up", Area.MOVE, InsightKind.WIN, 45, t("Tu bouges plus"),
                t("%1\$s pas par jour cette semaine, contre %2\$s avant. Belle progression !", num(f.stepsAvg7), num(prev)))
            else if (!drop && f.stepsAvg7 in 1 until 4000) out += Insight("move.low", Area.MOVE, InsightKind.TIP, 40, t("Chaque pas compte"),
                t("Tu fais en moyenne %1\$s pas par jour. Les études montrent un vrai bénéfice pour la santé dès 4 000 pas, qui augmente jusqu'à environ 7 000. Pas besoin de viser 10 000 : on monte petit à petit.", num(f.stepsAvg7)),
                action = "forme:carte", actionLabel = t("Partir marcher"), challenge = stepsChallenge(f))
        }
        if (i.programDays > 0) {
            val done = f.sessionsThisWeek
            val daysLeft = 8 - i.dayOfWeek   // aujourd'hui compris
            when {
                done >= i.programDays -> out += Insight("move.program.done", Area.MOVE, InsightKind.WIN, 50, t("Programme de la semaine fait"),
                    tp(done, "%1\$s séance faite cette semaine : objectif atteint. Bravo !", "%1\$s séances faites cette semaine : objectif atteint. Bravo !"))
                i.dayOfWeek >= 3 && i.programDays - done >= daysLeft -> out += Insight("move.program.late", Area.MOVE, InsightKind.TIP, 58, t("Ton programme prend du retard"),
                    t("Séances cette semaine : %1\$s sur %2\$s.", done, i.programDays) + " " +
                        tp(daysLeft, "Il reste %1\$s jour : on la fait aujourd'hui ? Même une séance plus courte compte.", "Il reste %1\$s jours : on cale la prochaine aujourd'hui ou demain ? Même une séance plus courte compte."),
                    action = "forme", actionLabel = t("Voir mes séances"),
                    challenge = Challenge("move:${i.programDays}", t("Faire mes %1\$s séances de la semaine", i.programDays), i.programDays, listOf(
                        t("Je note mes séances dans mon agenda comme un rendez-vous."),
                        t("Si je n'ai pas le temps, je fais une version de 15 minutes plutôt que rien.")
                    )))
            }
        }
    }

    private fun stepsChallenge(f: CoachFacts): Challenge? {
        val goal = f.adaptiveSteps.takeIf { it > 0 } ?: return null
        return Challenge("steps:$goal", t("Faire %1\$s pas 4 jours sur 7", num(goal)), 4, listOf(
            t("Quand je finis de déjeuner, je marche 10 minutes."),
            t("Quand je rentre le soir, je fais le tour du quartier avant de m'asseoir."),
            t("Je descends un arrêt plus tôt ou je prends les escaliers.")
        ))
    }

    // ---------- Sommeil : durée et régularité (jamais une seule nuit) ----------
    private fun sleep(f: CoachFacts, out: MutableList<Insight>) {
        if (!f.enoughSleep) return
        val h = f.sleepAvgMin / 60; val m = f.sleepAvgMin % 60
        val dur = if (com.goodlife.app.i18n.Lang.en) "${h}h${String.format("%02d", m)}" else "$h h ${String.format("%02d", m)}"
        if (f.sleepAvgMin < f.sleepNeedMin - 20) {
            val need = if (f.minor) t("8 à 10 h à ton âge") else t("7 à 9 h pour un adulte")
            out += Insight("sleep.short", Area.SLEEP, InsightKind.TIP, 56, t("Des nuits un peu courtes"),
                t("Tu dors en moyenne %1\$s par nuit cette semaine ; le repère est de %2\$s. Une nuit courte peut aussi donner plus faim le lendemain : c'est normal, pas un manque de volonté.", dur, need),
                action = "forme:sommeil", actionLabel = t("Voir mon sommeil"),
                challenge = f.usualBed?.let { bed ->
                    val target = ((bed - 30) / 15 * 15 + 1440) % 1440
                    Challenge("sleep", t("Dormir %1\$s ou plus 4 nuits sur 7", if (f.minor) t("8 h") else t("7 h")), 4, listOf(
                        t("Quand il est %1\$s, je pose le téléphone et je me prépare à dormir.", clock(target)),
                        t("Je mets un réveil « coucher » 30 minutes avant l'heure voulue.")
                    ))
                })
        } else if (f.bedSpread > 75) out += Insight("sleep.irregular", Area.SLEEP, InsightKind.TIP, 42, t("Des horaires de coucher qui varient"),
            t("Ton heure de coucher change beaucoup d'un soir à l'autre (plus d'une heure d'écart). Se coucher à peu près à la même heure aide autant que dormir longtemps."),
            action = "forme:sommeil", actionLabel = t("Voir mon sommeil"))
        else if (f.sleepAvgMin >= f.sleepNeedMin) out += Insight("sleep.ok", Area.SLEEP, InsightKind.WIN, 30, t("Bien dormi cette semaine"),
            t("%1\$s par nuit en moyenne, avec des horaires réguliers. Ton corps te dit merci.", dur))
    }

    private fun water(f: CoachFacts, out: MutableList<Insight>) {
        if (f.waterAvg < 0) return
        if (f.waterAvg < 0.6 * f.input.waterGoal) out += Insight("water.low", Area.WATER, InsightKind.TIP, 38, t("Pense à boire"),
            t("Tu bois en moyenne %1\$s ml par jour, pour un objectif de %2\$s ml. Un verre à chaque repas et une gourde à portée de main font souvent la différence (si ton médecin t'a donné une limite, suis-la).", num(f.waterAvg), num(f.input.waterGoal)),
            challenge = Challenge("water", t("Atteindre mon objectif d'eau 4 jours sur 7"), 4, listOf(
                t("Quand je m'installe pour travailler, je remplis ma gourde."),
                t("À chaque repas, je bois un grand verre d'eau.")
            )))
    }

    // ---------- Poids : seulement la tendance, jamais avant 18 ans ----------
    private fun weight(f: CoachFacts, out: MutableList<Insight>) {
        val i = f.input
        val rate = f.weightRate ?: return
        if (f.minor || f.care) return
        when (i.goal) {
            Goal.PERTE -> if (abs(rate) < 0.1 && f.enoughFood && i.targetKcal > 0 && f.avgKcal <= i.targetKcal * 1.05)
                out += Insight("weight.stall", Area.WEIGHT, InsightKind.TIP, 52, t("Ton poids ne bouge pas"),
                    t("Ton poids est stable depuis 3 semaines, alors que tes repas notés sont dans ton objectif. Les estimations par photo sous-estiment souvent les huiles, sauces et grosses portions : essaie de les préciser quand tu corriges un repas. Et un plateau de quelques semaines, c'est normal."))
                else if (rate in -1.0..-0.2) out += Insight("weight.good", Area.WEIGHT, InsightKind.WIN, 35, t("Un rythme sain"),
                    t("Tu perds environ %1\$s kg par semaine : c'est le rythme conseillé, celui qui tient dans la durée.", dec(-rate)))
            Goal.PRISE -> if (rate < 0.05 && f.enoughFood) out += Insight("weight.stall.gain", Area.WEIGHT, InsightKind.TIP, 50, t("Ton poids ne monte pas"),
                t("Ton poids est stable depuis 3 semaines. Pour prendre, il faut un peu plus que ce que tu manges aujourd'hui : une collation de plus par jour est souvent le plus simple."))
            Goal.MAINTIEN -> Unit
        }
    }

    // ---------- Suivi : sans données, le coach ne peut pas aider ----------
    private fun tracking(f: CoachFacts, out: MutableList<Insight>) {
        if (f.trackedPast7 in 1..3) out += Insight("track.few", Area.TRACK, InsightKind.TIP, 48, t("J'ai besoin de te voir manger 😉"),
            tp(f.trackedPast7, "Tu as noté tes repas %1\$s jour cette semaine. Pour que mes conseils soient justes, il me faut au moins 4 jours : une photo par repas suffit, même après coup.", "Tu as noté tes repas %1\$s jours cette semaine. Pour que mes conseils soient justes, il me faut au moins 4 jours : une photo par repas suffit, même après coup."),
            action = "add", actionLabel = t("Ajouter un repas"),
            challenge = Challenge("track", t("Noter mes repas 5 jours sur 7"), 5, listOf(
                t("Quand je m'assieds pour manger, je prends la photo avant la première bouchée."),
                t("Le soir, avant de dormir, je vérifie que mes repas du jour sont notés.")
            )))
    }
}
