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

/** Jour local au format AAAA-MM-JJ, décalé de [offset] jours. */
fun localDay(offset: Int = 0): String {
    val c = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, offset) }
    return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(c.time)
}

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
    val checkUpdates: Boolean = true,
    val lastUpdateCheck: Long = 0L,
    val latestTag: String = "",
    val latestUrl: String = "",
    val latestApkUrl: String = "",
    val latestApkDigest: String = "",
    val dismissedTag: String = "",
    val aiEnabled: Boolean = false,
    val aiConsentAsked: Boolean = false,
    val aiConsentAt: Long = 0L,
    val privacyAcceptedAt: Long = 0L
) {

    fun toJson(): JSONObject = JSONObject()
        .put("apiKey", apiKey).put("model", model).put("sleepAuto", sleepAuto)
        .put("manualSleepStart", manualSleepStart)
        .put("lastSleepConfidence", lastSleepConfidence)
        .put("lastSleepConfidenceAt", lastSleepConfidenceAt)
        .put("appLock", appLock)
        .put("blockScreenshots", blockScreenshots)
        .put("themeMode", themeMode)
        .put("themeColor", themeColor)
        .put("checkUpdates", checkUpdates)
        .put("lastUpdateCheck", lastUpdateCheck)
        .put("latestTag", latestTag)
        .put("latestUrl", latestUrl)
        .put("latestApkUrl", latestApkUrl)
        .put("latestApkDigest", latestApkDigest)
        .put("dismissedTag", dismissedTag)
        .put("aiEnabled", aiEnabled)
        .put("aiConsentAsked", aiConsentAsked)
        .put("aiConsentAt", aiConsentAt)
        .put("privacyAcceptedAt", privacyAcceptedAt)

    companion object {
        const val DEFAULT_MODEL = "gemini-3.5-flash-lite"
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
            checkUpdates = o.optBoolean("checkUpdates", true),
            lastUpdateCheck = o.optLong("lastUpdateCheck", 0L),
            latestTag = o.optString("latestTag"),
            latestUrl = o.optString("latestUrl"),
            latestApkUrl = o.optString("latestApkUrl"),
            latestApkDigest = o.optString("latestApkDigest"),
            dismissedTag = o.optString("dismissedTag"),
            aiEnabled = o.optBoolean("aiEnabled", false),
            aiConsentAsked = o.optBoolean("aiConsentAsked", false),
            aiConsentAt = o.optLong("aiConsentAt", 0L),
            privacyAcceptedAt = o.optLong("privacyAcceptedAt", 0L)
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

    private val _plan = MutableStateFlow<List<PlannedMeal>>(emptyList())
    val plan: StateFlow<List<PlannedMeal>> = _plan

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
        _plan.value = store.get(K_PLAN)?.let { s ->
            runCatching { JSONArray(s).mapObjects { PlannedMeal.fromJson(it) } }.getOrNull()
        } ?: emptyList()
        _settings.value = store.get(K_SETTINGS)?.let { runCatching { Settings.fromJson(JSONObject(it)) }.getOrNull() }
            ?: Settings()
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

    // ---------- Emploi du temps des repas ----------
    @Synchronized
    fun addPlanned(m: PlannedMeal) {
        _plan.value = _plan.value + m
        persistPlan()
    }

    @Synchronized
    fun updatePlanned(m: PlannedMeal) {
        _plan.value = _plan.value.map { if (it.id == m.id) m else it }
        persistPlan()
    }

    @Synchronized
    fun deletePlanned(id: Long) {
        _plan.value = _plan.value.filterNot { it.id == id }
        persistPlan()
    }

    private fun persistPlan() {
        // on garde 3 mois en arrière maximum
        val limit = localDay(-90)
        val kept = _plan.value.filter { it.date >= limit }
        _plan.value = kept
        store.put(K_PLAN, JSONArray().apply { kept.forEach { put(it.toJson()) } }.toString())
    }

    // ---------- Réglages ----------
    @Synchronized
    fun updateSettings(transform: (Settings) -> Settings) {
        val s = transform(_settings.value)
        _settings.value = s
        store.put(K_SETTINGS, s.toJson().toString())
    }

    @Synchronized
    fun wipeAll() {
        store.clear()
        _profile.value = null
        _meals.value = emptyList()
        _sleep.value = emptyList()
        _plan.value = emptyList()
        _settings.value = Settings()
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
    private const val K_PLAN = "plan"

    /** L'IA n'est utilisable qu'avec consentement explicite et pour les 18 ans et plus (conditions Google). */
    fun aiAllowed(): Boolean = _settings.value.aiEnabled && (_profile.value?.age ?: 0) >= 18

    /** Export RGPD (droit à la portabilité) : toutes les données locales, sans la clé API. */
    fun exportJson(): String {
        val s = _settings.value
        return JSONObject()
            .put("app", "GoodLife")
            .put("exportedAt", System.currentTimeMillis())
            .put("profile", _profile.value?.toJson() ?: JSONObject.NULL)
            .put("meals", JSONArray().apply { _meals.value.forEach { put(it.toJson()) } })
            .put("mealPlan", JSONArray().apply { _plan.value.forEach { put(it.toJson()) } })
            .put("sleep", JSONArray().apply { _sleep.value.forEach { put(it.toJson()) } })
            .put("settings", JSONObject()
                .put("aiEnabled", s.aiEnabled)
                .put("aiConsentAt", s.aiConsentAt)
                .put("privacyAcceptedAt", s.privacyAcceptedAt)
                .put("personalApiKeySaved", s.apiKey.isNotBlank())
                .put("checkUpdates", s.checkUpdates))
            .toString(2)
    }
}
