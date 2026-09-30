package com.goodlife.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/** Jour UTC au format AAAA-MM-JJ (même référence que le relais). */
fun utcDay(): String = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    .apply { timeZone = TimeZone.getTimeZone("UTC") }
    .format(java.util.Date())

data class Settings(
    val apiKey: String = "",
    val model: String = DEFAULT_MODEL,
    val sleepAuto: Boolean = false,
    val manualSleepStart: Long = 0L,
    val lastSleepConfidence: Int = -1,
    val lastSleepConfidenceAt: Long = 0L,
    val appLock: Boolean = false,
    val blockScreenshots: Boolean = true,
    val themeMode: String = "system",   // system | light | dark
    val themeColor: String = "auto",    // auto | blue | green | purple | orange | pink
    val installId: String = "",
    val relayRemaining: Int = -1,
    val relayDay: String = ""
) {
    /** Analyses gratuites restantes aujourd'hui via le relais (null = inconnu → limite pleine). */
    fun relayRemainingToday(limit: Int = RELAY_DAILY_LIMIT): Int =
        if (relayDay == utcDay() && relayRemaining >= 0) relayRemaining else limit

    fun toJson(): JSONObject = JSONObject()
        .put("apiKey", apiKey).put("model", model).put("sleepAuto", sleepAuto)
        .put("manualSleepStart", manualSleepStart)
        .put("lastSleepConfidence", lastSleepConfidence)
        .put("lastSleepConfidenceAt", lastSleepConfidenceAt)
        .put("appLock", appLock)
        .put("blockScreenshots", blockScreenshots)
        .put("themeMode", themeMode)
        .put("themeColor", themeColor)
        .put("installId", installId)
        .put("relayRemaining", relayRemaining)
        .put("relayDay", relayDay)

    companion object {
        const val DEFAULT_MODEL = "gemini-3.5-flash-lite"
        const val RELAY_DAILY_LIMIT = 10
        fun fromJson(o: JSONObject) = Settings(
            apiKey = o.optString("apiKey"),
            model = o.optString("model", DEFAULT_MODEL).ifBlank { DEFAULT_MODEL },
            sleepAuto = o.optBoolean("sleepAuto", false),
            manualSleepStart = o.optLong("manualSleepStart", 0L),
            lastSleepConfidence = o.optInt("lastSleepConfidence", -1),
            lastSleepConfidenceAt = o.optLong("lastSleepConfidenceAt", 0L),
            appLock = o.optBoolean("appLock", false),
            blockScreenshots = o.optBoolean("blockScreenshots", true),
            themeMode = o.optString("themeMode", "system").ifBlank { "system" },
            themeColor = o.optString("themeColor", "auto").ifBlank { "auto" },
            installId = o.optString("installId"),
            relayRemaining = o.optInt("relayRemaining", -1),
            relayDay = o.optString("relayDay")
        )
    }
}

/** Source unique des données de l'app, 100 % locale et chiffrée. */
object Repo {
    private lateinit var store: SecureStore

    private val _profile = MutableStateFlow<Profile?>(null)
    val profile: StateFlow<Profile?> = _profile

    private val _meals = MutableStateFlow<List<Meal>>(emptyList())
    val meals: StateFlow<List<Meal>> = _meals

    private val _sleep = MutableStateFlow<List<SleepSession>>(emptyList())
    val sleep: StateFlow<List<SleepSession>> = _sleep

    private val _settings = MutableStateFlow(Settings())
    val settings: StateFlow<Settings> = _settings

    @Synchronized
    fun init(context: Context) {
        if (::store.isInitialized) return
        store = SecureStore(context.applicationContext)
        _profile.value = store.get(K_PROFILE)?.let { runCatching { Profile.fromJson(JSONObject(it)) }.getOrNull() }
        _meals.value = store.get(K_MEALS)?.let { s ->
            runCatching { JSONArray(s).mapObjects { Meal.fromJson(it) } }.getOrNull()
        } ?: emptyList()
        _sleep.value = store.get(K_SLEEP)?.let { s ->
            runCatching { JSONArray(s).mapObjects { SleepSession.fromJson(it) } }.getOrNull()
        } ?: emptyList()
        _settings.value = store.get(K_SETTINGS)?.let { runCatching { Settings.fromJson(JSONObject(it)) }.getOrNull() }
            ?: Settings()
        // Identifiant anonyme et aléatoire de cette installation (sert au quota du relais).
        if (_settings.value.installId.isBlank()) {
            updateSettings { it.copy(installId = UUID.randomUUID().toString()) }
        }
    }

    // ---------- Profil ----------
    @Synchronized
    fun saveProfile(p: Profile) {
        _profile.value = p
        store.put(K_PROFILE, p.toJson().toString())
    }

    // ---------- Repas ----------
    @Synchronized
    fun addMeal(m: Meal) {
        _meals.value = (_meals.value + m).sortedByDescending { it.timestamp }
        persistMeals()
    }

    @Synchronized
    fun deleteMeal(id: Long) {
        _meals.value = _meals.value.filterNot { it.id == id }
        persistMeals()
    }

    private fun persistMeals() {
        // on garde 1 an d'historique maximum
        val limit = System.currentTimeMillis() - 365L * 24 * 3600 * 1000
        val kept = _meals.value.filter { it.timestamp >= limit }
        _meals.value = kept
        store.put(K_MEALS, JSONArray().apply { kept.forEach { put(it.toJson()) } }.toString())
    }

    fun mealsOfDay(all: List<Meal>, dayOffset: Int = 0): List<Meal> {
        val (from, to) = dayBounds(dayOffset)
        return all.filter { it.timestamp in from until to }
    }

    // ---------- Sommeil ----------
    @Synchronized
    fun addSleep(s: SleepSession) {
        if (s.end <= s.start) return
        val duplicate = _sleep.value.any { kotlin.math.abs(it.start - s.start) < 60_000 && it.source == s.source }
        if (duplicate) return
        _sleep.value = (_sleep.value + s).sortedByDescending { it.start }.take(400)
        persistSleep()
    }

    @Synchronized
    fun deleteSleep(id: Long) {
        _sleep.value = _sleep.value.filterNot { it.id == id }
        persistSleep()
    }

    private fun persistSleep() {
        store.put(K_SLEEP, JSONArray().apply { _sleep.value.forEach { put(it.toJson()) } }.toString())
    }

    // ---------- Réglages ----------
    @Synchronized
    fun updateSettings(transform: (Settings) -> Settings) {
        val s = transform(_settings.value)
        _settings.value = s
        store.put(K_SETTINGS, s.toJson().toString())
    }

    fun setRelayRemaining(remaining: Int) {
        updateSettings { it.copy(relayRemaining = remaining, relayDay = utcDay()) }
    }

    @Synchronized
    fun wipeAll() {
        store.clear()
        _profile.value = null
        _meals.value = emptyList()
        _sleep.value = emptyList()
        _settings.value = Settings()
        updateSettings { it.copy(installId = UUID.randomUUID().toString()) }
    }

    fun dayBounds(dayOffset: Int = 0): Pair<Long, Long> {
        val c = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, dayOffset)
        }
        val from = c.timeInMillis
        c.add(Calendar.DAY_OF_YEAR, 1)
        return from to c.timeInMillis
    }

    private const val K_PROFILE = "profile"
    private const val K_MEALS = "meals"
    private const val K_SLEEP = "sleep"
    private const val K_SETTINGS = "settings"
}
