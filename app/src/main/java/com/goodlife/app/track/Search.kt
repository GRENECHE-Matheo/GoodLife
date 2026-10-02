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

    suspend fun search(query: String, nearLat: Double? = null, nearLng: Double? = null): List<FoundPlace> = withContext(Dispatchers.IO) {
        val q = query.trim().take(120)
        if (q.length < 2) return@withContext emptyList()
        // Au plus une requête par seconde (règle de Nominatim)
        val wait = 1100 - (System.currentTimeMillis() - lastCall)
        if (wait > 0) kotlinx.coroutines.delay(wait)
        lastCall = System.currentTimeMillis()
        val lang = if (com.goodlife.app.i18n.Lang.en) "en" else "fr"
        val near = if (nearLat != null && nearLng != null) String.format(
            Locale.US, "&viewbox=%.3f,%.3f,%.3f,%.3f", nearLng - 0.6, nearLat + 0.4, nearLng + 0.6, nearLat - 0.4
        ) else ""
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
            }
        } catch (e: SearchError) {
            throw e
        } catch (e: IOException) {
            throw networkError("OpenStreetMap", e)
        } finally {
            conn.disconnect()
        }
    }
}
