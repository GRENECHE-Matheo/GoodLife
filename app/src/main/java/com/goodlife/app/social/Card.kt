package com.goodlife.app.social

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.goodlife.app.dex.Nutridex
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec

/** Encouragement tout prêt (pas de texte libre : rien à modérer). */
val CHEERS = listOf(
    "Bravo pour ta série ! 🔥",
    "Continue comme ça 💪",
    "Tu vas y arriver !",
    "Belle découverte au Nutridex 🥦",
    "On se fait une marche ? 🚶",
    "Fier de toi !"
)

/** Encouragement envoyé à [target] (id d'ami), le jour [day] (jours depuis 1970). */
data class Cheer(val target: String, val message: Int, val day: Int)

/** Bilan de la semaine [week] (numéro de semaine, lundi → dimanche) pour le défi entre amis. */
data class WeekStats(val week: Int, val days: Int, val steps: Int, val xp: Int)

/**
 * Carte d'un joueur, échangée par Tap to Sync, QR code ou StreetPass. Ne contient que ce que la personne
 * a choisi de partager, et elle est signée avec sa clé (Android Keystore) : impossible de la falsifier
 * ou de se faire passer pour quelqu'un d'autre.
 */
data class PlayerCard(
    val publicKey: ByteArray,
    val pseudo: String,
    val level: Int?,          // null = non partagé
    val streak: Int?,
    val bestStreak: Int?,
    val dex: Set<String>?,    // ids du Nutridex débloqués, null = non partagé
    val timestamp: Long,
    val cheers: List<Cheer> = emptyList(),
    val week: WeekStats? = null   // null = non partagé (v2)
) {
    val id: String get() = idOf(publicKey)

    companion object {
        fun idOf(publicKey: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(publicKey).take(8).joinToString("") { "%02x".format(it) }
    }
}

object Identity {
    private const val KEYSTORE = "AndroidKeyStore"
    private const val ALIAS = "goodlife_identity"
    /** v2 : ajoute le bilan de la semaine (défi entre amis). Les cartes v1 restent lisibles. */
    private const val VERSION = 2
    const val PREFIX = "GL1:"
    const val MAX_PSEUDO = 20

    @Synchronized
    private fun keyPair(): Pair<PrivateKey, PublicKey> {
        val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.PrivateKeyEntry)?.let { return it.privateKey to it.certificate.publicKey }
        val gen = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, KEYSTORE)
        gen.initialize(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
                .setDigests(KeyProperties.DIGEST_SHA256)
                .build()
        )
        val kp = gen.generateKeyPair()
        return kp.private to kp.public
    }

    fun myPublicKey(): ByteArray = keyPair().second.encoded
    fun myId(): String = PlayerCard.idOf(myPublicKey())

    /** Efface l'identité (effacement des données) : une nouvelle sera créée au besoin. */
    fun reset() {
        runCatching { KeyStore.getInstance(KEYSTORE).apply { load(null) }.deleteEntry(ALIAS) }
    }

    fun cleanPseudo(s: String): String =
        s.filter { !it.isISOControl() }.trim().replace(Regex("\\s+"), " ").take(MAX_PSEUDO)

    // ---------- Format binaire compact (tient dans un QR code et une caractéristique Bluetooth) ----------

    private fun body(c: PlayerCard): ByteArray {
        val out = ByteArrayOutputStream()
        DataOutputStream(out).apply {
            writeByte(VERSION)
            var flags = 0
            if (c.level != null) flags = flags or 1
            if (c.streak != null) flags = flags or 2
            if (c.dex != null) flags = flags or 4
            if (c.week != null) flags = flags or 8
            writeByte(flags)
            writeShort(c.publicKey.size); write(c.publicKey)
            val p = cleanPseudo(c.pseudo).toByteArray(Charsets.UTF_8).take(60).toByteArray()
            writeByte(p.size); write(p)
            writeShort(c.level ?: 0); writeShort(c.streak ?: 0); writeShort(c.bestStreak ?: 0)
            val bits = ByteArray((Nutridex.ENTRIES.size + 7) / 8)
            c.dex?.let { set -> Nutridex.ENTRIES.forEachIndexed { i, e -> if (e.id in set) bits[i / 8] = (bits[i / 8].toInt() or (1 shl (i % 8))).toByte() } }
            writeByte(bits.size); write(bits)
            writeLong(c.timestamp)
            val cheers = c.cheers.take(5)
            writeByte(cheers.size)
            cheers.forEach { ch ->
                write(ch.target.chunked(2).map { it.toInt(16).toByte() }.toByteArray().copyOf(8))
                writeByte(ch.message); writeShort(ch.day)
            }
            c.week?.let { w ->
                writeShort(w.week); writeByte(w.days.coerceIn(0, 7)); writeInt(w.steps.coerceIn(0, 1_000_000)); writeShort(w.xp.coerceIn(0, 9999))
            }
        }
        return out.toByteArray()
    }

    /** Ma carte signée. */
    fun sign(card: PlayerCard): ByteArray {
        val b = body(card)
        val sig = Signature.getInstance("SHA256withECDSA").apply { initSign(keyPair().first); update(b) }.sign()
        return b + byteArrayOf(sig.size.toByte()) + sig
    }

    fun toText(signed: ByteArray): String = PREFIX + Base64.encodeToString(signed, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)

    fun fromText(text: String): PlayerCard? {
        if (!text.startsWith(PREFIX) || text.length > 2000) return null
        val bytes = runCatching { Base64.decode(text.removePrefix(PREFIX), Base64.URL_SAFE) }.getOrNull() ?: return null
        return verify(bytes)
    }

    /** Lit et vérifie une carte ; null si elle est abîmée, falsifiée ou incohérente. */
    fun verify(bytes: ByteArray): PlayerCard? = runCatching {
        if (bytes.size > 1024) return null
        val input = DataInputStream(bytes.inputStream())
        val version = input.readUnsignedByte()
        if (version != 1 && version != 2) return null
        val flags = input.readUnsignedByte()
        val pk = ByteArray(input.readUnsignedShort().also { require(it in 50..200) }).also { input.readFully(it) }
        val pseudo = ByteArray(input.readUnsignedByte().also { require(it <= 60) }).also { input.readFully(it) }
            .toString(Charsets.UTF_8).let(::cleanPseudo)
        val level = input.readUnsignedShort(); val streak = input.readUnsignedShort(); val best = input.readUnsignedShort()
        val bits = ByteArray(input.readUnsignedByte().also { require(it <= 64) }).also { input.readFully(it) }
        val ts = input.readLong()
        val cheers = (0 until input.readUnsignedByte().also { require(it <= 5) }).map {
            val t = ByteArray(8).also { input.readFully(it) }.joinToString("") { b -> "%02x".format(b) }
            Cheer(t, input.readUnsignedByte(), input.readUnsignedShort())
        }
        val week = if (version >= 2 && flags and 8 != 0)
            WeekStats(input.readUnsignedShort(), input.readUnsignedByte().coerceIn(0, 7), input.readInt().coerceIn(0, 1_000_000), input.readUnsignedShort())
        else null
        val bodyLen = bytes.size - input.available()
        val sig = ByteArray(input.readUnsignedByte()).also { input.readFully(it) }
        val publicKey: PublicKey = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(pk))
        val ok = Signature.getInstance("SHA256withECDSA").apply {
            initVerify(publicKey); update(bytes, 0, bodyLen)
        }.verify(sig)
        if (!ok) return null
        if (pseudo.isBlank() || ts > System.currentTimeMillis() + 86_400_000L) return null
        PlayerCard(
            publicKey = pk,
            pseudo = pseudo,
            level = if (flags and 1 != 0) level.coerceIn(1, 999) else null,
            streak = if (flags and 2 != 0) streak.coerceIn(0, 9999) else null,
            bestStreak = if (flags and 2 != 0) best.coerceIn(0, 9999) else null,
            dex = if (flags and 4 != 0) Nutridex.ENTRIES.filterIndexed { i, _ ->
                i / 8 < bits.size && (bits[i / 8].toInt() shr (i % 8)) and 1 == 1
            }.map { it.id }.toSet() else null,
            timestamp = ts,
            cheers = cheers.filter { it.message in CHEERS.indices },
            week = week
        )
    }.getOrNull()
}
