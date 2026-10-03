package com.goodlife.app.data

import com.goodlife.app.i18n.t

import android.content.Context
import kotlinx.coroutines.launch
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
    val stepsGoalIaDay: String = "",          // jour du dernier calcul par l'IA (une fois par jour)
    val newsThemes: String = "food,sport,insolite,anecdote",   // thèmes d'actus choisis (vide = pas d'actus)
    val waterGoalMl: Int = 1500,              // repère : environ 1,5 L de boisson par jour pour un adulte
    val coachHistory: Boolean = true,         // garder l'historique des conversations du coach (chiffré, sur le téléphone)
    val planNotes: String = "",               // précisions pour le planning de la semaine (goûts du foyer…)
    val planShopping: Boolean = true,         // préparer la liste de courses avec le planning de la semaine
    val waterGoalMode: String = "ia",         // « ia » : calculé chaque jour par l'IA (si activée), sinon objectif fixe
    val waterGoalIa: Int = 0,                 // dernier objectif conseillé par l'IA (ml)
    val waterGoalIaWhy: String = "",
    val waterGoalIaDay: String = "",          // jour du dernier calcul (une fois par jour)
    val notifWater: Boolean = false,          // rappel d'hydratation l'après-midi
    val guardSnoozeUntil: Long = 0L,          // garde-fou bienveillant mis en pause jusqu'à cette date
    val language: String = "system",          // langue de l'app : « system » (celle du téléphone), « fr » ou « en »
    val lastNewsDay: String = "",
    // Amis : profil privé par défaut ; rien n'est partagé tant que « Profil public » est coupé
    val publicProfile: Boolean = false,
    val pseudo: String = "",
    val shareLevel: Boolean = true,
    val shareStreak: Boolean = true,
    val shareDex: Boolean = true,
    val shareWeek: Boolean = true,      // bilan de la semaine (jours validés, pas, XP) pour le défi entre amis
    val streetPass: Boolean = false,
    // Croisements : clé secrète (base64) donnée seulement aux amis, pour qu'eux seuls te reconnaissent en Bluetooth
    val crossSecret: String = "",
    // Jour d'arrivée des règles de score v0.7 (les jours d'avant gardent les anciennes règles)
    val scoreRulesFrom: String = "",
    val fridgeAutoRemove: Boolean = false,    // repas photographié : proposer de retirer ses aliments de « Mon frigo »
    val richScoreFrom: String = "",           // à partir de ce jour, score enrichi : calories, protéines, repas (v0.12)
    val foodOnlyFrom: String = "",            // à partir de ce jour, le score du jour ne compte que l'alimentation (v0.9.6)
    val routeStyle: String = "BALANCED",     // type de trajet choisi sur la carte (plus court, petites routes…)
    val preferredOuting: String = "RUN",    // activité préférée (course / marche / vélo), pré-choisie sur la carte
    // Notifications du coach : toutes coupées tant que la personne ne les a pas acceptées
    val notifMorning: Boolean = false,      // bilan de la veille, le matin
    val notifNoon: Boolean = false,         // repas prévu à midi + petit mot
    val notifEvening: Boolean = false,      // série en danger, le soir
    val notifWeekly: Boolean = false,       // bilan de la semaine, le dimanche
    val notifAsked: Boolean = false,
    val featuresAsked: Boolean = false,     // l'écran « Tes options » a été proposé (inscription, ou une fois pour les anciens comptes)        // la question a déjà été posée (inscription ou accueil)
    val lastNudgeDay: String = "",          // dernier « tu nous manques », pour ne pas insister
    val coachConsentAt: Long = 0L           // accord pour envoyer au coach le résumé de ses chiffres (0 = pas encore)
) {
    val anyNotif: Boolean get() = notifMorning || notifNoon || notifEvening || notifWeekly

    /** Jamais les secrets dans un texte (le toString() automatique d'une data class les écrirait en clair). */
    override fun toString(): String = "Settings(apiKey=${if (apiKey.isEmpty()) "" else "•••"}, backupKey=${if (backupKey.isEmpty()) "" else "•••"}, model=$model)"


    fun toJson(): JSONObject = JSONObject()
        .put("model", model).put("sleepAuto", sleepAuto)
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
        .put("backupName", backupName)
        .put("lastBackupAt", lastBackupAt)
        .put("backupError", backupError)
        .put("stepsEnabled", stepsEnabled)
        .put("stepsSource", stepsSource)
        .put("stepsGoalMode", stepsGoalMode)
        .put("stepsGoalManual", stepsGoalManual)
        .put("stepsGoalIa", stepsGoalIa)
        .put("stepsGoalIaWhy", stepsGoalIaWhy)
        .put("stepsGoalIaDay", stepsGoalIaDay)
        .put("newsThemes", newsThemes)
        .put("waterGoalMl", waterGoalMl)
        .put("coachHistory", coachHistory)
        .put("planNotes", planNotes)
        .put("planShopping", planShopping)
        .put("waterGoalMode", waterGoalMode)
        .put("waterGoalIa", waterGoalIa)
        .put("waterGoalIaWhy", waterGoalIaWhy)
        .put("waterGoalIaDay", waterGoalIaDay)
        .put("notifWater", notifWater)
        .put("guardSnoozeUntil", guardSnoozeUntil)
        .put("language", language)
        .put("lastNewsDay", lastNewsDay)
        .put("publicProfile", publicProfile)
        .put("pseudo", pseudo)
        .put("shareLevel", shareLevel)
        .put("shareStreak", shareStreak)
        .put("shareDex", shareDex)
        .put("shareWeek", shareWeek)
        .put("streetPass", streetPass)
        .put("crossSecret", crossSecret)
        .put("scoreRulesFrom", scoreRulesFrom)
        .put("foodOnlyFrom", foodOnlyFrom)
        .put("richScoreFrom", richScoreFrom)
        .put("fridgeAutoRemove", fridgeAutoRemove)
        .put("preferredOuting", preferredOuting)
        .put("routeStyle", routeStyle)
        .put("featuresAsked", featuresAsked)
        .put("notifMorning", notifMorning)
        .put("notifNoon", notifNoon)
        .put("notifEvening", notifEvening)
        .put("notifWeekly", notifWeekly)
        .put("notifAsked", notifAsked)
        .put("lastNudgeDay", lastNudgeDay)
        .put("coachConsentAt", coachConsentAt)

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
            stepsGoalIaDay = o.optString("stepsGoalIaDay"),
            newsThemes = if (o.has("newsThemes")) o.optString("newsThemes") else "food,sport,insolite,anecdote",
            waterGoalMl = o.optInt("waterGoalMl", 1500).coerceIn(500, 5000),
            coachHistory = o.optBoolean("coachHistory", true),
            planNotes = o.optString("planNotes").take(400),
            planShopping = o.optBoolean("planShopping", true),
            waterGoalMode = o.optString("waterGoalMode", "ia").takeIf { it == "ia" || it == "fixed" } ?: "ia",
            waterGoalIa = o.optInt("waterGoalIa", 0).coerceIn(0, 5000),
            waterGoalIaWhy = o.optString("waterGoalIaWhy"),
            waterGoalIaDay = o.optString("waterGoalIaDay"),
            notifWater = o.optBoolean("notifWater", false),
            guardSnoozeUntil = o.optLong("guardSnoozeUntil", 0L),
            language = o.optString("language", "system").takeIf { it in setOf("system", "fr", "en") } ?: "system",
            lastNewsDay = o.optString("lastNewsDay"),
            publicProfile = o.optBoolean("publicProfile", false),
            pseudo = o.optString("pseudo"),
            shareLevel = o.optBoolean("shareLevel", true),
            shareStreak = o.optBoolean("shareStreak", true),
            shareDex = o.optBoolean("shareDex", true),
            shareWeek = o.optBoolean("shareWeek", true),
            streetPass = o.optBoolean("streetPass", false),
            crossSecret = o.optString("crossSecret"),
            scoreRulesFrom = o.optString("scoreRulesFrom"),
            foodOnlyFrom = o.optString("foodOnlyFrom"),
            richScoreFrom = o.optString("richScoreFrom"),
            fridgeAutoRemove = o.optBoolean("fridgeAutoRemove", false),
            preferredOuting = o.optString("preferredOuting", "RUN").ifBlank { "RUN" },
            routeStyle = o.optString("routeStyle", "BALANCED").ifBlank { "BALANCED" },
            featuresAsked = o.optBoolean("featuresAsked", false),
            notifMorning = o.optBoolean("notifMorning", false),
            notifNoon = o.optBoolean("notifNoon", false),
            notifEvening = o.optBoolean("notifEvening", false),
            notifWeekly = o.optBoolean("notifWeekly", false),
            notifAsked = o.optBoolean("notifAsked", false),
            lastNudgeDay = o.optString("lastNudgeDay"),
            coachConsentAt = o.optLong("coachConsentAt", 0L)
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

    private val _sport = MutableStateFlow(SportState())
    val sport: StateFlow<SportState> = _sport

    private val _outings = MutableStateFlow<List<Outing>>(emptyList())
    val outings: StateFlow<List<Outing>> = _outings

    /** Eau bue par jour (ml) et ressenti du jour (humeur, énergie). */
    private val _water = MutableStateFlow<Map<String, Int>>(emptyMap())
    val water: StateFlow<Map<String, Int>> = _water
    private val _feelings = MutableStateFlow<Map<String, Feeling>>(emptyMap())
    val feelings: StateFlow<Map<String, Feeling>> = _feelings

    /** Repas favoris (modèles à rajouter en un appui), valeurs telles qu'elles ont été enregistrées. */
    private val _favMeals = MutableStateFlow<List<Meal>>(emptyList())
    val favMeals: StateFlow<List<Meal>> = _favMeals

    /** Compteur de modifications des données (hors réglages), pour ne sauvegarder que si besoin. */
    @Volatile var revision = 0L
        private set
    @Volatile private var backedUpRevision = 0L

    private fun put(key: String, value: String?) {
        store.put(key, value)
        revision++
    }

    var appContext: Context? = null
        private set

    @Synchronized
    fun init(context: Context) {
        if (::store.isInitialized) return
        appContext = context.applicationContext
        com.goodlife.app.ai.AppIdentity.init(context.applicationContext)
        store = SecureStore(context.applicationContext)
        // Langue choisie appliquée avant tout le reste (les libellés sont traduits dès leur premier usage)
        com.goodlife.app.i18n.Lang.apply(
            store.get(K_SETTINGS)?.let { runCatching { JSONObject(it).optString("language", "system") }.getOrNull() } ?: "system"
        )
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
        loadSecrets()
        _steps.value = store.get(K_STEPS)?.let { runCatching { StepsData.fromJson(JSONObject(it)) }.getOrNull() }
            ?: StepsData()
        _dex.value = store.get(K_DEX)?.let { runCatching { DexState.fromJson(JSONObject(it)) }.getOrNull() }
            ?: DexState()
        _social.value = store.get(K_SOCIAL)?.let { runCatching { SocialState.fromJson(JSONObject(it)) }.getOrNull() }
            ?: SocialState()
        _sport.value = store.get(K_SPORT)?.let { runCatching { SportState.fromJson(JSONObject(it)) }.getOrNull() }
            ?: SportState()
        _outings.value = store.get(K_OUTINGS)?.let { s -> runCatching { JSONArray(s).mapObjects { Outing.fromJson(it) } }.getOrNull() }
            ?: emptyList()
        _favMeals.value = store.get(K_FAVS)?.let { s -> runCatching { JSONArray(s).mapObjects { Meal.fromJson(it) } }.getOrNull() } ?: emptyList()
        _water.value = store.get(K_WATER)?.let { waterFromJson(it) } ?: emptyMap()
        _feelings.value = store.get(K_FEEL)?.let { feelingsFromJson(it) } ?: emptyMap()
        if (_settings.value.scoreRulesFrom.isBlank()) updateSettings { it.copy(scoreRulesFrom = localDay(0)) }
        if (_settings.value.foodOnlyFrom.isBlank()) updateSettings { it.copy(foodOnlyFrom = localDay(0)) }
        if (_settings.value.richScoreFrom.isBlank()) updateSettings { it.copy(richScoreFrom = localDay(0)) }
        // Repas prévus les jours passés et jamais validés (✓) : le chef les retire du planning
        if (_plan.value.any { it.date < localDay(0) && !it.done }) {
            _plan.value = _plan.value.filterNot { it.date < localDay(0) && !it.done }
            persistPlan()
        }
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

    // ---------- Hydratation et ressenti ----------
    private fun waterFromJson(s: String): Map<String, Int> = runCatching {
        val o = JSONObject(s); o.keys().asSequence().associateWith { o.optInt(it).coerceIn(0, 10_000) }
    }.getOrDefault(emptyMap())

    private fun feelingsFromJson(s: String): Map<String, Feeling> = runCatching {
        val o = JSONObject(s)
        o.keys().asSequence().associateWith { k -> o.getJSONObject(k).let { Feeling(it.optInt("mood").coerceIn(1, 5), it.optInt("energy").coerceIn(1, 5)) } }
    }.getOrDefault(emptyMap())

    private fun waterJson() = JSONObject().apply { _water.value.forEach { (k, v) -> put(k, v) } }
    private fun feelingsJson() = JSONObject().apply { _feelings.value.forEach { (k, f) -> put(k, JSONObject().put("mood", f.mood).put("energy", f.energy)) } }

    /** Ajoute (ou retire, si négatif) de l'eau bue aujourd'hui. */
    @Synchronized
    fun addWater(ml: Int) {
        val day = localDay(0)
        val limit = localDay(-400)
        _water.value = (_water.value + (day to ((_water.value[day] ?: 0) + ml).coerceIn(0, 6000))).filterKeys { it >= limit }
        put(K_WATER, waterJson().toString())
    }

    @Synchronized
    fun setFeeling(mood: Int, energy: Int) {
        val limit = localDay(-400)
        _feelings.value = (_feelings.value + (localDay(0) to Feeling(mood.coerceIn(1, 5), energy.coerceIn(1, 5)))).filterKeys { it >= limit }
        put(K_FEEL, feelingsJson().toString())
    }

    /** Ajoute ou retire un repas des favoris (reconnu par son nom). */
    @Synchronized
    fun toggleFavorite(m: Meal) {
        val key = m.name.trim().lowercase()
        _favMeals.value = if (_favMeals.value.any { it.name.trim().lowercase() == key }) _favMeals.value.filterNot { it.name.trim().lowercase() == key }
                          else (_favMeals.value + m.copy(id = 0, timestamp = 0)).takeLast(30)
        put(K_FAVS, JSONArray().apply { _favMeals.value.forEach { put(it.toJson()) } }.toString())
    }

    fun isFavorite(name: String): Boolean = _favMeals.value.any { it.name.trim().lowercase() == name.trim().lowercase() }

    /**
     * Repas fréquents : ceux notés au moins 2 fois ces 60 derniers jours (hors favoris), avec les valeurs
     * de la dernière fois. Aucun calcul : on reprend exactement ce qui avait été enregistré.
     */
    fun frequentMeals(limit: Int = 8): List<Meal> {
        val since = System.currentTimeMillis() - 60L * 86_400_000L
        return _meals.value.filter { it.timestamp >= since && !isFavorite(it.name) }
            .groupBy { it.name.trim().lowercase() }.values
            .filter { it.size >= 2 }
            .sortedByDescending { it.size }
            .take(limit)
            .map { list -> list.maxBy { it.timestamp } }
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

    /** Retire les repas prévus et pas encore mangés de ces jours (« Vider la semaine »). */
    @Synchronized
    fun clearPlanned(dates: Set<String>) {
        _plan.value = _plan.value.filterNot { it.date in dates && !it.done }
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

    // ---------- Amis (Tap to Sync, QR code, croisements) ----------
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
     * devient une amie ; par croisement (Bluetooth), c'est une rencontre (ou une mise à jour si c'est déjà un ami).
     */
    @Synchronized
    fun receiveCard(card: com.goodlife.app.social.PlayerCard, via: String, myId: String): Received {
        val id = card.id
        if (id == myId || id in _social.value.blocked) return Received.IGNORED
        val now = System.currentTimeMillis()
        val old = _social.value.people.firstOrNull { it.id == id }
        // Même identifiant mais autre clé : ce n'est pas la même personne (on garde la carte qu'on connaît)
        if (old != null && old.publicKey.isNotBlank() &&
            old.publicKey != android.util.Base64.encodeToString(card.publicKey, android.util.Base64.NO_WRAP)) return Received.IGNORED
        val fresh = old == null || card.timestamp > old.cardTime
        val base = old ?: Person(id, android.util.Base64.encodeToString(card.publicKey, android.util.Base64.NO_WRAP), card.pseudo, via = via)
        val updated = (if (fresh) base.copy(
            pseudo = card.pseudo, level = card.level, streak = card.streak, bestStreak = card.bestStreak,
            dex = card.dex, cardTime = card.timestamp,
            weekId = card.week?.week ?: -1, weekDays = card.week?.days ?: 0, weekSteps = card.week?.steps ?: 0, weekXp = card.week?.xp ?: 0
        ) else base).copy(
            seenAt = now,
            friend = base.friend || via != "street",
            encounters = if (old != null && via == "street" && now - old.seenAt > 3_600_000L) old.encounters + 1 else base.encounters,
            via = if (old == null) via else base.via
        )
        val withSecret = card.crossSecret?.let { updated.copy(crossSecret = android.util.Base64.encodeToString(it, android.util.Base64.NO_WRAP)) } ?: updated
        // Encouragements qui me sont adressés (dédoublonnés)
        val newCheers = card.cheers.filter { it.target == myId }.map { CheerRecord(id, myId, it.message, it.day, now) }
            .filter { c -> _social.value.cheersIn.none { it.from == c.from && it.message == c.message && it.day == c.day } }
        saveSocial { st ->
            st.copy(people = st.people.filterNot { it.id == id } + withSecret, cheersIn = st.cheersIn + newCheers)
        }
        return when {
            old == null && via == "street" -> Received.NEW_ENCOUNTER
            old == null || (!old.friend && via != "street") -> Received.NEW_FRIEND
            via == "street" && !fresh -> Received.SEEN_AGAIN
            else -> Received.UPDATED
        }
    }

    /**
     * Joueur inconnu croisé (anonyme : identifiant qui change toutes les 15 min). false s'il a déjà été compté sur
     * ce créneau. Les 200 dernières rencontres sont gardées.
     */
    @Synchronized
    fun addAnonEncounter(rid: String, level: Int?): Boolean {
        if (_social.value.anon.any { it.rid == rid }) return false
        saveSocial { st -> st.copy(anon = (st.anon + AnonEncounter(rid, level, System.currentTimeMillis())).takeLast(200)) }
        return true
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
        // Croisements actifs : l'encouragement part dès qu'on croise cet ami (balise refaite tout de suite)
        appContext?.let { com.goodlife.app.social.StreetPass.refresh(it) }
        return true
    }

    fun markCheersSeen() = saveSocial { st -> st.copy(cheersIn = st.cheersIn.map { it.copy(seen = true) }) }

    // ---------- Sport ----------
    @Synchronized
    private fun saveSport(s: SportState) {
        _sport.value = s
        put(K_SPORT, s.toJson().toString())
    }

    /** Nouveau programme (ou null = supprimé). Les séances déjà faites et l'XP gagnée restent. */
    fun setProgram(p: SportProgram?) = saveSport(SportState(program = p, done = _sport.value.done))

    /** Lance le chrono d'une séance du programme (une seule à la fois). */
    fun startSession(index: Int) = saveSport(_sport.value.copy(running = index, runningSince = System.currentTimeMillis()))

    fun cancelSession() = saveSport(_sport.value.copy(running = -1, runningSince = 0L))

    /**
     * Changements proposés par le chef et validés : chaque séance remplace celle du même jour (ou s'ajoute), et les
     * jours listés sont retirés. Sans programme, un programme « du chef » est créé avec ces séances.
     */
    @Synchronized
    fun applySessions(sessions: List<SportSession>, removeDays: List<Int>) {
        val st = _sport.value
        val old = st.program
        val kept = (old?.sessions ?: emptyList()).filter { s -> s.day !in removeDays && sessions.none { it.day == s.day } }
        val all = (kept + sessions).sortedBy { it.day }
        if (all.isEmpty()) { saveSport(st.copy(program = null, running = -1, runningSince = 0L)); return }
        val program = (old ?: SportProgram(System.currentTimeMillis(), t("Programme du chef"), t("Débutant"), emptyList(), all.size,
            all.map { it.minutes }.average().toInt(), "", "", all, "")).copy(sessions = all, daysPerWeek = all.size)
        // Les index de séances changent : on arrête un éventuel chrono en cours
        saveSport(st.copy(program = program, running = -1, runningSince = 0L))
    }

    /** Ajoute de l'XP sport au jour donné, sans dépasser le plafond quotidien. Renvoie l'XP réellement gagnée. */
    @Synchronized
    fun addSportXp(xp: Int, day: String = localDay(0)): Int {
        val cur = _game.value.sportXp[day] ?: 0
        val gained = xp.coerceAtMost(com.goodlife.app.game.Game.SPORT_XP_PER_DAY - cur).coerceAtLeast(0)
        if (gained > 0) updateGame { g -> g.copy(sportXp = g.sportXp + (day to cur + gained)) }
        return gained
    }

    /**
     * Fin de la séance lancée : l'XP dépend du temps réellement passé (voir Game.sessionXp). Une séance compte une
     * fois par jour. Renvoie (XP gagnée, minutes), ou null si aucune séance n'était lancée ou si elle est déjà faite.
     */
    @Synchronized
    fun finishSession(plannedMinutes: Int): Pair<Int, Int>? {
        val st = _sport.value
        if (st.running < 0 || st.runningSince <= 0L) return null
        val key = "${localDay(0)}#${st.running}"
        val minutes = ((System.currentTimeMillis() - st.runningSince) / 60_000L).toInt().coerceAtLeast(0)
        if (key in st.done) { cancelSession(); return null }
        val xpEarned = com.goodlife.app.game.Game.sessionXp(minutes, plannedMinutes)
        val limit = localDay(-120)
        // Moins de 5 minutes : la séance n'est pas comptée (on peut la refaire plus tard)
        val done = if (xpEarned > 0) st.done.filter { it.substringBefore('#') >= limit }.toSet() + key else st.done
        saveSport(st.copy(done = done, running = -1, runningSince = 0L))
        return (if (xpEarned > 0) addSportXp(xpEarned) else 0) to minutes
    }

    // ---------- Sorties GPS ----------
    private fun persistOutings() = put(K_OUTINGS, JSONArray().apply { _outings.value.forEach { put(it.toJson()) } }.toString())

    /** Enregistre une sortie et son tracé (chiffré). Au plus 500 sorties gardées. */
    @Synchronized
    fun addOuting(o: Outing, track: List<TrackPoint>) {
        store.putBytes("trk_${o.id}", Outing.encodeTrack(track).toByteArray(Charsets.UTF_8))
        val all = (_outings.value + o).sortedByDescending { it.start }
        all.drop(500).forEach { store.putBytes("trk_${it.id}", null) }
        _outings.value = all.take(500)
        persistOutings()
    }

    fun track(id: Long): List<TrackPoint> = store.getBytes("trk_$id")?.let { Outing.decodeTrack(String(it, Charsets.UTF_8)) } ?: emptyList()

    @Synchronized
    fun deleteOuting(id: Long) {
        store.putBytes("trk_$id", null)
        _outings.value = _outings.value.filterNot { it.id == id }
        persistOutings()
    }

    @Synchronized
    fun renameOuting(id: Long, name: String) {
        _outings.value = _outings.value.map { if (it.id == id || it.routeId == id) it.copy(name = name.take(40)) else it }
        persistOutings()
    }

    /** Tracé de référence d'un parcours (celui d'un autre passage si la première sortie a été supprimée). */
    fun routeTrack(routeId: Long): List<TrackPoint> = track(routeId).ifEmpty {
        _outings.value.filter { it.routeId == routeId }.sortedBy { it.start }.asSequence()
            .map { track(it.id) }.firstOrNull { it.size >= 2 } ?: emptyList()
    }

    /** Meilleur temps (en mouvement) sur un parcours, parmi les sorties valides. */
    fun bestOn(routeId: Long): Outing? = _outings.value.filter { it.routeId == routeId && it.valid }.minByOrNull { it.movingMs }

    // ---------- Petits états annexes (actus du jour…) ----------
    fun getExtra(key: String): String? = store.get("x_$key")
    fun putExtra(key: String, value: String?) = store.put("x_$key", value)

    // ---------- Réglages ----------
    /** Attend que tout soit écrit sur le disque (avant de redémarrer l'app). */
    fun flush() = store.flush()

    /**
     * Secrets (clé API, clé de la sauvegarde) : dans un coffre à part, chiffré par une clé de la puce de sécurité,
     * utilisable seulement téléphone déverrouillé. Les anciennes versions les gardaient dans les réglages : ils sont
     * déplacés une fois, puis effacés des réglages.
     */
    private fun loadSecrets() {
        val s = _settings.value
        if (s.apiKey.isNotEmpty() || s.backupKey.isNotEmpty()) {
            // Téléphone verrouillé (app lancée en arrière-plan) : on réessaiera au prochain démarrage
            val moved = runCatching {
                if (s.apiKey.isNotEmpty()) store.putSecret(S_API_KEY, s.apiKey)
                if (s.backupKey.isNotEmpty()) store.putSecret(S_BACKUP_KEY, s.backupKey)
            }.isSuccess
            if (moved) store.put(K_SETTINGS, s.toJson().toString())   // réécrits sans les secrets
            return
        }
        _settings.value = s.copy(apiKey = store.getSecret(S_API_KEY) ?: "", backupKey = store.getSecret(S_BACKUP_KEY) ?: "")
    }

    /** À l'ouverture de l'app : relit les secrets si l'app avait démarré téléphone verrouillé (ils étaient illisibles). */
    fun reloadSecretsIfNeeded() {
        if (!::store.isInitialized) return
        val s = _settings.value
        if ((s.apiKey.isEmpty() && store.hasSecret(S_API_KEY)) || (s.backupKey.isEmpty() && store.hasSecret(S_BACKUP_KEY))) {
            _settings.value = s.copy(
                apiKey = s.apiKey.ifEmpty { store.getSecret(S_API_KEY) ?: "" },
                backupKey = s.backupKey.ifEmpty { store.getSecret(S_BACKUP_KEY) ?: "" }
            )
        }
    }

    @Synchronized
    fun updateSettings(transform: (Settings) -> Settings) {
        val old = _settings.value
        val s = transform(old)
        _settings.value = s
        // Un secret change (clé remplacée ou retirée) : nouveau coffre, l'ancien est détruit sans trace
        if (s.apiKey != old.apiKey || s.backupKey != old.backupKey) {
            runCatching { store.replaceSecrets(mapOf(S_API_KEY to s.apiKey, S_BACKUP_KEY to s.backupKey)) }
        }
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
            .put("sport", _sport.value.toJson())
            .put("outings", JSONArray().apply { _outings.value.forEach { put(it.toJson()) } })
            .put("favMeals", JSONArray().apply { _favMeals.value.forEach { put(it.toJson()) } })
            .put("water", waterJson())
            .put("feelings", feelingsJson())
            .put("extras", JSONObject().apply { BACKUP_EXTRAS.forEach { k -> getExtra(k)?.let { put(k, it) } } })
            .put("tracks", JSONObject().apply { _outings.value.forEach { o -> put(o.id.toString(), Outing.encodeTrack(track(o.id))) } })
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
        require(o.optString("app") == "GoodLife") { t("Ce fichier n'est pas une sauvegarde GoodLife.") }
        val profile = o.optJSONObject("profile")?.let { Profile.fromJson(it) }
            ?: throw IllegalArgumentException(t("La sauvegarde ne contient pas de profil."))
        require(profile.age in com.goodlife.app.ai.Nutrition.MIN_AGE..110 && profile.weightKg in 25.0..350.0 && profile.heightCm in 100.0..250.0) {
            t("Le profil de la sauvegarde est invalide.")
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
        val sport = o.optJSONObject("sport")?.let { runCatching { SportState.fromJson(it) }.getOrNull() } ?: SportState()
        saveSport(sport)
        _outings.value.forEach { store.putBytes("trk_${it.id}", null) }
        val outs = o.optJSONArray("outings")?.mapObjects { Outing.fromJson(it) }?.take(500) ?: emptyList()
        val tracks = o.optJSONObject("tracks")
        outs.forEach { out -> tracks?.optString(out.id.toString())?.takeIf { it.isNotBlank() }?.let {
            store.putBytes("trk_${out.id}", Outing.encodeTrack(Outing.decodeTrack(it)).toByteArray(Charsets.UTF_8))
        } }
        _outings.value = outs
        persistOutings()
        _favMeals.value = o.optJSONArray("favMeals")?.mapObjects { Meal.fromJson(it) }?.take(30) ?: emptyList()
        put(K_FAVS, JSONArray().apply { _favMeals.value.forEach { put(it.toJson()) } }.toString())
        _water.value = o.optJSONObject("water")?.let { waterFromJson(it.toString()) } ?: emptyMap()
        put(K_WATER, waterJson().toString())
        _feelings.value = o.optJSONObject("feelings")?.let { feelingsFromJson(it.toString()) } ?: emptyMap()
        put(K_FEEL, feelingsJson().toString())
        o.optJSONObject("extras")?.let { x -> BACKUP_EXTRAS.forEach { k -> x.optString(k).takeIf { it.isNotBlank() }?.let { putExtra(k, it) } } }
        Fridge.reload()
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
        _sport.value = SportState()
        _outings.value = emptyList()
        _favMeals.value = emptyList()
        _water.value = emptyMap()
        _feelings.value = emptyMap()
        com.goodlife.app.social.Identity.reset()
        _settings.value = Settings()
        Fridge.reload()
        appContext?.let { ctx ->
            // Tout le cache : tuiles d'itinéraires et d'altitude (elles trahissent les zones parcourues), photos, mises à jour
            runCatching { ctx.cacheDir.listFiles()?.forEach { it.deleteRecursively() } }
            // Zones hors ligne et cache de la carte (leur nom peut être personnel : « Chez moi »…)
            runCatching { com.goodlife.app.track.OfflineMaps.deleteAll(ctx) }
            // Croisements : le service s'arrête tout de suite (il ne diffuse plus l'ancienne carte)
            runCatching { com.goodlife.app.social.StreetPass.sync(ctx) }
            // Health Connect : l'accès aux pas accordé à l'app est retiré
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                runCatching {
                    if (androidx.health.connect.client.HealthConnectClient.getSdkStatus(ctx) == androidx.health.connect.client.HealthConnectClient.SDK_AVAILABLE)
                        androidx.health.connect.client.HealthConnectClient.getOrCreate(ctx).permissionController.revokeAllPermissions()
                }
            }
            com.goodlife.app.coach.CoachNotifier.schedule(ctx)
        }
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
    private const val S_API_KEY = "api_key"
    private const val S_BACKUP_KEY = "backup_key"
    private const val K_PLAN = "plan"
    private const val K_GAME = "game"
    private const val K_AVATAR = "avatar"
    private const val K_STEPS = "steps"
    private const val K_DEX = "dex"
    private const val K_SOCIAL = "social"
    private const val K_SPORT = "sport"
    private const val K_OUTINGS = "outings"
    private const val K_FAVS = "fav_meals"
    private const val K_WATER = "water"
    private const val K_FEEL = "feelings"
    /** États annexes gardés dans la sauvegarde (mémoire du quiz et des actus, pour ne jamais rien répéter). */
    private val BACKUP_EXTRAS = listOf("quiz_state", "news_bank", "news_feed", "shopping", "badges_seen", "fridge")

    /** L'IA n'est utilisable qu'avec consentement explicite et pour les 18 ans et plus (conditions Google). */
    fun aiAllowed(): Boolean = _settings.value.aiEnabled && (_profile.value?.age ?: 0) >= 18

    /** L'objectif d'eau vient-il de l'IA aujourd'hui ? (IA activée, clé, mode « ia » et un calcul disponible) */
    fun waterGoalFromAi(): Boolean {
        val s = _settings.value
        return s.waterGoalMode == "ia" && aiAllowed() && s.apiKey.isNotBlank() && s.waterGoalIa > 0
    }

    /** Objectif d'eau du jour (ml) : celui de l'IA s'il y en a un, sinon l'objectif fixe choisi. */
    fun waterGoal(): Int = if (waterGoalFromAi()) _settings.value.waterGoalIa else _settings.value.waterGoalMl

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
            .put("sport", _sport.value.toJson())
            .put("sorties", JSONArray().apply { _outings.value.forEach { put(it.toJson()) } })
            .put("repasFavoris", JSONArray().apply { _favMeals.value.forEach { put(it.toJson()) } })
            .put("eau", waterJson())
            .put("frigo", getExtra("fridge")?.let { runCatching { JSONObject(it) }.getOrNull() } ?: JSONObject.NULL)
            .put("conversationsCoach", getExtra("coach_history")?.let { runCatching { JSONObject(it) }.getOrNull() } ?: JSONObject.NULL)
            .put("ressenti", feelingsJson())
            .put("settings", JSONObject()
                .put("aiEnabled", s.aiEnabled)
                .put("aiConsentAt", s.aiConsentAt)
                .put("privacyAcceptedAt", s.privacyAcceptedAt)
                .put("personalApiKeySaved", s.apiKey.isNotBlank())
                .put("checkUpdates", s.checkUpdates)
                .put("coachConsentAt", s.coachConsentAt)
                .put("notifications", JSONObject()
                    .put("bilanDuMatin", s.notifMorning).put("motDeMidi", s.notifNoon)
                    .put("serieEnDanger", s.notifEvening).put("bilanDeLaSemaine", s.notifWeekly)))
            .toString(2)
    }
}
