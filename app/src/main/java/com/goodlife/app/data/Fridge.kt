package com.goodlife.app.data

import com.goodlife.app.i18n.t
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** Un aliment du frigo ou des placards : quantité dans son unité (pièce, g, ml…) et rayon. */
data class FridgeItem(
    val name: String,
    val qty: Double,
    val unit: String,
    val aisle: String,
    val addedAt: Long = System.currentTimeMillis()
) {
    /** « 3 », « 250 g », « 1,5 L », « 2 boîtes »… */
    fun qtyText(): String {
        val n = if (qty % 1.0 == 0.0) qty.toLong().toString() else String.format(com.goodlife.app.i18n.Lang.locale, "%.1f", qty)
        val plural = if (com.goodlife.app.i18n.Lang.en) qty != 1.0 else qty >= 2.0
        return when (unit) {
            Fridge.PIECE -> n
            "boîte" -> t(if (plural) "%1\$s boîtes" else "%1\$s boîte", n)
            "paquet" -> t(if (plural) "%1\$s paquets" else "%1\$s paquet", n)
            "bouteille" -> t(if (plural) "%1\$s bouteilles" else "%1\$s bouteille", n)
            else -> "$n $unit"
        }
    }
}

/**
 * Mon frigo : ce que j'ai à la maison, gardé chiffré sur le téléphone (et dans la sauvegarde). Rempli à la main,
 * depuis la liste de courses, par la photo d'un ticket de caisse, ou par le chef quand on lui dit ce qu'on a.
 */
object Fridge {
    private const val KEY = "fridge"
    const val PIECE = "pièce"
    val UNITS = listOf(PIECE, "g", "kg", "ml", "L", "boîte", "paquet", "bouteille")
    val AISLES = listOf("Fruits et légumes", "Viandes et poissons", "Produits frais", "Épicerie", "Surgelés", "Boulangerie", "Boissons", "Autres")

    private val _items = MutableStateFlow<List<FridgeItem>>(emptyList())
    val items: StateFlow<List<FridgeItem>> = _items
    private var loaded = false

    @Synchronized
    fun load(): List<FridgeItem> {
        if (!loaded) {
            _items.value = runCatching {
                val a = JSONObject(Repo.getExtra(KEY) ?: "{}").optJSONArray("items") ?: JSONArray()
                (0 until a.length()).mapNotNull { i ->
                    val o = a.getJSONObject(i)
                    FridgeItem(
                        o.optString("n").ifBlank { return@mapNotNull null }, o.optDouble("q", 1.0),
                        o.optString("u", PIECE), o.optString("r", "Autres"), o.optLong("t", System.currentTimeMillis())
                    )
                }
            }.getOrDefault(emptyList())
            loaded = true
        }
        return _items.value
    }

    /** À rappeler après une restauration de sauvegarde ou un effacement des données. */
    @Synchronized
    fun reload() { loaded = false; load() }

    @Synchronized
    private fun save(list: List<FridgeItem>) {
        val kept = list.filter { it.qty > 0.0 }.take(200)
        _items.value = kept
        Repo.putExtra(KEY, JSONObject().put("items", JSONArray().apply {
            kept.forEach { put(JSONObject().put("n", it.name).put("q", it.qty).put("u", it.unit).put("r", it.aisle).put("t", it.addedAt)) }
        }).toString())
    }

    private fun key(name: String) = name.trim().lowercase(Locale.FRENCH)

    fun cleanUnit(u: String): String = UNITS.firstOrNull { it.equals(u.trim(), ignoreCase = true) } ?: when (u.trim().lowercase(Locale.FRENCH)) {
        "pièces", "piece", "pieces", "unité", "unités", "u", "" -> PIECE
        "gr", "grammes", "gramme" -> "g"
        "l", "litre", "litres" -> "L"
        "cl" -> "ml"
        else -> PIECE
    }

    /** Nom affiché d'une unité (choix dans l'app). */
    fun unitLabel(u: String): String = when (u) {
        PIECE -> t("pièce")
        "boîte" -> t("boîte")
        "paquet" -> t("paquet")
        "bouteille" -> t("bouteille")
        else -> u
    }

    /** Nom affiché d'un rayon. */
    fun aisleLabel(a: String): String = when (a) {
        "Fruits et légumes" -> t("Fruits et légumes")
        "Viandes et poissons" -> t("Viandes et poissons")
        "Produits frais" -> t("Produits frais")
        "Épicerie" -> t("Épicerie")
        "Surgelés" -> t("Surgelés")
        "Boulangerie" -> t("Boulangerie")
        "Boissons" -> t("Boissons")
        else -> t("Autres")
    }

    fun cleanAisle(a: String): String = AISLES.firstOrNull { it.equals(a.trim(), ignoreCase = true) } ?: "Autres"

    /** Ajoute (ou complète un article du même nom et de la même unité). */
    @Synchronized
    fun add(more: List<FridgeItem>) {
        val cur = load().toMutableList()
        for (m in more) {
            if (m.name.isBlank() || m.qty <= 0.0) continue
            val i = cur.indexOfFirst { key(it.name) == key(m.name) && it.unit == m.unit }
            if (i >= 0) cur[i] = cur[i].copy(qty = (cur[i].qty + m.qty).coerceAtMost(100_000.0))
            else cur += m.copy(name = m.name.trim().take(60), qty = m.qty.coerceAtMost(100_000.0))
        }
        save(cur)
    }

    @Synchronized
    fun setQty(item: FridgeItem, qty: Double) =
        save(load().map { if (it.name == item.name && it.unit == item.unit) it.copy(qty = qty.coerceIn(0.0, 100_000.0)) else it })

    @Synchronized
    fun delete(item: FridgeItem) = save(load().filterNot { it.name == item.name && it.unit == item.unit })

    /**
     * Retire des quantités (repas mangé, chef). Renvoie l'état d'avant pour pouvoir annuler.
     * Un article inconnu est ignoré ; une quantité qui tombe à 0 fait disparaître l'article.
     */
    @Synchronized
    fun remove(used: List<Pair<String, Double>>): List<FridgeItem> {
        val before = load()
        var cur = before
        for ((name, q) in used) {
            if (q <= 0.0) continue
            cur = cur.map { if (key(it.name) == key(name)) it.copy(qty = (it.qty - q).coerceAtLeast(0.0)) else it }
        }
        save(cur)
        return before
    }

    @Synchronized
    fun restore(list: List<FridgeItem>) = save(list)

    /** Dernier retrait (texte affiché, état d'avant) : l'accueil propose « Annuler » tant qu'on ne l'a pas fermé. */
    val lastRemoval = MutableStateFlow<Pair<String, List<FridgeItem>>?>(null)

    fun removeWithUndo(used: List<Pair<String, Double>>) {
        if (used.isEmpty()) return
        val before = remove(used)
        val text = used.joinToString(", ") { (n, q) ->
            val unit = before.firstOrNull { it.name.equals(n, ignoreCase = true) }?.unit ?: PIECE
            FridgeItem(n, q, unit, "").qtyText().let { if (unit == PIECE) "$it × $n" else "$it $n" }
        }
        lastRemoval.value = text to before
    }

    fun undoLastRemoval() {
        lastRemoval.value?.let { restore(it.second) }
        lastRemoval.value = null
    }

    /** Pour l'IA : « Œufs (6 pièce) ; Riz (500 g) ; … » (vide si le frigo est vide). */
    fun promptList(): String = load().joinToString(" ; ") { "${it.name} (${it.qty.let { q -> if (q % 1.0 == 0.0) q.toLong().toString() else "%.1f".format(Locale.US, q) }} ${it.unit})" }
}
