package com.goodlife.app.ai

import com.goodlife.app.i18n.t

import android.util.Base64
import com.goodlife.app.data.Repo
import com.goodlife.app.data.FoodAnalysis
import com.goodlife.app.data.Meal
import com.goodlife.app.data.MealSuggestion
import com.goodlife.app.data.MealSlot
import com.goodlife.app.data.PlannedMeal
import com.goodlife.app.data.SportProgram
import com.goodlife.app.data.SportSession
import com.goodlife.app.data.Exercise
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

/** Nom affiché d'un repas : nombre et aliment (« 2× Banane »). Les calories affichées à côté sont le total. */
fun mealName(count: Int, name: String): String = t("%1\$s× %2\$s", count.coerceAtLeast(1), name.trim())

/** Un message de conversation avec l'IA. */
/** Message d'une conversation ; [image] : photo envoyée par la personne (JPEG réduit), jamais enregistrée. */
data class ChatMessage(val fromUser: Boolean, val text: String, val image: ByteArray? = null)

/** Article de la liste de courses. */
data class ShopItem(val name: String, val qty: String, val aisle: String, val checked: Boolean = false)

/** Idée de recette à partir de ce qu'il y a dans le frigo. */
data class FridgeIdea(
    val name: String, val moment: String, val kcal: Int, val minutes: Int,
    val uses: List<String>, val missing: List<String>, val steps: List<String>
)

data class FridgeResult(val seen: List<String>, val ideas: List<FridgeIdea>, val tip: String)

/** Repas proposé par le coach, ajouté au planning seulement si la personne appuie sur « Ajouter ». */
data class CoachMeal(val date: String, val slot: MealSlot, val name: String, val kcal: Int, val description: String)

data class CoachReply(val text: String, val meals: List<CoachMeal>, val shopping: List<ShopItem> = emptyList())

/**
 * Client minimal de l'API Gemini (Google AI Studio), avec la clé personnelle de chaque utilisateur.
 * La clé est fournie par l'utilisateur et stockée chiffrée sur le téléphone.
 * Seules la photo du repas et les infos nécessaires sont envoyées, rien d'autre.
 */
class Gemini(private val apiKey: String, private val model: String) {

    /** Garde-fous communs : consentement, âge, clé. */
    private fun guard() {
        // Garde-fou RGPD : aucune donnée n'est envoyée sans consentement explicite.
        if (!Repo.settings.value.aiEnabled) {
            throw AiException(t("Les fonctions IA sont désactivées. Tu peux les activer dans Paramètres › Intelligence artificielle."))
        }
        if ((Repo.profile.value?.age ?: 0) < 18) {
            throw AiException(t("Les fonctions IA (Google Gemini) sont réservées aux personnes de 18 ans et plus."))
        }
        if (apiKey.isBlank()) {
            throw AiException(t("Ajoute ta clé API Gemini dans Paramètres › Intelligence artificielle."))
        }
    }

    /** Demande qui attend une réponse JSON (analyse, objectif, recettes…). */
    private suspend fun call(prompt: String, jpeg: ByteArray?): JSONObject = withContext(Dispatchers.IO) {
        guard()
        val body = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", parts(prompt, jpeg))))
            .put("generationConfig", JSONObject()
                .put("responseMimeType", "application/json")
                .put("temperature", 0.2))
        extractJson(text(send(body)))
    }

    /**
     * Conversation (texte libre) : [system] donne le contexte et les règles, [image] est jointe au premier
     * message, [history] alterne utilisateur / IA en commençant par l'utilisateur.
     */
    suspend fun chat(system: String, image: ByteArray?, history: List<ChatMessage>): String = withContext(Dispatchers.IO) {
        guard()
        val contents = JSONArray()
        history.forEachIndexed { i, m ->
            contents.put(
                JSONObject().put("role", if (m.fromUser) "user" else "model")
                    .put("parts", parts(m.text, if (i == 0) image else null))
            )
        }
        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            .put("contents", contents)
            .put("generationConfig", JSONObject().put("temperature", 0.5).put("maxOutputTokens", 900))
        text(send(body)).trim().ifBlank { throw AiException(t("L'IA n'a pas répondu. Reformule ta question.")) }
    }

    /**
     * Le coach : conversation dont chaque réponse est un JSON {"reply", "meals"}. Les repas proposés ne sont
     * jamais ajoutés automatiquement : l'app les affiche avec un bouton « Ajouter au planning ».
     */
    suspend fun coach(system: String, history: List<ChatMessage>): CoachReply = withContext(Dispatchers.IO) {
        guard()
        val contents = JSONArray()
        // Les photos ne sont renvoyées que pour les 4 derniers messages (au-delà, une simple mention), pour limiter les envois
        history.forEachIndexed { i, m ->
            val recent = i >= history.size - 4
            val text = if (m.image != null && !recent) m.text + " [photo envoyée plus tôt]" else m.text
            contents.put(JSONObject().put("role", if (m.fromUser) "user" else "model").put("parts", parts(text, if (recent) m.image else null)))
        }
        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            .put("contents", contents)
            .put("generationConfig", JSONObject()
                .put("responseMimeType", "application/json")
                .put("temperature", 0.6)
                .put("maxOutputTokens", 2000))
        val o = extractJson(text(send(body)))
        val reply = o.optString("reply").trim().ifBlank { throw AiException(t("Le chef n'a pas répondu. Reformule ta question.")) }
        val today = com.goodlife.app.data.localDay(0)
        val last = com.goodlife.app.data.localDay(13)
        val meals = o.optJSONArray("meals")?.mapObjects { m ->
            CoachMeal(
                date = m.optString("date"),
                slot = MealSlot.entries.firstOrNull { it.name == m.optString("slot") } ?: MealSlot.guess(m.optString("slot")),
                name = m.optString("name").trim().take(80),
                kcal = m.optDouble("kcal", 0.0).roundToInt().coerceIn(0, 2500),
                description = m.optString("description").trim().take(400)
            )
        }?.filter { it.name.isNotBlank() && it.date.matches(Regex("""\d{4}-\d{2}-\d{2}""")) && it.date in today..last }?.take(21)
            ?: emptyList()
        val shopping = o.optJSONArray("courses")?.mapObjects {
            ShopItem(it.optString("nom").trim().take(60), it.optString("quantite").trim().take(30), it.optString("rayon").trim().ifBlank { t("Autres") }.take(30))
        }?.filter { it.name.isNotBlank() }?.take(80) ?: emptyList()
        CoachReply(reply.take(3000), meals, shopping)
    }

    /**
     * Résumé fidèle d'un article d'organisme public (Anses, Santé publique France). Aucune donnée personnelle
     * n'est envoyée : seulement le titre et le texte public de l'article.
     */
    suspend fun summarizeArticle(source: String, title: String, text: String): List<String> {
        val prompt = """
            Résume fidèlement cet article publié par $source, pour le grand public, en français simple.
            Règles : n'ajoute AUCUNE information absente du texte, ne change pas le sens, garde les chiffres exacts,
            pas de conseil personnel ni médical en plus. S'il s'agit d'une recommandation officielle, dis-le.
            3 à 5 points courts (une phrase chacun).
            Titre : $title
            Texte :
            $text
            Réponds UNIQUEMENT en JSON : {"points": ["..."]}
        """.trimIndent()
        val o = call(prompt, null)
        return o.optJSONArray("points")?.strings()?.map { it.trim().take(300) }?.filter { it.isNotBlank() }?.take(6)
            ?.ifEmpty { null } ?: throw AiException(t("L'IA n'a pas pu résumer cet article."))
    }

    /** Liste de courses regroupée par rayon pour les repas prévus ([people] personnes). */
    suspend fun shoppingList(meals: List<PlannedMeal>, people: Int): List<ShopItem> {
        val lines = meals.joinToString("\n") { m ->
            "- ${m.name}" + (m.recipe?.let { r -> " (recette pour ${r.servings} : ${r.ingredients.joinToString(", ")})" } ?: "") +
                (if (m.description.isNotBlank()) " — ${m.description}" else "")
        }
        val prompt = """
            Fais la liste de courses pour préparer ces repas pour $people personne(s), en France.
            Regroupe les ingrédients identiques et additionne les quantités (quantités réalistes, en g, kg, L ou unités).
            N'ajoute PAS le sel, le poivre, l'huile ni l'eau. Range chaque article dans un rayon parmi :
            "Fruits et légumes", "Viandes et poissons", "Produits frais", "Épicerie", "Surgelés", "Boulangerie", "Autres".
            Repas :
            $lines
            Réponds UNIQUEMENT en JSON : {"articles": [{"nom": "...", "quantite": "...", "rayon": "..."}]}
        """.trimIndent()
        val o = call(prompt, null)
        return o.optJSONArray("articles")?.mapObjects {
            ShopItem(it.optString("nom").trim().take(60), it.optString("quantite").trim().take(30), it.optString("rayon").trim().ifBlank { t("Autres") }.take(30))
        }?.filter { it.name.isNotBlank() }?.take(80) ?: emptyList()
    }

    /** Que cuisiner avec ce qu'il y a dans le frigo (photo et/ou liste écrite). */
    suspend fun fridge(jpeg: ByteArray?, written: String, p: Profile, remainingKcal: Int): FridgeResult {
        val prompt = """
            Tu es un chef bienveillant. ${if (jpeg != null) "Regarde la photo du frigo ou des placards (jointe)." else ""}
            ${if (written.isNotBlank()) "La personne a aussi écrit qu'elle a : $written." else ""}
            1) Liste les ingrédients que tu vois ou qui sont écrits (seulement ceux dont tu es sûr).
            2) Propose 3 recettes simples, avec surtout ces ingrédients (anti-gaspi), pour 1 personne.
               Il lui reste environ $remainingKcal kcal pour aujourd'hui ; objectif : ${p.goal.label}.
               ALLERGIES (à exclure absolument) : ${p.allergies.ifBlank { "aucune" }}. Habitudes : ${p.habits.ifBlank { "non précisées" }}.
               Pour chaque recette : les ingrédients utilisés, ceux qui manquent (le moins possible), et des étapes courtes.
            Réponds UNIQUEMENT en JSON :
            {"vus": ["..."], "recettes": [{"nom": "...", "moment": "déjeuner/dîner/petit-déjeuner/collation", "kcal": 0,
              "minutes": 0, "utilise": ["..."], "manque": ["..."], "etapes": ["..."]}], "conseil": "une phrase"}
        """.trimIndent()
        val o = call(prompt, jpeg)
        return FridgeResult(
            seen = o.optJSONArray("vus")?.strings()?.map { it.take(40) }?.take(40) ?: emptyList(),
            ideas = o.optJSONArray("recettes")?.mapObjects {
                FridgeIdea(
                    name = it.optString("nom").take(60), moment = it.optString("moment"),
                    kcal = it.optDouble("kcal", 0.0).roundToInt().coerceIn(0, 2500), minutes = it.optInt("minutes", 0).coerceIn(0, 300),
                    uses = it.optJSONArray("utilise")?.strings() ?: emptyList(), missing = it.optJSONArray("manque")?.strings() ?: emptyList(),
                    steps = it.optJSONArray("etapes")?.strings()?.take(12) ?: emptyList()
                )
            }?.filter { it.name.isNotBlank() }?.take(4) ?: emptyList(),
            tip = o.optString("conseil")
        )
    }

    /** Réponse avec recherche Google : texte, sources consultées et suggestions de recherche (à afficher, règles Google). */
    data class Grounded(val text: String, val sources: List<Pair<String, String>>, val suggestionsHtml: String?, val searched: Boolean)

    /**
     * Conversation avec accès à la recherche Google (outil « google_search » de Gemini) : le modèle cherche lui-même
     * sur le web et cite ses sources. Si le modèle ou la clé ne permettent pas la recherche, on répond sans.
     */
    suspend fun chatWithSearch(system: String, history: List<ChatMessage>): Grounded = withContext(Dispatchers.IO) {
        guard()
        val contents = JSONArray()
        history.forEach { m -> contents.put(JSONObject().put("role", if (m.fromUser) "user" else "model").put("parts", parts(m.text, null))) }
        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            .put("contents", contents)
            .put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))
            .put("generationConfig", JSONObject().put("temperature", 0.4).put("maxOutputTokens", 1200))
        val models = listOf(model) + FALLBACK_MODELS.filter { it != model }
        for (m in models) {
            val (code, response) = post(m, body)
            when {
                code in 200..299 -> {
                    val txt = text(response).trim().ifBlank { throw AiException(t("L'IA n'a pas répondu. Reformule ta question.")) }
                    val meta = JSONObject(response).optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("groundingMetadata")
                    val sources = meta?.optJSONArray("groundingChunks")?.mapObjects { c ->
                        c.optJSONObject("web")?.let { w -> w.optString("title").ifBlank { t("Source") } to w.optString("uri") }
                    }?.filterNotNull()?.filter { it.second.startsWith("https://") }?.distinctBy { it.second }?.take(5) ?: emptyList()
                    val html = meta?.optJSONObject("searchEntryPoint")?.optString("renderedContent")?.takeIf { it.isNotBlank() }
                    return@withContext Grounded(txt, sources, html, meta != null)
                }
                code == 404 -> continue
                code == 429 -> throw AiException(t("Quota de ta clé atteint pour le moment. Réessaie dans une minute."))
                code == 401 || code == 403 -> throw AiException(t("Clé API refusée (%1\$s). Vérifie-la dans Paramètres.", errorMessage(response)))
                else -> break   // recherche non disponible pour ce modèle ou cette clé : réponse sans recherche
            }
        }
        Grounded(chat(system, null, history), emptyList(), null, false)
    }

    private fun parts(text: String, jpeg: ByteArray?): JSONArray {
        val parts = JSONArray().put(JSONObject().put("text", text))
        if (jpeg != null) {
            parts.put(
                JSONObject().put(
                    "inline_data", JSONObject()
                        .put("mime_type", "image/jpeg")
                        .put("data", Base64.encodeToString(jpeg, Base64.NO_WRAP))
                )
            )
        }
        return parts
    }

    /** Envoie la requête ; si un modèle n'existe plus, essaie les suivants. Renvoie la réponse brute. */
    private fun send(body: JSONObject): String {
        val models = listOf(model) + FALLBACK_MODELS.filter { it != model }
        var lastError = t("Erreur inconnue")
        for (m in models) {
            val (code, response) = post(m, body)
            when {
                code in 200..299 -> return response
                code == 404 -> { lastError = t("Modèle %1\$s indisponible", m); continue }
                code == 429 -> throw AiException(t("Quota de ta clé atteint pour le moment. Réessaie dans une minute."))
                code == 400 || code == 401 || code == 403 ->
                    throw AiException(t("Clé API refusée (%1\$s). Vérifie-la dans Paramètres.", errorMessage(response)))
                else -> { lastError = t("Erreur serveur %1\$s : %2\$s", code, errorMessage(response)); continue }
            }
        }
        throw AiException(lastError)
    }

    private fun post(model: String, body: JSONObject): Pair<Int, String> {
        // Nom du modèle contrôlé : l'adresse ne peut pas être détournée vers autre chose que l'API Gemini de Google
        require(MODEL_NAME.matches(model)) { t("Modèle %1\$s indisponible", model.take(40)) }
        val conn = (URL("$BASE/$model:generateContent").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            instanceFollowRedirects = false   // la clé (en-tête) ne suit jamais une redirection vers une autre adresse
            connectTimeout = 20_000
            readTimeout = 90_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("x-goog-api-key", apiKey)
            // Permettent de limiter la clé à GoodLife (console Google Cloud › restriction « Applications Android »)
            AppIdentity.packageName?.let { setRequestProperty("X-Android-Package", it) }
            AppIdentity.certSha1?.let { setRequestProperty("X-Android-Cert", it) }
        }
        return try {
            conn.outputStream.use { it.write(withLanguage(body).toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            code to (stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: "")
        } catch (e: java.io.IOException) {
            throw AiException(com.goodlife.app.net.networkError("Google Gemini", e).message ?: t("Problème réseau."))
        } finally {
            conn.disconnect()
        }
    }

    /**
     * App en anglais : les consignes restent en français (l'IA les comprend), mais on lui demande d'écrire
     * en anglais tout ce que la personne lira. Les valeurs imposées (moments, rayons…) restent celles demandées.
     */
    private fun withLanguage(body: JSONObject): JSONObject {
        if (!com.goodlife.app.i18n.Lang.en) return body
        val note = "LANGUAGE: the person uses the app in English. Write every text they will read (answers, dish and " +
            "recipe names, descriptions, ingredients, steps, advice, exercise names) in English. Keep JSON keys and any " +
            "value whose allowed list is given in the instructions exactly as written there."
        val copy = JSONObject(body.toString())
        val sys = copy.optJSONObject("systemInstruction") ?: JSONObject().put("parts", JSONArray()).also { copy.put("systemInstruction", it) }
        (sys.optJSONArray("parts") ?: JSONArray().also { sys.put("parts", it) }).put(JSONObject().put("text", note))
        return copy
    }

    private fun errorMessage(body: String): String = runCatching {
        JSONObject(body).getJSONObject("error").optString("message")
    }.getOrNull()?.let { redact(it) }?.take(160) ?: t("réponse invalide")

    /** Retire la clé d'un texte (au cas où un message d'erreur la répéterait). */
    private fun redact(text: String): String =
        if (apiKey.length >= 8) text.replace(apiKey, "•••") else text

    /** Texte de la réponse (sans les « pensées » du modèle). */
    private fun text(body: String): String {
        val root = JSONObject(body)
        val candidates = root.optJSONArray("candidates")
            ?: throw AiException(t("L'IA n'a pas répondu (contenu bloqué ?)."))
        val parts = candidates.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
            ?: throw AiException(t("Réponse IA vide."))
        return buildString {
            for (i in 0 until parts.length()) {
                val p = parts.optJSONObject(i) ?: continue
                if (p.optBoolean("thought", false)) continue
                append(p.optString("text"))
            }
        }
    }

    private fun extractJson(text: String): JSONObject {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) throw AiException(t("Réponse IA illisible."))
        return runCatching { JSONObject(text.substring(start, end + 1)) }
            .getOrElse { throw AiException(t("Réponse IA illisible.")) }
    }

    // ------------------------------------------------------------------

    suspend fun analyzeFood(jpeg: ByteArray, profile: Profile?): FoodAnalysis {
        val allergies = profile?.allergies?.takeIf { it.isNotBlank() } ?: t("aucune connue")
        val prompt = """
            Tu aides à estimer les calories d'un repas (usage bien-être, pas d'avis médical). Analyse la photo de nourriture.
            Identifie chaque aliment, estime les portions visibles (en grammes) et les calories.
            Allergies de l'utilisateur : $allergies. Signale tout aliment qui pourrait en contenir.
            Si l'image ne contient pas de nourriture, mets "kcal": 0 et "plat": "Aucun aliment détecté".
            Catalogue Nutridex (id=nom) : ${com.goodlife.app.dex.Nutridex.promptList()}
            Dans "dex", liste au plus 4 id du catalogue CLAIREMENT visibles sur la photo (le plat lui-même s'il y est,
            et ses ingrédients bien reconnaissables). Liste vide si rien ne correspond ou si ce n'est pas une vraie photo de nourriture.
            Dans "portions", donne chaque aliment ou plat servi avec son NOMBRE visible (ex. 2 bananes → {"nom": "Banane", "nombre": 2} ;
            une assiette de pâtes → {"nom": "Assiette de pâtes", "nombre": 1}). Noms courts, au singulier, avec une majuscule.
            Réponds UNIQUEMENT en JSON, en français, avec exactement ce format :
            {"plat": "nom court du plat",
             "aliments": [{"nom": "...", "quantite_g": 0, "kcal": 0}],
             "kcal": 0, "proteines_g": 0, "glucides_g": 0, "lipides_g": 0,
             "allergenes_detectes": ["..."], "confiance": 0.0,
             "conseil": "une phrase courte et bienveillante",
             "dex": ["id"],
             "portions": [{"nom": "...", "nombre": 1}]}
        """.trimIndent()
        val o = call(prompt, jpeg)
        val items = o.optJSONArray("aliments")?.mapObjects { a ->
            val q = a.optDouble("quantite_g", 0.0).roundToInt()
            val k = a.optDouble("kcal", 0.0).roundToInt()
            "${a.optString("nom")} · ${q} g · $k kcal"
        } ?: emptyList()
        val portions = o.optJSONArray("portions")?.mapObjects { p ->
            val n = p.optInt("nombre", 1).coerceIn(1, 50)
            p.optString("nom").trim().take(40).takeIf { it.isNotBlank() }?.let { mealName(n, it) }
        }?.filterNotNull()?.take(6) ?: emptyList()
        return FoodAnalysis(
            dish = portions.joinToString(", ").take(80).ifBlank { mealName(1, o.optString("plat", t("Repas")).ifBlank { t("Repas") }) },
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
        slots: List<MealSlot>,
        notes: String = ""
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
            ${if (notes.isNotBlank()) "Précisions du foyer, à respecter (goûts, aliments que quelqu'un n'aime pas, contraintes) : ${notes.take(400)}" else ""}
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
        if (meals.isEmpty()) throw AiException(t("L'IA n'a pas proposé de planning. Réessaie."))
        return meals to o.optString("conseil")
    }

    /** Programme sportif de la semaine, adapté au gabarit, au but, au matériel et aux envies. */
    suspend fun sportProgram(
        p: Profile,
        goal: String,
        level: String,
        equipment: List<String>,
        daysPerWeek: Int,
        minutes: Int,
        likes: String,
        limits: String
    ): SportProgram {
        val prompt = """
            Tu es un coach sportif bienveillant (pas un professionnel de santé, pas d'avis médical). Crée un programme
            d'une semaine, motivant et ludique, réaliste pour cette personne.
            Âge : ${p.age} ans · Sexe : ${p.sex.label} · Poids : ${p.weightKg} kg · Taille : ${p.heightCm} cm
            IMC : ${"%.1f".format(Nutrition.bmi(p))} · Activité habituelle : ${p.activity.label}
            But visé : $goal · Niveau : $level
            Séances par semaine : $daysPerWeek, d'environ $minutes minutes
            Matériel disponible : ${if (equipment.isEmpty()) "aucun (poids du corps uniquement)" else equipment.joinToString(", ")}
            Envies / ce que la personne aime : ${likes.ifBlank { "non précisé" }}
            Douleurs ou limites à respecter : ${limits.ifBlank { "aucune signalée" }}
            Règles : progressif et sans danger, échauffement et retour au calme à chaque séance, exercices expliqués
            simplement, alternatives faciles pour un débutant, rien de risqué pour les articulations, respecte
            strictement les douleurs/limites (évite les mouvements concernés). Varie pour que ce soit amusant
            (défis, circuits, jeux). Répartis les séances dans la semaine avec des jours de repos.
            Utilise uniquement le matériel listé.
            Réponds UNIQUEMENT en JSON :
            {"seances": [{"jour": 1, "titre": "titre court et motivant", "minutes": 30,
                          "echauffement": "1 phrase",
                          "exercices": [{"nom": "...", "detail": "3 × 12 répétitions ou 30 s", "repos": "45 s",
                                         "conseil": "1 phrase simple (posture, variante plus facile)"}],
                          "retour_au_calme": "1 phrase"}],
             "conseil": "1 à 2 phrases d'encouragement et de sécurité"}
            "jour" : 1 = lundi … 7 = dimanche. Exactement $daysPerWeek séances, 4 à 8 exercices chacune.
        """.trimIndent()
        val o = call(prompt, null)
        val sessions = o.optJSONArray("seances")?.mapObjects { se ->
            SportSession(
                day = se.optInt("jour", 1).coerceIn(1, 7),
                title = se.optString("titre").take(60).ifBlank { t("Séance") },
                minutes = se.optInt("minutes", minutes).coerceIn(5, 180),
                warmup = se.optString("echauffement").take(300),
                exercises = se.optJSONArray("exercices")?.mapObjects { ex ->
                    Exercise(ex.optString("nom").take(60), ex.optString("detail").take(60), ex.optString("repos").take(30), ex.optString("conseil").take(200))
                }?.filter { it.name.isNotBlank() }?.take(12) ?: emptyList(),
                cooldown = se.optString("retour_au_calme").take(300)
            )
        }?.filter { it.exercises.isNotEmpty() }?.sortedBy { it.day }?.take(7) ?: emptyList()
        if (sessions.isEmpty()) throw AiException(t("L'IA n'a pas proposé de programme. Réessaie."))
        return SportProgram(
            System.currentTimeMillis(), goal, level, equipment, daysPerWeek, minutes, likes, limits,
            sessions, o.optString("conseil").take(400)
        )
    }

    /** Objectif de pas quotidien progressif et atteignable (bien-être, pas d'avis médical). */
    /**
     * Objectif de pas du jour. [week] = pas réels des 7 derniers jours (jour → pas et objectif de ce jour-là),
     * [previous] = objectif d'hier. L'app borne ensuite le résultat : ±15 % par rapport à hier, entre 3 000 et 20 000,
     * arrondi à 250 pas, pour une progression réaliste.
     */
    suspend fun recommendSteps(
        p: Profile, average: Int,
        week: List<Pair<String, com.goodlife.app.data.StepDay>> = emptyList(), previous: Int = 0
    ): Pair<Int, String> {
        val detail = week.joinToString("; ") { (d, s) -> t("%1\$s : %2\$s pas (objectif %3\$s)", d, s.steps, s.goal) }.ifBlank { t("pas d'historique") }
        val met = week.count { it.second.goal > 0 && it.second.steps >= it.second.goal }
        val prompt = """
            Tu es un coach bien-être (pas un professionnel de santé, pas d'avis médical). Propose l'objectif de pas
            d'AUJOURD'HUI : motivant mais atteignable, qui fait progresser petit à petit et reste réaliste.
            Si l'objectif a été souvent atteint, augmente un peu ; s'il a souvent été raté, baisse un peu ; jamais de saut brutal.
            Âge : ${p.age} ans · Sexe : ${p.sex.label} · Activité : ${p.activity.label} · Objectif : ${p.goal.label}
            Moyenne des 7 derniers jours : ${if (average > 0) "$average pas/jour" else "inconnue"}
            Détail des 7 derniers jours : $detail
            Objectif atteint $met jour(s) sur ${week.size}. Objectif d'hier : ${if (previous > 0) "$previous pas" else "aucun"}.
            Réponds UNIQUEMENT en JSON : {"pas": 0, "explication": "1 à 2 phrases simples en français, avec les vrais chiffres"}
        """.trimIndent()
        val o = call(prompt, null)
        var steps = o.optDouble("pas", 0.0).roundToInt()
        if (previous > 0) steps = steps.coerceIn((previous * 0.85).roundToInt(), (previous * 1.15).roundToInt())
        steps = ((steps / 250.0).roundToInt() * 250).coerceIn(3000, 20_000)
        return steps to o.optString("explication")
    }

    /**
     * Objectif d'eau du jour (ml de boissons) selon les besoins de la personne et son activité d'hier.
     * Bornes de l'app : 1 à 4 L, arrondi à 250 ml, ±25 % par rapport à l'objectif d'hier.
     */
    suspend fun recommendWater(p: Profile, stepsYesterday: Int, sessionsYesterday: Int, outingMinutesYesterday: Int, previous: Int = 0): Pair<Int, String> {
        val prompt = """
            Tu es un coach bien-être (pas un professionnel de santé, pas d'avis médical). Propose la quantité de BOISSONS
            (surtout de l'eau) à viser AUJOURD'HUI, en ml, en t'appuyant sur les repères officiels (EFSA : apport total en eau
            d'environ 2,0 L/jour pour une femme et 2,5 L/jour pour un homme adultes, dont environ 20 à 30 % viennent des aliments ;
            besoins plus élevés avec le poids, l'activité physique et la transpiration).
            Âge : ${p.age} ans · Sexe : ${p.sex.label} · Poids : ${p.weightKg} kg · Taille : ${p.heightCm} cm
            Activité habituelle : ${p.activity.label} · Objectif : ${p.goal.label} · Apport visé : ${p.targetKcal} kcal/jour
            Hier : ${if (stepsYesterday > 0) "$stepsYesterday pas" else "pas inconnus"}, $sessionsYesterday séance(s) de sport,
            $outingMinutesYesterday min de sortie (course, marche, vélo).
            Objectif d'eau d'hier : ${if (previous > 0) "$previous ml" else "aucun"}.
            Reste raisonnable et progressif ; ne dépasse jamais 4 L.
            Réponds UNIQUEMENT en JSON : {"ml": 0, "explication": "1 à 2 phrases simples en français, avec les vrais chiffres"}
        """.trimIndent()
        val o = call(prompt, null)
        var ml = o.optDouble("ml", 0.0).roundToInt()
        if (ml <= 0) throw AiException(t("Réponse IA illisible."))
        if (previous > 0) ml = ml.coerceIn((previous * 0.75).roundToInt(), (previous * 1.25).roundToInt())
        ml = ((ml / 250.0).roundToInt() * 250).coerceIn(1000, 4000)
        return ml to o.optString("explication").take(300)
    }

    suspend fun suggestMeals(p: Profile, today: List<Meal>, slot: MealSlot? = null, avoid: List<String> = emptyList()): List<MealSuggestion> {
        val eaten = today.sumOf { it.kcal }
        val remaining = (p.targetKcal - eaten).coerceAtLeast(0)
        val list = today.joinToString("; ") { t("%1\$s (%2\$s kcal)", it.name, it.kcal) }.ifBlank { t("rien encore") }
        val what = if (slot == null) t("4 idées de repas simples pour la suite de la journée ou demain")
                   else t("4 idées de %1\$s simples (toutes pour ce repas, « moment » = « %2\$s »)", slot.label.lowercase(), slot.label.lowercase())
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
        private val MODEL_NAME = Regex("[a-z0-9][a-z0-9.\\-]{1,60}")
        /** Si un modèle n'existe plus chez Google, on essaie automatiquement les suivants. */
        val FALLBACK_MODELS = listOf("gemini-3.5-flash-lite", "gemini-2.5-flash-lite", "gemini-2.5-flash")

        /** Modèles proposés dans les paramètres : id → description. */
        val KNOWN_MODELS = listOf(
            "gemini-3.5-flash-lite" to t("Rapide et économique (recommandé)"),
            "gemini-3.8-flash" to t("Plus précis, quota plus petit"),
            "gemini-2.5-flash" to t("Ancien modèle, en secours")
        )
    }
}

/** Identité de l'app (nom du paquet et empreinte SHA-1 du certificat de signature), lue une fois. */
object AppIdentity {
    @Volatile var packageName: String? = null; private set
    @Volatile var certSha1: String? = null; private set

    fun init(context: android.content.Context) {
        if (packageName != null) return
        packageName = context.packageName
        certSha1 = runCatching {
            val pm = context.packageManager
            val sig = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                pm.getPackageInfo(context.packageName, android.content.pm.PackageManager.GET_SIGNING_CERTIFICATES)
                    .signingInfo?.apkContentsSigners?.firstOrNull()
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(context.packageName, android.content.pm.PackageManager.GET_SIGNATURES).signatures?.firstOrNull()
            } ?: return@runCatching null
            java.security.MessageDigest.getInstance("SHA-1").digest(sig.toByteArray()).joinToString("") { "%02X".format(it) }
        }.getOrNull()
    }
}
