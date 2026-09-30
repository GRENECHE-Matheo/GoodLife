package com.goodlife.app.food

import android.content.Context
import java.text.Normalizer

/** Un aliment de la table Ciqual : valeurs pour 100 g. */
data class CiqualFood(
    val name: String,
    val kcal: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val group: String
) {
    internal val key: String = Ciqual.normalize(name)
}

/**
 * Table de composition nutritionnelle Ciqual 2025 (Anses), intégrée à l'app : recherche hors ligne,
 * sans IA ni clé. Licence Ouverte Etalab 2.0 — source à citer : « Anses, table Ciqual 2025 ».
 */
object Ciqual {
    const val SOURCE = "Valeurs : table Ciqual 2025 de l'Anses (Licence Ouverte)."

    @Volatile private var foods: List<CiqualFood>? = null

    fun normalize(s: String): String =
        Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace("œ", "oe")
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()

    /** Chargée une seule fois (≈ 300 Ko), à appeler hors du fil principal. */
    fun load(context: Context): List<CiqualFood> = foods ?: synchronized(this) {
        foods ?: context.assets.open("ciqual.tsv").bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.filter { it.isNotBlank() && !it.startsWith("#") }.mapNotNull { line ->
                val f = line.split('\t')
                if (f.size < 6) return@mapNotNull null
                CiqualFood(
                    name = f[0],
                    kcal = f[1].toDoubleOrNull() ?: return@mapNotNull null,
                    protein = f[2].toDoubleOrNull() ?: 0.0,
                    carbs = f[3].toDoubleOrNull() ?: 0.0,
                    fat = f[4].toDoubleOrNull() ?: 0.0,
                    group = f[5]
                )
            }.toList()
        }.also { foods = it }
    }

    /** Tous les mots tapés doivent être présents ; les noms qui commencent par le premier mot et les plus courts d'abord. */
    fun search(context: Context, query: String, limit: Int = 30): List<CiqualFood> {
        val words = normalize(query).split(' ').filter { it.length >= 2 }
        if (words.isEmpty()) return emptyList()
        return load(context)
            .filter { f -> words.all { w -> f.key.contains(w) } }
            .sortedWith(
                compareByDescending<CiqualFood> { it.key.startsWith(words.first()) }
                    .thenByDescending { f -> f.key.split(' ').any { it == words.first() } }
                    .thenBy { it.name.length }
            )
            .take(limit)
    }
}
