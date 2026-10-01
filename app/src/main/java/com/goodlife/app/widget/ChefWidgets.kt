package com.goodlife.app.widget

import com.goodlife.app.i18n.t

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.goodlife.app.MainActivity
import com.goodlife.app.R
import com.goodlife.app.coach.Coach
import com.goodlife.app.data.Repo
import com.goodlife.app.data.localDay
import com.goodlife.app.game.DayStatus
import com.goodlife.app.game.GameSummary
import com.goodlife.app.ui.ChefMood
import com.goodlife.app.ui.chefBitmap
import java.util.Calendar

/**
 * Widgets de l'écran d'accueil : le chef (dont l'humeur et la pose changent selon ta journée, et varient d'un jour
 * à l'autre), ta série, tes gels, tes calories et tes pas du jour, et pour le grand widget un petit mot.
 * Tout est calculé sur le téléphone. Si le verrouillage par empreinte est activé, aucun chiffre de santé n'est affiché.
 */
object ChefWidgets {
    fun updateAll(context: Context) {
        val mgr = AppWidgetManager.getInstance(context) ?: return
        listOf(ChefWidgetSmall::class.java to true, ChefWidgetMedium::class.java to false).forEach { (cls, small) ->
            val ids = runCatching { mgr.getAppWidgetIds(ComponentName(context, cls)) }.getOrNull() ?: return@forEach
            if (ids.isNotEmpty()) runCatching { mgr.updateAppWidget(ids, render(context, small)) }
        }
    }

    /** Humeur du chef : selon l'heure et la journée, avec une variante différente chaque jour. */
    fun mood(s: GameSummary?, hour: Int): ChefMood {
        val day = (System.currentTimeMillis() / 86_400_000L).toInt()
        fun pick(vararg m: ChefMood) = m[day % m.size]
        if (s == null) return ChefMood.CONTENT
        val sportToday = Repo.sport.value.done.any { it.startsWith(localDay(0)) } ||
            Repo.outings.value.any { it.start >= Repo.dayBounds(0).first }
        return when {
            hour >= 22 || hour < 6 -> ChefMood.SOMMEIL
            s.today.status == DayStatus.REUSSI -> pick(ChefMood.BRAVO, ChefMood.FIER, ChefMood.COEUR)
            sportToday -> ChefMood.SPORT
            s.recoverableStreak > 0 -> ChefMood.TRISTE
            hour >= 18 && s.today.kcal == 0 && s.streak > 0 -> ChefMood.QUESTION
            else -> pick(ChefMood.CONTENT, ChefMood.CLIN, ChefMood.CONTENT, ChefMood.FIER)
        }
    }

    private fun render(context: Context, small: Boolean): RemoteViews {
        Repo.init(context)
        val s = Coach.summary()
        val p = Repo.profile.value
        val locked = Repo.settings.value.appLock
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val v = RemoteViews(context.packageName, if (small) R.layout.widget_chef_small else R.layout.widget_chef_medium)
        v.setImageViewBitmap(R.id.widget_chef, chefBitmap(mood(s, hour), 220))
        val freezes = Repo.game.value.freezes
        val streak = s?.streak ?: 0
        v.setTextViewText(R.id.widget_streak, buildString {
            if (streak == 0) append(if (small) "🔥 0" else t("🔥 Série à lancer"))
            else { append("🔥 "); append(if (small) streak.toString() else com.goodlife.app.ui.days(streak)) }
            if (freezes > 0) append("  ❄️ ").append(freezes)
        })
        // Pas du jour : relevé enregistré (capteur ou Health Connect), si le suivi est activé
        val stepsOn = Repo.settings.value.stepsEnabled && s != null
        val stepsToday = Repo.steps.value.days[localDay(0)]?.steps ?: 0
        val stepGoal = com.goodlife.app.steps.Steps.goal().coerceAtLeast(1)
        val showNumbers = s != null && p != null && !locked
        if (small) {
            v.setTextViewText(R.id.widget_kcal, if (showNumbers) t("%1\$s kcal", Coach.fmt(s!!.today.kcal)) else "")
            v.setTextViewText(R.id.widget_steps, if (showNumbers && stepsOn) t("%1\$s pas", Coach.fmt(stepsToday)) else "")
            v.setViewVisibility(R.id.widget_kcal, if (showNumbers) View.VISIBLE else View.GONE)
            v.setViewVisibility(R.id.widget_steps, if (showNumbers && stepsOn) View.VISIBLE else View.GONE)
        } else {
            v.setViewVisibility(R.id.widget_stats, if (showNumbers) View.VISIBLE else View.GONE)
            when {
                s == null || p == null -> v.setTextViewText(R.id.widget_message, t("Ouvre GoodLife pour commencer avec le chef !"))
                locked -> v.setTextViewText(R.id.widget_message, t("GoodLife est verrouillé : ouvre l'app pour voir ta journée."))
                else -> {
                    v.setTextViewText(R.id.widget_kcal, t("%1\$s kcal", Coach.fmt(s.today.kcal)))
                    v.setTextViewText(R.id.widget_kcal_sub, t("sur %1\$s · score %2\$s/100", Coach.fmt(p.targetKcal), s.today.score))
                    v.setProgressBar(R.id.widget_kcal_bar, 100, (s.today.kcal * 100 / p.targetKcal.coerceAtLeast(1)).coerceIn(0, 100), false)
                    v.setViewVisibility(R.id.widget_steps_box, if (stepsOn) View.VISIBLE else View.INVISIBLE)
                    if (stepsOn) {
                        v.setTextViewText(R.id.widget_steps, t("%1\$s pas", Coach.fmt(stepsToday)))
                        v.setTextViewText(R.id.widget_steps_sub, t("objectif %1\$s", Coach.fmt(stepGoal)))
                        v.setProgressBar(R.id.widget_steps_bar, 100, (stepsToday * 100 / stepGoal).coerceIn(0, 100), false)
                    }
                    v.setTextViewText(R.id.widget_message, Coach.homeMessage(s, p).second)
                }
            }
        }
        val open = PendingIntent.getActivity(
            context, if (small) 51 else 52, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        v.setOnClickPendingIntent(R.id.widget_root, open)
        return v
    }
}

class ChefWidgetSmall : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = ChefWidgets.updateAll(context)
}

class ChefWidgetMedium : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = ChefWidgets.updateAll(context)
}
