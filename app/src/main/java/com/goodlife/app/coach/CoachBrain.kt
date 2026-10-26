package com.goodlife.app.coach

import com.goodlife.app.i18n.t

import com.goodlife.app.data.MealSlot
import com.goodlife.app.data.Repo
import com.goodlife.app.data.Sex
import com.goodlife.app.data.localDay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Relie le coach aux données de l'app : prépare [CoachInput] à partir du Repo, choisit « la chose du jour »
 * et décide des rappels (repas pas noté, bouger, bilan). Rien ne quitte le téléphone ici.
 */
object CoachBrain {
    private const val DAY = 86_400_000L
    private val dayFmt get() = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    fun dayOf(ms: Long): String = dayFmt.format(Date(ms))
    fun minuteOf(ms: Long): Int = Calendar.getInstance().apply { timeInMillis = ms }.let { it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE) }

    fun tone(): Tone = Tone.of(Repo.settings.value.coachTone)

    /** Toutes les données utiles, sur 35 jours. */
    fun input(now: Long = System.currentTimeMillis()): CoachInput? {
        val p = Repo.profile.value ?: return null
        val days = (34 downTo 0).map { localDay(-it) }
        val from = Repo.dayBounds(-34).first
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val mem = CoachStore.get()
        val program = Repo.sport.value.program
        return CoachInput(
            days = days,
            nowMinute = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE),
            dayOfWeek = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1,
            age = p.age, female = p.sex == Sex.FEMME, weightKg = p.weightKg, heightCm = p.heightCm,
            activityFactor = p.activity.factor, goal = p.goal, targetKcal = p.targetKcal,
            meals = Repo.meals.value.filter { it.timestamp >= from }.map {
                MealFact(dayOf(it.timestamp), minuteOf(it.timestamp), it.kcal, it.proteinG, it.carbsG, it.fatG)
            },
            steps = Repo.steps.value.days.mapValues { it.value.steps },
            stepsEnabled = Repo.settings.value.stepsEnabled,
            sleep = Repo.sleep.value.filter { it.end >= from }.map { SleepFact(dayOf(it.end), it.durationMin.toInt(), minuteOf(it.start)) },
            water = Repo.water.value,
            waterGoal = Repo.waterGoal(),
            sessions = Repo.sport.value.done.groupingBy { it.substringBefore('#') }.eachCount(),
            programDays = program?.daysPerWeek ?: 0,
            programDaysOfWeek = program?.sessions?.map { it.day }?.toSet() ?: emptySet(),
            outings = Repo.outings.value.filter { it.valid && it.start >= from }.groupingBy { dayOf(it.start) }.eachCount(),
            weights = Repo.game.value.weights,
            moods = Repo.feelings.value.mapValues { it.value.mood },
            skipped = mem.skipped,
            fasting = mem.fasting,
            careSnoozed = Repo.settings.value.guardSnoozeUntil > now,
            tone = tone()
        )
    }

    fun insights(): List<Insight> = input()?.let { Insights.all(it) } ?: emptyList()

    /**
     * La chose du jour : le constat le plus important, en faisant tourner ceux qui restent en tête plusieurs jours
     * (sinon on finit par ne plus les lire). Les messages d'inquiétude ne tournent jamais.
     */
    fun thingOfTheDay(list: List<Insight> = insights()): Insight? {
        if (list.isEmpty()) return null
        val mem = CoachStore.get()
        val today = localDay(0)
        fun age(id: String): Int = mem.shown[id]?.let { daysBetween(it, today) } ?: 0
        val best = list.maxByOrNull { i -> i.priority - if (i.kind != InsightKind.CARE && age(i.id) in 3..9) 30 else 0 } ?: return null
        val first = mem.shown[best.id]
        if (first == null || daysBetween(first, today) >= 10) {
            CoachStore.update { m -> m.copy(shown = (m.shown + (best.id to today)).filterValues { daysBetween(it, today) < 30 }) }
        }
        return best
    }

    fun daysBetween(a: String, b: String): Int = runCatching {
        ((dayFmt.parse(b)!!.time - dayFmt.parse(a)!!.time + DAY / 2) / DAY).toInt()
    }.getOrDefault(0)

    fun weekReport(): WeekReport? {
        val i = input() ?: return null
        return Weekly.build(i, Insights.all(i), CoachStore.get().challenge)
    }

    // ---------- Heures apprises et heures calmes ----------

    /** Heure habituelle d'un repas (minute de la journée) et si elle vient de tes repas (true) ou d'un horaire par défaut. */
    fun usualMeal(slot: MealSlot, input: CoachInput? = input()): Pair<Int, Boolean>? {
        val f = input?.let { CoachFacts(it) } ?: return null
        f.usualTime(slot)?.let { return it to true }
        return when (slot) {
            MealSlot.DEJEUNER -> 12 * 60 + 30 to false
            MealSlot.DINER -> 19 * 60 + 45 to false
            else -> null
        }
    }

    /** Heure de coucher habituelle (d'après le sommeil détecté ou noté), sinon null. */
    fun usualBed(input: CoachInput? = input()): Int? = input?.let { CoachFacts(it).usualBed }

    /** Début des heures calmes : 30 min avant le coucher habituel (entre 21 h et minuit), sinon 21 h 30. */
    fun quietFrom(input: CoachInput? = input()): Int {
        val bed = usualBed(input) ?: return 21 * 60 + 30
        val b = if (bed < 12 * 60) bed + 1440 else bed
        return (b - 30).coerceIn(21 * 60, 24 * 60 - 1)
    }

    const val QUIET_UNTIL = 8 * 60

    fun isQuiet(minute: Int, input: CoachInput? = input()): Boolean = minute >= quietFrom(input) || minute < QUIET_UNTIL

    /** Heure du rappel « tu as mangé ? » pour un créneau : 1 h 30 après l'heure habituelle, sans déborder. */
    fun checkMinute(slot: MealSlot, input: CoachInput? = input()): Int? {
        val (usual, _) = usualMeal(slot, input) ?: return null
        val limit = when (slot) {
            MealSlot.PETIT_DEJ -> 11 * 60 + 15
            MealSlot.DEJEUNER -> 15 * 60 + 30
            else -> 22 * 60 + 15
        }
        val at = minOf(usual + 90, limit, quietFrom(input) - 10)
        return at.takeIf { it >= usual + 45 && it >= QUIET_UNTIL }
    }

    /** Créneaux surveillés selon le ton (le petit-déjeuner seulement en « exigeant », ou s'il est très régulier). */
    fun watchedSlots(input: CoachInput? = input()): List<MealSlot> {
        val mem = CoachStore.get()
        val f = input?.let { CoachFacts(it) }
        val breakfast = f?.usualTime(MealSlot.PETIT_DEJ) != null && (tone() == Tone.EXIGEANT || (f.past7.count { d -> f.mealsOn(d).any { it.slot == MealSlot.PETIT_DEJ } } >= 5))
        return listOfNotNull(MealSlot.PETIT_DEJ.takeIf { breakfast }, MealSlot.DEJEUNER, MealSlot.DINER).filter { it !in mem.fasting }
    }

    // ---------- Plafonds ----------

    fun dailyCap(): Int = when (tone()) { Tone.DOUX -> 1; Tone.EQUILIBRE -> 2; Tone.EXIGEANT -> 3 }
    fun weeklyCap(): Int = when (tone()) { Tone.DOUX -> 6; Tone.EQUILIBRE -> 10; Tone.EXIGEANT -> 14 }

    /** Types de rappels qui comptent dans le plafond (ceux qui sonnent). Les bilans silencieux ne comptent pas. */
    val CAPPED = setOf("meal", "move", "evening", "water")

    fun sentToday(kind: String? = null): Int {
        val start = Repo.dayBounds(0).first
        return CoachStore.get().sent.count { it.first >= start && (if (kind == null) it.second in CAPPED else it.second == kind) }
    }

    fun sentWeek(): Int {
        val now = System.currentTimeMillis()
        return CoachStore.get().sent.count { now - it.first < 7 * DAY && it.second in CAPPED }
    }

    fun lastSent(kind: String): Long = CoachStore.get().sent.filter { it.second == kind }.maxOfOrNull { it.first } ?: 0L

    /** Peut-on envoyer ce rappel maintenant ? (plafonds, heures calmes, recul quand il est ignoré) */
    fun mayRemind(kind: String, now: Long = System.currentTimeMillis()): Boolean {
        val mem = CoachStore.get()
        if (kind in mem.muteAsk) return false                         // ignoré 5 fois : on attend que la personne choisisse
        if (sentToday() >= dailyCap() || sentWeek() >= weeklyCap()) return false
        if (isQuiet(minuteOf(now))) return false
        if (now - mem.lastOpen < 2 * 3600_000L && kind != "meal") return false   // l'app vient d'être ouverte
        val calm = mem.calmUntil[kind] ?: 0L
        if (calm > now && (now / DAY) % 2 == 0L) return false          // ignoré 3 fois : un jour sur deux pendant 7 jours
        return true
    }

    /** À l'envoi : on note l'heure ; si le précédent n'a pas eu de réaction, il compte comme ignoré. */
    fun recordSent(kind: String, now: Long = System.currentTimeMillis()) = CoachStore.update { m ->
        val ignoredBefore = m.pending.containsKey(kind)
        val n = if (ignoredBefore) (m.ignored[kind] ?: 0) + 1 else 0
        m.copy(
            sent = m.sent + (now to kind),
            pending = m.pending + (kind to now),
            ignored = m.ignored + (kind to n),
            calmUntil = if (n >= 3 && (m.calmUntil[kind] ?: 0L) < now) m.calmUntil + (kind to now + 7 * DAY) else m.calmUntil,
            muteAsk = if (n >= 5) m.muteAsk + kind else m.muteAsk
        )
    }

    // ---------- Repas pas noté ----------

    /** Le créneau a-t-il un repas noté (ou une réponse « pas mangé ») aujourd'hui ? */
    fun slotDone(slot: MealSlot, day: String = localDay(0)): Boolean {
        if ("$day#${slot.name}" in CoachStore.get().skipped) return true
        val (from, to) = Repo.dayBounds(daysBetween(localDay(0), day))
        return Repo.meals.value.any { m ->
            m.timestamp in from until to && slotOf(minuteOf(m.timestamp)).let { s -> s == slot || (slot == MealSlot.DINER && s == MealSlot.COLLATION && minuteOf(m.timestamp) >= 17 * 60 + 30) }
        }
    }

    /** Un repas a-t-il été noté dans les 3 dernières heures ? (alors pas de rappel) */
    fun recentMeal(now: Long = System.currentTimeMillis()): Boolean = Repo.meals.value.any { now - it.timestamp in 0..3 * 3600_000L }

    /** La personne note-t-elle ses repas en ce moment ? (sinon le rappel d'absence du matin prend le relais) */
    fun activeLogger(): Boolean {
        val since = Repo.dayBounds(-3).first
        return Repo.meals.value.any { it.timestamp >= since }
    }

    /** Heure (ms) à laquelle le repas sera noté s'il est ajouté après coup : son heure habituelle de ce jour-là. */
    fun usualTimeOn(slot: MealSlot, day: String): Long {
        val minute = usualMeal(slot)?.first ?: when (slot) { MealSlot.PETIT_DEJ -> 8 * 60; MealSlot.DEJEUNER -> 12 * 60 + 30; MealSlot.COLLATION -> 16 * 60; MealSlot.DINER -> 19 * 60 + 45 }
        val (from, _) = Repo.dayBounds(daysBetween(localDay(0), day))
        return from + minute * 60_000L
    }

    // ---------- Textes des rappels (plusieurs tournures, pour ne pas lasser) ----------

    fun mealCheckTitle(slot: MealSlot): String = t("Tu as mangé %1\$s ?", slotMoment(slot))

    fun mealCheckText(slot: MealSlot, usual: Int, learned: Boolean): String {
        val noun = slotNoun(slot)
        val time = clock(usual)
        val bank = when (tone()) {
            Tone.EXIGEANT -> listOf(
                t("Ton %1\$s n'est pas noté. Oubli ou repas sauté ? Réponds-moi, ça prend 5 secondes.", noun),
                t("Pas de %1\$s noté. Si tu as mangé, on le note maintenant, tant que tu t'en souviens.", noun),
                t("Je ne vois pas ton %1\$s. Un repas oublié, c'est un conseil faussé : on le note ?", noun),
                t("Tu manges d'habitude vers %2\$s, et rien n'est noté pour ton %1\$s. Tu as mangé ?", noun, time)
            )
            else -> listOfNotNull(
                t("Je n'ai rien vu passer pour ton %1\$s. Oubli de le noter, ou tu n'as pas mangé ?", noun),
                if (learned) t("D'habitude, tu manges vers %2\$s. Rien de noté pour ton %1\$s : tout va bien ?", noun, time) else null,
                t("Ton %1\$s n'est pas encore noté. Une photo suffit, même après coup.", noun),
                t("Petit point du chef : ton %1\$s n'apparaît pas. Tu me dis ?", noun),
                t("Rien de noté pour ton %1\$s. Si tu as sauté le repas, dis-le-moi aussi : ça m'aide à mieux te conseiller.", noun),
                t("Je garde ta journée à jour : ton %1\$s, c'est fait ou pas aujourd'hui ?", noun)
            )
        }
        return Coach.pick(bank, slot.ordinal * 7 + 3)
    }

    /** Rappel pour bouger : séance du jour, ou pas en baisse depuis quelques jours. null = rien à dire. */
    data class MoveNudge(val title: String, val text: String, val action: String, val motive: String)

    fun moveNudge(now: Long = System.currentTimeMillis()): MoveNudge? {
        if (com.goodlife.app.track.Tracker.live.value != null || Repo.sport.value.running >= 0) return null
        val i = input(now) ?: return null
        val today = i.today
        val activeToday = (i.sessions[today] ?: 0) + (i.outings[today] ?: 0) > 0
        if (activeToday) return null
        val tone = i.tone
        val program = Repo.sport.value.program
        val session = program?.sessions?.firstOrNull { it.day == i.dayOfWeek }
        if (session != null) return MoveNudge(
            t("Séance du jour : %1\$s", session.title),
            when (tone) {
                Tone.EXIGEANT -> Coach.pick(listOf(
                    t("%1\$s minutes, c'est aujourd'hui. On s'y met avant le dîner ?", session.minutes),
                    t("Ta séance t'attend (%1\$s min). Pas besoin d'être motivé pour commencer : la motivation vient en faisant.", session.minutes),
                    t("Rendez-vous avec toi-même : %1\$s minutes de séance. Allez, on y va !", session.minutes)
                ), 21)
                else -> Coach.pick(listOf(
                    t("%1\$s minutes prévues aujourd'hui. Tu la fais quand ?", session.minutes),
                    t("C'est le jour de ta séance (%1\$s min). Même une version plus courte compte !", session.minutes),
                    t("Ta séance du jour est prête (%1\$s min). Un petit créneau ce soir ?", session.minutes)
                ), 21)
            },
            "forme", "session"
        )
        if (!i.stepsEnabled) return null
        val f = CoachFacts(i)
        val todaySteps = i.steps[today] ?: 0
        val gap = when (tone) { Tone.DOUX -> 14; Tone.EQUILIBRE -> 7; Tone.EXIGEANT -> 3 }
        val sinceDrop = (now - lastSentMotive("drop")) / DAY
        if (f.stepsHistory.size >= 14 && f.stepsUsual > 0 && f.steps3 in 1 until (0.7 * f.stepsUsual).roundToInt() &&
            todaySteps < 0.6 * f.stepsUsual && sinceDrop >= gap) {
            val bank = when (tone) {
                Tone.EXIGEANT -> listOf(
                    t("3 jours sous ton rythme : %1\$s pas par jour contre %2\$s d'habitude. Allez, 15 minutes de marche, maintenant ou après le dîner.", num(f.steps3), num(f.stepsUsual)),
                    t("Ça fait 3 jours que tu bouges moins. 10 minutes dehors, même autour du pâté de maisons, ça relance tout."),
                    t("Ton rythme habituel, c'est %1\$s pas. Aujourd'hui on y retourne : 15 minutes de marche suffisent pour relancer.", num(f.stepsUsual))
                )
                else -> listOf(
                    t("Ces derniers jours sont plus calmes côté pas (%1\$s par jour contre %2\$s d'habitude). Une marche de 15 minutes aujourd'hui, ça te dit ?", num(f.steps3), num(f.stepsUsual)),
                    t("Tu bouges un peu moins que d'habitude depuis 3 jours. Même 10 minutes dehors, ça compte."),
                    t("Petite baisse de pas ces jours-ci. Et si tu allais marcher après le prochain repas ?")
                )
            }
            return MoveNudge(t("On bouge un peu ?"), Coach.pick(bank, 23), "forme:carte", "drop")
        }
        // Exigeant seulement : journée très calme en fin d'après-midi (au plus 2 fois par semaine)
        if (tone == Tone.EXIGEANT && f.adaptiveSteps > 0 && todaySteps < 0.4 * f.adaptiveSteps && (now - lastSentMotive("calm")) / DAY >= 3) {
            return MoveNudge(t("Encore le temps de bouger"),
                t("%1\$s pas pour l'instant, ton objectif est à %2\$s. Il reste la soirée : 20 minutes de marche et tu y es presque.", num(todaySteps), num(f.adaptiveSteps)),
                "forme:carte", "calm")
        }
        return null
    }

    private fun lastSentMotive(motive: String): Long = CoachStore.get().sent.filter { it.second == "move:$motive" }.maxOfOrNull { it.first } ?: 0L
}
