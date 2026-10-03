package com.goodlife.app.track

import com.goodlife.app.i18n.t
import com.goodlife.app.net.USER_AGENT
import com.goodlife.app.net.networkError
import com.goodlife.app.net.readCapped
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/** Un lieu trouvé par la recherche : nom court, détail (ville, région) et position. */
data class FoundPlace(val name: String, val detail: String, val lat: Double, val lng: Double)

/**
 * Recherche d'une ville ou d'une adresse avec Nominatim (OpenStreetMap). Seul le texte tapé est envoyé, avec la zone
 * affichée sur la carte pour proposer d'abord les lieux proches. Une recherche par appui sur « Rechercher » (pas de
 * recherche à chaque lettre), comme le demandent les règles d'utilisation de Nominatim.
 */
object PlaceSearch {
    private class SearchError(msg: String) : IOException(msg)

    private const val ENDPOINT = "https://nominatim.openstreetmap.org/search"
    @Volatile private var lastCall = 0L

    /** Derniers résultats (texte, langue, zone) : une même recherche ne repart pas au serveur (règle de Nominatim). */
    private val cache = object : LinkedHashMap<String, Pair<Long, List<FoundPlace>>>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, List<FoundPlace>>>?) = size > 50
    }

    /**
     * Interrupteur à distance demandé par OpenStreetMap : une fois par jour au plus, et seulement quand on cherche un
     * lieu, l'app lit services.json sur la page GitHub du projet. Si OpenStreetMap demande d'arrêter, la recherche est
     * coupée sans mise à jour de l'app. Seule l'adresse officielle de Nominatim est acceptée (rien d'autre ne peut
     * recevoir le texte tapé).
     */
    @Volatile private var allowed = true
    @Volatile private var checkedAt = 0L
    private fun checkSwitch() {
        val now = System.currentTimeMillis()
        if (now - checkedAt < 86_400_000L) return
        checkedAt = now
        val owner = com.goodlife.app.BuildConfig.UPDATE_REPO.substringBefore('/').lowercase()
        val repo = com.goodlife.app.BuildConfig.UPDATE_REPO.substringAfter('/')
        runCatching {
            val (code, body) = com.goodlife.app.net.httpGet("https://$owner.github.io/$repo/services.json")
            if (code == 200) allowed = org.json.JSONObject(body).optString("placeSearch", ENDPOINT) == ENDPOINT
        } // pas de réponse (hors ligne, page absente) : on garde le dernier état connu
    }

    suspend fun search(query: String, nearLat: Double? = null, nearLng: Double? = null): List<FoundPlace> = withContext(Dispatchers.IO) {
        val q = query.trim().take(120)
        if (q.length < 2) return@withContext emptyList()
        checkSwitch()
        if (!allowed) throw SearchError(t("La recherche de lieux est suspendue pour le moment. Tu peux toujours appuyer longtemps sur la carte."))
        val lang = if (com.goodlife.app.i18n.Lang.en) "en" else "fr"
        val near = if (nearLat != null && nearLng != null) String.format(
            Locale.US, "&viewbox=%.3f,%.3f,%.3f,%.3f", nearLng - 0.6, nearLat + 0.4, nearLng + 0.6, nearLat - 0.4
        ) else ""
        val key = "$q|$lang|$near"
        synchronized(cache) { cache[key] }?.takeIf { System.currentTimeMillis() - it.first < 86_400_000L }?.let { return@withContext it.second }
        // Au plus une requête par seconde (règle de Nominatim)
        val wait = 1100 - (System.currentTimeMillis() - lastCall)
        if (wait > 0) kotlinx.coroutines.delay(wait)
        lastCall = System.currentTimeMillis()
        val url = "$ENDPOINT?q=${URLEncoder.encode(q, "UTF-8")}&format=jsonv2&limit=6&addressdetails=0&accept-language=$lang$near"
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000; readTimeout = 15_000; instanceFollowRedirects = false
            setRequestProperty("User-Agent", USER_AGENT)
        }
        try {
            val code = conn.responseCode
            if (code == 429) throw SearchError(t("Trop de recherches d'un coup. Réessaie dans quelques secondes."))
            if (code !in 200..299) throw SearchError(t("Recherche indisponible (%1\$s).", code))
            val a = JSONArray(String(conn.inputStream.use { it.readCapped(1024 * 1024) }, Charsets.UTF_8))
            (0 until a.length()).mapNotNull { i ->
                val o = a.optJSONObject(i) ?: return@mapNotNull null
                val lat = o.optString("lat").toDoubleOrNull() ?: return@mapNotNull null
                val lng = o.optString("lon").toDoubleOrNull() ?: return@mapNotNull null
                val full = o.optString("display_name")
                val name = o.optString("name").ifBlank { full.substringBefore(',') }.take(80)
                FoundPlace(name, full.substringAfter(", ", "").take(120), lat, lng)
            }.also { found -> synchronized(cache) { cache[key] = System.currentTimeMillis() to found } }
        } catch (e: SearchError) {
            throw e
        } catch (e: IOException) {
            throw networkError("OpenStreetMap", e)
        } finally {
            conn.disconnect()
        }
    }
}
