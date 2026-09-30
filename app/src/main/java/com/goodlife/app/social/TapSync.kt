package com.goodlife.app.social

import android.app.Activity
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.cardemulation.HostApduService
import android.nfc.tech.IsoDep
import android.os.Bundle
import com.goodlife.app.data.Repo
import java.io.ByteArrayOutputStream

/**
 * Tap to Sync : on colle deux téléphones, ils s'échangent leurs cartes par NFC.
 * Un téléphone joue le « lecteur », l'autre la « carte » (émulation de carte, HCE) ; comme les deux ont
 * l'écran ouvert, ils alternent les rôles au hasard jusqu'à ce que l'un lise l'autre, puis l'échange se fait
 * dans les deux sens en un seul contact. Rien n'est actif en dehors de l'écran « Ajouter un ami ».
 */
object TapSync {
    /** AID propriétaire de GoodLife : F0 + « GOODLIFE » en ASCII. */
    val AID: ByteArray = byteArrayOf(0xF0.toByte()) + "GOODLIFE".toByteArray(Charsets.US_ASCII)
    private const val CHUNK = 200
    val OK = byteArrayOf(0x90.toByte(), 0x00)
    val NOT_READY = byteArrayOf(0x69.toByte(), 0x85.toByte())
    val NOTHING = byteArrayOf(0x6A.toByte(), 0x82.toByte())

    /** L'écran d'ajout d'ami est ouvert (sinon le téléphone ne répond pas aux lecteurs NFC). */
    @Volatile var active = false

    fun available(activity: Activity): Boolean = NfcAdapter.getDefaultAdapter(activity) != null
    fun enabled(activity: Activity): Boolean = NfcAdapter.getDefaultAdapter(activity)?.isEnabled == true

    fun chunks(data: ByteArray): List<ByteArray> = data.toList().chunked(CHUNK).map { it.toByteArray() }

    fun startReader(activity: Activity, onDone: () -> Unit) {
        val nfc = NfcAdapter.getDefaultAdapter(activity) ?: return
        nfc.enableReaderMode(
            activity,
            { tag -> exchangeAsReader(tag); onDone() },
            NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK or NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS,
            Bundle().apply { putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 250) }
        )
    }

    fun stopReader(activity: Activity) {
        runCatching { NfcAdapter.getDefaultAdapter(activity)?.disableReaderMode(activity) }
    }

    @Volatile var busy = false

    /** Côté lecteur : sélectionne GoodLife sur l'autre téléphone, envoie ma carte, lit la sienne. */
    private fun exchangeAsReader(tag: Tag) {
        val iso = IsoDep.get(tag) ?: return
        busy = true
        try {
            iso.connect()
            iso.timeout = 3000
            val select = byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00, AID.size.toByte()) + AID + byteArrayOf(0x00)
            if (!iso.transceive(select).endsWith(OK)) return
            // 1. J'envoie ma carte (seulement si mon profil est public)
            Social.mySignedCard()?.let { mine ->
                val parts = chunks(mine)
                parts.forEachIndexed { i, part ->
                    val cmd = byteArrayOf(0x80.toByte(), 0x10, i.toByte(), parts.size.toByte(), part.size.toByte()) + part
                    if (!iso.transceive(cmd).endsWith(OK)) return
                }
            }
            // 2. Je lis la sienne, morceau par morceau (le premier donne la taille totale)
            val first = iso.transceive(byteArrayOf(0x80.toByte(), 0x20, 0x00, 0x00, 0x00))
            if (!first.endsWith(OK) || first.size < 4) return
            val total = ((first[0].toInt() and 0xFF) shl 8) or (first[1].toInt() and 0xFF)
            if (total !in 1..1024) return
            val buf = ByteArrayOutputStream().apply { write(first, 2, first.size - 4) }
            var i = 1
            while (buf.size() < total && i < 8) {
                val r = iso.transceive(byteArrayOf(0x80.toByte(), 0x20, i.toByte(), 0x00, 0x00))
                if (!r.endsWith(OK)) return
                buf.write(r, 0, r.size - 2)
                i++
            }
            Social.receive(buf.toByteArray().copyOf(total), "tap")
        } catch (_: Exception) {
            // contact perdu : on réessaiera au prochain contact
        } finally {
            runCatching { iso.close() }
            busy = false
        }
    }

    private fun ByteArray.endsWith(sw: ByteArray) =
        size >= 2 && this[size - 2] == sw[0] && this[size - 1] == sw[1]
}

/** Côté « carte » : répond au téléphone lecteur, seulement quand l'écran d'ajout d'ami est ouvert. */
class TapSyncService : HostApduService() {
    private val incoming = ByteArrayOutputStream()
    private var outgoing: ByteArray? = null

    override fun processCommandApdu(apdu: ByteArray, extras: Bundle?): ByteArray {
        if (!TapSync.active || apdu.size < 4) return TapSync.NOT_READY
        Repo.init(applicationContext)
        val ins = apdu[1].toInt() and 0xFF
        val p1 = apdu[2].toInt() and 0xFF
        return when {
            // SELECT de notre AID
            apdu[0] == 0x00.toByte() && ins == 0xA4 -> {
                incoming.reset()
                outgoing = Social.mySignedCard()
                TapSync.OK
            }
            // Réception d'un morceau de sa carte
            ins == 0x10 && apdu.size >= 5 -> {
                val len = apdu[4].toInt() and 0xFF
                if (apdu.size < 5 + len || incoming.size() + len > 1024) return TapSync.NOTHING
                incoming.write(apdu, 5, len)
                val total = apdu[3].toInt() and 0xFF
                if (p1 == total - 1) {
                    Social.receive(incoming.toByteArray(), "tap")
                    incoming.reset()
                }
                TapSync.OK
            }
            // Envoi de ma carte, morceau par morceau
            ins == 0x20 -> {
                val mine = outgoing ?: return TapSync.NOTHING
                val parts = TapSync.chunks(mine)
                if (p1 >= parts.size) return TapSync.NOTHING
                val head = if (p1 == 0) byteArrayOf((mine.size shr 8).toByte(), mine.size.toByte()) else ByteArray(0)
                head + parts[p1] + TapSync.OK
            }
            else -> TapSync.NOTHING
        }
    }

    override fun onDeactivated(reason: Int) {
        incoming.reset()
        outgoing = null
    }
}
