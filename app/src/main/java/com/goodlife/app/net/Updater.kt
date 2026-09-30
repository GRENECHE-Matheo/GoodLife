package com.goodlife.app.net

import com.goodlife.app.BuildConfig
import com.goodlife.app.data.Repo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Vérifie s'il existe une version plus récente dans les releases GitHub officielles du projet.
 * Aucun téléchargement automatique : l'app ouvre la page de la release, et Android refuse
 * toute mise à jour qui ne serait pas signée avec la même clé que l'app installée.
 */
object Updater {
    private const val INTERVAL_MS = 12 * 3600_000L

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

    /** Vérifie au plus toutes les 12 h (ou tout de suite si [force]). Renvoie un message d'erreur éventuel. */
    suspend fun check(force: Boolean = false): String? = withContext(Dispatchers.IO) {
        val s = Repo.settings.value
        if (!force && (!s.checkUpdates || System.currentTimeMillis() - s.lastUpdateCheck < INTERVAL_MS)) {
            return@withContext null
        }
        runCatching {
            val (code, body) = httpGet(
                "https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest",
                accept = "application/vnd.github+json"
            )
            if (code !in 200..299) error("GitHub indisponible ($code)")
            val o = JSONObject(body)
            val tag = o.optString("tag_name")
            val url = o.optString("html_url")
            val official = "https://github.com/${BuildConfig.UPDATE_REPO}/releases/"
            Repo.updateSettings {
                it.copy(
                    lastUpdateCheck = System.currentTimeMillis(),
                    latestTag = tag,
                    // On n'ouvre jamais une autre adresse que la page officielle des releases
                    latestUrl = if (url.startsWith(official)) url else official + "latest"
                )
            }
        }.exceptionOrNull()?.message
    }

    fun availableUpdate(): Pair<String, String>? {
        val s = Repo.settings.value
        return if (s.latestTag.isNotBlank() && isNewer(s.latestTag)) s.latestTag to s.latestUrl else null
    }
}
