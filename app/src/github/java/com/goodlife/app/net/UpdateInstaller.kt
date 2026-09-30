package com.goodlife.app.net

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Téléchargement et installation d'une mise à jour, avec vérifications avant de lancer l'installeur Android :
 *  1. l'APK vient du dépôt officiel (adresse vérifiée par [Updater]) ;
 *  2. son empreinte SHA-256 correspond à celle publiée par GitHub (si disponible) ;
 *  3. c'est bien le même package, dans une version plus récente ;
 *  4. il est signé avec exactement la même clé que l'app installée.
 * Android refait lui-même le contrôle de signature au moment de l'installation.
 */
object UpdateInstaller {
    private const val MAX_SIZE = 150L * 1024 * 1024

    private fun dir(context: Context) = File(context.cacheDir, "updates").apply { mkdirs() }

    /** Supprime les APK téléchargés ; si [onlyInstalled], garde ceux d'une version pas encore installée. */
    fun cleanup(context: Context, onlyInstalled: Boolean = false) {
        runCatching {
            dir(context).listFiles()?.forEach { f ->
                val tag = f.name.removePrefix("GoodLife-").removeSuffix(".apk")
                if (!onlyInstalled || !Updater.isNewer(tag)) f.delete()
            }
        }
    }

    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    /** Ouvre le réglage Android « Installer des applis inconnues » pour GoodLife. */
    fun openInstallPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    suspend fun download(context: Context, update: AvailableUpdate, onProgress: (Float) -> Unit): File =
        withContext(Dispatchers.IO) {
            if (!update.apkUrl.startsWith(Updater.RELEASES + "download/")) throw IOException("Adresse de mise à jour non officielle.")
            cleanup(context)
            val target = File(dir(context), "GoodLife-${update.tag}.apk")
            val conn = (URL(update.apkUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 20_000
                readTimeout = 60_000
                setRequestProperty("User-Agent", USER_AGENT)
            }
            val sha = MessageDigest.getInstance("SHA-256")
            try {
                if (conn.responseCode !in 200..299) throw IOException("Téléchargement impossible (${conn.responseCode}).")
                val total = conn.contentLengthLong
                if (total > MAX_SIZE) throw IOException("Fichier trop gros.")
                conn.inputStream.use { input ->
                    target.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            sha.update(buf, 0, n)
                            done += n
                            if (done > MAX_SIZE) throw IOException("Fichier trop gros.")
                            if (total > 0) onProgress(done.toFloat() / total)
                        }
                    }
                }
            } catch (e: Exception) {
                target.delete()
                throw if (e is IOException) e else IOException(e.message)
            } finally {
                conn.disconnect()
            }
            val hex = sha.digest().joinToString("") { "%02x".format(it) }
            val expected = update.digest.removePrefix("sha256:").lowercase()
            if (expected.isNotBlank() && expected != hex) {
                target.delete()
                throw IOException("Fichier corrompu ou modifié : empreinte incorrecte.")
            }
            verifyApk(context, target)
            target
        }

    @SuppressLint("PackageManagerGetSignatures")
    @Suppress("DEPRECATION")
    private fun verifyApk(context: Context, apk: File) {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES
                    else PackageManager.GET_SIGNATURES
        val archive = pm.getPackageArchiveInfo(apk.absolutePath, flags)
        val installed = pm.getPackageInfo(context.packageName, flags)
        fun fail(msg: String): Nothing { apk.delete(); throw IOException(msg) }
        if (archive == null) fail("Fichier de mise à jour illisible.")
        if (archive.packageName != context.packageName) fail("Ce fichier n'est pas GoodLife.")
        if (versionCode(archive) <= versionCode(installed)) fail("Cette version n'est pas plus récente.")
        if (signatures(archive) != signatures(installed) || signatures(installed).isEmpty()) {
            fail("Signature différente : mise à jour refusée par sécurité.")
        }
    }

    @Suppress("DEPRECATION")
    private fun versionCode(p: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) p.longVersionCode else p.versionCode.toLong()

    @Suppress("DEPRECATION")
    private fun signatures(p: PackageInfo): Set<String> {
        val sigs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) p.signingInfo?.apkContentsSigners else p.signatures
        return sigs.orEmpty().map { s ->
            MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
        }.toSet()
    }

    /** Lance l'installeur Android (l'utilisateur confirme toujours). */
    fun install(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
