package com.goodlife.app.net

import com.goodlife.app.i18n.t

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class FoodProduct(
    val barcode: String,
    val name: String,
    val brand: String,
    val kcal100: Double,
    val protein100: Double,
    val carbs100: Double,
    val fat100: Double,
    val servingGrams: Double?,
    val ingredients: String = ""   // liste d'ingrédients de l'emballage (pour le Nutridex), vide si inconnue
)

/**
 * Open Food Facts : base de données ouverte et collaborative (association française), sans clé ni compte.
 * Seul le numéro du code-barres est envoyé.
 */
object OpenFoodFacts {
    private val BARCODE = Regex("^\\d{8,14}$")

    suspend fun product(barcode: String): FoodProduct? = withContext(Dispatchers.IO) {
        if (!BARCODE.matches(barcode)) return@withContext null
        val (code, body) = httpGet(
            "https://world.openfoodfacts.org/api/v2/product/$barcode?fields=product_name,product_name_fr,brands,nutriments,serving_quantity,ingredients_text_fr,ingredients_text"
        )
        if (code == 404) return@withContext null
        if (code !in 200..299) throw java.io.IOException(t("Open Food Facts indisponible (%1\$s).", code))
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return@withContext null
        if (root.optInt("status", 0) != 1) return@withContext null
        val p = root.optJSONObject("product") ?: return@withContext null
        val n = p.optJSONObject("nutriments") ?: JSONObject()
        var kcal = n.optDouble("energy-kcal_100g", Double.NaN)
        if (kcal.isNaN()) kcal = n.optDouble("energy_100g", Double.NaN) / 4.184
        if (kcal.isNaN()) return@withContext null
        FoodProduct(
            barcode = barcode,
            name = p.optString("product_name_fr").ifBlank { p.optString("product_name") }.ifBlank { t("Produit %1\$s", barcode) },
            brand = p.optString("brands").split(",").firstOrNull()?.trim().orEmpty(),
            kcal100 = kcal,
            protein100 = n.optDouble("proteins_100g", 0.0).takeUnless { it.isNaN() } ?: 0.0,
            carbs100 = n.optDouble("carbohydrates_100g", 0.0).takeUnless { it.isNaN() } ?: 0.0,
            fat100 = n.optDouble("fat_100g", 0.0).takeUnless { it.isNaN() } ?: 0.0,
            servingGrams = p.optDouble("serving_quantity", Double.NaN).takeUnless { it.isNaN() || it <= 0 },
            ingredients = p.optString("ingredients_text_fr").ifBlank { p.optString("ingredients_text") }.take(2000)
        )
    }
}
