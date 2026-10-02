package com.goodlife.app.widget

import com.goodlife.app.i18n.t

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.util.SizeF
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.goodlife.app.MainActivity
import com.goodlife.app.R
import com.goodlife.app.coach.Coach
import com.goodlife.app.data.MealSlot
import com.goodlife.app.data.Repo
import com.goodlife.app.data.localDay
import com.goodlife.app.game.DayStatus
import com.goodlife.app.game.GameSummary
import com.goodlife.app.i18n.Lang
import com.goodlife.app.social.AppNav
import com.goodlife.app.ui.ChefMood
import com.goodlife.app.ui.chefBitmap
import java.util.Calendar
import kotlin.math.roundToInt

/**
 * Widgets de l'écran d'accueil : le chef (dont l'humeur et la pose changent selon ta journée, et varient d'un jour
 * à l'autre), ta série, tes gels, et tes chiffres du jour (calories, pas, eau, protéines), le prochain repas prévu
 * et deux raccourcis pour le grand widget. La mise en page s'adapte à la taille du widget (Android 12+ : plusieurs
 * mises en page fournies d'un coup ; avant : choisie selon la taille donnée par le lanceur).
 * Tout est calculé sur le téléphone. Si le verrouillage par empreinte est activé, aucun chiffre de santé n'est affiché
 * (ni le prochain repas), et le bouton « + Eau » disparaît.
 */
object ChefWidgets {
    const val ACTION_ADD_WATER = "com.goodlife.app.widget.ADD_WATER"
    const val GLASS_ML = 250

    fun updateAll(context: Context) {
        val mgr = AppWidgetManager.getInstance(context) ?: return
        val providers = listOf(ChefWidgetSmall::class.java to true, ChefWidgetMedium::class.java to false)
        val all = providers.map { (cls, small) ->
            small to (runCatching { mgr.getAppWidgetIds(ComponentName(context, cls)) }.getOrNull() ?: IntArray(0))
        }
        if (all.all { it.second.isEmpty() }) return
        val d = runCatching { snapshot(context) }.getOrNull() ?: return
        all.forEach { (small, ids) ->
            ids.forEach { id -> runCatching { mgr.updateAppWidget(id, views(context, mgr, id, small, d)) } }
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

    // ---------- Données du jour (lues une seule fois pour tous les widgets) ----------

    private class Snapshot(
        val res: android.content.res.Resources,   // textes dans la langue de l'app
        val chef: Bitmap,                          // une seule image partagée par toutes les tailles
        val ready: Boolean,                        // profil créé
        val locked: Boolean,                       // verrouillage par empreinte : aucun chiffre de santé
        val streak: Int,
        val freezes: Int,
        val kcal: Int,
        val kcalGoal: Int,
        val stepsOn: Boolean,
        val steps: Int,
        val stepGoal: Int,
        val water: Int,
        val waterGoal: Int,
        val protein: Int,
        val proteinGoal: Int,
        val message: String,
        val nextMeal: String?
    ) {
        val numbers: Boolean get() = ready && !locked
    }

    private fun snapshot(context: Context): Snapshot {
        Repo.init(context)
        val s = Coach.summary()
        val p = Repo.profile.value
        val locked = Repo.settings.value.appLock
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val res = localized(context).resources
        val ready = s != null && p != null
        val protein = Repo.mealsOfDay(Repo.meals.value, 0).sumOf { it.proteinG }.roundToInt()
        return Snapshot(
            res = res,
            chef = chefAvatar(mood(s, hour)),
            ready = ready,
            locked = locked,
            streak = s?.streak ?: 0,
            freezes = Repo.game.value.freezes,
            kcal = s?.today?.kcal ?: 0,
            kcalGoal = (p?.targetKcal ?: 0).coerceAtLeast(1),
            stepsOn = Repo.settings.value.stepsEnabled && s != null,
            steps = Repo.steps.value.days[localDay(0)]?.steps ?: 0,
            stepGoal = com.goodlife.app.steps.Steps.goal().coerceAtLeast(1),
            water = Repo.water.value[localDay(0)] ?: 0,
            waterGoal = Repo.waterGoal().coerceAtLeast(1),
            protein = protein,
            proteinGoal = p?.proteinG ?: 0,
            message = when {
                !ready -> t("Ouvre GoodLife pour commencer avec le chef !")
                locked -> t("GoodLife est verrouillé : ouvre l'app pour voir ta journée.")
                else -> Coach.homeMessage(s!!, p!!).second
            },
            nextMeal = if (ready && !locked) nextMeal(res, hour) else null
        )
    }

    /** Ressources dans la langue choisie dans l'app (qui peut différer de celle du téléphone). */
    private fun localized(context: Context): Context = runCatching {
        val conf = Configuration(context.resources.configuration).apply { setLocale(Lang.locale) }
        context.createConfigurationContext(conf)
    }.getOrDefault(context)

    /** Prochain repas prévu dans le planning : le prochain créneau d'aujourd'hui, sinon (le soir) le premier de demain. */
    private fun nextMeal(res: android.content.res.Resources, hour: Int): String? {
        fun endHour(slot: MealSlot) = when (slot) {
            MealSlot.PETIT_DEJ -> 10
            MealSlot.DEJEUNER -> 14
            MealSlot.COLLATION -> 17
            MealSlot.DINER -> 22
        }
        val plan = Repo.plan.value.filter { !it.done && it.name.isNotBlank() }
        fun line(m: com.goodlife.app.data.PlannedMeal) = buildString {
            append(m.slot.label).append(" · ").append(m.name.trim())
            if (m.kcal > 0) append(" · ").append(res.getString(R.string.widget_kcal_value, Coach.fmt(m.kcal)))
        }
        plan.filter { it.date == localDay(0) && hour < endHour(it.slot) }.minByOrNull { it.slot.ordinal }
            ?.let { return line(it) }
        if (hour >= 18) {
            plan.filter { it.date == localDay(1) }.minByOrNull { it.slot.ordinal }
                ?.let { return res.getString(R.string.widget_tomorrow) + " · " + line(it) }
        }
        return null
    }

    /** Le chef dans un médaillon rond (comme une photo de profil), un peu réduit pour que la toque ne soit pas coupée. */
    private fun chefAvatar(mood: ChefMood, px: Int = 200): Bitmap {
        val scale = 0.86f
        val placed = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        Canvas(placed).drawBitmap(
            chefBitmap(mood, (px * scale).roundToInt()), null,
            RectF(px * (1 - scale) / 2, px * (1 - scale), px * (1 + scale) / 2, px.toFloat()),
            Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        )
        val out = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = BitmapShader(placed, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP) }
        Canvas(out).drawCircle(px / 2f, px / 2f, px / 2f, paint)
        return out
    }

    // ---------- Tailles ----------

    /** Petit widget : taille de l'anneau et du chef (dp), chiffres affichés ou non, grande version. */
    private data class SmallSpec(val ring: Float, val chef: Float, val stats: Boolean, val big: Boolean)

    /** Moyen widget : taille du chef, lignes du petit mot, boutons, détails sous les jauges, repas, protéines. */
    private data class MediumSpec(
        val chef: Float, val button: Float, val compact: Boolean, val stats: Boolean, val hints: Boolean,
        val meal: Boolean, val protein: Boolean, val buttons: Boolean, val labels: Boolean
    )

    // Points de rupture (largeur × hauteur en dp) : Android prend la plus grande mise en page qui tient.
    private val SMALL = listOf(
        SizeF(96f, 96f) to SmallSpec(ring = 56f, chef = 42f, stats = false, big = false),
        SizeF(96f, 136f) to SmallSpec(ring = 62f, chef = 46f, stats = true, big = false),
        SizeF(120f, 156f) to SmallSpec(ring = 76f, chef = 56f, stats = true, big = false),
        SizeF(170f, 200f) to SmallSpec(ring = 100f, chef = 74f, stats = true, big = true)
    )

    private val MEDIUM: List<Pair<SizeF, MediumSpec>> = buildList {
        // Étroit : pas de boutons ; moyen : boutons en icônes seules ; large : boutons avec texte et protéines
        for (w in listOf(200f, 250f, 320f)) {
            val buttons = w >= 250f
            val wide = w >= 320f
            val chef = if (wide) 60f else 52f
            add(SizeF(w, 96f) to MediumSpec(52f, 24f, compact = true, stats = false, hints = false, meal = false, protein = wide, buttons = buttons, labels = wide))
            add(SizeF(w, 134f) to MediumSpec(52f, 24f, compact = true, stats = true, hints = false, meal = false, protein = wide, buttons = buttons, labels = wide))
            add(SizeF(w, 165f) to MediumSpec(chef, 28f, compact = false, stats = true, hints = false, meal = true, protein = wide, buttons = buttons, labels = wide))
            add(SizeF(w, 190f) to MediumSpec(chef, 28f, compact = false, stats = true, hints = true, meal = true, protein = wide, buttons = buttons, labels = wide))
        }
    }

    private fun views(context: Context, mgr: AppWidgetManager, id: Int, small: Boolean, d: Snapshot): RemoteViews {
        fun build(spec: Any, sized: Boolean) =
            if (small) smallViews(context, d, spec as SmallSpec, sized) else mediumViews(context, d, spec as MediumSpec, sized)
        val specs: List<Pair<SizeF, Any>> = if (small) SMALL else MEDIUM
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ : toutes les mises en page d'un coup, le lanceur choisit selon la taille réelle
            return RemoteViews(specs.associate { (size, spec) -> size to build(spec, true) })
        }
        // Avant Android 12 : taille donnée par le lanceur (portrait : largeur mini, hauteur maxi)
        val o: Bundle? = runCatching { mgr.getAppWidgetOptions(id) }.getOrNull()
        val w = o?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) ?: 0
        val h = o?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT) ?: 0
        val spec = if (w <= 0 || h <= 0) specs[if (small) 2 else specs.size - 2].second
        else specs.lastOrNull { it.first.width <= w && it.first.height <= h }?.second ?: specs.first().second
        return build(spec, false)
    }

    private fun RemoteViews.size(id: Int, w: Float, h: Float, sized: Boolean) {
        if (!sized || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        setViewLayoutWidth(id, w, TypedValue.COMPLEX_UNIT_DIP)
        setViewLayoutHeight(id, h, TypedValue.COMPLEX_UNIT_DIP)
    }

    private fun RemoteViews.height(id: Int, h: Float, sized: Boolean) {
        if (sized && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setViewLayoutHeight(id, h, TypedValue.COMPLEX_UNIT_DIP)
    }

    private fun RemoteViews.show(id: Int, visible: Boolean) = setViewVisibility(id, if (visible) View.VISIBLE else View.GONE)

    private fun pct(value: Int, goal: Int) = (value * 100L / goal.coerceAtLeast(1)).toInt().coerceIn(0, 100)

    /** « 1 240 » en gras, suivi d'un complément plus petit (« kcal », « / 2 100 »). */
    private fun value(main: String, rest: String = ""): CharSequence {
        val b = SpannableStringBuilder(main)
        b.setSpan(StyleSpan(Typeface.BOLD), 0, main.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        if (rest.isNotEmpty()) {
            val start = b.length
            b.append(rest)
            b.setSpan(RelativeSizeSpan(0.75f), start, b.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return b
    }

    private fun kcalHint(d: Snapshot): String = when {
        d.kcal > d.kcalGoal -> d.res.getString(R.string.widget_kcal_over, Coach.fmt(d.kcal - d.kcalGoal))
        else -> d.res.getString(R.string.widget_kcal_left, Coach.fmt(d.kcalGoal - d.kcal))
    }

    private fun goalHint(d: Snapshot, value: Int, goal: Int, goalText: String): String =
        if (value >= goal) d.res.getString(R.string.widget_goal_reached) else d.res.getString(R.string.widget_of, goalText)

    // ---------- Petit widget ----------

    private fun smallViews(context: Context, d: Snapshot, spec: SmallSpec, sized: Boolean): RemoteViews {
        val v = RemoteViews(context.packageName, R.layout.widget_chef_small)
        v.setImageViewBitmap(R.id.widget_chef, d.chef)
        v.size(R.id.widget_ring, spec.ring, spec.ring, sized)
        v.size(R.id.widget_chef, spec.chef, spec.chef, sized)
        val big = spec.big
        v.setTextViewTextSize(R.id.widget_streak, TypedValue.COMPLEX_UNIT_SP, if (big) 16f else 14f)
        v.setTextViewText(R.id.widget_streak, "🔥 " + Coach.fmt(d.streak))
        v.setTextViewText(R.id.widget_freeze, if (d.freezes > 0) "❄️ ${d.freezes}" else "")
        v.show(R.id.widget_freeze, d.freezes > 0)

        // Anneau des calories : seulement si les chiffres peuvent être affichés
        v.show(R.id.widget_ring, d.numbers)
        v.setProgressBar(R.id.widget_ring, 100, if (d.numbers) pct(d.kcal, d.kcalGoal) else 0, false)

        v.show(R.id.widget_stats, d.numbers && spec.stats)
        v.show(R.id.widget_note, !d.numbers && spec.stats)
        v.setTextViewText(R.id.widget_note, d.res.getString(if (!d.ready) R.string.widget_start else R.string.widget_locked))
        if (d.numbers) {
            listOf(R.id.widget_kcal, R.id.widget_steps).forEach { v.setTextViewTextSize(it, TypedValue.COMPLEX_UNIT_SP, if (big) 17f else 14f) }
            listOf(R.id.widget_kcal_sub, R.id.widget_steps_sub).forEach { v.setTextViewTextSize(it, TypedValue.COMPLEX_UNIT_SP, if (big) 12f else 10f) }
            v.setTextViewText(R.id.widget_kcal, value(Coach.fmt(d.kcal), " kcal"))
            v.setTextViewText(R.id.widget_kcal_sub, kcalHint(d))
            // Pas si le suivi est activé, sinon l'eau du jour
            if (d.stepsOn) {
                v.setTextViewText(R.id.widget_steps, value(Coach.fmt(d.steps)))
                v.setTextViewText(R.id.widget_steps_sub, if (d.steps >= d.stepGoal) "✓ " + d.res.getString(R.string.widget_steps_unit) else d.res.getString(R.string.widget_steps_unit))
            } else {
                v.setTextViewText(R.id.widget_steps, value(Coach.fmt(d.water), " ml"))
                v.setTextViewText(R.id.widget_steps_sub, d.res.getString(R.string.widget_lbl_water))
            }
        }
        v.setContentDescription(R.id.widget_chef, d.res.getString(R.string.widget_chef_desc))
        v.setOnClickPendingIntent(android.R.id.background, openApp(context, 51, null))
        return v
    }

    // ---------- Widget moyen ----------

    private fun mediumViews(context: Context, d: Snapshot, spec: MediumSpec, sized: Boolean): RemoteViews {
        val v = RemoteViews(context.packageName, R.layout.widget_chef_medium)
        v.setImageViewBitmap(R.id.widget_chef, d.chef)
        v.size(R.id.widget_chef, spec.chef, spec.chef, sized)
        val pad = (if (spec.compact) 10f else 12f).dp(context)
        val side = 14f.dp(context)
        v.setViewPadding(android.R.id.background, side, pad, side, pad)

        v.setTextViewText(R.id.widget_streak, buildString {
            if (d.streak == 0) append(t("🔥 Série à lancer"))
            else append("🔥 ").append(com.goodlife.app.ui.days(d.streak))
            if (d.freezes > 0) append("  ❄️ ").append(d.freezes)
        })
        v.setTextViewText(R.id.widget_message, d.message)
        v.setInt(R.id.widget_message, "setMaxLines", if (spec.compact && spec.stats) 1 else 2)

        // Raccourcis : photo d'un repas (onglet Scanner) et + 1 verre d'eau (masqué si l'app est verrouillée)
        v.show(R.id.widget_actions, spec.buttons)
        v.height(R.id.widget_btn_photo, spec.button, sized)
        v.height(R.id.widget_btn_water, spec.button, sized)
        v.setTextViewText(R.id.widget_btn_photo_txt, d.res.getString(R.string.widget_btn_photo))
        v.setTextViewText(R.id.widget_btn_water_txt, d.res.getString(R.string.widget_btn_water))
        // Moins de place : icônes seules (le texte reste lu par TalkBack grâce à la description)
        v.show(R.id.widget_btn_photo_txt, spec.labels)
        v.show(R.id.widget_btn_water_txt, spec.labels)
        val (padStart, padEnd) = if (spec.labels) 10f.dp(context) to 12f.dp(context) else 14f.dp(context) to 14f.dp(context)
        listOf(R.id.widget_btn_photo, R.id.widget_btn_water).forEach { v.setViewPadding(it, padStart, 0, padEnd, 0) }
        v.setContentDescription(R.id.widget_btn_photo, d.res.getString(R.string.widget_btn_photo_desc))
        v.setContentDescription(R.id.widget_btn_water, d.res.getString(R.string.widget_btn_water_desc))
        v.show(R.id.widget_btn_water, d.numbers)
        v.setOnClickPendingIntent(R.id.widget_btn_photo, openApp(context, 53, "scan"))
        v.setOnClickPendingIntent(R.id.widget_btn_water, addWater(context))

        // Jauges du jour
        v.show(R.id.widget_stats, d.numbers && spec.stats)
        if (d.numbers && spec.stats) {
            v.setTextViewText(R.id.widget_kcal_lbl, d.res.getString(R.string.widget_lbl_kcal))
            v.setTextViewText(R.id.widget_kcal, value(Coach.fmt(d.kcal), " / " + Coach.fmt(d.kcalGoal)))
            v.setProgressBar(R.id.widget_kcal_bar, 100, pct(d.kcal, d.kcalGoal), false)
            v.setTextViewText(R.id.widget_kcal_sub, kcalHint(d))   // toujours visible : calories restantes

            v.show(R.id.widget_steps_box, d.stepsOn)
            if (d.stepsOn) {
                v.setTextViewText(R.id.widget_steps_lbl, d.res.getString(R.string.widget_lbl_steps))
                v.setTextViewText(R.id.widget_steps, value(Coach.fmt(d.steps)))
                v.setProgressBar(R.id.widget_steps_bar, 100, pct(d.steps, d.stepGoal), false)
                v.setTextViewText(R.id.widget_steps_sub, goalHint(d, d.steps, d.stepGoal, Coach.fmt(d.stepGoal)))
            }

            v.setTextViewText(R.id.widget_water_lbl, d.res.getString(R.string.widget_lbl_water))
            v.setTextViewText(R.id.widget_water, value(Coach.fmt(d.water), " ml"))
            v.setProgressBar(R.id.widget_water_bar, 100, pct(d.water, d.waterGoal), false)
            v.setTextViewText(R.id.widget_water_sub, goalHint(d, d.water, d.waterGoal, d.res.getString(R.string.widget_ml, Coach.fmt(d.waterGoal))))

            // Protéines : s'il y a un objectif et de la place (ou si les pas ne sont pas suivis)
            val protein = d.proteinGoal > 0 && (spec.protein || !d.stepsOn)
            v.show(R.id.widget_protein_box, protein)
            if (protein) {
                v.setTextViewText(R.id.widget_protein_lbl, d.res.getString(R.string.widget_lbl_protein))
                v.setTextViewText(R.id.widget_protein, value(Coach.fmt(d.protein), " g"))
                v.setProgressBar(R.id.widget_protein_bar, 100, pct(d.protein, d.proteinGoal), false)
                v.setTextViewText(R.id.widget_protein_sub, goalHint(d, d.protein, d.proteinGoal, d.res.getString(R.string.widget_grams, Coach.fmt(d.proteinGoal))))
            }

            // Détails sous les jauges : en grand, ou s'il n'y a pas de repas à afficher et assez de place
            val hints = spec.hints || (spec.meal && d.nextMeal == null)
            listOf(R.id.widget_steps_sub, R.id.widget_water_sub, R.id.widget_protein_sub).forEach { v.show(it, hints) }
        }

        // Prochain repas prévu dans le planning
        v.show(R.id.widget_meal, spec.meal && d.nextMeal != null)
        v.setTextViewText(R.id.widget_meal_text, d.nextMeal ?: "")

        v.setContentDescription(R.id.widget_chef, d.res.getString(R.string.widget_chef_desc))
        v.setOnClickPendingIntent(android.R.id.background, openApp(context, 52, null))
        return v
    }

    private fun Float.dp(context: Context): Int = (this * context.resources.displayMetrics.density).roundToInt()

    /** Ouvre l'app (éventuellement sur un onglet précis : « scan » = Scanner). Intent explicite, PendingIntent immuable. */
    private fun openApp(context: Context, code: Int, open: String?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (open != null) intent.putExtra(AppNav.EXTRA, open)
        return PendingIntent.getActivity(context, code, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /** « + Eau » : un verre ajouté sans ouvrir l'app (récepteur interne, non exporté). */
    private fun addWater(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 54, Intent(context, WidgetWaterReceiver::class.java).setAction(ACTION_ADD_WATER),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
}

class ChefWidgetSmall : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = ChefWidgets.updateAll(context)
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) =
        ChefWidgets.updateAll(context)
}

class ChefWidgetMedium : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) = ChefWidgets.updateAll(context)
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) =
        ChefWidgets.updateAll(context)
}

/**
 * Bouton « + Eau » du widget : ajoute un verre (250 ml) à l'eau du jour, puis rafraîchit les widgets.
 * Refusé si l'app est verrouillée par empreinte (le bouton est alors masqué) ou s'il n'y a pas encore de profil.
 */
class WidgetWaterReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ChefWidgets.ACTION_ADD_WATER) return
        val app = context.applicationContext
        val pending = goAsync()
        Thread {
            try {
                Repo.init(app)
                if (Repo.profile.value != null && !Repo.settings.value.appLock) {
                    Repo.addWater(ChefWidgets.GLASS_ML)
                    Repo.flush()
                }
                ChefWidgets.updateAll(app)
            } catch (_: Throwable) {
            } finally {
                pending.finish()
            }
        }.start()
    }
}
