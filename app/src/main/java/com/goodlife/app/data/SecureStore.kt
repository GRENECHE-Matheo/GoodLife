package com.goodlife.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stockage local chiffré : AES-256-GCM avec une clé générée et gardée
 * dans l'Android Keystore (la clé ne quitte jamais le téléphone).
 * Pas de sauvegarde Android automatique (désactivée dans le manifest) : la seule copie possible est la
 * sauvegarde chiffrée par mot de passe, activée par l'utilisateur (voir Backup).
 */
class SecureStore(context: Context) {

    private val prefs = context.getSharedPreferences("goodlife_secure", Context.MODE_PRIVATE)
    private val filesDir = java.io.File(context.filesDir, "secure").apply { mkdirs() }

    @Synchronized
    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        gen.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    @Synchronized
    fun put(name: String, value: String?) {
        if (value == null) {
            prefs.edit().remove(name).apply()
            return
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val packed = cipher.iv + encrypted
        prefs.edit().putString(name, Base64.encodeToString(packed, Base64.NO_WRAP)).apply()
    }

    @Synchronized
    fun get(name: String): String? {
        val stored = prefs.getString(name, null) ?: return null
        return runCatching {
            val bytes = Base64.decode(stored, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, IV_SIZE))
            String(cipher.doFinal(bytes, IV_SIZE, bytes.size - IV_SIZE), Charsets.UTF_8)
        }.getOrNull()
    }

    /** Fichier binaire chiffré (ex. photos du Nutridex), même clé Keystore. [name] : lettres, chiffres, - et _. */
    @Synchronized
    fun putBytes(name: String, value: ByteArray?) {
        val f = fileFor(name)
        if (value == null) { f.delete(); return }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val tmp = java.io.File(f.path + ".tmp")
        tmp.writeBytes(cipher.iv + cipher.doFinal(value))
        tmp.renameTo(f)
    }

    @Synchronized
    fun getBytes(name: String): ByteArray? {
        val f = fileFor(name)
        if (!f.exists()) return null
        return runCatching {
            val bytes = f.readBytes()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, IV_SIZE))
            cipher.doFinal(bytes, IV_SIZE, bytes.size - IV_SIZE)
        }.getOrNull()
    }

    private fun fileFor(name: String): java.io.File {
        require(name.matches(Regex("[a-zA-Z0-9_-]{1,80}"))) { "Nom de fichier invalide" }
        return java.io.File(filesDir, "$name.bin")
    }

    @Synchronized
    fun clear() {
        prefs.edit().clear().apply()
        filesDir.listFiles()?.forEach { it.delete() }
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "goodlife_master_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
    }
}
