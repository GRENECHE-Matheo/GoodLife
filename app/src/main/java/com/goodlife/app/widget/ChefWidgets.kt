package com.goodlife.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
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
 * à l'autre), ta série, tes gels et, pour le grand widget, le score du jour et un petit mot.
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
            if (streak == 0) append(if (small) "🔥 0" else "🔥 Série à lancer")
            else { append("🔥 ").append(streak); if (!small) append(if (streak > 1) " jours" else " jour") }
            if (freezes > 0) append("  ❄️ ").append(freezes)
        })
        if (!small) {
            if (s == null || p == null) {
                v.setTextViewText(R.id.widget_line, "")
                v.setTextViewText(R.id.widget_message, "Ouvre GoodLife pour commencer avec le chef !")
                v.setProgressBar(R.id.widget_score, 100, 0, false)
            } else if (locked) {
                v.setTextViewText(R.id.widget_line, "GoodLife est verrouillé")
                v.setTextViewText(R.id.widget_message, "Ouvre l'app pour voir ta journée.")
                v.setProgressBar(R.id.widget_score, 100, 0, false)
            } else {
                v.setProgressBar(R.id.widget_score, 100, s.today.score.coerceIn(0, 100), false)
                v.setTextViewText(R.id.widget_line, buildString {
                    append("Score ").append(s.today.score).append("/100 · ")
                    append(Coach.fmt(s.today.kcal)).append(" / ").append(Coach.fmt(p.targetKcal)).append(" kcal")
                    if (s.today.stepGoal > 0) append(" · ").append(Coach.fmt(s.today.steps)).append(" pas")
                })
                v.setTextViewText(R.id.widget_message, Coach.homeMessage(s, p).second)
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
