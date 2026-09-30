package com.goodlife.app.data

import org.json.JSONArray
import org.json.JSONObject

enum class Sex(val label: String) { HOMME("Homme"), FEMME("Femme") }

enum class ActivityLevel(val label: String, val factor: Double) {
    SEDENTAIRE("Sédentaire", 1.2),
    LEGER("Léger", 1.375),
    MODERE("Modéré", 1.55),
    ACTIF("Actif", 1.725),
    TRES_ACTIF("Très actif", 1.9)
}

enum class Goal(val label: String, val deltaKcal: Int) {
    PERTE("Perdre du poids", -400),
    MAINTIEN("Maintenir", 0),
    PRISE("Prendre du poids", 300)
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
    val description: String,
    val why: String
)

data class FoodAnalysis(
    val dish: String,
    val kcal: Int,
    val proteinG: Double,
    val carbsG: Double,
    val fatG: Double,
    val items: List<String>,
    val allergens: List<String>,
    val confidence: Double,
    val advice: String
)

internal fun <T> JSONArray.mapObjects(block: (JSONObject) -> T): List<T> =
    (0 until length()).mapNotNull { i -> optJSONObject(i)?.let(block) }

internal fun JSONArray.strings(): List<String> =
    (0 until length()).mapNotNull { i -> optString(i).takeIf { it.isNotBlank() } }
