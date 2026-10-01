package com.goodlife.app.net

import com.goodlife.app.i18n.t

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.goodlife.app.BuildConfig
import java.io.IOException
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import javax.net.ssl.SSLException

internal val USER_AGENT = t("GoodLife-Android/%1\$s (github.com/%2\$s)", BuildConfig.VERSION_NAME, BuildConfig.UPDATE_REPO)

/** Contexte de l'app, pour savoir si le téléphone a vraiment internet (renseigné au démarrage). */
@Volatile internal var appContext: Context? = null

private enum class NetState { NONE, NOT_VALIDATED, OK, UNKNOWN }

/** État du réseau vu par Android : aucun réseau, réseau sans internet vérifié, ou internet OK. */
private fun netState(): NetState {
    val cm = appContext?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return NetState.UNKNOWN
    val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return NetState.NONE) ?: return NetState.NONE
    return if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) NetState.OK else NetState.NOT_VALIDATED
}

/**
 * Transforme une erreur réseau en message utile : avant, tout devenait « Pas de connexion internet »,
 * ce qui cachait la vraie cause (site bloqué, date du téléphone fausse, VPN…).
 */
internal fun networkError(host: String, e: IOException): IOException {
    val state = netState()
    val msg = when {
        // Android répond « aucun réseau » aussi quand l'accès internet de l'app est bloqué dans les réglages
        state == NetState.NONE ->
            t("Android ne donne pas accès à internet à GoodLife. Si les autres applis marchent, l'accès réseau de GoodLife est sûrement bloqué : Réglages › Applis › GoodLife › Données mobiles et Wi-Fi (ou « Utilisation des données »), économiseur de données, ou pare-feu / contrôle parental.")
        state == NetState.NOT_VALIDATED ->
            t("Le téléphone est connecté, mais Android indique que ce réseau n'a pas accès à internet : DNS privé mal réglé (Réglages › Réseau › DNS privé), portail de connexion Wi-Fi à valider, ou réseau limité.")
        e is UnknownHostException ->
            t("Impossible de joindre %1\$s. Internet marche, mais ce site est bloqué ou l'app n'a pas accès au réseau : vérifie le DNS privé / bloqueur de pubs / VPN, les autorisations réseau de GoodLife (Wi-Fi et données mobiles), ou essaie un autre réseau (certains réseaux d'école ou de travail bloquent GitHub).", host)
        e is SSLException ->
            t("Connexion sécurisée refusée avec %1\$s. Vérifie que la date et l'heure du téléphone sont automatiques, et qu'aucun VPN ou antivirus n'intercepte les connexions.", host)
        e is SocketTimeoutException -> t("%1\$s met trop de temps à répondre. Réessaie dans un moment ou sur un autre réseau.", host)
        e is ConnectException -> t("Connexion refusée par le réseau vers %1\$s (réseau restreint, VPN ou pare-feu ?).", host)
        else -> t("Problème réseau vers %1\$s.", host)
    }
    return IOException("$msg (${e.javaClass.simpleName})", e)
}

/**
 * Lecture plafonnée : une réponse (ou un fichier) plus grosse que [max] octets est refusée au lieu de remplir la
 * mémoire du téléphone (serveur défaillant, fichier choisi par erreur…).
 */
internal fun java.io.InputStream.readCapped(max: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buf = ByteArray(16 * 1024)
    var total = 0
    while (true) {
        val n = read(buf)
        if (n < 0) break
        total += n
        if (total > max) throw IOException(t("Réponse trop volumineuse."))
        out.write(buf, 0, n)
    }
    return out.toByteArray()
}

/** GET simple en HTTPS. Renvoie (code, corps). */
internal fun httpGet(url: String, accept: String = "application/json"): Pair<Int, String> {
    require(url.startsWith("https://")) { t("HTTPS obligatoire") }
    val u = URL(url)
    val conn = (u.openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000
        readTimeout = 20_000
        setRequestProperty("User-Agent", USER_AGENT)
        setRequestProperty("Accept", accept)
    }
    return try {
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        code to (stream?.use { String(it.readCapped(8 * 1024 * 1024), Charsets.UTF_8) } ?: "")
    } catch (e: IOException) {
        throw networkError(u.host, e)
    } finally {
        conn.disconnect()
    }
}
