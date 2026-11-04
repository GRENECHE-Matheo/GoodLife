package com.goodlife.app.coach

import com.goodlife.app.data.MealSlot
import com.goodlife.app.data.Repo
import com.goodlife.app.data.localDay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Un message du chef, gardé dans « Messages du chef » (notification ou constat du jour). */
data class CoachMsg(
    val id: Long,
    val at: Long,
    val kind: String,            // meal | move | weekly | morning | noon | evening | water | insight | care
    val title: String,
    val text: String,
    val action: String = "",     // ex. « check:DEJEUNER:2026-10-26 », « forme », « coach:week »
    val read: Boolean = false
) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("at", at).put("kind", kind).put("title", title)
        .put("text", text).put("action", action).put("read", read)

    companion object {
        fun fromJson(o: JSONObject) = CoachMsg(o.optLong("id"), o.optLong("at"), o.optString("kind"), o.optString("title"),
            o.optString("text"), o.optString("action"), o.optBoolean("read"))
    }
}

/**
 * Ce que le coach retient, chiffré sur le téléphone comme le reste (jamais envoyé) :
 * repas « pas mangé », créneaux sautés volontairement, journal des notifications (pour ne pas en envoyer trop
 * et s'arrêter quand elles sont ignorées), constats déjà montrés, défi de la semaine et messages du chef.
 * Visible et effaçable dans Paramètres › Le coach › Ce que le chef a appris.
 */
data class CoachMemory(
    val skipped: Set<String> = emptySet(),               // « jour#CRÉNEAU »
    val fasting: Set<MealSlot> = emptySet(),
    val sent: List<Pair<Long, String>> = emptyList(),    // (heure, type) des 30 derniers jours
    val pending: Map<String, Long> = emptyMap(),         // type → heure d'envoi, tant que la personne n'a pas réagi
    val ignored: Map<String, Int> = emptyMap(),          // type → notifications ignorées d'affilée
    val calmUntil: Map<String, Long> = emptyMap(),       // type → fréquence réduite jusqu'à cette date
    val muteAsk: Set<String> = emptySet(),               // types pour lesquels on propose de couper
    val shown: Map<String, String> = emptyMap(),         // constat → dernier jour où il a été mis en avant
    val challenge: ActiveChallenge? = null,
    val challengeDone: Map<String, Int> = emptyMap(),    // défis non mesurables : résultat donné par la personne
    val inbox: List<CoachMsg> = emptyList(),
    val snoozed: Map<String, Long> = emptyMap(),         // « check:SLOT:jour » → rappel « plus tard » prévu
    val backdate: Long = 0L,                             // le prochain repas noté prend cette heure (repas noté après coup)
    val backdateSetAt: Long = 0L,
    val lastOpen: Long = 0L,
    val reportSeen: String = "",                         // dernier bilan de la semaine ouvert (jour)
    val catchupDismissed: Set<String> = emptySet(),      // « jour#CRÉNEAU » : « ne plus demander » sur la carte des oublis
    val catchupAsked: String = ""                        // dernier jour pour lequel on a demandé « ta journée d'hier ? »
) {
    fun toJson(): JSONObject = JSONObject()
        .put("skipped", JSONArray(skipped.toList()))
        .put("fasting", JSONArray(fasting.map { it.name }))
        .put("sent", JSONArray().apply { sent.forEach { put(JSONObject().put("at", it.first).put("k", it.second)) } })
        .put("pending", JSONObject().apply { pending.forEach { (k, v) -> put(k, v) } })
        .put("ignored", JSONObject().apply { ignored.forEach { (k, v) -> put(k, v) } })
        .put("calmUntil", JSONObject().apply { calmUntil.forEach { (k, v) -> put(k, v) } })
        .put("muteAsk", JSONArray(muteAsk.toList()))
        .put("shown", JSONObject().apply { shown.forEach { (k, v) -> put(k, v) } })
        .put("challenge", challenge?.let {
            JSONObject().put("id", it.id).put("title", it.title).put("target", it.target).put("plan", it.plan).put("start", it.start)
        } ?: JSONObject.NULL)
        .put("challengeDone", JSONObject().apply { challengeDone.forEach { (k, v) -> put(k, v) } })
        .put("inbox", JSONArray().apply { inbox.forEach { put(it.toJson()) } })
        .put("snoozed", JSONObject().apply { snoozed.forEach { (k, v) -> put(k, v) } })
        .put("backdate", backdate).put("backdateSetAt", backdateSetAt)
        .put("lastOpen", lastOpen).put("reportSeen", reportSeen)
        .put("catchupDismissed", JSONArray(catchupDismissed.toList())).put("catchupAsked", catchupAsked)

    companion object {
        private fun JSONObject.longs(): Map<String, Long> = keys().asSequence().associateWith { optLong(it) }
        private fun JSONObject.ints(): Map<String, Int> = keys().asSequence().associateWith { optInt(it) }
        private fun JSONArray.list(): List<String> = (0 until length()).mapNotNull { optString(it).takeIf { s -> s.isNotBlank() } }

        fun fromJson(o: JSONObject) = CoachMemory(
            skipped = o.optJSONArray("skipped")?.list()?.toSet() ?: emptySet(),
            fasting = o.optJSONArray("fasting")?.list()?.mapNotNull { n -> MealSlot.entries.firstOrNull { it.name == n } }?.toSet() ?: emptySet(),
            sent = o.optJSONArray("sent")?.let { a -> (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { it.optLong("at") to it.optString("k") } } } ?: emptyList(),
            pending = o.optJSONObject("pending")?.longs() ?: emptyMap(),
            ignored = o.optJSONObject("ignored")?.ints() ?: emptyMap(),
            calmUntil = o.optJSONObject("calmUntil")?.longs() ?: emptyMap(),
            muteAsk = o.optJSONArray("muteAsk")?.list()?.toSet() ?: emptySet(),
            shown = o.optJSONObject("shown")?.let { s -> s.keys().asSequence().associateWith { s.optString(it) } } ?: emptyMap(),
            challenge = o.optJSONObject("challenge")?.let {
                ActiveChallenge(it.optString("id"), it.optString("title"), it.optInt("target", 1), it.optString("plan"), it.optString("start"))
            },
            challengeDone = o.optJSONObject("challengeDone")?.ints() ?: emptyMap(),
            inbox = o.optJSONArray("inbox")?.let { a -> (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.let { CoachMsg.fromJson(it) } } } ?: emptyList(),
            snoozed = o.optJSONObject("snoozed")?.longs() ?: emptyMap(),
            backdate = o.optLong("backdate"), backdateSetAt = o.optLong("backdateSetAt"),
            lastOpen = o.optLong("lastOpen"), reportSeen = o.optString("reportSeen"),
            catchupDismissed = o.optJSONArray("catchupDismissed")?.list()?.toSet() ?: emptySet(),
            catchupAsked = o.optString("catchupAsked")
        )
    }
}

object CoachStore {
    private const val KEY = "coach_memory"
    private const val DAY = 86_400_000L
    private val _mem = MutableStateFlow(CoachMemory())
    val mem: StateFlow<CoachMemory> = _mem
    @Volatile private var loaded = false

    @Synchronized
    fun load() {
        if (loaded) return
        _mem.value = Repo.getExtra(KEY)?.let { runCatching { CoachMemory.fromJson(JSONObject(it)) }.getOrNull() } ?: CoachMemory()
        loaded = true
    }

    @Synchronized
    fun update(transform: (CoachMemory) -> CoachMemory) {
        load()
        val now = System.currentTimeMillis()
        val cut = localDay(-40)
        val m = transform(_mem.value).let { m ->
            // Ménage : 30 jours de journal, 40 jours de repas sautés, 40 messages
            m.copy(
                sent = m.sent.filter { now - it.first < 30 * DAY },
                skipped = m.skipped.filter { it.substringBefore('#') >= cut }.toSet(),
                catchupDismissed = m.catchupDismissed.filter { it.substringBefore('#') >= localDay(-3) }.toSet(),
                inbox = m.inbox.sortedByDescending { it.at }.take(40),
                snoozed = m.snoozed.filterValues { now - it < DAY }
            )
        }
        _mem.value = m
        Repo.putExtra(KEY, m.toJson().toString())
    }

    fun get(): CoachMemory { load(); return _mem.value }

    /** Après une restauration ou un effacement : on relit ce qui est enregistré. */
    @Synchronized
    fun reload() { loaded = false; load() }

    /** Efface tout ce que le coach a appris (les repas, eux, restent). */
    @Synchronized
    fun forget() {
        _mem.value = CoachMemory()
        Repo.putExtra(KEY, null)
    }

    fun addMessage(kind: String, title: String, text: String, action: String = ""): CoachMsg {
        val msg = CoachMsg(System.currentTimeMillis(), System.currentTimeMillis(), kind, title, text, action)
        update { it.copy(inbox = it.inbox + msg) }
        return msg
    }

    fun markRead(id: Long) = update { m -> m.copy(inbox = m.inbox.map { if (it.id == id) it.copy(read = true) else it }) }

    fun message(id: Long): CoachMsg? = get().inbox.firstOrNull { it.id == id }

    /** La personne a réagi (appui sur la notification ou sur un bouton) : ce type n'est plus « ignoré ». */
    fun acted(kind: String) = update { it.copy(pending = it.pending - kind, ignored = it.ignored - kind, muteAsk = it.muteAsk - kind) }

    /** Ouverture de l'app : les notifications des 2 dernières heures comptent comme lues. */
    fun opened() = update { m ->
        val now = System.currentTimeMillis()
        val recent = m.pending.filterValues { now - it < 2 * 3600_000L }.keys
        m.copy(lastOpen = now, pending = m.pending - recent, ignored = m.ignored - recent)
    }

    fun skip(day: String, slot: MealSlot) = update { it.copy(skipped = it.skipped + "$day#${slot.name}") }
    fun skipAll(day: String, slots: List<MealSlot>) = update { m -> m.copy(skipped = m.skipped + slots.map { "$day#${it.name}" }) }

    /** « Ne plus demander » sur la carte des repas oubliés (seulement pour ces repas-là). */
    fun dismissCatchup(items: List<Pair<String, MealSlot>>) = update { m -> m.copy(catchupDismissed = m.catchupDismissed + items.map { "${it.first}#${it.second.name}" }) }

    /** Dernier repas noté après coup (heure qu'il a prise), pour le confirmer sur l'accueil. */
    val notice = MutableStateFlow<Long?>(null)
    fun unskip(day: String, slot: MealSlot) = update { it.copy(skipped = it.skipped - "$day#${slot.name}") }
    /** Un repas vient d'être noté : s'il avait été marqué « pas mangé », on retire la marque. */
    fun mealAdded(timestamp: Long) {
        val key = CoachBrain.dayOf(timestamp) + "#" + slotOf(CoachBrain.minuteOf(timestamp)).name
        if (key in get().skipped) update { it.copy(skipped = it.skipped - key) }
    }

    fun setFasting(slot: MealSlot, on: Boolean) = update { it.copy(fasting = if (on) it.fasting + slot else it.fasting - slot) }

    /** Le prochain repas noté (dans les 30 min) prendra l'heure [at] : utile quand on note un repas après coup. */
    fun backdateNext(at: Long) = update { it.copy(backdate = at, backdateSetAt = System.currentTimeMillis()) }

    /** Heure à donner au repas qu'on ajoute (appelé par Repo.addMeal). */
    fun mealTime(timestamp: Long): Long {
        val m = get()
        if (m.backdate <= 0L) return timestamp
        val fresh = timestamp - m.backdateSetAt in 0..30 * 60_000L
        update { it.copy(backdate = 0L, backdateSetAt = 0L) }
        if (fresh) notice.value = m.backdate
        return if (fresh) m.backdate else timestamp
    }
}
