package com.goodlife.app.net

import com.goodlife.app.BuildConfig
import com.goodlife.app.data.Repo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class AvailableUpdate(val tag: String, val pageUrl: String, val apkUrl: String, val digest: String)

/**
 * Vérifie les releases publiques du dépôt officiel GoodLife sur GitHub.
 * Ne fait confiance qu'aux adresses de ce dépôt ; l'APK est ensuite vérifié par [UpdateInstaller].
 */
object Updater {
    /** Écart minimal entre deux vérifications automatiques (évite de redemander en passant d'une app à l'autre). */
    private const val INTERVAL_MS = 5 * 60_000L
    @Volatile private var checkedAt = 0L
    val RELEASES = "https://github.com/${BuildConfig.UPDATE_REPO}/releases/"

    /** Compare « v0.3.1 » et « 0.3 » numériquement. */
    fun isNewer(tag: String, current: String = BuildConfig.VERSION_NAME): Boolean {
        fun parts(v: String) = v.trim().removePrefix("v").removePrefix("V")
            .split('.', '-').mapNotNull { it.toIntOrNull() }
        val a = parts(tag)
        val b = parts(current)
        if (a.isEmpty()) return false
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    /**
     * Vérifie à chaque ouverture de l'app (une seule petite requête, rien en arrière-plan), ou tout de suite si [force].
     */
    suspend fun check(force: Boolean = false): String? = withContext(Dispatchers.IO) {
        val s = Repo.settings.value
        val now = System.currentTimeMillis()
        if (!force && (!s.checkUpdates || now - checkedAt < INTERVAL_MS)) return@withContext null
        checkedAt = now
        runCatching {
            val (code, body) = httpGet(
                "https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest",
                accept = "application/vnd.github+json"
            )
            if (code == 403 || code == 429) {
                error("GitHub limite le nombre de vérifications depuis ce réseau (réseau partagé ?). Réessaie dans une heure ou sur un autre réseau.")
            }
            if (code !in 200..299) error("GitHub indisponible (code $code). Réessaie plus tard.")
            val o = JSONObject(body)
            val tag = o.optString("tag_name")
            val page = o.optString("html_url").takeIf { it.startsWith(RELEASES) } ?: (RELEASES + "latest")
            var apk = ""
            var digest = ""
            val assets = o.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val a = assets.optJSONObject(i) ?: continue
                    val url = a.optString("browser_download_url")
                    if (a.optString("name").endsWith(".apk") && url.startsWith(RELEASES + "download/")) {
                        apk = url
                        digest = a.optString("digest") // ex. "sha256:…" (fourni par GitHub)
                        break
                    }
                }
            }
            Repo.updateSettings {
                it.copy(
                    lastUpdateCheck = System.currentTimeMillis(),
                    latestTag = tag, latestUrl = page, latestApkUrl = apk, latestApkDigest = digest
                )
            }
        }.exceptionOrNull()?.message
    }

    fun availableUpdate(): AvailableUpdate? {
        val s = Repo.settings.value
        return if (s.latestTag.isNotBlank() && isNewer(s.latestTag)) {
            AvailableUpdate(s.latestTag, s.latestUrl, s.latestApkUrl, s.latestApkDigest)
        } else null
    }
}
