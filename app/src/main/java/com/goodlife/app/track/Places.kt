package com.goodlife.app.track

import com.goodlife.app.i18n.t

import com.goodlife.app.net.USER_AGENT
import com.goodlife.app.net.networkError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.roundToInt

/** Un lieu de sport (données OpenStreetMap). Les champs vides = information non renseignée. */
data class SportPlace(
    val id: String,
    val lat: Double,
    val lng: Double,
    val name: String,
    val kind: String,
    val sports: String,
    val website: String,
    val phone: String,
    val hours: String,
    val price: String,
    val address: String
)

/**
 * Clubs et équipements sportifs autour d'un point, via l'API Overpass d'OpenStreetMap (données ouvertes, ODbL).
 * Seules les coordonnées de la zone cherchée sont envoyées. Résultats gardés 24 h par zone pour limiter les requêtes.
 */
object Places {
    private const val ENDPOINT = "https://overpass-api.de/api/interpreter"
    private val cache = HashMap<String, Pair<Long, List<SportPlace>>>()

    private fun kindLabel(tags: JSONObject): String = when {
        tags.optString("leisure") == "fitness_centre" -> t("Salle de sport")
        tags.optString("leisure") == "swimming_pool" -> t("Piscine")
        tags.optString("leisure") == "stadium" -> t("Stade")
        tags.optString("leisure") == "sports_hall" -> t("Gymnase")
        tags.optString("leisure") == "sports_centre" -> t("Centre sportif")
        tags.optString("club") == "sport" -> t("Club sportif")
        else -> t("Sport")
    }

    private val SPORTS_FR = mapOf(
        "soccer" to "football", "tennis" to "tennis", "swimming" to "natation", "fitness" to "fitness",
        "basketball" to "basket", "handball" to "handball", "volleyball" to "volley", "rugby_union" to "rugby",
        "athletics" to "athlétisme", "climbing" to "escalade", "martial_arts" to "arts martiaux", "judo" to "judo",
        "karate" to "karaté", "boxing" to "boxe", "yoga" to "yoga", "dance" to "danse", "gymnastics" to "gymnastique",
        "table_tennis" to "tennis de table", "badminton" to "badminton", "equestrian" to "équitation",
        "cycling" to "cyclisme", "running" to "course à pied", "multi" to "multisport", "golf" to "golf",
        "squash" to "squash", "padel" to "padel", "skateboard" to "skate", "rowing" to "aviron"
    )

    private fun clean(url: String): String = url.trim().let {
        when {
            it.isBlank() -> ""
            it.startsWith("https://") || it.startsWith("http://") -> it
            else -> "https://$it"
        }
    }.take(300)

    suspend fun around(lat: Double, lng: Double, radiusM: Int = 4000): List<SportPlace> = withContext(Dispatchers.IO) {
        val key = "${(lat * 50).roundToInt()}:${(lng * 50).roundToInt()}:$radiusM"
        cache[key]?.let { (at, list) -> if (System.currentTimeMillis() - at < 86_400_000L) return@withContext list }
        val q = """
            [out:json][timeout:20];
            (
              nwr["leisure"~"^(sports_centre|fitness_centre|stadium|sports_hall)$"](around:$radiusM,$lat,$lng);
              nwr["leisure"="swimming_pool"]["access"!~"private|no"]["name"](around:$radiusM,$lat,$lng);
              nwr["club"="sport"](around:$radiusM,$lat,$lng);
            );
            out center tags 150;
        """.trimIndent()
        // Le serveur public est parfois surchargé (429 / 504) : jusqu'à 3 essais, espacés de 2 puis 4 s
        var body: String? = null
        var lastError: IOException? = null
        for (attempt in 0 until 3) {
            if (attempt > 0) kotlinx.coroutines.delay(2000L * attempt)
            val conn = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 30_000
                doOutput = true
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            }
            try {
                conn.outputStream.use { it.write(("data=" + URLEncoder.encode(q, "UTF-8")).toByteArray()) }
                val code = conn.responseCode
                if (code == 429 || code in 502..504) {
                    lastError = IOException(t("Le service de données OpenStreetMap est surchargé. Réessaie dans une minute."))
                    continue
                }
                if (code !in 200..299) throw IOException(t("Service OpenStreetMap indisponible (%1\$s).", code))
                body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                break
            } catch (e: IOException) {
                lastError = if (e.message?.contains("OpenStreetMap") == true) e else networkError("OpenStreetMap", e)
                if (e.message?.contains("indisponible") == true) break
            } finally {
                conn.disconnect()
            }
        }
        if (body == null) throw lastError ?: IOException(t("Service OpenStreetMap indisponible."))
        val elements = JSONObject(body).optJSONArray("elements") ?: return@withContext emptyList()
        val list = (0 until elements.length()).mapNotNull { i ->
            val e = elements.optJSONObject(i) ?: return@mapNotNull null
            val tags = e.optJSONObject("tags") ?: return@mapNotNull null
            val name = tags.optString("name").trim()
            if (name.isEmpty()) return@mapNotNull null
            val la = if (e.has("lat")) e.optDouble("lat") else e.optJSONObject("center")?.optDouble("lat") ?: return@mapNotNull null
            val lo = if (e.has("lon")) e.optDouble("lon") else e.optJSONObject("center")?.optDouble("lon") ?: return@mapNotNull null
            val sports = tags.optString("sport").split(';').mapNotNull { s -> s.trim().takeIf { it.isNotBlank() }?.let { if (com.goodlife.app.i18n.Lang.en) it.replace('_', ' ') else SPORTS_FR[it] ?: it } }
            val price = listOf(tags.optString("charge"), tags.optString("fee:conditional"))
                .firstOrNull { it.isNotBlank() }
                ?: when (tags.optString("fee")) { "no" -> t("Gratuit"); "yes" -> t("Payant (tarif non renseigné)"); else -> "" }
            SportPlace(
                id = "${e.optString("type")}/${e.optLong("id")}",
                lat = la, lng = lo,
                name = name.take(80),
                kind = kindLabel(tags),
                sports = sports.joinToString(", ").take(120),
                website = clean(tags.optString("website").ifBlank { tags.optString("contact:website") }.ifBlank { tags.optString("url") }),
                phone = tags.optString("phone").ifBlank { tags.optString("contact:phone") }.take(30),
                hours = tags.optString("opening_hours").take(120),
                price = price.take(80),
                address = listOf(
                    listOf(tags.optString("addr:housenumber"), tags.optString("addr:street")).filter { it.isNotBlank() }.joinToString(" "),
                    tags.optString("addr:city")
                ).filter { it.isNotBlank() }.joinToString(", ")
            )
        }.distinctBy { it.name + it.kind }
        cache[key] = System.currentTimeMillis() to list
        list
    }
}
