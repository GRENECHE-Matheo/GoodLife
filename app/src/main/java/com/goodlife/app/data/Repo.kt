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
    val privacyAcceptedAt: Long = 0L,
    val sounds: Boolean = true,
    // Sauvegarde automatique chiffrée : fichier choisi, clé dérivée du mot de passe (jamais le mot de passe)
    val backupUri: String = "",
    val backupKey: String = "",
    val backupName: String = "",
    val lastBackupAt: Long = 0L,
    val backupError: String = "",
    // Pas : désactivé tant que l'utilisateur ne l'a pas activé (permission « Activité physique »)
    val stepsEnabled: Boolean = false,
    val stepsSource: String = "sensor",   // sensor (capteur du téléphone) | hc (Health Connect)
    val stepsGoalMode: String = "auto",   // auto | manual | ia
    val stepsGoalManual: Int = 8000,
    val stepsGoalIa: Int = 0,
    val stepsGoalIaWhy: String = "",
    val lastNewsDay: String = "",
    // Amis : profil privé par défaut ; rien n'est partagé tant que « Profil public » est coupé
    val publicProfile: Boolean = false,
    val pseudo: String = "",
    val shareLevel: Boolean = true,
    val shareStreak: Boolean = true,
    val shareDex: Boolean = true,
    val streetPass: Boolean = false,
    // Jour d'arrivée des règles de score v0.7 (les jours d'avant gardent les anciennes règles)
    val scoreRulesFrom: String = ""
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
        .put("sounds", sounds)
        .put("backupUri", backupUri)
        .put("backupKey", backupKey)
        .put("backupName", backupName)
        .put("lastBackupAt", lastBackupAt)
        .put("backupError", backupError)
        .put("stepsEnabled", stepsEnabled)
        .put("stepsSource", stepsSource)
        .put("stepsGoalMode", stepsGoalMode)
        .put("stepsGoalManual", stepsGoalManual)
        .put("stepsGoalIa", stepsGoalIa)
        .put("stepsGoalIaWhy", stepsGoalIaWhy)
        .put("lastNewsDay", lastNewsDay)
        .put("publicProfile", publicProfile)
        .put("pseudo", pseudo)
        .put("shareLevel", shareLevel)
        .put("shareStreak", shareStreak)
        .put("shareDex", shareDex)
        .put("streetPass", streetPass)
        .put("scoreRulesFrom", scoreRulesFrom)

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
            privacyAcceptedAt = o.optLong("privacyAcceptedAt", 0L),
            sounds = o.optBoolean("sounds", true),
            backupUri = o.optString("backupUri"),
            backupKey = o.optString("backupKey"),
            backupName = o.optString("backupName"),
            lastBackupAt = o.optLong("lastBackupAt", 0L),
            backupError = o.optString("backupError"),
            stepsEnabled = o.optBoolean("stepsEnabled", false),
            stepsSource = o.optString("stepsSource", "sensor").ifBlank { "sensor" },
            stepsGoalMode = o.optString("stepsGoalMode", "auto").ifBlank { "auto" },
            stepsGoalManual = o.optInt("stepsGoalManual", 8000),
            stepsGoalIa = o.optInt("stepsGoalIa", 0),
            stepsGoalIaWhy = o.optString("stepsGoalIaWhy"),
            lastNewsDay = o.optString("lastNewsDay"),
            publicProfile = o.optBoolean("publicProfile", false),
            pseudo = o.optString("pseudo"),
            shareLevel = o.optBoolean("shareLevel", true),
            shareStreak = o.optBoolean("shareStreak", true),
            shareDex = o.optBoolean("shareDex", true),
            streetPass = o.optBoolean("streetPass", false),
            scoreRulesFrom = o.optString("scoreRulesFrom")
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

    private val _game = MutableStateFlow(GameState())
    val game: StateFlow<GameState> = _game

    private val _avatar = MutableStateFlow<ByteArray?>(null)
    val avatar: StateFlow<ByteArray?> = _avatar

    private val _settings = MutableStateFlow(Settings())
    val settings: StateFlow<Settings> = _settings

    private val _steps = MutableStateFlow(StepsData())
    val steps: StateFlow<StepsData> = _steps

    private val _dex = MutableStateFlow(DexState())
    val dex: StateFlow<DexState> = _dex

    private val _social = MutableStateFlow(SocialState())
    val social: StateFlow<SocialState> = _social

    /** Compteur de modifications des données (hors réglages), pour ne sauvegarder que si besoin. */
    @Volatile var revision = 0L
        private set
    @Volatile private var backedUpRevision = 0L

    private fun put(key: String, value: String?) {
        store.put(key, value)
        revision++
    }

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
        _game.value = store.get(K_GAME)?.let { runCatching { GameState.fromJson(JSONObject(it)) }.getOrNull() }
            ?: GameState()
        _avatar.value = store.get(K_AVATAR)?.let { runCatching { android.util.Base64.decode(it, android.util.Base64.NO_WRAP) }.getOrNull() }
        _settings.value = store.get(K_SETTINGS)?.let { runCatching { Settings.fromJson(JSONObject(it)) }.getOrNull() }
            ?: Settings()
        _steps.value = store.get(K_STEPS)?.let { runCatching { StepsData.fromJson(JSONObject(it)) }.getOrNull() }
            ?: StepsData()
        _dex.value = store.get(K_DEX)?.let { runCatching { DexState.fromJson(JSONObject(it)) }.getOrNull() }
            ?: DexState()
        _social.value = store.get(K_SOCIAL)?.let { runCatching { SocialState.fromJson(JSONObject(it)) }.getOrNull() }
            ?: SocialState()
        if (_settings.value.scoreRulesFrom.isBlank()) updateSettings { it.copy(scoreRulesFrom = localDay(0)) }
        // Profil créé avant les garde-fous santé : l'objectif « Perdre du poids » non autorisé repasse en « Maintenir »
        _profile.value?.let { p ->
            if (p.goal == Goal.PERTE && !com.goodlife.app.ai.Nutrition.weightLossAllowed(p)) {
                saveProfile(com.goodlife.app.ai.Nutrition.formulaTarget(p))
            }
        }
    }

    // ---------- Profil ----------
    @Synchronized
    fun saveProfile(p: Profile) {
        val old = _profile.value
        _profile.value = p
        put(K_PROFILE, p.toJson().toString())
        if (old == null || old.weightKg != p.weightKg) logWeight(p.weightKg)
    }

    // ---------- Jeu : séries, quiz, pesées ----------
    @Synchronized
    fun updateGame(transform: (GameState) -> GameState) {
        val g = transform(_game.value)
        _game.value = g
        put(K_GAME, g.toJson().toString())
    }

    fun logWeight(kg: Double) {
        val today = localDay(0)
        updateGame { g -> g.copy(weights = (g.weights.filterNot { it.first == today } + (today to kg)).sortedBy { it.first }.takeLast(400)) }
    }

    @Synchronized
    fun saveAvatar(jpeg: ByteArray?) {
        _avatar.value = jpeg
        put(K_AVATAR, jpeg?.let { android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP) })
    }

    // ---------- Repas ----------
    @Synchronized
    fun addMeal(meal: Meal) {
        // Garde-fou commun à toutes les sources (saisie, photo IA, code-barres, planning)
        val m = meal.copy(
            kcal = meal.kcal.coerceIn(0, MAX_MEAL_KCAL),
            proteinG = meal.proteinG.safeGrams(), carbsG = meal.carbsG.safeGrams(), fatG = meal.fatG.safeGrams()
        )
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
        put(K_MEALS, JSONArray().apply { kept.forEach { put(it.toJson()) } }.toString())
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
        put(K_SLEEP, JSONArray().apply { _sleep.value.forEach { put(it.toJson()) } }.toString())
    }

    // ---------- Emploi du temps des repas ----------
    @Synchronized
    fun addPlanned(m: PlannedMeal) {
        _plan.value = _plan.value + m
        persistPlan()
    }

    /** Ajoute un planning généré ; remplace ce qui était prévu (et pas encore mangé) sur ces jours et créneaux. */
    @Synchronized
    fun addPlannedWeek(meals: List<PlannedMeal>, dates: Set<String>, slots: Set<MealSlot>) {
        _plan.value = _plan.value.filterNot { it.date in dates && it.slot in slots && !it.done } + meals
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
        put(K_PLAN, JSONArray().apply { kept.forEach { put(it.toJson()) } }.toString())
    }

    // ---------- Pas ----------
    @Synchronized
    fun updateSteps(transform: (StepsData) -> StepsData) {
        val limit = localDay(-400)
        val d = transform(_steps.value).let { it.copy(days = it.days.filterKeys { k -> k >= limit }) }
        if (d == _steps.value) return
        _steps.value = d
        put(K_STEPS, d.toJson().toString())
    }

    // ---------- Nutridex ----------
    /** Débloque des entrées ; la photo (petite, JPEG) est gardée chiffrée pour les nouvelles. Renvoie les nouvelles. */
    @Synchronized
    fun unlockDex(ids: List<String>, photoJpeg: ByteArray?): List<String> {
        val fresh = ids.distinct().filter { com.goodlife.app.dex.Nutridex.byId(it) != null && it !in _dex.value.unlocked }
        if (fresh.isEmpty()) return emptyList()
        val now = System.currentTimeMillis()
        _dex.value = DexState(_dex.value.unlocked + fresh.associateWith { now })
        put(K_DEX, _dex.value.toJson().toString())
        if (photoJpeg != null) fresh.forEach { store.putBytes("dex_${it.replace('-', '_')}", photoJpeg) }
        return fresh
    }

    fun dexPhoto(id: String): ByteArray? = store.getBytes("dex_${id.replace('-', '_')}")

    // ---------- Amis (Tap to Sync, QR code, StreetPass) ----------
    enum class Received { NEW_FRIEND, UPDATED, NEW_ENCOUNTER, SEEN_AGAIN, IGNORED }

    @Synchronized
    private fun saveSocial(transform: (SocialState) -> SocialState) {
        val s = transform(_social.value)
        // Garde-fous : au plus 200 amis et 300 rencontres (les plus anciennes rencontres partent d'abord)
        val friends = s.people.filter { it.friend }.sortedByDescending { it.seenAt }.take(200)
        val others = s.people.filter { !it.friend }.sortedByDescending { it.seenAt }.take(300)
        val capped = s.copy(
            people = friends + others,
            cheersIn = s.cheersIn.sortedByDescending { it.at }.take(100),
            cheersOut = s.cheersOut.filter { it.at > System.currentTimeMillis() - 30L * 86_400_000 }.take(100)
        )
        _social.value = capped
        put(K_SOCIAL, capped.toJson().toString())
    }

    /**
     * Enregistre une carte reçue (déjà vérifiée par sa signature). Par Tap to Sync ou QR code, la personne
     * devient une amie ; par StreetPass, c'est une rencontre (ou une mise à jour si c'est déjà un ami).
     */
    @Synchronized
    fun receiveCard(card: com.goodlife.app.social.PlayerCard, via: String, myId: String): Received {
        val id = card.id
        if (id == myId || id in _social.value.blocked) return Received.IGNORED
        val now = System.currentTimeMillis()
        val old = _social.value.people.firstOrNull { it.id == id }
        val fresh = old == null || card.timestamp > old.cardTime
        val base = old ?: Person(id, android.util.Base64.encodeToString(card.publicKey, android.util.Base64.NO_WRAP), card.pseudo, via = via)
        val updated = (if (fresh) base.copy(
            pseudo = card.pseudo, level = card.level, streak = card.streak, bestStreak = card.bestStreak,
            dex = card.dex, cardTime = card.timestamp
        ) else base).copy(
            seenAt = now,
            friend = base.friend || via != "street",
            encounters = if (old != null && via == "street" && now - old.seenAt > 3_600_000L) old.encounters + 1 else base.encounters,
            via = if (old == null) via else base.via
        )
        // Encouragements qui me sont adressés (dédoublonnés)
        val newCheers = card.cheers.filter { it.target == myId }.map { CheerRecord(id, myId, it.message, it.day, now) }
            .filter { c -> _social.value.cheersIn.none { it.from == c.from && it.message == c.message && it.day == c.day } }
        saveSocial { st ->
            st.copy(people = st.people.filterNot { it.id == id } + updated, cheersIn = st.cheersIn + newCheers)
        }
        return when {
            old == null && via == "street" -> Received.NEW_ENCOUNTER
            old == null || (!old.friend && via != "street") -> Received.NEW_FRIEND
            via == "street" && !fresh -> Received.SEEN_AGAIN
            else -> Received.UPDATED
        }
    }

    fun setFriend(id: String, friend: Boolean) = saveSocial { st ->
        st.copy(people = st.people.map { if (it.id == id) it.copy(friend = friend) else it })
    }

    /** Bloque : la personne disparaît et ses cartes seront ignorées. */
    fun blockPerson(id: String) = saveSocial { st ->
        st.copy(people = st.people.filterNot { it.id == id }, blocked = st.blocked + id,
            cheersIn = st.cheersIn.filterNot { it.from == id })
    }

    fun unblockAll() = saveSocial { it.copy(blocked = emptySet()) }

    fun removePerson(id: String) = saveSocial { st -> st.copy(people = st.people.filterNot { it.id == id }) }

    /** Un encouragement par ami et par jour ; il partira avec ta carte à la prochaine synchro. */
    fun sendCheer(to: String, message: Int, myId: String): Boolean {
        val day = (System.currentTimeMillis() / 86_400_000L).toInt()
        if (_social.value.cheersOut.any { it.to == to && it.day == day }) return false
        saveSocial { it.copy(cheersOut = it.cheersOut + CheerRecord(myId, to, message, day, System.currentTimeMillis())) }
        return true
    }

    fun markCheersSeen() = saveSocial { st -> st.copy(cheersIn = st.cheersIn.map { it.copy(seen = true) }) }

    // ---------- Réglages ----------
    @Synchronized
    fun updateSettings(transform: (Settings) -> Settings) {
        val s = transform(_settings.value)
        _settings.value = s
        store.put(K_SETTINGS, s.toJson().toString())
    }

    // ---------- Sauvegarde chiffrée ----------
    fun backupNeeded(): Boolean = revision != backedUpRevision || _settings.value.lastBackupAt == 0L

    fun onBackupDone(atRevision: Long, error: String?) {
        if (error == null) backedUpRevision = atRevision
        updateSettings {
            if (error == null) it.copy(lastBackupAt = System.currentTimeMillis(), backupError = "")
            else it.copy(backupError = error)
        }
    }

    /** Contenu de la sauvegarde : toutes les données, sans la clé API ni les réglages propres au téléphone. */
    fun backupJson(): String {
        val s = _settings.value
        return JSONObject()
            .put("app", "GoodLife")
            .put("format", 1)
            .put("createdAt", System.currentTimeMillis())
            .put("profile", _profile.value?.toJson() ?: JSONObject.NULL)
            .put("meals", JSONArray().apply { _meals.value.forEach { put(it.toJson()) } })
            .put("mealPlan", JSONArray().apply { _plan.value.forEach { put(it.toJson()) } })
            .put("sleep", JSONArray().apply { _sleep.value.forEach { put(it.toJson()) } })
            .put("game", _game.value.toJson())
            .put("avatar", _avatar.value?.let { android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP) } ?: JSONObject.NULL)
            .put("steps", JSONObject().put("days", _steps.value.toJson().getJSONObject("days")))
            .put("dex", _dex.value.toJson())
            .put("social", _social.value.toJson())
            .put("dexPhotos", JSONObject().apply {
                _dex.value.unlocked.keys.forEach { id ->
                    dexPhoto(id)?.let { put(id, android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP)) }
                }
            })
            .put("settings", JSONObject()
                .put("themeMode", s.themeMode)
                .put("themeColor", s.themeColor)
                .put("sounds", s.sounds)
                .put("checkUpdates", s.checkUpdates)
                .put("privacyAcceptedAt", s.privacyAcceptedAt)
                .put("stepsGoalMode", s.stepsGoalMode)
                .put("stepsGoalManual", s.stepsGoalManual)
                .put("pseudo", s.pseudo))
            .toString()
    }

    /**
     * Remplace toutes les données par celles d'une sauvegarde. Les réglages de sécurité, l'IA (consentement
     * et clé) et la configuration de sauvegarde de ce téléphone sont conservés.
     */
    @Synchronized
    fun restore(json: String) {
        val o = JSONObject(json)
        require(o.optString("app") == "GoodLife") { "Ce fichier n'est pas une sauvegarde GoodLife." }
        val profile = o.optJSONObject("profile")?.let { Profile.fromJson(it) }
            ?: throw IllegalArgumentException("La sauvegarde ne contient pas de profil.")
        require(profile.age in com.goodlife.app.ai.Nutrition.MIN_AGE..110 && profile.weightKg in 25.0..350.0 && profile.heightCm in 100.0..250.0) {
            "Le profil de la sauvegarde est invalide."
        }
        _profile.value = profile
        put(K_PROFILE, profile.toJson().toString())
        _meals.value = o.optJSONArray("meals")?.mapObjects { Meal.fromJson(it) }?.sortedByDescending { it.timestamp } ?: emptyList()
        persistMeals()
        _plan.value = o.optJSONArray("mealPlan")?.mapObjects { PlannedMeal.fromJson(it) } ?: emptyList()
        persistPlan()
        _sleep.value = o.optJSONArray("sleep")?.mapObjects { SleepSession.fromJson(it) }?.sortedByDescending { it.start } ?: emptyList()
        persistSleep()
        val game = o.optJSONObject("game")?.let { GameState.fromJson(it) } ?: GameState()
        _game.value = game
        put(K_GAME, game.toJson().toString())
        val avatar = o.optString("avatar").takeIf { it.isNotBlank() && it != "null" }
        _avatar.value = avatar?.let { runCatching { android.util.Base64.decode(it, android.util.Base64.NO_WRAP) }.getOrNull() }
        put(K_AVATAR, avatar)
        // Pas : l'historique est restauré ; le compteur du capteur, propre au téléphone, repart de zéro
        val stepDays = o.optJSONObject("steps")?.let { StepsData.fromJson(it).days } ?: emptyMap()
        _steps.value = StepsData(days = stepDays)
        put(K_STEPS, _steps.value.toJson().toString())
        val social = o.optJSONObject("social")?.let { runCatching { SocialState.fromJson(it) }.getOrNull() } ?: SocialState()
        _social.value = social
        put(K_SOCIAL, social.toJson().toString())
        val dex = o.optJSONObject("dex")?.let { DexState.fromJson(it) }
            ?.let { d -> DexState(d.unlocked.filterKeys { com.goodlife.app.dex.Nutridex.byId(it) != null }) } ?: DexState()
        _dex.value = dex
        put(K_DEX, dex.toJson().toString())
        val photos = o.optJSONObject("dexPhotos")
        dex.unlocked.keys.forEach { id ->
            val b64 = photos?.optString(id).orEmpty()
            val bytes = if (b64.isBlank()) null else runCatching { android.util.Base64.decode(b64, android.util.Base64.NO_WRAP) }.getOrNull()
            store.putBytes("dex_${id.replace('-', '_')}", bytes?.takeIf { it.size < 200_000 })
        }
        val st = o.optJSONObject("settings")
        updateSettings { cur ->
            cur.copy(
                themeMode = st?.optString("themeMode")?.ifBlank { null } ?: cur.themeMode,
                themeColor = st?.optString("themeColor")?.ifBlank { null } ?: cur.themeColor,
                sounds = st?.optBoolean("sounds", cur.sounds) ?: cur.sounds,
                checkUpdates = st?.optBoolean("checkUpdates", cur.checkUpdates) ?: cur.checkUpdates,
                privacyAcceptedAt = st?.optLong("privacyAcceptedAt", 0L)?.takeIf { it > 0 } ?: cur.privacyAcceptedAt,
                pseudo = st?.optString("pseudo")?.ifBlank { null } ?: cur.pseudo,
                stepsGoalMode = st?.optString("stepsGoalMode")?.ifBlank { null } ?: cur.stepsGoalMode,
                stepsGoalManual = st?.optInt("stepsGoalManual", cur.stepsGoalManual) ?: cur.stepsGoalManual
            )
        }
    }

    @Synchronized
    fun wipeAll() {
        store.clear()
        _profile.value = null
        _meals.value = emptyList()
        _sleep.value = emptyList()
        _plan.value = emptyList()
        _game.value = GameState()
        _avatar.value = null
        _steps.value = StepsData()
        _dex.value = DexState()
        _social.value = SocialState()
        com.goodlife.app.social.Identity.reset()
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

    const val MAX_MEAL_KCAL = 5000
    private fun Double.safeGrams() = if (isNaN()) 0.0 else coerceIn(0.0, 1000.0)

    private const val K_PROFILE = "profile"
    private const val K_MEALS = "meals"
    private const val K_SLEEP = "sleep"
    private const val K_SETTINGS = "settings"
    private const val K_PLAN = "plan"
    private const val K_GAME = "game"
    private const val K_AVATAR = "avatar"
    private const val K_STEPS = "steps"
    private const val K_DEX = "dex"
    private const val K_SOCIAL = "social"

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
            .put("game", _game.value.toJson())
            .put("steps", _steps.value.toJson().getJSONObject("days"))
            .put("nutridex", _dex.value.toJson())
            .put("amis", _social.value.toJson())
            .put("settings", JSONObject()
                .put("aiEnabled", s.aiEnabled)
                .put("aiConsentAt", s.aiConsentAt)
                .put("privacyAcceptedAt", s.privacyAcceptedAt)
                .put("personalApiKeySaved", s.apiKey.isNotBlank())
                .put("checkUpdates", s.checkUpdates))
            .toString(2)
    }
}
