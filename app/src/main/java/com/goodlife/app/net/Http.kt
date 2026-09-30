package com.goodlife.app.net

import com.goodlife.app.BuildConfig
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

internal val USER_AGENT = "GoodLife-Android/${BuildConfig.VERSION_NAME} (github.com/${BuildConfig.UPDATE_REPO})"

/** GET simple en HTTPS. Renvoie (code, corps). */
internal fun httpGet(url: String, accept: String = "application/json"): Pair<Int, String> {
    require(url.startsWith("https://")) { "HTTPS obligatoire" }
    val conn = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000
        readTimeout = 20_000
        setRequestProperty("User-Agent", USER_AGENT)
        setRequestProperty("Accept", accept)
    }
    return try {
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        code to (stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: "")
    } catch (e: IOException) {
        throw IOException("Pas de connexion internet.", e)
    } finally {
        conn.disconnect()
    }
}
