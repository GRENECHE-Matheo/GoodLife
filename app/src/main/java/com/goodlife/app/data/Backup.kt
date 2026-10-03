package com.goodlife.app.data

import com.goodlife.app.net.readCapped
import com.goodlife.app.i18n.t

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Sauvegarde chiffrée par mot de passe (fichier « .goodlife »).
 *
 * - La clé est dérivée du mot de passe (PBKDF2-HMAC-SHA256, 310 000 itérations, sel aléatoire).
 *   Le mot de passe n'est jamais enregistré : seule la clé dérivée est gardée, elle-même chiffrée
 *   par l'Android Keystore, pour pouvoir refaire la sauvegarde automatiquement.
 * - Le contenu est chiffré en AES-256-GCM (l'en-tête du fichier est authentifié avec).
 * - Sans le mot de passe, le fichier est illisible, y compris pour le service qui le stocke.
 * - La clé API Gemini, le consentement IA et les réglages propres au téléphone ne sont pas inclus.
 */
object Backup {
    private val MAGIC = byteArrayOf('G'.code.toByte(), 'L'.code.toByte(), 'B'.code.toByte(), 'K'.code.toByte())
    private const val VERSION = 1
    private const val ITERATIONS = 310_000
    private const val SALT_SIZE = 16
    private const val IV_SIZE = 12
    const val MIN_PASSWORD = 8
    const val MIME = "application/octet-stream"
    const val FILE_NAME = "Lifoody-sauvegarde.goodlife"

    class WrongPassword : Exception(t("Mot de passe incorrect, ou fichier abîmé."))
    class NotABackup : Exception(t("Ce fichier n'est pas une sauvegarde Lifoody."))

    /** Clé dérivée + paramètres nécessaires pour que le fichier soit relisible avec le mot de passe. */
    data class DerivedKey(val key: ByteArray, val salt: ByteArray, val iterations: Int) {
        fun encode(): String = listOf(key, salt).joinToString(":") { Base64.encodeToString(it, Base64.NO_WRAP) } + ":$iterations"

        companion object {
            fun decode(s: String): DerivedKey? = runCatching {
                val (k, salt, it) = s.split(":")
                DerivedKey(Base64.decode(k, Base64.NO_WRAP), Base64.decode(salt, Base64.NO_WRAP), it.toInt())
            }.getOrNull()
        }
    }

    /** Lent volontairement (≈ 1 s) pour freiner les essais de mots de passe : à appeler hors du thread principal. */
    fun deriveKey(password: String, salt: ByteArray = randomBytes(SALT_SIZE), iterations: Int = ITERATIONS): DerivedKey {
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, 256)
        val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        spec.clearPassword()
        return DerivedKey(key, salt, iterations)
    }

    fun encrypt(json: String, k: DerivedKey): ByteArray {
        val iv = randomBytes(IV_SIZE)
        val header = ByteArrayOutputStream().also { bytes ->
            DataOutputStream(bytes).apply {
                write(MAGIC); writeByte(VERSION); writeInt(k.iterations)
                writeByte(k.salt.size); write(k.salt); writeByte(iv.size); write(iv)
            }
        }.toByteArray()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(k.key, "AES"), GCMParameterSpec(128, iv))
        cipher.updateAAD(header)
        return header + cipher.doFinal(json.toByteArray(Charsets.UTF_8))
    }

    /** Déchiffre un fichier avec le mot de passe. Renvoie le JSON et la clé dérivée (réutilisable). */
    fun decrypt(file: ByteArray, password: String): Pair<String, DerivedKey> {
        val input = DataInputStream(file.inputStream())
        val magic = ByteArray(4)
        if (file.size < 40 || input.read(magic) != 4 || !magic.contentEquals(MAGIC)) throw NotABackup()
        if (input.readUnsignedByte() != VERSION) throw NotABackup()
        val iterations = input.readInt()
        if (iterations !in 10_000..5_000_000) throw NotABackup()
        val salt = ByteArray(input.readUnsignedByte()).also { input.readFully(it) }
        val iv = ByteArray(input.readUnsignedByte()).also { input.readFully(it) }
        val headerSize = 4 + 1 + 4 + 1 + salt.size + 1 + iv.size
        val k = deriveKey(password, salt, iterations)
        val plain = runCatching {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(k.key, "AES"), GCMParameterSpec(128, iv))
            cipher.updateAAD(file, 0, headerSize)
            cipher.doFinal(file, headerSize, file.size - headerSize)
        }.getOrElse { throw WrongPassword() }
        return String(plain, Charsets.UTF_8) to k
    }

    /** Une sauvegarde fait quelques Mo au plus : au-delà de 64 Mo, ce n'est pas un fichier GoodLife. */
    fun read(context: Context, uri: Uri): ByteArray =
        context.contentResolver.openInputStream(uri)?.use {
            runCatching { it.readCapped(64 * 1024 * 1024) }
                .getOrElse { throw IllegalStateException(t("Fichier trop volumineux pour être une sauvegarde Lifoody.")) }
        } ?: throw IllegalStateException(t("Fichier illisible."))

    fun write(context: Context, uri: Uri, bytes: ByteArray) {
        // « wt » vide le fichier avant d'écrire ; certains services (ex. Drive) ne connaissent que « w ».
        val out = runCatching { context.contentResolver.openOutputStream(uri, "wt") }.getOrNull()
            ?: context.contentResolver.openOutputStream(uri, "w")
            ?: throw IllegalStateException(t("Impossible d'écrire le fichier."))
        out.use { it.write(bytes) }
    }

    /** Garde l'accès au fichier choisi après un redémarrage du téléphone (sauvegarde automatique). */
    fun keepAccess(context: Context, uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
    }

    fun releaseAccess(context: Context, uri: Uri) {
        runCatching {
            context.contentResolver.releasePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
    }

    /**
     * Sauvegarde automatique : réécrit le fichier choisi si les données ont changé.
     * Appelée quand l'app passe en arrière-plan ([force] : bouton « Sauvegarder maintenant »).
     * Ne fait rien si la sauvegarde n'est pas configurée.
     */
    @Synchronized
    fun autoBackup(context: Context, force: Boolean = false) {
        val s = Repo.settings.value
        if (s.backupUri.isBlank() || (!force && !Repo.backupNeeded())) return
        val key = DerivedKey.decode(s.backupKey) ?: return
        val revision = Repo.revision
        val result = runCatching {
            write(context, Uri.parse(s.backupUri), encrypt(Repo.backupJson(), key))
        }
        Repo.onBackupDone(revision, result.exceptionOrNull()?.let { t("La dernière sauvegarde a échoué : %1\$s", it.message ?: t("fichier inaccessible")) })
    }

    private fun randomBytes(n: Int) = ByteArray(n).also { SecureRandom().nextBytes(it) }
}
