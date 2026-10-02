package com.goodlife.app.data

import com.goodlife.app.i18n.t

import com.goodlife.app.game.Game
import org.json.JSONArray
import org.json.JSONObject

enum class Sex(val label: String) { HOMME(t("Homme")), FEMME(t("Femme")) }

enum class ActivityLevel(val label: String, val factor: Double) {
    SEDENTAIRE(t("Sédentaire"), 1.2),
    LEGER(t("Léger"), 1.375),
    MODERE(t("Modéré"), 1.55),
    ACTIF(t("Actif"), 1.725),
    TRES_ACTIF(t("Très actif"), 1.9)
}

enum class Goal(val label: String, val deltaKcal: Int) {
    PERTE(t("Perdre du poids"), -400),
    MAINTIEN(t("Maintenir"), 0),
    PRISE(t("Prendre du poids"), 300)
}

private inline fun <reified T : Enum<T>> enumOr(value: String?, default: T): T =
    runCatching { enumValueOf<T>(value ?: "") }.getOrDefault(default)

data class Profile(
    val name: String = "",
    val age: Int = 25,
    val sex: Sex = Sex.HOMME,
    val weightKg: Double = 70.0,
    val heightCm: Double = 175.0,
    val activity: ActivityLevel = ActivityLevel.MODERE,
    val goal: Goal = Goal.MAINTIEN,
    val habits: String = "",
    val allergies: String = "",
    val targetKcal: Int = 0,
    val proteinG: Int = 0,
    val carbsG: Int = 0,
    val fatG: Int = 0,
    val targetSource: String = "formule",
    val targetExplanation: String = ""
) {
    fun toJson(): JSONObject = JSONObject()
        .put("name", name).put("age", age).put("sex", sex.name)
        .put("weightKg", weightKg).put("heightCm", heightCm)
        .put("activity", activity.name).put("goal", goal.name)
        .put("habits", habits).put("allergies", allergies)
        .put("targetKcal", targetKcal).put("proteinG", proteinG)
        .put("carbsG", carbsG).put("fatG", fatG)
        .put("targetSource", targetSource).put("targetExplanation", targetExplanation)

    companion object {
        fun fromJson(o: JSONObject) = Profile(
            name = o.optString("name"),
            age = o.optInt("age", 25),
            sex = enumOr(o.optString("sex"), Sex.HOMME),
            weightKg = o.optDouble("weightKg", 70.0),
            heightCm = o.optDouble("heightCm", 175.0),
            activity = enumOr(o.optString("activity"), ActivityLevel.MODERE),
            goal = enumOr(o.optString("goal"), Goal.MAINTIEN),
            habits = o.optString("habits"),
            allergies = o.optString("allergies"),
            targetKcal = o.optInt("targetKcal", 0),
            proteinG = o.optInt("proteinG", 0),
            carbsG = o.optInt("carbsG", 0),
            fatG = o.optInt("fatG", 0),
            targetSource = o.optString("targetSource", "formule"),
            targetExplanation = o.optString("targetExplanation")
        )
    }
}

data class Meal(
    val id: Long = System.currentTimeMillis(),
    val timestamp: Long = System.currentTimeMillis(),
    val name: String,
    val kcal: Int,
    val proteinG: Double = 0.0,
    val carbsG: Double = 0.0,
    val fatG: Double = 0.0,
    val details: String = "",
    val source: String = "manuel"
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("timestamp", timestamp).put("name", name).put("kcal", kcal)
        .put("proteinG", proteinG).put("carbsG", carbsG).put("fatG", fatG)
        .put("details", details).put("source", source)

    companion object {
        fun fromJson(o: JSONObject) = Meal(
            id = o.optLong("id"),
            timestamp = o.optLong("timestamp"),
            name = o.optString("name"),
            kcal = o.optInt("kcal"),
            proteinG = o.optDouble("proteinG", 0.0),
            carbsG = o.optDouble("carbsG", 0.0),
            fatG = o.optDouble("fatG", 0.0),
            details = o.optString("details"),
            source = o.optString("source", "manuel")
        )
    }
}

data class SleepSession(
    val id: Long = System.currentTimeMillis(),
    val start: Long,
    val end: Long,
    val source: String = "auto"
) {
    val durationMin: Long get() = ((end - start) / 60_000L).coerceAtLeast(0)

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("start", start).put("end", end).put("source", source)

    companion object {
        fun fromJson(o: JSONObject) = SleepSession(
            id = o.optLong("id"),
            start = o.optLong("start"),
            end = o.optLong("end"),
            source = o.optString("source", "auto")
        )
    }
}

data class MealSuggestion(
    val name: String,
    val moment: String,
    val kcal: Int,
    val summary: String,
    val description: String,
    val why: String
)

enum class MealSlot(val label: String) {
    PETIT_DEJ(t("Petit-déjeuner")),
    DEJEUNER(t("Déjeuner")),
    COLLATION(t("Collation")),
    DINER(t("Dîner"));

    companion object {
        /** Devine le créneau à partir du « moment » renvoyé par l'IA. */
        fun guess(moment: String): MealSlot {
            val m = moment.lowercase()
            return when {
                "petit" in m || "matin" in m || "breakfast" in m -> PETIT_DEJ
                "collation" in m || "goûter" in m || "gouter" in m || "snack" in m -> COLLATION
                "dîner" in m || "diner" in m || "soir" in m || "dinner" in m || "supper" in m -> DINER
                else -> DEJEUNER
            }
        }
    }
}

data class Recipe(
    val servings: Int,
    val minutes: Int,
    val kcalPerServing: Int,
    val ingredients: List<String>,
    val steps: List<String>,
    val tip: String,
    val costEur: Double = 0.0     // coût estimé de la recette entière (0 = inconnu)
) {
    fun toJson(): JSONObject = JSONObject()
        .put("servings", servings).put("minutes", minutes).put("kcalPerServing", kcalPerServing)
        .put("ingredients", JSONArray(ingredients)).put("steps", JSONArray(steps)).put("tip", tip).put("costEur", costEur)

    companion object {
        fun fromJson(o: JSONObject) = Recipe(
            servings = o.optInt("servings", 1),
            minutes = o.optInt("minutes", 0),
            kcalPerServing = o.optInt("kcalPerServing", 0),
            ingredients = o.optJSONArray("ingredients")?.strings() ?: emptyList(),
            steps = o.optJSONArray("steps")?.strings() ?: emptyList(),
            tip = o.optString("tip"),
            costEur = o.optDouble("costEur", 0.0).takeUnless { it.isNaN() } ?: 0.0
        )
    }
}

data class PlannedMeal(
    val id: Long = System.currentTimeMillis(),
    val date: String,              // AAAA-MM-JJ (heure locale)
    val slot: MealSlot,
    val name: String,
    val kcal: Int,
    val description: String = "",
    val recipe: Recipe? = null,
    val done: Boolean = false,
    val costEur: Double = 0.0      // coût estimé par l'IA (0 = inconnu)
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("date", date).put("slot", slot.name).put("name", name).put("kcal", kcal)
        .put("description", description).put("done", done).put("costEur", costEur)
        .apply { if (recipe != null) put("recipe", recipe.toJson()) }

    companion object {
        fun fromJson(o: JSONObject) = PlannedMeal(
            id = o.optLong("id"),
            date = o.optString("date"),
            slot = enumOr(o.optString("slot"), MealSlot.DEJEUNER),
            name = o.optString("name"),
            kcal = o.optInt("kcal"),
            description = o.optString("description"),
            recipe = o.optJSONObject("recipe")?.let { Recipe.fromJson(it) },
            done = o.optBoolean("done", false),
            costEur = o.optDouble("costEur", 0.0).takeUnless { it.isNaN() }?.coerceIn(0.0, 500.0) ?: 0.0
        )
    }
}

/** Un aliment reconnu sur la photo : poids estimé (g) et calories pour ce poids. */
data class FoodPart(val name: String, val grams: Int, val kcal: Int)

data class FoodAnalysis(
    val dish: String,
    val kcal: Int,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val items: List<String>,
    val allergens: List<String>,
    val confidence: Double,
    val advice: String,
    val dexIds: List<String> = emptyList(),  // entrées du Nutridex reconnues sur la photo
    val waterMl: Int = 0,                    // eau (ou thé, café, infusion sans sucre) bue : compte dans l'eau du jour
    val drinkOnly: Boolean = false,          // la photo ne montre qu'une boisson
    val fridgeUsed: List<Pair<String, Double>> = emptyList(),  // aliments de « Mon frigo » utilisés (nom, quantité)
    val foods: List<FoodPart> = emptyList()  // détail modifiable : on corrige le poids, les calories suivent
)

internal fun <T> JSONArray.mapObjects(block: (JSONObject) -> T): List<T> =
    (0 until length()).mapNotNull { i -> optJSONObject(i)?.let(block) }

internal fun JSONArray.strings(): List<String> =
    (0 until length()).mapNotNull { i -> optString(i).takeIf { it.isNotBlank() } }


/** Progression : jours rattrapés grâce au quiz, résultats des quiz, historique des pesées. */
data class GameState(
    val recoveredDays: Set<String> = emptySet(),
    val quizResults: Map<String, Int> = emptyMap(),   // jour → bonnes réponses (premier essai)
    val weights: List<Pair<String, Double>> = emptyList(),
    val sportXp: Map<String, Int> = emptyMap(),       // jour → XP gagnée en faisant du sport (plafonnée)
    val freezes: Int = 0,                             // gels de série disponibles
    val freezeMark: Int = -1,                         // palier de série déjà récompensé (-1 = pas encore calculé)
    val freezeUsed: Set<String> = emptySet(),         // jours sauvés avec un gel
    val missionXp: Map<String, Int> = emptyMap()      // missions de la première semaine déjà récompensées
) {
    fun toJson(): JSONObject = JSONObject()
        .put("recoveredDays", JSONArray(recoveredDays.toList()))
        .put("quizResults", JSONObject().apply { quizResults.forEach { (k, v) -> put(k, v) } })
        .put("weights", JSONArray().apply { weights.forEach { (d, w) -> put(JSONObject().put("date", d).put("kg", w)) } })
        .put("sportXp", JSONObject().apply { sportXp.forEach { (k, v) -> put(k, v) } })
        .put("freezes", freezes).put("freezeMark", freezeMark).put("freezeUsed", JSONArray(freezeUsed.toList()))
        .put("missionXp", JSONObject().apply { missionXp.forEach { (k, v) -> put(k, v) } })

    companion object {
        fun fromJson(o: JSONObject): GameState {
            val q = o.optJSONObject("quizResults")
            return GameState(
                recoveredDays = o.optJSONArray("recoveredDays")?.strings()?.toSet() ?: emptySet(),
                quizResults = q?.keys()?.asSequence()?.associateWith { q.optInt(it) } ?: emptyMap(),
                weights = o.optJSONArray("weights")?.mapObjects { it.optString("date") to it.optDouble("kg") } ?: emptyList(),
                sportXp = o.optJSONObject("sportXp")?.let { x -> x.keys().asSequence().associateWith { x.optInt(it).coerceIn(0, Game.SPORT_XP_PER_DAY) } } ?: emptyMap(),
                freezes = o.optInt("freezes", 0).coerceIn(0, Game.MAX_FREEZES),
                freezeMark = o.optInt("freezeMark", -1),
                freezeUsed = o.optJSONArray("freezeUsed")?.strings()?.toSet() ?: emptySet(),
                missionXp = o.optJSONObject("missionXp")?.let { x -> x.keys().asSequence().associateWith { x.optInt(it).coerceIn(0, 100) } } ?: emptyMap()
            )
        }
    }
}

/** Humeur et énergie du jour, de 1 (au plus bas) à 5 (au top). */
data class Feeling(val mood: Int, val energy: Int)

/** Pas d'une journée et objectif valable ce jour-là (pour que changer d'objectif ne réécrive pas le passé). */
data class StepDay(val steps: Int, val goal: Int)

/** Historique des pas (jour AAAA-MM-JJ → pas) et dernier relevé du capteur (compteur depuis le démarrage). */
data class StepsData(
    val days: Map<String, StepDay> = emptyMap(),
    val lastCounter: Long = -1L
) {
    fun toJson(): JSONObject = JSONObject()
        .put("days", JSONObject().apply { days.forEach { (d, s) -> put(d, JSONObject().put("steps", s.steps).put("goal", s.goal)) } })
        .put("lastCounter", lastCounter)

    companion object {
        fun fromJson(o: JSONObject): StepsData {
            val d = o.optJSONObject("days")
            return StepsData(
                days = d?.keys()?.asSequence()?.mapNotNull { k ->
                    d.optJSONObject(k)?.let { k to StepDay(it.optInt("steps").coerceIn(0, 200_000), it.optInt("goal")) }
                }?.toMap() ?: emptyMap(),
                lastCounter = o.optLong("lastCounter", -1L)
            )
        }
    }
}

/** Nutridex : entrées débloquées (id → date du déblocage, en ms). Les photos sont stockées à part, chiffrées. */
data class DexState(val unlocked: Map<String, Long> = emptyMap()) {
    fun toJson(): JSONObject = JSONObject().apply { unlocked.forEach { (k, v) -> put(k, v) } }

    companion object {
        fun fromJson(o: JSONObject) = DexState(o.keys().asSequence().associateWith { o.optLong(it) })
    }
}

/**
 * Personne connue via Tap to Sync, QR code ou StreetPass. [friend] = ajoutée en ami ;
 * sinon simple rencontre StreetPass. Les données viennent uniquement de sa carte signée.
 */
data class Person(
    val id: String,
    val publicKey: String,          // base64
    val pseudo: String,
    val level: Int? = null,
    val streak: Int? = null,
    val bestStreak: Int? = null,
    val dex: Set<String>? = null,
    val cardTime: Long = 0L,        // horodatage de sa dernière carte reçue
    val seenAt: Long = 0L,
    val via: String = "qr",         // tap | qr | street
    val friend: Boolean = false,
    val encounters: Int = 1,
    val weekId: Int = -1,           // semaine du dernier bilan reçu (-1 = aucun)
    val weekDays: Int = 0,
    val weekSteps: Int = 0,
    val weekXp: Int = 0
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("pk", publicKey).put("pseudo", pseudo)
        .put("level", level ?: -1).put("streak", streak ?: -1).put("best", bestStreak ?: -1)
        .put("dex", dex?.let { JSONArray(it.toList()) } ?: JSONObject.NULL)
        .put("cardTime", cardTime).put("seenAt", seenAt).put("via", via)
        .put("friend", friend).put("encounters", encounters)
        .put("weekId", weekId).put("weekDays", weekDays).put("weekSteps", weekSteps).put("weekXp", weekXp)

    companion object {
        fun fromJson(o: JSONObject) = Person(
            id = o.optString("id"),
            publicKey = o.optString("pk"),
            pseudo = o.optString("pseudo"),
            level = o.optInt("level", -1).takeIf { it >= 0 },
            streak = o.optInt("streak", -1).takeIf { it >= 0 },
            bestStreak = o.optInt("best", -1).takeIf { it >= 0 },
            dex = o.optJSONArray("dex")?.strings()?.toSet(),
            cardTime = o.optLong("cardTime"),
            seenAt = o.optLong("seenAt"),
            via = o.optString("via", "qr"),
            friend = o.optBoolean("friend", false),
            encounters = o.optInt("encounters", 1),
            weekId = o.optInt("weekId", -1),
            weekDays = o.optInt("weekDays", 0),
            weekSteps = o.optInt("weekSteps", 0),
            weekXp = o.optInt("weekXp", 0)
        )
    }
}

/** Encouragement reçu (de [from], id d'ami) ou envoyé (à [to]). */
data class CheerRecord(val from: String, val to: String, val message: Int, val day: Int, val at: Long, val seen: Boolean = false) {
    fun toJson(): JSONObject = JSONObject().put("from", from).put("to", to).put("m", message).put("d", day).put("at", at).put("seen", seen)

    companion object {
        fun fromJson(o: JSONObject) = CheerRecord(
            o.optString("from"), o.optString("to"), o.optInt("m"), o.optInt("d"), o.optLong("at"), o.optBoolean("seen")
        )
    }
}

data class SocialState(
    val people: List<Person> = emptyList(),
    val blocked: Set<String> = emptySet(),
    val cheersOut: List<CheerRecord> = emptyList(),
    val cheersIn: List<CheerRecord> = emptyList()
) {
    fun toJson(): JSONObject = JSONObject()
        .put("people", JSONArray().apply { people.forEach { put(it.toJson()) } })
        .put("blocked", JSONArray(blocked.toList()))
        .put("cheersOut", JSONArray().apply { cheersOut.forEach { put(it.toJson()) } })
        .put("cheersIn", JSONArray().apply { cheersIn.forEach { put(it.toJson()) } })

    companion object {
        fun fromJson(o: JSONObject) = SocialState(
            people = o.optJSONArray("people")?.mapObjects { Person.fromJson(it) } ?: emptyList(),
            blocked = o.optJSONArray("blocked")?.strings()?.toSet() ?: emptySet(),
            cheersOut = o.optJSONArray("cheersOut")?.mapObjects { CheerRecord.fromJson(it) } ?: emptyList(),
            cheersIn = o.optJSONArray("cheersIn")?.mapObjects { CheerRecord.fromJson(it) } ?: emptyList()
        )
    }
}

/** Un exercice d'une séance (proposé par l'IA). */
data class Exercise(val name: String, val detail: String, val rest: String, val tip: String) {
    fun toJson(): JSONObject = JSONObject().put("name", name).put("detail", detail).put("rest", rest).put("tip", tip)

    companion object {
        fun fromJson(o: JSONObject) = Exercise(o.optString("name"), o.optString("detail"), o.optString("rest"), o.optString("tip"))
    }
}

/** Une séance du programme ; [day] = jour de la semaine (1 = lundi … 7 = dimanche). */
data class SportSession(
    val day: Int, val title: String, val minutes: Int,
    val warmup: String, val exercises: List<Exercise>, val cooldown: String
) {
    fun toJson(): JSONObject = JSONObject().put("day", day).put("title", title).put("minutes", minutes)
        .put("warmup", warmup).put("cooldown", cooldown)
        .put("exercises", JSONArray().apply { exercises.forEach { put(it.toJson()) } })

    companion object {
        fun fromJson(o: JSONObject) = SportSession(
            o.optInt("day", 1).coerceIn(1, 7), o.optString("title"), o.optInt("minutes", 30).coerceIn(5, 180),
            o.optString("warmup"), o.optJSONArray("exercises")?.mapObjects { Exercise.fromJson(it) } ?: emptyList(),
            o.optString("cooldown")
        )
    }
}

/** Programme sportif de la semaine, avec les réponses du questionnaire qui l'ont produit. */
data class SportProgram(
    val createdAt: Long,
    val goal: String,
    val level: String,
    val equipment: List<String>,
    val daysPerWeek: Int,
    val minutes: Int,
    val likes: String,
    val limits: String,
    val sessions: List<SportSession>,
    val advice: String
) {
    fun toJson(): JSONObject = JSONObject().put("createdAt", createdAt).put("goal", goal).put("level", level)
        .put("equipment", JSONArray(equipment)).put("daysPerWeek", daysPerWeek).put("minutes", minutes)
        .put("likes", likes).put("limits", limits).put("advice", advice)
        .put("sessions", JSONArray().apply { sessions.forEach { put(it.toJson()) } })

    companion object {
        fun fromJson(o: JSONObject) = SportProgram(
            o.optLong("createdAt"), o.optString("goal"), o.optString("level"),
            o.optJSONArray("equipment")?.strings() ?: emptyList(), o.optInt("daysPerWeek", 3), o.optInt("minutes", 30),
            o.optString("likes"), o.optString("limits"),
            o.optJSONArray("sessions")?.mapObjects { SportSession.fromJson(it) } ?: emptyList(),
            o.optString("advice")
        )
    }
}

/** Programme en cours et séances faites (« AAAA-MM-JJ#indice »). */
/**
 * Programme sportif, séances faites (« jour#index ») et séance en cours : [running] = index de la séance lancée
 * (-1 = aucune), [runningSince] = heure de départ du chrono (gardée même si l'app est fermée).
 */
data class SportState(
    val program: SportProgram? = null,
    val done: Set<String> = emptySet(),
    val running: Int = -1,
    val runningSince: Long = 0L
) {
    fun toJson(): JSONObject = JSONObject()
        .put("program", program?.toJson() ?: JSONObject.NULL)
        .put("done", JSONArray(done.toList()))
        .put("running", running)
        .put("runningSince", runningSince)

    companion object {
        fun fromJson(o: JSONObject) = SportState(
            o.optJSONObject("program")?.let { SportProgram.fromJson(it) },
            o.optJSONArray("done")?.strings()?.toSet() ?: emptySet(),
            o.optInt("running", -1),
            o.optLong("runningSince", 0L)
        )
    }
}

/** Type de sortie GPS. */
enum class OutingType(val label: String, val emoji: String) { RUN(t("Course"), "🏃"), WALK(t("Marche"), "🚶"), BIKE(t("Vélo"), "🚴") }

/** Point d'un tracé : latitude, longitude, altitude (m, NaN si inconnue), temps (ms). */
data class TrackPoint(val lat: Double, val lng: Double, val alt: Double, val t: Long)

/**
 * Résumé d'une sortie (le tracé complet est stocké à part, chiffré).
 * [routeId] : parcours de référence (id de la première sortie sur ce trajet) ; [valid] : le parcours a bien été suivi.
 */
data class Outing(
    val id: Long,
    val type: OutingType,
    val start: Long,
    val end: Long,
    val movingMs: Long,
    val distanceM: Double,
    val elevGainM: Double,
    val maxSpeed: Double,        // m/s
    val routeId: Long,
    val valid: Boolean = true,
    val name: String = ""
) {
    val avgSpeed: Double get() = if (movingMs > 0) distanceM / (movingMs / 1000.0) else 0.0

    fun toJson(): JSONObject = JSONObject().put("id", id).put("type", type.name).put("start", start).put("end", end)
        .put("movingMs", movingMs).put("distanceM", distanceM).put("elevGainM", elevGainM).put("maxSpeed", maxSpeed)
        .put("routeId", routeId).put("valid", valid).put("name", name)

    companion object {
        fun fromJson(o: JSONObject) = Outing(
            o.optLong("id"), enumOr(o.optString("type"), OutingType.RUN), o.optLong("start"), o.optLong("end"),
            o.optLong("movingMs"), o.optDouble("distanceM", 0.0), o.optDouble("elevGainM", 0.0), o.optDouble("maxSpeed", 0.0),
            o.optLong("routeId"), o.optBoolean("valid", true), o.optString("name")
        )

        fun encodeTrack(points: List<TrackPoint>): String = JSONArray().apply {
            points.forEach { put(JSONArray().put(it.lat).put(it.lng).put(if (it.alt.isNaN()) JSONObject.NULL else it.alt).put(it.t)) }
        }.toString()

        fun decodeTrack(s: String): List<TrackPoint> = runCatching {
            val a = JSONArray(s)
            (0 until a.length()).mapNotNull { i ->
                a.optJSONArray(i)?.let { p -> TrackPoint(p.optDouble(0), p.optDouble(1), p.optDouble(2, Double.NaN), p.optLong(3)) }
            }
        }.getOrDefault(emptyList())
    }
}
