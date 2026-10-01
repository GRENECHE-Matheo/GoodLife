package com.goodlife.app.data

import com.goodlife.app.i18n.t

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

    /**
     * Clé Keystore réservée aux secrets (clé API Gemini, clé de la sauvegarde) :
     * - dans la puce de sécurité dédiée (StrongBox) si le téléphone en a une, sinon dans l'environnement sécurisé (TEE) ;
     * - utilisable seulement quand le téléphone est déverrouillé (Android 9+) ;
     * - jamais exportable : même avec les fichiers de l'app, personne ne peut la lire (ni l'utilisateur, ni le développeur).
     */
    @Synchronized
    private fun secretKey(): SecretKey {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (ks.getEntry(SECRET_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        fun spec(strongBox: Boolean, unlocked: Boolean) = KeyGenParameterSpec.Builder(SECRET_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .apply {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    if (unlocked) setUnlockedDeviceRequired(true)
                    if (strongBox) setIsStrongBoxBacked(true)
                }
            }
            .build()
        // Du plus protégé au plus simple : puce StrongBox + déverrouillage requis, puis TEE + déverrouillage requis,
        // puis TEE seul (téléphone sans code de verrouillage : Android refuse alors l'option « déverrouillé »).
        val options = listOf(true to true, false to true, false to false)
        var last: Throwable? = null
        for ((strongBox, unlocked) in options) {
            val key = runCatching {
                val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
                gen.init(spec(strongBox, unlocked))
                gen.generateKey().also { k ->
                    // Vérifie tout de suite qu'elle est utilisable (sinon Android ne le dit qu'au premier chiffrement)
                    Cipher.getInstance(TRANSFORMATION).init(Cipher.ENCRYPT_MODE, k)
                }
            }
            if (key.isSuccess) return key.getOrThrow()
            last = key.exceptionOrNull()
            runCatching { ks.deleteEntry(SECRET_ALIAS) }
        }
        throw last ?: IllegalStateException("Keystore")
    }

    /** Enregistre un secret (null = l'effacer). Chiffré avec [secretKey], à part des autres données. */
    @Synchronized
    fun putSecret(name: String, value: String?) {
        if (value.isNullOrEmpty()) { prefs.edit().remove(SECRET_PREFIX + name).commit(); return }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val packed = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        prefs.edit().putString(SECRET_PREFIX + name, Base64.encodeToString(packed, Base64.NO_WRAP)).commit()
    }

    /** Secret déchiffré, ou null s'il n'existe pas ou si le téléphone est verrouillé. */
    @Synchronized
    fun getSecret(name: String): String? {
        val stored = prefs.getString(SECRET_PREFIX + name, null) ?: return null
        return runCatching {
            val bytes = Base64.decode(stored, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, bytes, 0, IV_SIZE))
            String(cipher.doFinal(bytes, IV_SIZE, bytes.size - IV_SIZE), Charsets.UTF_8)
        }.getOrNull()
    }

    fun hasSecret(name: String): Boolean = prefs.contains(SECRET_PREFIX + name)

    /**
     * Remplace tous les secrets d'un coup, sans laisser de trace : les anciens sont effacés, la clé du coffre est
     * détruite dans la puce puis recréée, et seuls les secrets donnés sont rechiffrés avec la nouvelle clé.
     * Une ancienne copie chiffrée (restée par exemple dans la mémoire flash) ne pourra donc plus jamais être lue.
     */
    @Synchronized
    fun replaceSecrets(values: Map<String, String?>) {
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith(SECRET_PREFIX) }.forEach { editor.remove(it) }
        editor.commit()
        runCatching { KeyStore.getInstance(KEYSTORE).apply { load(null) }.deleteEntry(SECRET_ALIAS) }
        values.forEach { (name, value) -> if (!value.isNullOrEmpty()) putSecret(name, value) }
    }

    /** Écrit tout de suite sur le disque (avant un redémarrage de l'app, par exemple). */
    fun flush() {
        prefs.edit().commit()
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
        require(name.matches(Regex("[a-zA-Z0-9_-]{1,80}"))) { t("Nom de fichier invalide") }
        return java.io.File(filesDir, "$name.bin")
    }

    @Synchronized
    fun clear() {
        prefs.edit().clear().apply()
        filesDir.listFiles()?.forEach { it.delete() }
        // La clé des secrets est détruite : même une ancienne copie des fichiers ne pourrait plus être déchiffrée
        runCatching { KeyStore.getInstance(KEYSTORE).apply { load(null) }.deleteEntry(SECRET_ALIAS) }
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "goodlife_master_key"
        const val SECRET_ALIAS = "goodlife_secret_key"
        const val SECRET_PREFIX = "secret_"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_SIZE = 12
    }
}
