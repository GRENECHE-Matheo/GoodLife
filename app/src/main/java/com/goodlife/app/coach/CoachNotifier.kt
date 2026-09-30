package com.goodlife.app.coach

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
import com.goodlife.app.ui.ChefMood
import com.goodlife.app.ui.chefBitmap
import java.util.Calendar
import java.util.Locale

/**
 * Notifications du coach, préparées sur le téléphone (aucun réseau, aucune IA) et jamais plus d'une à la fois :
 * - le matin (8 h 30) : bilan de la veille, félicitations aux paliers de série, ou un seul « tu nous manques » après 3 jours d'absence ;
 * - à midi (11 h 45) : le repas prévu au planning et un petit encouragement ;
 * - le soir (20 h) : seulement si rien n'a été noté de la journée et qu'une série est en cours ;
 * - le dimanche (19 h) : le bilan de la semaine.
 * Chacune ne sonne que si elle a quelque chose d'utile à dire, et se coupe dans Paramètres.
 */
object CoachNotifier {
    private const val CH_SUMMARY = "coach_bilans"
    private const val CH_REMIND = "coach_rappels"

    enum class Kind(val hour: Int, val minute: Int, val code: Int, val weekly: Boolean = false) {
        MORNING(8, 30, 41), NOON(11, 45, 42), EVENING(20, 0, 43), WEEKLY(19, 0, 44, weekly = true)
    }

    private fun enabled(kind: Kind): Boolean {
        val s = Repo.settings.value
        return when (kind) {
            Kind.MORNING -> s.notifMorning
            Kind.NOON -> s.notifNoon
            Kind.EVENING -> s.notifEvening
            Kind.WEEKLY -> s.notifWeekly
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
            val pi = pending(context, kind)
            if (profileOk && enabled(kind)) {
                // Heure approximative (fenêtre de 15 min) : pas besoin de la permission « alarmes exactes »
                am.setWindow(AlarmManager.RTC_WAKEUP, next(kind), 15 * 60_000L, pi)
            } else {
                am.cancel(pi)
            }
        }
    }

    private fun pending(context: Context, kind: Kind): PendingIntent = PendingIntent.getBroadcast(
        context, kind.code,
        Intent(context, CoachReceiver::class.java).setAction("com.goodlife.app.COACH_${kind.name}"),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

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

    /** Contenu d'une notification : titre, texte, humeur du chef, canal. */
    data class Note(val title: String, val text: String, val mood: ChefMood, val remind: Boolean = false)

    fun build(kind: Kind): Note? {
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
                        "Le chef garde ta place au chaud 👨‍🍳",
                        Coach.pick(listOf(
                            "Ça fait quelques jours ! Pas de pression : note juste ton prochain repas pour repartir en douceur.",
                            "Tu nous manques ! Une photo de ton prochain repas suffit pour reprendre.",
                            "On reprend quand tu veux, sans se juger. Le quiz du chef t'attend aussi."
                        ), 3),
                        ChefMood.QUESTION
                    )
                }
                val y = s.history.lastOrNull()?.takeIf { it.date == localDay(-1) } ?: return null
                if (y.kcal == 0) return null
                val ok = y.status == DayStatus.REUSSI || y.status == DayStatus.RATTRAPE
                val lines = mutableListOf("Score : ${y.score}/100 · ${Coach.fmt(y.kcal)} / ${Coach.fmt(p.targetKcal)} kcal")
                if (y.stepGoal > 0) lines += "${Coach.fmt(y.steps)} pas / ${Coach.fmt(y.stepGoal)}"
                when {
                    ok && s.streak in Coach.MILESTONES -> Note(
                        "🎉 ${s.streak} jours de série !",
                        (lines + "Bravo, c'est un vrai palier. Le chef est fier de toi !").joinToString("\n"), ChefMood.BRAVO
                    )
                    ok -> Note(
                        "Hier : journée validée ✅",
                        (lines + if (s.streak > 1) "Série : ${s.streak} jours 🔥" else "C'est parti pour une nouvelle série !").joinToString("\n"),
                        ChefMood.CONTENT
                    )
                    s.recoverableStreak > 0 -> Note(
                        "Ta série peut encore être sauvée",
                        (lines + "Réussis le quiz du chef aujourd'hui pour garder ta série de ${s.recoverableStreak} jour${if (s.recoverableStreak > 1) "s" else ""}.").joinToString("\n"),
                        ChefMood.TRISTE, remind = true
                    )
                    else -> Note(
                        "Ton bilan d'hier",
                        (lines + "Pas grave : aujourd'hui est une nouvelle page 💪").joinToString("\n"), ChefMood.CONTENT
                    )
                }
            }
            Kind.NOON -> {
                // Déjà noté un repas depuis 11 h : pas besoin de rappel
                val c = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 11); set(Calendar.MINUTE, 0) }
                if (today.any { it.timestamp >= c.timeInMillis }) return null
                val planned = Repo.plan.value.firstOrNull { it.date == localDay(0) && it.slot == MealSlot.DEJEUNER && !it.done }
                val tip = Coach.pick(Coach.ENCOURAGEMENTS, 11)
                if (planned != null) Note("Ce midi : ${planned.name}", "${planned.kcal} kcal, prévu dans ton planning.\n$tip", ChefMood.CONTENT)
                else Note("Le petit mot du chef", tip, ChefMood.CONTENT)
            }
            Kind.EVENING -> {
                if (today.isNotEmpty() || s.streak < 1) return null
                Note(
                    "Ta série de ${s.streak} jour${if (s.streak > 1) "s" else ""} t'attend 🔥",
                    "Tu n'as encore rien noté aujourd'hui. Ajoute tes repas pour la garder !", ChefMood.QUESTION, remind = true
                )
            }
            Kind.WEEKLY -> {
                val w = Coach.week(s)
                if (w.trackedDays == 0 && w.outings == 0 && w.sessions == 0) return null
                val lines = mutableListOf("${w.validated} jour${if (w.validated > 1) "s" else ""} validé${if (w.validated > 1) "s" else ""} sur 7 · score moyen ${w.avgScore}/100")
                val move = mutableListOf<String>()
                if (w.steps > 0) move += "${Coach.fmt(w.steps)} pas"
                if (w.sessions > 0) move += "${w.sessions} séance${if (w.sessions > 1) "s" else ""}"
                if (w.outings > 0) move += "${w.outings} sortie${if (w.outings > 1) "s" else ""} (${String.format(Locale.FRANCE, "%.1f", w.outingKm)} km)"
                if (move.isNotEmpty()) lines += move.joinToString(" · ")
                Coach.weightLine(w.weightDelta, p.goal)?.let { lines += it }
                w.best?.let { lines += "Meilleure journée : ${Coach.dayName(it.date)} (${it.score}/100)" }
                lines += when {
                    w.validated >= 6 -> "Semaine incroyable, bravo ! 🏆"
                    w.validated >= 4 -> "Belle semaine, continue comme ça !"
                    else -> "Chaque semaine compte. On vise un jour de plus la semaine prochaine ?"
                }
                Note("Ta semaine avec GoodLife 📊", lines.joinToString("\n"), if (w.validated >= 4) ChefMood.BRAVO else ChefMood.CONTENT)
            }
        }
    }

    fun fire(context: Context, kind: Kind) {
        Repo.init(context)
        if (!enabled(kind) || !permissionGranted(context)) return
        val note = build(kind) ?: return
        post(context, kind.code, note)
    }

    private fun post(context: Context, id: Int, note: Note) {
        createChannels(context)
        val open = PendingIntent.getActivity(
            context, id, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val channel = if (note.remind) CH_REMIND else CH_SUMMARY
        // Écran verrouillé : seulement « GoodLife », sans les chiffres (données de santé)
        val public = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notif_chef)
            .setContentTitle("GoodLife")
            .setContentText("Un message du chef")
            .build()
        val n = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notif_chef)
            .setLargeIcon(runCatching { chefBitmap(note.mood) }.getOrNull())
            .setContentTitle(note.title)
            .setContentText(note.text.lineSequence().first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(note.text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(public)
            .setCategory(if (note.remind) NotificationCompat.CATEGORY_REMINDER else NotificationCompat.CATEGORY_RECOMMENDATION)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(id, n) }
    }

    private fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CH_SUMMARY, "Bilans et petits mots du chef", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Bilan du matin, mot de midi et bilan de la semaine (sans son)."
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_REMIND, "Rappels de série", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Quand ta série risque de s'arrêter."
            }
        )
    }

    /** Montre un exemple tout de suite (bouton « Essayer » dans les Paramètres). */
    fun preview(context: Context) {
        val note = build(Kind.MORNING) ?: Note("Le petit mot du chef", Coach.pick(Coach.ENCOURAGEMENTS, 5), ChefMood.BRAVO)
        post(context, 40, note)
    }
}

/** Réveil du coach : affiche la notification prévue (si elle a lieu d'être) puis programme la suivante. */
class CoachReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val kind = CoachNotifier.Kind.entries.firstOrNull { intent.action == "com.goodlife.app.COACH_${it.name}" } ?: return
        runCatching { CoachNotifier.fire(context, kind) }
        CoachNotifier.schedule(context)
    }
}
