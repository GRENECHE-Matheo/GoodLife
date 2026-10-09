package com.goodlife.app.ai

import com.goodlife.app.BuildConfig
import com.goodlife.app.data.Repo
import com.goodlife.app.data.Settings
import com.goodlife.app.i18n.t
import com.goodlife.app.net.readCapped
import com.goodlife.app.store.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Accès à l'IA :
 * - version GitHub : avec la clé Gemini personnelle de la personne, directement chez Google ;
 * - version Google Play : via le relais Lifoody (serveur de Fanix Studio), qui garde sa propre clé, vérifie
 *   l'abonnement Premium et compte les essais gratuits. La personne n'a aucune clé à créer.
 */
object AiAccess {
    val viaRelay: Boolean get() = BuildConfig.AI_RELAY

    /** L'IA peut-elle être appelée (sans compter le consentement et l'âge, vérifiés à part) ? */
    fun ready(s: Settings): Boolean = if (viaRelay) BuildConfig.RELAY_URL.isNotBlank() else s.apiKey.isNotBlank()

    /** Calculs automatiques du jour (eau, pas) : avec sa clé, ou avec Premium (ils ne consomment jamais d'essai gratuit). */
    fun autoReady(s: Settings): Boolean = ready(s) && (!viaRelay || Store.premium.value || Relay.status.value?.premium == true)
}

/** Ce que le relais indique : offre et ce qu'il reste aujourd'hui. */
data class RelayStatus(
    val premium: Boolean, val photosLeft: Int, val messagesLeft: Int, val trialsLeft: Int, val premiumUntil: Long = 0,
    val fixesLeft: Int = 0,
    // Limites des abonnés, données par le serveur (réglables sans mettre l'app à jour)
    val photosPerDay: Int = 8, val messagesPerDay: Int = 20, val fixesPerDay: Int = 10
)

/** Erreur du relais avec son code (premium_required, limit_day…), pour proposer la bonne action. */
class RelayException(val code: String, message: String) : AiException(message)

object Relay {
    private val _status = MutableStateFlow<RelayStatus?>(null)
    val status: StateFlow<RelayStatus?> = _status

    /** Identifiant d'installation aléatoire (rien à voir avec l'appareil ou la personne), créé une fois. */
    private fun installId(): String = Repo.getExtra("relay_install")?.takeIf { it.matches(Regex("[a-f0-9]{32}")) }
        ?: ByteArray(16).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }.also { Repo.putExtra("relay_install", it) }

    private fun sha256Hex(b: ByteArray) = MessageDigest.getInstance("SHA-256").digest(b).joinToString("") { "%02x".format(it) }

    private fun open(path: String): HttpURLConnection =
        (URL(BuildConfig.RELAY_URL.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            connectTimeout = 20_000
            readTimeout = 90_000
            setRequestProperty("X-Lifoody-Install", installId())
            Store.purchaseToken()?.let { setRequestProperty("X-Lifoody-Purchase", it) }
            setRequestProperty("User-Agent", com.goodlife.app.net.USER_AGENT)
        }

    /**
     * Envoie une demande au relais. [task] : photo, coach, chat, json, search ou auto (le serveur compte les
     * photos et les messages séparément). Le jeton Play Integrity est lié au contenu exact de la demande.
     * Renvoie le code HTTP et la réponse (même forme que la réponse de Gemini).
     */
    suspend fun generate(task: String, request: JSONObject): Pair<Int, String> = withContext(Dispatchers.IO) {
        val bytes = JSONObject().put("task", task).put("request", request).toString().toByteArray(Charsets.UTF_8)
        val integrity = Store.integrityToken(sha256Hex(bytes))
        val conn = open("/v1/generate").apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            integrity?.let { setRequestProperty("X-Lifoody-Integrity", it) }
        }
        try {
            conn.outputStream.use { it.write(bytes) }
            val code = conn.responseCode
            conn.getHeaderField("X-Lifoody-Remaining")?.let { parseStatus(it) }
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            code to (stream?.use { String(it.readCapped(8 * 1024 * 1024), Charsets.UTF_8) } ?: "")
        } catch (e: java.io.IOException) {
            throw AiException(com.goodlife.app.net.networkError(t("le service IA de Lifoody"), e).message ?: t("Problème réseau."))
        } finally {
            conn.disconnect()
        }
    }

    /** Statut (offre, essais, limites du jour), demandé à l'ouverture de l'app et après un achat. */
    suspend fun refreshStatus(): RelayStatus? = withContext(Dispatchers.IO) {
        if (!AiAccess.viaRelay || BuildConfig.RELAY_URL.isBlank()) return@withContext null
        runCatching {
            val conn = open("/v1/status").apply { requestMethod = "GET" }
            try {
                if (conn.responseCode != 200) return@runCatching null
                parseStatus(String(conn.inputStream.use { it.readCapped(64 * 1024) }, Charsets.UTF_8))
            } finally { conn.disconnect() }
        }.getOrNull()
    }

    private fun parseStatus(json: String): RelayStatus? = runCatching {
        val o = JSONObject(json)
        val prev = _status.value
        val limits = o.optJSONObject("limits")   // seulement dans /v1/status ; sinon on garde les précédentes
        RelayStatus(
            premium = o.optString("tier") == "premium",
            photosLeft = o.optInt("photos"), messagesLeft = o.optInt("messages"), trialsLeft = o.optInt("trials"),
            premiumUntil = o.optLong("premiumUntil", prev?.premiumUntil ?: 0),
            fixesLeft = o.optInt("fixes"),
            photosPerDay = limits?.optInt("photosPerDay", 8) ?: prev?.photosPerDay ?: 8,
            messagesPerDay = limits?.optInt("messagesPerDay", 20) ?: prev?.messagesPerDay ?: 20,
            fixesPerDay = limits?.optInt("fixesPerDay", 10) ?: prev?.fixesPerDay ?: 10
        )
    }.getOrNull()?.also { _status.value = it }

    /** Message clair (traduit) pour un refus du relais. */
    fun error(code: Int, body: String): RelayException {
        val c = runCatching { JSONObject(body).optString("error") }.getOrNull().orEmpty()
        // Essais gratuits épuisés : on propose directement Premium
        if (c == "premium_required" || code == 402) com.goodlife.app.ui.screens.Paywall.show()
        val msg = when (c) {
            "premium_required" -> t("Tes essais IA gratuits sont utilisés. Passe à Lifoody Premium pour continuer.")
            "limit_day" -> t("Limite du jour atteinte. Elle repart demain à minuit.")
            "limit_minute" -> t("Doucement ! Attends une minute avant la prochaine demande.")
            "trial_busy" -> t("Trop d'essais gratuits depuis ce réseau aujourd'hui. Réessaie demain.")
            "budget" -> t("Le service IA est très demandé en ce moment. Réessaie un peu plus tard.")
            "integrity" -> t("Cette version de l'app n'est pas reconnue. Installe Lifoody depuis Google Play.")
            "config" -> t("Le service IA de Lifoody n'est pas encore disponible.")
            "bad_request" -> t("Demande refusée par le service IA (trop longue ou photo trop lourde).")
            else -> if (code == 402) t("Passe à Lifoody Premium pour utiliser l'IA.") else t("L'IA ne répond pas pour le moment. Réessaie dans un instant.")
        }
        return RelayException(c.ifBlank { "http_$code" }, msg)
    }
}
