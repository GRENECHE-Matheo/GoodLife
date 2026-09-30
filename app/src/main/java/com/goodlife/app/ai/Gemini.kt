package com.goodlife.app.ai

import android.util.Base64
import com.goodlife.app.data.Repo
import com.goodlife.app.data.FoodAnalysis
import com.goodlife.app.data.Meal
import com.goodlife.app.data.MealSuggestion
import com.goodlife.app.data.MealSlot
import com.goodlife.app.data.PlannedMeal
import com.goodlife.app.data.Profile
import com.goodlife.app.data.Recipe
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
 * Client minimal de l'API Gemini (Google AI Studio), avec la clé personnelle de chaque utilisateur.
 * La clé est fournie par l'utilisateur et stockée chiffrée sur le téléphone.
 * Seules la photo du repas et les infos nécessaires sont envoyées, rien d'autre.
 */
class Gemini(private val apiKey: String, private val model: String) {

    /** Appel direct à Google avec la clé personnelle de l'utilisateur (stockée chiffrée sur le téléphone). */
    private suspend fun call(prompt: String, jpeg: ByteArray?): JSONObject = withContext(Dispatchers.IO) {
        // Garde-fou RGPD : aucune donnée n'est envoyée sans consentement explicite.
        if (!Repo.settings.value.aiEnabled) {
            throw AiException("Les fonctions IA sont désactivées. Tu peux les activer dans Paramètres › Intelligence artificielle.")
        }
        if ((Repo.profile.value?.age ?: 0) < 18) {
            throw AiException("Les fonctions IA (Google Gemini) sont réservées aux personnes de 18 ans et plus.")
        }
        if (apiKey.isBlank()) {
            throw AiException("Ajoute ta clé API Gemini dans Paramètres › Intelligence artificielle.")
        }
        callDirect(prompt, jpeg)
    }

    private fun callDirect(prompt: String, jpeg: ByteArray?): JSONObject {
        val models = listOf(model) + FALLBACK_MODELS.filter { it != model }
        var lastError = "Erreur inconnue"
        for (m in models) {
            val (code, body) = post(m, prompt, jpeg)
            when {
                code in 200..299 -> return parse(body)
                code == 404 -> { lastError = "Modèle $m indisponible"; continue }
                code == 429 -> throw AiException("Quota de ta clé atteint pour le moment. Réessaie dans une minute.")
                code == 400 || code == 401 || code == 403 ->
                    throw AiException("Clé API refusée (${errorMessage(body)}). Vérifie-la dans Paramètres.")
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
        return extractJson(text)
    }

    private fun extractJson(text: String): JSONObject {
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
            Tu aides à estimer les calories d'un repas (usage bien-être, pas d'avis médical). Analyse la photo de nourriture.
            Identifie chaque aliment, estime les portions visibles (en grammes) et les calories.
            Allergies de l'utilisateur : $allergies. Signale tout aliment qui pourrait en contenir.
            Si l'image ne contient pas de nourriture, mets "kcal": 0 et "plat": "Aucun aliment détecté".
            Catalogue Nutridex (id=nom) : ${com.goodlife.app.dex.Nutridex.promptList()}
            Dans "dex", liste au plus 4 id du catalogue CLAIREMENT visibles sur la photo (le plat lui-même s'il y est,
            et ses ingrédients bien reconnaissables). Liste vide si rien ne correspond ou si ce n'est pas une vraie photo de nourriture.
            Réponds UNIQUEMENT en JSON, en français, avec exactement ce format :
            {"plat": "nom court du plat",
             "aliments": [{"nom": "...", "quantite_g": 0, "kcal": 0}],
             "kcal": 0, "proteines_g": 0, "glucides_g": 0, "lipides_g": 0,
             "allergenes_detectes": ["..."], "confiance": 0.0,
             "conseil": "une phrase courte et bienveillante",
             "dex": ["id"]}
        """.trimIndent()
        val o = call(prompt, jpeg)
        val items = o.optJSONArray("aliments")?.mapObjects { a ->
            val q = a.optDouble("quantite_g", 0.0).roundToInt()
            val k = a.optDouble("kcal", 0.0).roundToInt()
            "${a.optString("nom")} · ${q} g · $k kcal"
        } ?: emptyList()
        return FoodAnalysis(
            dish = o.optString("plat", "Repas").ifBlank { "Repas" },
            kcal = o.optDouble("kcal", 0.0).roundToInt().coerceIn(0, Repo.MAX_MEAL_KCAL),
            proteinG = o.optDouble("proteines_g", 0.0).coerceIn(0.0, 1000.0),
            carbsG = o.optDouble("glucides_g", 0.0).coerceIn(0.0, 1000.0),
            fatG = o.optDouble("lipides_g", 0.0).coerceIn(0.0, 1000.0),
            items = items,
            allergens = o.optJSONArray("allergenes_detectes")?.strings() ?: emptyList(),
            confidence = o.optDouble("confiance", 0.0),
            advice = o.optString("conseil"),
            dexIds = o.optJSONArray("dex")?.strings()?.filter { com.goodlife.app.dex.Nutridex.byId(it) != null }?.take(4) ?: emptyList()
        )
    }

    suspend fun recommendTarget(profile: Profile, stepsAverage: Int = 0): Profile {
        // Même garde-fou santé que le calcul hors-ligne (pas de perte de poids pour mineurs / IMC < 18,5)
        val base = Nutrition.formulaTarget(profile)
        val p = profile.copy(goal = base.goal)
        val prompt = """
            Tu es un coach bien-être (pas un professionnel de santé, pas d'avis médical). Propose un apport
            calorique journalier indicatif et la répartition des macronutriments pour cette personne.
            Sois prudent et réaliste (perte max ~0,5 kg/semaine). Si la situation semble nécessiter un suivi
            (poids très bas ou très élevé, maladie), dis-le dans l'explication et conseille un médecin.
            Âge : ${p.age} ans · Sexe : ${p.sex.label} · Poids : ${p.weightKg} kg · Taille : ${p.heightCm} cm
            Activité : ${p.activity.label} · Objectif : ${p.goal.label}
            Habitudes alimentaires : ${p.habits.ifBlank { "non précisées" }}
            Allergies : ${p.allergies.ifBlank { "aucune" }}
            Pas par jour (moyenne 7 jours) : ${stepsAverage.takeIf { it > 0 }?.toString() ?: "inconnue"}
            Référence calculée (Mifflin-St Jeor) : ${base.targetKcal} kcal.
            Réponds UNIQUEMENT en JSON :
            {"kcal": 0, "proteines_g": 0, "glucides_g": 0, "lipides_g": 0,
             "explication": "2 à 3 phrases simples en français"}
        """.trimIndent()
        val o = call(prompt, null)
        val kcal = Nutrition.clampTarget(p, o.optDouble("kcal", base.targetKcal.toDouble()).roundToInt())
        return p.copy(
            targetKcal = kcal,
            proteinG = o.optDouble("proteines_g", base.proteinG.toDouble()).roundToInt().coerceIn(0, 500),
            carbsG = o.optDouble("glucides_g", base.carbsG.toDouble()).roundToInt().coerceIn(0, 900),
            fatG = o.optDouble("lipides_g", base.fatG.toDouble()).roundToInt().coerceIn(0, 400),
            targetSource = "ia",
            targetExplanation = o.optString("explication").ifBlank { base.targetExplanation }
        )
    }

    /**
     * Idées de repas. [slot] : un repas précis (null = n'importe quel moment de la journée).
     * [avoid] : plats déjà proposés, pour que « Régénérer » donne vraiment d'autres idées.
     */
    /**
     * Planning de la semaine avec budget. Les prix sont des estimations (prix moyens en supermarché en France),
     * pas des prix relevés en direct. Renvoie les repas (jour 0 = premier jour) et un court conseil.
     */
    suspend fun planWeek(
        p: Profile,
        startDate: String,
        budgetEur: Int,
        people: Int,
        slots: List<MealSlot>
    ): Pair<List<PlannedMeal>, String> {
        val slotNames = slots.joinToString(", ") { it.label.lowercase() }
        val prompt = """
            Tu es un coach nutrition qui fait les courses en France. Prépare un planning de repas sur 7 jours
            à partir du $startDate (jour 0) pour $people personne(s).
            Repas à prévoir chaque jour : $slotNames.
            Budget total de la semaine pour ces repas : $budgetEur € pour tout le foyer. Rapproche-toi le plus
            possible de ce budget sans le dépasser (ni beaucoup moins : utilise-le intelligemment).
            Estime le coût de chaque repas pour tout le foyer avec les prix moyens actuels en supermarché en France
            (marques distributeur, produits de saison), et reste réaliste.
            Objectif de la personne : ${p.goal.label}, environ ${p.targetKcal} kcal par jour pour elle.
            Habitudes alimentaires : ${p.habits.ifBlank { "non précisées" }}
            ALLERGIES (à exclure absolument) : ${p.allergies.ifBlank { "aucune" }}
            Varie les plats, réutilise les restes et les mêmes ingrédients dans la semaine pour limiter le coût.
            Réponds UNIQUEMENT en JSON :
            {"repas": [{"jour": 0, "moment": "déjeuner", "nom": "nom court (max 5 mots)",
                        "kcal": 0, "cout_eur": 0.0, "description": "ingrédients principaux en 1 phrase"}],
             "total_eur": 0.0, "conseil": "1 phrase pour les courses"}
            "kcal" = pour une portion (une personne). "cout_eur" = pour tout le foyer.
        """.trimIndent()
        val o = call(prompt, null)
        val start = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).parse(startDate)!!
        val meals = o.optJSONArray("repas")?.mapObjects { m ->
            val day = m.optInt("jour", -1)
            if (day !in 0..6) return@mapObjects null
            val date = java.util.Calendar.getInstance().apply { time = start; add(java.util.Calendar.DAY_OF_YEAR, day) }
            PlannedMeal(
                id = System.nanoTime(),
                date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(date.time),
                slot = MealSlot.guess(m.optString("moment")),
                name = m.optString("nom").take(60).ifBlank { return@mapObjects null },
                kcal = m.optDouble("kcal", 0.0).roundToInt().coerceIn(0, Repo.MAX_MEAL_KCAL),
                description = m.optString("description").take(300),
                costEur = m.optDouble("cout_eur", 0.0).takeUnless { it.isNaN() }?.coerceIn(0.0, 500.0) ?: 0.0
            )
        }?.filterNotNull() ?: emptyList()
        if (meals.isEmpty()) throw AiException("L'IA n'a pas proposé de planning. Réessaie.")
        return meals to o.optString("conseil")
    }

    /** Objectif de pas quotidien progressif et atteignable (bien-être, pas d'avis médical). */
    suspend fun recommendSteps(p: Profile, average: Int): Pair<Int, String> {
        val prompt = """
            Tu es un coach bien-être (pas un professionnel de santé, pas d'avis médical). Propose un objectif
            de pas quotidien motivant mais atteignable, qui fait progresser doucement (environ +10 % par rapport
            à l'habitude actuelle, jamais un saut brutal).
            Âge : ${p.age} ans · Sexe : ${p.sex.label} · Activité : ${p.activity.label} · Objectif : ${p.goal.label}
            Moyenne actuelle : ${if (average > 0) "$average pas/jour" else "inconnue"}
            Réponds UNIQUEMENT en JSON : {"pas": 0, "explication": "1 à 2 phrases simples en français"}
        """.trimIndent()
        val o = call(prompt, null)
        val steps = o.optDouble("pas", 0.0).roundToInt().coerceIn(3000, 15_000)
        return steps to o.optString("explication")
    }

    suspend fun suggestMeals(p: Profile, today: List<Meal>, slot: MealSlot? = null, avoid: List<String> = emptyList()): List<MealSuggestion> {
        val eaten = today.sumOf { it.kcal }
        val remaining = (p.targetKcal - eaten).coerceAtLeast(0)
        val list = today.joinToString("; ") { "${it.name} (${it.kcal} kcal)" }.ifBlank { "rien encore" }
        val what = if (slot == null) "4 idées de repas simples pour la suite de la journée ou demain"
                   else "4 idées de ${slot.label.lowercase()} simples (toutes pour ce repas, « moment » = « ${slot.label.lowercase()} »)"
        val avoidLine = if (avoid.isEmpty()) "" else "Ne propose PAS ces plats déjà suggérés : ${avoid.joinToString(", ")}. Varie vraiment."
        val prompt = """
            Tu es un coach nutrition. Propose $what.
            $avoidLine
            Objectif : ${p.goal.label}, cible ${p.targetKcal} kcal/jour, déjà consommé $eaten kcal, reste $remaining kcal.
            Mangé aujourd'hui : $list
            Habitudes alimentaires : ${p.habits.ifBlank { "non précisées" }}
            ALLERGIES (à exclure absolument) : ${p.allergies.ifBlank { "aucune" }}
            Plats de saison, faciles, variés.
            Réponds UNIQUEMENT en JSON :
            {"repas": [{"nom": "nom court (max 5 mots)",
                        "moment": "petit-déjeuner/déjeuner/collation/dîner",
                        "kcal": 0,
                        "resume": "max 8 mots",
                        "description": "ingrédients principaux et préparation en 2 phrases",
                        "pourquoi": "pourquoi c'est adapté, 1 phrase"}]}
        """.trimIndent()
        val o = call(prompt, null)
        return o.optJSONArray("repas")?.mapObjects {
            MealSuggestion(
                name = it.optString("nom"),
                moment = it.optString("moment"),
                kcal = it.optDouble("kcal", 0.0).roundToInt(),
                summary = it.optString("resume"),
                description = it.optString("description"),
                why = it.optString("pourquoi")
            )
        } ?: emptyList()
    }

    suspend fun recipe(p: Profile?, name: String, description: String, kcal: Int): Recipe {
        val prompt = """
            Donne une recette simple et réaliste pour : "$name" (${kcal} kcal par portion environ).
            Contexte : $description
            ALLERGIES (à exclure absolument) : ${p?.allergies?.ifBlank { "aucune" } ?: "aucune"}
            Habitudes : ${p?.habits?.ifBlank { "non précisées" } ?: "non précisées"}
            Quantités précises en grammes ou unités, étapes courtes, en français.
            Réponds UNIQUEMENT en JSON :
            {"portions": 1, "minutes": 0, "kcal_portion": 0,
             "ingredients": ["quantité + ingrédient"],
             "etapes": ["étape courte"],
             "astuce": "une astuce courte"}
        """.trimIndent()
        val o = call(prompt, null)
        return Recipe(
            servings = o.optInt("portions", 1).coerceAtLeast(1),
            minutes = o.optInt("minutes", 0),
            kcalPerServing = o.optDouble("kcal_portion", kcal.toDouble()).roundToInt(),
            ingredients = o.optJSONArray("ingredients")?.strings() ?: emptyList(),
            steps = o.optJSONArray("etapes")?.strings() ?: emptyList(),
            tip = o.optString("astuce")
        )
    }

    companion object {
        private const val BASE = "https://generativelanguage.googleapis.com/v1beta/models"
        /** Si un modèle n'existe plus chez Google, on essaie automatiquement les suivants. */
        val FALLBACK_MODELS = listOf("gemini-3.5-flash-lite", "gemini-2.5-flash-lite", "gemini-2.5-flash")

        /** Modèles proposés dans les paramètres : id → description. */
        val KNOWN_MODELS = listOf(
            "gemini-3.5-flash-lite" to "Rapide et économique (recommandé)",
            "gemini-3.8-flash" to "Plus précis, quota plus petit",
            "gemini-2.5-flash" to "Ancien modèle, en secours"
        )
    }
}
