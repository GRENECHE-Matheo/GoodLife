package com.goodlife.app.game

import com.goodlife.app.i18n.Lang
import com.goodlife.app.i18n.t

import android.content.Context
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Questions du quiz fabriquées à partir de la table Ciqual 2025 de l'Anses (assets/quiz_foods.tsv : 420 aliments
 * courants). Les réponses viennent toujours des données officielles, et les combinaisons se comptent en millions :
 * avec la mémoire des questions déjà posées (QuizBank), on ne retombe jamais sur la même.
 */
object QuizGen {
    /** [name] (français) sert de clé pour la mémoire des questions ; [shown] est le nom affiché dans la langue de l'app. */
    data class Food(val name: String, val kcal: Double, val protein: Double, val carbs: Double, val fat: Double, val family: String, val en: String = "") {
        val shown: String get() = if (Lang.en && en.isNotBlank()) en else name
    }

    /** Une question fabriquée et sa clé (pour ne jamais la reposer). */
    data class Made(val key: String, val question: DailyQuestion)

    private enum class Nutrient(val label: String, val unit: String, val minDiff: Double) {
        KCAL("calories", "kcal", 40.0), PROTEIN("protéines", "g", 3.0), CARBS("glucides", "g", 5.0), FAT("lipides (graisses)", "g", 3.0);

        val shown: String get() = t(label)

        fun of(f: Food) = when (this) { KCAL -> f.kcal; PROTEIN -> f.protein; CARBS -> f.carbs; FAT -> f.fat }
        fun show(v: Double) = if (this == KCAL) "${v.roundToInt()} kcal" else "${fmt1(v)} g"
    }

    private const val SOURCE = "Valeurs pour 100 g, table Ciqual 2025 de l'Anses."

    @Volatile private var foods: List<Food>? = null

    fun load(context: Context): List<Food> = foods ?: synchronized(this) {
        foods ?: context.assets.open("quiz_foods.tsv").bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.filter { it.isNotBlank() && !it.startsWith("#") }.mapNotNull { line ->
                val f = line.split('\t')
                if (f.size < 6) return@mapNotNull null
                Food(f[0], f[1].toDoubleOrNull() ?: return@mapNotNull null, f[2].toDoubleOrNull() ?: 0.0,
                    f[3].toDoubleOrNull() ?: 0.0, f[4].toDoubleOrNull() ?: 0.0, f[5], f.getOrElse(6) { "" }.trim())
            }.toList()
        }.also { foods = it }
    }

    /** Fabrique une question d'un type tiré au hasard ; null si le tirage ne donne pas une question sans ambiguïté. */
    fun make(all: List<Food>, rnd: Random, type: Int): Made? = when (type) {
        0 -> most(all, rnd, Nutrient.PROTEIN)
        1 -> most(all, rnd, Nutrient.KCAL)
        2 -> most(all, rnd, Nutrient.FAT)
        3 -> most(all, rnd, Nutrient.CARBS)
        4 -> least(all, rnd)
        5 -> estimate(all, rnd, Nutrient.KCAL)
        6 -> estimate(all, rnd, Nutrient.PROTEIN)
        7 -> energySource(all, rnd)
        8 -> trueFalse(all, rnd)
        else -> duel(all, rnd)
    }

    const val TYPES = 10

    // ------------------------------------------------------------------ types de questions

    /** « Lequel contient le plus de … ? » (parfois entre aliments de la même famille). */
    private fun most(all: List<Food>, rnd: Random, n: Nutrient): Made? {
        val pool = if (rnd.nextInt(3) == 0) all.filter { it.family == all[rnd.nextInt(all.size)].family } else all
        if (pool.size < 8) return null
        val four = pool.shuffled(rnd).distinctBy { it.name }.take(4)
        val sorted = four.sortedByDescending { n.of(it) }
        val (a, b) = sorted[0] to sorted[1]
        if (n.of(a) < n.of(b) * 1.3 || n.of(a) - n.of(b) < n.minDiff) return null
        val q = pick(rnd, listOf(
            t("Lequel de ces aliments contient le plus de %1\$s pour 100 g ?", n.shown),
            t("Pour 100 g, lequel apporte le plus de %1\$s ?", n.shown),
            t("Défi du chef : qui a le plus de %1\$s (pour 100 g) ?", n.shown),
            t("À poids égal (100 g), lequel est le plus riche en %1\$s ?", n.shown)
        ))
        return Made("M${n.ordinal}:" + key(four), build(q, a.shown, four.map { it.shown }, detail(four, n)))
    }

    /** « Lequel est le moins calorique ? » */
    private fun least(all: List<Food>, rnd: Random): Made? {
        val four = all.shuffled(rnd).distinctBy { it.name }.take(4)
        val sorted = four.sortedBy { it.kcal }
        if (sorted[1].kcal < sorted[0].kcal * 1.3 || sorted[1].kcal - sorted[0].kcal < 40) return null
        val q = pick(rnd, listOf(
            t("Lequel de ces aliments est le moins calorique pour 100 g ?"),
            t("Pour 100 g, lequel apporte le moins de calories ?"),
            t("Le chef cherche le plus léger : lequel a le moins de calories pour 100 g ?")
        ))
        return Made("L:" + key(four), build(q, sorted[0].shown, four.map { it.shown }, detail(four, Nutrient.KCAL)))
    }

    /** « Environ combien de kcal (ou de protéines) dans 100 g de … ? » */
    private fun estimate(all: List<Food>, rnd: Random, n: Nutrient): Made? {
        val f = all[rnd.nextInt(all.size)]
        val v = n.of(f)
        if (n == Nutrient.KCAL && v < 25 || n == Nutrient.PROTEIN && v < 4) return null
        fun round(x: Double) = when {
            n == Nutrient.PROTEIN -> x.roundToInt().toDouble()
            x < 100 -> (x / 5).roundToInt() * 5.0
            else -> (x / 10).roundToInt() * 10.0
        }
        val good = round(v)
        val factors = listOf(0.35, 0.55, 1.7, 2.4, 3.2).shuffled(rnd)
        val options = mutableListOf(good)
        for (k in factors) {
            val o = round(v * k)
            if (o > 0 && options.all { abs(it - o) >= maxOf(it, o) * 0.28 }) options += o
            if (options.size == 4) break
        }
        if (options.size < 4) return null
        val label = { x: Double -> if (n == Nutrient.KCAL) "${x.roundToInt()} kcal" else "${x.roundToInt()} g" }
        val q = if (n == Nutrient.KCAL) pick(rnd, listOf(
            t("Environ combien de calories dans 100 g %1\$s ?", de(f)),
            t("100 g %1\$s, ça fait environ combien de kcal ?", de(f))
        )) else pick(rnd, listOf(
            t("Environ combien de protéines dans 100 g %1\$s ?", de(f)),
            t("100 g %1\$s apportent environ combien de grammes de protéines ?", de(f))
        ))
        val expl = t("100 g %1\$s : %2\$s", de(f), n.show(v)) +
            (if (n == Nutrient.KCAL) t(" (protéines %1\$s g, glucides %2\$s g, lipides %3\$s g). ", fmt1(f.protein), fmt1(f.carbs), fmt1(f.fat)) else ". ") + t(SOURCE)
        return Made("E${n.ordinal}:" + f.name, build(q, label(good), options.map(label), expl))
    }

    /** « D'où vient surtout l'énergie de … ? » (glucides, lipides ou protéines). */
    private fun energySource(all: List<Food>, rnd: Random): Made? {
        val f = all[rnd.nextInt(all.size)]
        val parts = listOf(t("Des glucides") to f.carbs * 4, t("Des lipides (graisses)") to f.fat * 9, t("Des protéines") to f.protein * 4)
        val short = listOf(t("glucides"), t("lipides"), t("protéines"))
        val total = parts.sumOf { it.second }
        if (total < 40) return null
        val top = parts.maxBy { it.second }
        if (top.second / total < 0.6) return null
        val q = pick(rnd, listOf(
            t("%1\$s : d'où vient surtout son énergie ?", f.shown),
            t("%1\$s : ses calories viennent surtout…", f.shown)
        ))
        val pct = parts.indices.joinToString(", ") { i -> "${short[i]} ${(parts[i].second * 100 / total).roundToInt()} %" }
        return Made("S:" + f.name, build(q, top.first, parts.map { it.first },
            t("Part des calories (%1\$s) : %2\$s (1 g de lipides = 9 kcal, 1 g de glucides ou de protéines = 4 kcal). %3\$s", lower(f.shown), pct, t(SOURCE))))
    }

    /** « Vrai ou faux : 100 g de X contiennent plus de … que 100 g de Y. » */
    private fun trueFalse(all: List<Food>, rnd: Random): Made? {
        val n = Nutrient.entries[rnd.nextInt(Nutrient.entries.size)]
        val (x, y) = all.shuffled(rnd).distinctBy { it.name }.take(2).let { it[0] to it[1] }
        val (hi, lo) = if (n.of(x) >= n.of(y)) x to y else y to x
        if (n.of(hi) < n.of(lo) * 1.5 || n.of(hi) - n.of(lo) < n.minDiff) return null
        val statementTrue = rnd.nextBoolean()
        val (a, b) = if (statementTrue) hi to lo else lo to hi
        val what = if (n == Nutrient.KCAL) t("plus de calories") else t("plus de %1\$s", n.shown)
        val q = t("Vrai ou faux : 100 g %1\$s contiennent %2\$s que 100 g %3\$s.", de(a), what, de(b))
        val good = if (statementTrue) t("Vrai") else t("Faux")
        return Made("T${n.ordinal}:" + key(listOf(x, y)), build(q, good, listOf(t("Vrai"), t("Faux")),
            t("Pour 100 g : %1\$s %2\$s, %3\$s %4\$s. %5\$s", hi.shown, n.show(n.of(hi)), lo.shown, n.show(n.of(lo)), t(SOURCE)), shuffle = false))
    }

    /** « Le plus calorique : X ou Y ? » */
    private fun duel(all: List<Food>, rnd: Random): Made? {
        val fam = all[rnd.nextInt(all.size)].family
        val pool = all.filter { it.family == fam }
        if (pool.size < 4) return null
        val two = pool.shuffled(rnd).distinctBy { it.name }.take(2)
        val (hi, lo) = if (two[0].kcal >= two[1].kcal) two[0] to two[1] else two[1] to two[0]
        if (hi.kcal < lo.kcal * 1.4 || hi.kcal - lo.kcal < 40) return null
        val q = pick(rnd, listOf(
            t("Duel du chef : lequel est le plus calorique pour 100 g ?"),
            t("Pour 100 g, qui gagne le match des calories ?")
        ))
        return Made("D:" + key(two), build(q, hi.shown, two.map { it.shown }, detail(two, Nutrient.KCAL)))
    }

    // ------------------------------------------------------------------ outils

    private fun build(q: String, good: String, options: List<String>, expl: String, shuffle: Boolean = true): DailyQuestion {
        val opts = if (shuffle) options.shuffled() else options
        return DailyQuestion(q, opts, opts.indexOf(good), expl)
    }

    private fun detail(foods: List<Food>, n: Nutrient) =
        t("Pour 100 g : ") + foods.sortedByDescending { n.of(it) }.joinToString(", ") { "${it.shown} ${n.show(n.of(it))}" } + ". " + t(SOURCE)

    private fun key(foods: List<Food>) = foods.map { it.name }.sorted().joinToString("|")

    private fun <T> pick(rnd: Random, list: List<T>) = list[rnd.nextInt(list.size)]

    private fun fmt1(v: Double): String = if (v >= 10 || v % 1.0 == 0.0) v.roundToInt().toString()
                                          else String.format(com.goodlife.app.i18n.Lang.locale, "%.1f", v)

    private fun lower(name: String): String {
        // Garde la majuscule des noms propres (Comté, Brie de Meaux…) ; sinon minuscule au milieu de la phrase
        val proper = listOf("Brie", "Comté", "Cantal", "Beaufort", "Parmesan", "Roquefort", "Camembert", "Reblochon", "Munster",
            "Maroilles", "Livarot", "Époisses", "Chaource", "Coulommiers", "Morbier", "Salers", "Abondance", "Langres",
            "Neufchâtel", "Pont", "Saint", "Tête de moine", "Tête de Moine", "Gouda", "Edam", "Cheddar", "Mozzarella", "Feta", "Emmental",
            "Gorgonzola", "Grana", "Asiago", "Fontina", "Provolone", "Burrata", "Mascarpone", "Fourme", "Bresaola", "Coppa",
            "Bleu d'Auvergne", "French Gruyère", "Brussels", "Jerusalem", "Bayonne", "Morteau", "Vienna", "Mirabelle", "Greengage",
            "Savoy", "Breton", "Pacific")
        return if (proper.any { name.startsWith(it) }) name else name.replaceFirstChar { it.lowercase() }
    }

    /** Voyelle ou h muet (« d'huile », mais « de haricots »). */
    private fun startsWithVowel(s: String) = s.firstOrNull()?.lowercaseChar()?.let { it in "aeiouyéèêàâîôûœ" } == true ||
        s.lowercase().let { it.startsWith("huile") || it.startsWith("huître") || it.startsWith("herbe") }

    /** « de pomme », « d'amandes » (en anglais : « of apple »). */
    private fun de(f: Food): String =
        if (Lang.en) "of " + lower(f.shown) else lower(f.name).let { if (startsWithVowel(it)) "d'$it" else "de $it" }
}
