package com.goodlife.app.social

import android.util.Base64
import com.goodlife.app.data.Repo
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Balise des croisements, lue en Bluetooth par les téléphones GoodLife proches. Elle ne contient pas la carte en
 * clair :
 * - un identifiant tiré de ma clé de croisement, qui change toutes les 15 minutes (un inconnu ne peut pas relier
 *   deux croisements entre eux, ni me suivre d'un endroit à l'autre) ;
 * - mon niveau, si je le partage ;
 * - ma carte signée, chiffrée (AES-GCM) avec une clé du créneau tirée de ma clé de croisement.
 * Mes amis ont reçu ma clé de croisement avec ma carte (QR, NFC, lien) : eux seuls retrouvent l'identifiant,
 * déchiffrent la carte et me reconnaissent. Pour les autres, je suis « un joueur niveau 12 », anonyme.
 */
object Crossing {
    private const val MAGIC = 0xC3
    private const val VERSION = 1
    private const val SLOT_MS = 15 * 60_000L
    private const val HEADER = 2 + 8 + 2 + 12 + 2

    fun slot(now: Long = System.currentTimeMillis()): Long = now / SLOT_MS

    /** Délai avant le prochain créneau (pour changer de balise pile à l'heure). */
    fun msToNextSlot(now: Long = System.currentTimeMillis()): Long = SLOT_MS - now % SLOT_MS

    private fun hmac(secret: ByteArray, label: String, slot: Long): ByteArray =
        Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(secret, "HmacSHA256"))
            update(label.toByteArray(Charsets.US_ASCII))
            update(ByteBuffer.allocate(8).putLong(slot).array())
            doFinal()
        }

    private fun rid(secret: ByteArray, slot: Long) = hmac(secret, "rid", slot).copyOf(8)
    private fun key(secret: ByteArray, slot: Long) = SecretKeySpec(hmac(secret, "key", slot).copyOf(16), "AES")

    /** Ma balise pour le créneau en cours (null si je ne partage rien). */
    fun beacon(): ByteArray? {
        val card = Social.myCard(withSecret = false) ?: return null
        val signed = runCatching { Identity.sign(card) }.getOrNull() ?: return null
        val secret = Social.crossSecret()
        val s = slot()
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val ct = Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.ENCRYPT_MODE, key(secret, s), GCMParameterSpec(128, iv))
            doFinal(signed)
        }
        val level = (card.level ?: 0).coerceIn(0, 999)
        return ByteArrayOutputStream().apply {
            write(MAGIC); write(VERSION)
            write(rid(secret, s))
            write(level shr 8); write(level and 0xFF)
            write(iv)
            write(ct.size shr 8); write(ct.size and 0xFF)
            write(ct)
        }.toByteArray()
    }

    /** Ce qu'une balise lue m'apprend. */
    sealed class Seen {
        /** Un ami : sa carte, déchiffrée et dont la signature est vérifiée. */
        data class Friend(val card: PlayerCard) : Seen()
        /** Un inconnu : seulement l'identifiant du créneau et son niveau (s'il le partage). */
        data class Stranger(val rid: String, val level: Int?) : Seen()
    }

    /** true si ces octets sont une balise (et pas une carte en clair d'une ancienne version de l'app). */
    fun isBeacon(bytes: ByteArray) = bytes.size >= HEADER && (bytes[0].toInt() and 0xFF) == MAGIC

    fun read(bytes: ByteArray, people: List<com.goodlife.app.data.Person> = Repo.social.value.people): Seen? = runCatching {
        if (!isBeacon(bytes) || bytes.size > 1400 || bytes[1].toInt() != VERSION) return null
        val rid = bytes.copyOfRange(2, 10)
        val level = ((bytes[10].toInt() and 0xFF) shl 8) or (bytes[11].toInt() and 0xFF)
        val iv = bytes.copyOfRange(12, 24)
        val len = ((bytes[24].toInt() and 0xFF) shl 8) or (bytes[25].toInt() and 0xFF)
        if (HEADER + len > bytes.size) return null
        val ct = bytes.copyOfRange(HEADER, HEADER + len)
        val now = slot()
        // Un ami ? On essaie sa clé sur le créneau en cours et ses voisins (horloges un peu décalées)
        for (p in people) {
            if (!p.friend || p.crossSecret.isBlank()) continue
            val secret = runCatching { Base64.decode(p.crossSecret, Base64.NO_WRAP) }.getOrNull()?.takeIf { it.size == 32 } ?: continue
            for (s in now - 1..now + 1) {
                if (!MessageDigest.isEqual(rid(secret, s), rid)) continue
                val plain = runCatching {
                    Cipher.getInstance("AES/GCM/NoPadding").run {
                        init(Cipher.DECRYPT_MODE, key(secret, s), GCMParameterSpec(128, iv))
                        doFinal(ct)
                    }
                }.getOrNull() ?: continue
                val card = Identity.verify(plain) ?: continue
                if (card.id == p.id) return Seen.Friend(card)
            }
        }
        Seen.Stranger(rid.joinToString("") { "%02x".format(it) }, level.takeIf { it in 1..999 })
    }.getOrNull()
}
