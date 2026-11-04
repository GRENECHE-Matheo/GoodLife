package com.goodlife.app.coach

import com.goodlife.app.i18n.t
import com.goodlife.app.i18n.tp

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.goodlife.app.MainActivity
import com.goodlife.app.R
import com.goodlife.app.data.MealSlot
import com.goodlife.app.data.Repo
import com.goodlife.app.data.localDay
import com.goodlife.app.game.DayStatus
import com.goodlife.app.social.AppNav
import com.goodlife.app.ui.ChefMood
import com.goodlife.app.ui.chefBitmap
import java.util.Calendar
import java.util.Locale

/**
 * Notifications du coach, préparées sur le téléphone (aucun réseau, aucune IA) :
 * - le matin (8 h 30) : bilan de la veille, félicitations aux paliers, ou un seul « on reprend ? » après 3 jours d'absence ;
 * - à midi (11 h 45) : le repas prévu au planning et un petit mot ;
 * - « Tu as mangé ce midi ? » : 1 h 30 après l'heure habituelle du repas (apprise de tes repas), seulement s'il n'est pas noté ;
 *   le soir, si rien n'est noté de la journée : « Aucune assiette aujourd'hui ? » ;
 * - « Ton dîner d'hier ? » vers 9 h : un repas habituel de la veille toujours pas noté (oubli ou pas mangé), à rattraper ;
 * - « On bouge ? » vers 17 h 30 : séance du jour du programme, ou pas en baisse depuis 3 jours ;
 * - le soir (20 h) : seulement si rien n'a été noté de la journée et qu'une série est en cours ;
 * - le dimanche (19 h) : le bilan de la semaine.
 * Garde-fous : plafond par jour et par semaine selon le ton choisi, heures calmes (avant le coucher habituel, la nuit),
 * rien si c'est déjà fait, et le chef recule tout seul quand ses rappels sont ignorés.
 * Appuyer sur une notification ouvre toujours le message complet dans l'app (même quand l'app est verrouillée).
 */
object CoachNotifier {
    private const val CH_SUMMARY = "coach_bilans"
    private const val CH_REMIND = "coach_rappels"
    private const val CH_MEAL = "coach_repas"
    private const val CH_MOVE = "coach_bouger"

    const val ACT_SKIP = "com.goodlife.app.COACH_SKIP"
    const val ACT_LATER = "com.goodlife.app.COACH_LATER"
    const val ACT_NOT_TODAY = "com.goodlife.app.COACH_NOT_TODAY"
    const val ACT_LATER_FIRE = "com.goodlife.app.COACH_LATER_FIRE"
    private const val LATER_CODE = 48

    enum class Kind(val hour: Int, val minute: Int, val code: Int, val key: String, val weekly: Boolean = false) {
        MORNING(8, 30, 41, "morning"), NOON(11, 45, 42, "noon"), EVENING(20, 0, 43, "evening"),
        WEEKLY(19, 0, 44, "weekly", weekly = true), WATER(15, 30, 45, "water"),
        MEAL(0, 0, 46, "meal"), MOVE(17, 30, 47, "move")
    }

    private fun enabled(kind: Kind): Boolean {
        val s = Repo.settings.value
        return when (kind) {
            Kind.MORNING -> s.notifMorning
            Kind.NOON -> s.notifNoon
            Kind.EVENING -> s.notifEvening
            Kind.WEEKLY -> s.notifWeekly
            Kind.WATER -> s.notifWater
            Kind.MEAL -> s.notifMeal
            Kind.MOVE -> s.notifMove
        }
    }

    fun permissionGranted(context: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** (Re)programme les rappels activés et annule les autres. À appeler au démarrage et à chaque changement. */
    fun schedule(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val profileOk = Repo.profile.value != null
        Kind.entries.forEach { kind ->
            val next = if (kind == Kind.MEAL) nextMealCheck() else null
            val pi = pending(context, kind, next)
            if (profileOk && enabled(kind) && (kind != Kind.MEAL || next != null)) {
                // Heure approximative (fenêtre de 15 min) : pas besoin de la permission « alarmes exactes »
                am.setWindow(AlarmManager.RTC_WAKEUP, next?.at ?: next(kind), 15 * 60_000L, pi)
            } else {
                am.cancel(pi)
            }
        }
    }

    private fun pending(context: Context, kind: Kind, check: MealCheckAt? = null): PendingIntent = PendingIntent.getBroadcast(
        context, kind.code,
        Intent(context, CoachReceiver::class.java).setAction("com.goodlife.app.COACH_${kind.name}").apply {
            check?.let { putExtra("slot", it.slot?.name ?: "ALL"); putExtra("day", it.day); putExtra("catchup", it.catchUp) }
        },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    /** Un rappel de repas prévu : créneau (null = toute la journée), jour, heure, et s'il s'agit d'un rattrapage de la veille. */
    data class MealCheckAt(val slot: MealSlot?, val day: String, val at: Long, val catchUp: Boolean = false)

    private fun next(kind: Kind): Long {
        val now = Calendar.getInstance()
        val c = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, kind.hour); set(Calendar.MINUTE, kind.minute)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        if (kind.weekly) {
            while (c.get(Calendar.DAY_OF_WEEK) != Calendar.SUNDAY || !c.after(now)) c.add(Calendar.DAY_OF_YEAR, 1)
        } else if (!c.after(now)) {
            c.add(Calendar.DAY_OF_YEAR, 1)
        }
        return c.timeInMillis
    }

    private const val CATCHUP_MINUTE = 9 * 60

    /**
     * Prochain rappel de repas : le premier créneau surveillé dont l'heure n'est pas passée (aujourd'hui ou demain), ou,
     * vers 9 h, le rattrapage de la veille. Tout est revérifié au moment d'envoyer (rien si le repas a été noté entre-temps).
     */
    private fun nextMealCheck(): MealCheckAt? {
        val input = CoachBrain.input() ?: return null
        val now = System.currentTimeMillis()
        val nowMin = CoachBrain.minuteOf(now)
        val slots = CoachBrain.watchedSlots(input)
        val (todayStart, tomorrowStart) = Repo.dayBounds(0)
        val today = slots.mapNotNull { s -> CoachBrain.checkMinute(s, input)?.takeIf { it > nowMin && !CoachBrain.slotDone(s) }?.let { MealCheckAt(s, localDay(0), todayStart + it * 60_000L) } }.toMutableList()
        val yesterday = localDay(-1)
        if (nowMin < CATCHUP_MINUTE && CoachStore.get().catchupAsked != yesterday && CoachBrain.forgotten(input).any { it.first == yesterday })
            today += MealCheckAt(null, yesterday, todayStart + CATCHUP_MINUTE * 60_000L, catchUp = true)
        val tomorrow = slots.mapNotNull { s -> CoachBrain.checkMinute(s, input)?.let { MealCheckAt(s, localDay(1), tomorrowStart + it * 60_000L) } } +
            MealCheckAt(null, localDay(0), tomorrowStart + CATCHUP_MINUTE * 60_000L, catchUp = true)
        return today.minByOrNull { it.at } ?: tomorrow.minByOrNull { it.at }
    }

    /** Contenu d'une notification : titre, texte, humeur du chef, canal, et destination dans l'app. */
    data class Note(val title: String, val text: String, val mood: ChefMood, val remind: Boolean = false, val action: String = "", val channel: String = "", val snooze: Boolean = true)

    /** « Ton dîner d'hier ? » (un repas) ou « Ta journée d'hier ? » (plusieurs). */
    private fun catchUpNote(day: String, slots: List<MealSlot>): Note = Note(
        CoachBrain.catchUpTitle(slots), CoachBrain.catchUpText(slots), ChefMood.QUESTION, remind = true,
        action = if (slots.size == 1) "check:${slots[0].name}:$day" else "check:ALL:$day", channel = CH_MEAL, snooze = false
    )

    fun build(kind: Kind, slot: MealSlot? = null, day: String? = null, catchUp: Boolean = false): Note? {
        val p = Repo.profile.value ?: return null
        val s = Coach.summary() ?: return null
        val today = Repo.mealsOfDay(Repo.meals.value)
        val lastMeal = Repo.meals.value.maxOfOrNull { it.timestamp } ?: 0L
        return when (kind) {
            Kind.MORNING -> {
                // Absence de 3 jours ou plus : un seul petit mot, puis plus rien jusqu'au retour
                val lastDay = if (lastMeal > 0) java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).format(java.util.Date(lastMeal)) else ""
                if (lastMeal > 0 && lastDay <= localDay(-3)) {
                    if (Repo.settings.value.lastNudgeDay > lastDay) return null
                    Repo.updateSettings { it.copy(lastNudgeDay = localDay(0)) }
                    return Note(
                        t("Le chef garde ta place au chaud 👨‍🍳"),
                        Coach.pick(listOf(
                            t("Ça fait quelques jours ! Pas de pression : note juste ton prochain repas pour repartir en douceur."),
                            t("Content de te revoir quand tu veux. On reprend simple : une photo de ton prochain repas suffit."),
                            t("On reprend quand tu veux, sans se juger. Le quiz du chef t'attend aussi.")
                        ), 3),
                        ChefMood.QUESTION, action = "add"
                    )
                }
                // Repas d'hier pas notés : pas de bilan faux. Le rattrapage de 9 h s'en charge, ou ce message s'il est coupé.
                val missYesterday = CoachBrain.forgotten().filter { it.first == localDay(-1) }.map { it.second }
                if (missYesterday.isNotEmpty()) {
                    if (Repo.settings.value.notifMeal || CoachStore.get().catchupAsked == localDay(-1)) return null
                    CoachStore.update { it.copy(catchupAsked = localDay(-1)) }
                    return catchUpNote(localDay(-1), missYesterday).copy(remind = false, channel = "")
                }
                val y = s.history.lastOrNull()?.takeIf { it.date == localDay(-1) } ?: return null
                if (y.kcal == 0) return null
                val ok = y.status == DayStatus.REUSSI || y.status == DayStatus.RATTRAPE
                val lines = mutableListOf(t("Score : %1\$s/100 · %2\$s / %3\$s kcal", y.score, Coach.fmt(y.kcal), Coach.fmt(p.targetKcal)))
                if (y.stepGoal > 0) lines += t("%1\$s pas / %2\$s", Coach.fmt(y.steps), Coach.fmt(y.stepGoal))
                // La chose du jour, s'il y en a une : c'est elle qui fait avancer
                CoachBrain.thingOfTheDay()?.takeIf { it.kind != InsightKind.WIN }?.let { lines += "👉 " + it.title }
                when {
                    ok && s.streak in Coach.MILESTONES -> Note(
                        t("🎉 %1\$s jours de série !", s.streak),
                        (lines + t("Bravo, c'est un vrai palier. Le chef est fier de toi !")).joinToString("\n"), ChefMood.BRAVO
                    )
                    ok -> Note(
                        t("Hier : journée validée ✅"),
                        (lines + if (s.streak > 1) t("Série : %1\$s jours 🔥", s.streak) else t("C'est parti pour une nouvelle série !")).joinToString("\n"),
                        ChefMood.CONTENT
                    )
                    s.recoverableStreak > 0 -> Note(
                        t("Ta série peut encore être sauvée"),
                        (lines + tp(s.recoverableStreak, "Réussis le quiz du chef aujourd'hui pour garder ta série de %1\$s jour.", "Réussis le quiz du chef aujourd'hui pour garder ta série de %1\$s jours.")).joinToString("\n"),
                        ChefMood.TRISTE, remind = true, action = "quiz"
                    )
                    else -> Note(
                        t("Ton bilan d'hier"),
                        (lines + t("Pas grave : aujourd'hui est une nouvelle page 💪")).joinToString("\n"), ChefMood.CONTENT
                    )
                }
            }
            Kind.NOON -> {
                // Déjà noté un repas depuis 11 h : pas besoin de rappel
                val c = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 11); set(Calendar.MINUTE, 0) }
                if (today.any { it.timestamp >= c.timeInMillis }) return null
                val planned = Repo.plan.value.firstOrNull { it.date == localDay(0) && it.slot == MealSlot.DEJEUNER && !it.done }
                val tip = Coach.pick(Coach.ENCOURAGEMENTS, 11)
                if (planned != null) Note(t("Ce midi : %1\$s", planned.name), t("%1\$s kcal, prévu dans ton planning.\n%2\$s", planned.kcal, tip), ChefMood.CONTENT, action = "repas:planning")
                else Note(t("Le petit mot du chef"), tip, ChefMood.CONTENT)
            }
            Kind.EVENING -> {
                if (today.isNotEmpty() || s.streak < 1) return null
                // « Tu as mangé ce soir ? » s'en occupe déjà
                if (Repo.settings.value.notifMeal && CoachBrain.sentToday("meal") > 0) return null
                Note(
                    tp(s.streak, "Ta série de %1\$s jour t'attend 🔥", "Ta série de %1\$s jours t'attend 🔥"),
                    t("Tu n'as encore rien noté aujourd'hui. Ajoute tes repas pour la garder !"), ChefMood.QUESTION, remind = true, action = "add"
                )
            }
            Kind.WATER -> {
                val ml = Repo.water.value[localDay(0)] ?: 0
                val goal = Repo.waterGoal()
                if (ml >= goal / 2) return null
                Note(t("Pense à boire 💧"), t("Tu en es à %1\$s ml sur %2\$s ml aujourd'hui. Un grand verre d'eau ?", Coach.fmt(ml), Coach.fmt(goal)), ChefMood.CONTENT, remind = true)
            }
            Kind.MEAL -> {
                if (!CoachBrain.activeLogger()) return null
                // Rattrapage de la veille : une seule fois par jour, et seulement s'il manque encore quelque chose
                if (catchUp) {
                    val d = day ?: localDay(-1)
                    val miss = CoachBrain.forgotten().filter { it.first == d }.map { it.second }
                    if (miss.isEmpty() || CoachStore.get().catchupAsked == d) return null
                    CoachStore.update { it.copy(catchupAsked = d) }
                    return catchUpNote(d, miss)
                }
                val sl = slot ?: return null
                if (CoachBrain.slotDone(sl) || CoachBrain.recentMeal()) return null
                // Le soir, rien de noté de toute la journée : une seule question pour la journée entière
                if (sl == MealSlot.DINER && today.isEmpty() && !CoachBrain.slotDone(MealSlot.DEJEUNER))
                    return Note(t("Aucune assiette aujourd'hui ?"),
                        t("Je n'ai vu aucun repas noté aujourd'hui. Oubli de noter, ou journée sans manger ? Tu peux tout rattraper en une minute."),
                        ChefMood.QUESTION, remind = true, action = "check:ALL:${localDay(0)}", channel = CH_MEAL)
                val (usual, learned) = CoachBrain.usualMeal(sl) ?: return null
                Note(CoachBrain.mealCheckTitle(sl), CoachBrain.mealCheckText(sl, usual, learned), ChefMood.QUESTION, remind = true,
                    action = "check:${sl.name}:${localDay(0)}", channel = CH_MEAL)
            }
            Kind.MOVE -> {
                val n = CoachBrain.moveNudge() ?: return null
                Note(n.title, n.text, ChefMood.SPORT, remind = true, action = n.action + "|" + n.motive, channel = CH_MOVE)
            }
            Kind.WEEKLY -> {
                val r = CoachBrain.weekReport() ?: return null
                val w = Coach.week(s)
                if (w.trackedDays == 0 && w.outings == 0 && w.sessions == 0) return null
                val lines = mutableListOf(r.win)
                r.findings.firstOrNull()?.let { lines += t("Le point à travailler : %1\$s.", it.title.replaceFirstChar { c -> c.lowercase() }) }
                lines += t("Ouvre ton bilan pour voir tes progrès et choisir ton défi de la semaine.")
                Note(t("Ton bilan de la semaine est prêt 📊"), lines.joinToString("\n"), ChefMood.BRAVO, action = "coach:week")
            }
        }
    }

        /** [force] : seulement pour les tests de la version de développement (ignore plafonds et heures calmes). */
    fun fire(context: Context, kind: Kind, slot: MealSlot? = null, force: Boolean = false, day: String? = null, catchUp: Boolean = false) {
        Repo.init(context)
        CoachStore.load()
        if (!enabled(kind) || !permissionGranted(context)) return
        if (!force && kind.key in CoachBrain.CAPPED && !CoachBrain.mayRemind(kind.key)) return
        val note = build(kind, slot, day, catchUp) ?: return
        post(context, kind.code, note, kind.key)
        CoachBrain.recordSent(kind.key)
        if (kind == Kind.MOVE) CoachStore.update { it.copy(sent = it.sent + (System.currentTimeMillis() to "move:" + note.action.substringAfter('|'))) }
    }

    /** « Plus tard » : on redemande une seule fois, 45 minutes après (si le repas n'est toujours pas noté). */
    fun later(context: Context, slot: MealSlot, day: String) {
        NotificationManagerCompat.from(context).cancel(Kind.MEAL.code)
        CoachStore.acted("meal")
        val key = "check:${slot.name}:$day"
        if (CoachStore.get().snoozed.containsKey(key)) return
        CoachStore.update { it.copy(snoozed = it.snoozed + (key to System.currentTimeMillis())) }
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = PendingIntent.getBroadcast(context, LATER_CODE,
            Intent(context, CoachReceiver::class.java).setAction(ACT_LATER_FIRE).putExtra("slot", slot.name).putExtra("day", day),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        am.setWindow(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + 45 * 60_000L, 10 * 60_000L, pi)
    }

    fun laterFire(context: Context, slot: MealSlot, day: String) {
        Repo.init(context)
        CoachStore.load()
        if (day != localDay(0) || !permissionGranted(context) || CoachBrain.slotDone(slot) || CoachBrain.recentMeal()) return
        if (CoachBrain.isQuiet(CoachBrain.minuteOf(System.currentTimeMillis()))) return
        val note = build(Kind.MEAL, slot) ?: return
        post(context, Kind.MEAL.code, note, "meal", later = false)
    }

    @android.annotation.SuppressLint("MissingPermission")   // vérifiée juste en dessous (permissionGranted)
    private fun post(context: Context, id: Int, note: Note, kindKey: String, later: Boolean = true) {
        if (!permissionGranted(context)) return
        createChannels(context)
        // Chaque notification devient un message du chef, qu'on retrouve en appuyant dessus (et dans « Messages du chef »)
        val msg = CoachStore.addMessage(kindKey, note.title, note.text, note.action)
        fun open(code: Int, target: String) = PendingIntent.getActivity(
            context, code, Intent(context, MainActivity::class.java).putExtra(AppNav.EXTRA, target)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        fun act(code: Int, action: String) = PendingIntent.getBroadcast(
            context, code, Intent(context, CoachReceiver::class.java).setAction(action).putExtra("action", note.action).putExtra("kind", kindKey),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val channel = note.channel.ifBlank { if (note.remind) CH_REMIND else CH_SUMMARY }
        // App verrouillée par empreinte : le contenu reste discret aussi dans le volet des notifications
        val locked = Repo.settings.value.appLock
        val title = if (locked) t("Un message du chef") else note.title
        val text = if (locked) t("Appuie pour le lire dans Lifoody.") else note.text
        // Écran verrouillé : seulement « Lifoody », sans les chiffres (données de santé)
        val public = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notif_chef)
            .setContentTitle("Lifoody")
            .setContentText(t("Un message du chef"))
            .build()
        val b = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notif_chef)
            .setLargeIcon(runCatching { chefBitmap(note.mood) }.getOrNull())
            .setContentTitle(title)
            .setContentText(text.lineSequence().first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open(id, "coach:msg:${msg.id}"))
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(public)
            .setCategory(if (note.remind) NotificationCompat.CATEGORY_REMINDER else NotificationCompat.CATEGORY_RECOMMENDATION)
        // Boutons (3 au plus) : répondre sans ouvrir l'app quand c'est possible
        if (!locked) when {
            note.action.startsWith("check:") -> {
                val all = note.action.startsWith("check:ALL:")
                b.addAction(0, if (all) t("Je les note") else t("Je le note"), open(id * 10 + 1, note.action + ":add:${msg.id}"))
                b.addAction(0, t("Pas mangé"), act(id * 10 + 2, ACT_SKIP))
                if (later && note.snooze) b.addAction(0, t("Plus tard"), act(id * 10 + 3, ACT_LATER))
            }
            kindKey == "move" -> {
                b.addAction(0, if (note.action.startsWith("forme:carte")) t("Je pars marcher") else t("Voir ma séance"), open(id * 10 + 1, note.action.substringBefore('|') + "|msg:${msg.id}"))
                b.addAction(0, t("Pas aujourd'hui"), act(id * 10 + 2, ACT_NOT_TODAY))
            }
        }
        runCatching { NotificationManagerCompat.from(context).notify(id, b.build()) }
    }

    private fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CH_SUMMARY, t("Bilans et petits mots du chef"), NotificationManager.IMPORTANCE_LOW).apply {
                description = t("Bilan du matin, mot de midi et bilan de la semaine (sans son).")
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_REMIND, t("Rappels de série et d'eau"), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = t("Quand ta série risque de s'arrêter, ou pour penser à boire.")
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_MEAL, t("Repas pas notés"), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = t("« Tu as mangé ce midi ? » quand un repas habituel n'est pas noté.")
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_MOVE, t("Bouger"), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = t("Séance du jour, ou quand tu bouges moins que d'habitude.")
            }
        )
    }

    /** Retire les rappels « Tu as mangé ? » et « On bouge ? » affichés (après un appui sur leurs boutons). */
    fun cancelReminders(context: Context) {
        NotificationManagerCompat.from(context).apply { cancel(Kind.MEAL.code); cancel(Kind.MOVE.code); cancel(Kind.MORNING.code) }
    }

    /** Montre un exemple tout de suite (bouton « Voir un exemple » dans les Paramètres). */
    fun preview(context: Context) {
        val note = if (Repo.settings.value.notifMeal) Note(CoachBrain.mealCheckTitle(MealSlot.DEJEUNER),
            CoachBrain.mealCheckText(MealSlot.DEJEUNER, CoachBrain.usualMeal(MealSlot.DEJEUNER)?.first ?: 750, false), ChefMood.QUESTION,
            remind = true, action = "check:DEJEUNER:${localDay(0)}", channel = CH_MEAL)
        else build(Kind.MORNING) ?: Note(t("Le petit mot du chef"), Coach.pick(Coach.ENCOURAGEMENTS, 5), ChefMood.BRAVO)
        post(context, 40, note, "preview")
    }
}

/** Réveil du coach : affiche la notification prévue (si elle a lieu d'être) puis programme la suivante ; boutons des notifications. */
class CoachReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Repo.init(context)
        CoachStore.load()
        val slot = intent.getStringExtra("slot")?.let { n -> MealSlot.entries.firstOrNull { it.name == n } }
        when (intent.action) {
            CoachNotifier.ACT_SKIP, CoachNotifier.ACT_LATER -> {
                // action = « check:CRÉNEAU:jour » (CRÉNEAU = ALL pour toute la journée)
                val parts = intent.getStringExtra("action").orEmpty().split(':')
                val day = parts.getOrNull(2) ?: localDay(0)
                val all = parts.getOrNull(1) == "ALL"
                val s = parts.getOrNull(1)?.let { n -> MealSlot.entries.firstOrNull { it.name == n } }
                if (intent.action == CoachNotifier.ACT_SKIP) {
                    if (all) CoachStore.skipAll(day, CoachBrain.missingOn(day)) else if (s != null) CoachStore.skip(day, s)
                    CoachStore.acted("meal")
                    CoachNotifier.cancelReminders(context)
                } else CoachNotifier.later(context, s ?: MealSlot.DINER, day)
                return
            }
            CoachNotifier.ACT_NOT_TODAY -> {
                CoachStore.acted(intent.getStringExtra("kind") ?: "move")
                NotificationManagerCompat.from(context).cancel(CoachNotifier.Kind.MOVE.code)
                return
            }
            CoachNotifier.ACT_LATER_FIRE -> {
                if (slot != null) runCatching { CoachNotifier.laterFire(context, slot, intent.getStringExtra("day") ?: localDay(0)) }
                return
            }
        }
        val kind = CoachNotifier.Kind.entries.firstOrNull { intent.action == "com.goodlife.app.COACH_${it.name}" } ?: return
        runCatching { CoachNotifier.fire(context, kind, slot, day = intent.getStringExtra("day"), catchUp = intent.getBooleanExtra("catchup", false)) }
        CoachNotifier.schedule(context)
        runCatching { com.goodlife.app.widget.ChefWidgets.updateAll(context) }
    }
}
