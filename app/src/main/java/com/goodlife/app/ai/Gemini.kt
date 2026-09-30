package com.goodlife.app.ai

import android.util.Base64
import com.goodlife.app.data.FoodAnalysis
import com.goodlife.app.data.Meal
import com.goodlife.app.data.MealSuggestion
import com.goodlife.app.data.Profile
import com.goodlife.app.data.mapObjects
import com.goodlife.app.data.strings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

class AiException(message: String) : Exception(message)

/**
 * Client minimal de l'API Gemini (Google AI Studio, offre gratuite).
 * La clé est fournie par l'utilisateur et stockée chiffrée sur le téléphone.
 * Seules la photo du repas et les infos nécessaires sont envoyées, rien d'autre.
 */
class Gemini(private val apiKey: String, private val model: String) {

    private suspend fun call(prompt: String, jpeg: ByteArray?): JSONObject = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) throw AiException("Ajoute ta clé API Gemini dans Profil › Clé IA.")
        val models = listOf(model) + FALLBACK_MODELS.filter { it != model }
        var lastError = "Erreur inconnue"
        for (m in models) {
            val (code, body) = post(m, prompt, jpeg)
            when {
                code in 200..299 -> return@withContext parse(body)
                code == 404 -> { lastError = "Modèle $m indisponible"; continue }
                code == 429 -> throw AiException("Quota gratuit atteint pour le moment. Réessaie dans une minute.")
                code == 400 || code == 401 || code == 403 ->
                    throw AiException("Clé API refusée (${errorMessage(body)}). Vérifie-la dans Profil › Clé IA.")
                else -> { lastError = "Erreur serveur $code : ${errorMessage(body)}"; continue }
            }
        }
        throw AiException(lastError)
    }

    private fun post(model: String, prompt: String, jpeg: ByteArray?): Pair<Int, String> {
        val parts = JSONArray().put(JSONObject().put("text", prompt))
        if (jpeg != null) {
            parts.put(
                JSONObject().put(
                    "inline_data", JSONObject()
                        .put("mime_type", "image/jpeg")
                        .put("data", Base64.encodeToString(jpeg, Base64.NO_WRAP))
                )
            )
        }
        val body = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", parts)))
            .put("generationConfig", JSONObject()
                .put("responseMimeType", "application/json")
                .put("temperature", 0.2))

        val conn = (URL("$BASE/$model:generateContent").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 90_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("x-goog-api-key", apiKey)
        }
        return try {
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            code to (stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: "")
        } catch (e: java.io.IOException) {
            throw AiException("Pas de connexion internet (${e.message ?: "réseau"}).")
        } finally {
            conn.disconnect()
        }
    }

    private fun errorMessage(body: String): String = runCatching {
        JSONObject(body).getJSONObject("error").optString("message")
    }.getOrNull()?.take(160) ?: "réponse invalide"

    private fun parse(body: String): JSONObject {
        val root = JSONObject(body)
        val candidates = root.optJSONArray("candidates")
            ?: throw AiException("L'IA n'a pas répondu (contenu bloqué ?).")
        val parts = candidates.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
            ?: throw AiException("Réponse IA vide.")
        val text = buildString {
            for (i in 0 until parts.length()) {
                val p = parts.optJSONObject(i) ?: continue
                if (p.optBoolean("thought", false)) continue
                append(p.optString("text"))
            }
        }
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) throw AiException("Réponse IA illisible.")
        return runCatching { JSONObject(text.substring(start, end + 1)) }
            .getOrElse { throw AiException("Réponse IA illisible.") }
    }

    // ------------------------------------------------------------------

    suspend fun analyzeFood(jpeg: ByteArray, profile: Profile?): FoodAnalysis {
        val allergies = profile?.allergies?.takeIf { it.isNotBlank() } ?: "aucune connue"
        val prompt = """
            Tu es un nutritionniste expert. Analyse la photo de nourriture.
            Identifie chaque aliment, estime les portions visibles (en grammes) et les calories.
            Allergies de l'utilisateur : $allergies. Signale tout aliment qui pourrait en contenir.
            Si l'image ne contient pas de nourriture, mets "kcal": 0 et "plat": "Aucun aliment détecté".
            Réponds UNIQUEMENT en JSON, en français, avec exactement ce format :
            {"plat": "nom court du plat",
             "aliments": [{"nom": "...", "quantite_g": 0, "kcal": 0}],
             "kcal": 0, "proteines_g": 0, "glucides_g": 0, "lipides_g": 0,
             "allergenes_detectes": ["..."], "confiance": 0.0,
             "conseil": "une phrase courte et bienveillante"}
        """.trimIndent()
        val o = call(prompt, jpeg)
        val items = o.optJSONArray("aliments")?.mapObjects { a ->
            val q = a.optDouble("quantite_g", 0.0).roundToInt()
            val k = a.optDouble("kcal", 0.0).roundToInt()
            "${a.optString("nom")} · ${q} g · $k kcal"
        } ?: emptyList()
        return FoodAnalysis(
            dish = o.optString("plat", "Repas").ifBlank { "Repas" },
            kcal = o.optDouble("kcal", 0.0).roundToInt().coerceAtLeast(0),
            proteinG = o.optDouble("proteines_g", 0.0),
            carbsG = o.optDouble("glucides_g", 0.0),
            fatG = o.optDouble("lipides_g", 0.0),
            items = items,
            allergens = o.optJSONArray("allergenes_detectes")?.strings() ?: emptyList(),
            confidence = o.optDouble("confiance", 0.0),
            advice = o.optString("conseil")
        )
    }

    suspend fun recommendTarget(p: Profile): Profile {
        val base = Nutrition.formulaTarget(p)
        val prompt = """
            Tu es un diététicien. Détermine l'apport calorique journalier recommandé et la répartition
            des macronutriments pour cette personne. Sois prudent et réaliste (perte max ~0,5 kg/semaine).
            Âge : ${p.age} ans · Sexe : ${p.sex.label} · Poids : ${p.weightKg} kg · Taille : ${p.heightCm} cm
            Activité : ${p.activity.label} · Objectif : ${p.goal.label}
            Habitudes alimentaires : ${p.habits.ifBlank { "non précisées" }}
            Allergies : ${p.allergies.ifBlank { "aucune" }}
            Référence calculée (Mifflin-St Jeor) : ${base.targetKcal} kcal.
            Réponds UNIQUEMENT en JSON :
            {"kcal": 0, "proteines_g": 0, "glucides_g": 0, "lipides_g": 0,
             "explication": "2 à 3 phrases simples en français"}
        """.trimIndent()
        val o = call(prompt, null)
        val kcal = Nutrition.clampTarget(p, o.optDouble("kcal", base.targetKcal.toDouble()).roundToInt())
        return p.copy(
            targetKcal = kcal,
            proteinG = o.optDouble("proteines_g", base.proteinG.toDouble()).roundToInt(),
            carbsG = o.optDouble("glucides_g", base.carbsG.toDouble()).roundToInt(),
            fatG = o.optDouble("lipides_g", base.fatG.toDouble()).roundToInt(),
            targetSource = "ia",
            targetExplanation = o.optString("explication").ifBlank { base.targetExplanation }
        )
    }

    suspend fun suggestMeals(p: Profile, today: List<Meal>): List<MealSuggestion> {
        val eaten = today.sumOf { it.kcal }
        val remaining = (p.targetKcal - eaten).coerceAtLeast(0)
        val list = today.joinToString("; ") { "${it.name} (${it.kcal} kcal)" }.ifBlank { "rien encore" }
        val prompt = """
            Tu es un coach nutrition. Propose 3 idées de repas adaptées pour la suite de la journée.
            Objectif : ${p.goal.label}, cible ${p.targetKcal} kcal/jour, déjà consommé $eaten kcal, reste $remaining kcal.
            Mangé aujourd'hui : $list
            Habitudes alimentaires : ${p.habits.ifBlank { "non précisées" }}
            ALLERGIES (à exclure absolument) : ${p.allergies.ifBlank { "aucune" }}
            Plats simples, de saison, faciles à préparer.
            Réponds UNIQUEMENT en JSON :
            {"repas": [{"nom": "...", "moment": "déjeuner/dîner/collation", "kcal": 0,
                        "description": "ingrédients et préparation en 1 phrase",
                        "pourquoi": "pourquoi c'est adapté, 1 phrase"}]}
        """.trimIndent()
        val o = call(prompt, null)
        return o.optJSONArray("repas")?.mapObjects {
            MealSuggestion(
                name = it.optString("nom"),
                moment = it.optString("moment"),
                kcal = it.optDouble("kcal", 0.0).roundToInt(),
                description = it.optString("description"),
                why = it.optString("pourquoi")
            )
        } ?: emptyList()
    }

    companion object {
        private const val BASE = "https://generativelanguage.googleapis.com/v1beta/models"
        /** Si un modèle n'existe plus chez Google, on essaie automatiquement les suivants. */
        val FALLBACK_MODELS = listOf("gemini-3.5-flash-lite", "gemini-2.5-flash-lite", "gemini-2.5-flash")

        /** Modèles proposés dans les paramètres : id → description. */
        val KNOWN_MODELS = listOf(
            "gemini-3.5-flash-lite" to "Rapide, gros quota gratuit (recommandé)",
            "gemini-3.8-flash" to "Plus précis, quota gratuit plus petit",
            "gemini-2.5-flash" to "Ancien modèle, en secours"
        )
    }
}
