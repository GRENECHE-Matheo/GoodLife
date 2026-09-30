package com.goodlife.app.ui

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.goodlife.app.data.Repo
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Petits sons du quiz, synthétisés dans l'app (aucun fichier audio, aucune licence).
 * Joués sur le volume « média », seulement si l'option Sons est activée dans les Paramètres.
 */
enum class Sfx { CORRECT, WRONG, LEVEL_UP, XP_TICK }

object Sounds {
    private const val RATE = 44_100
    private val executor = Executors.newSingleThreadScheduledExecutor()
    private val cache = HashMap<Sfx, ShortArray>()
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    fun play(sfx: Sfx) {
        if (!Repo.settings.value.sounds) return
        executor.execute {
            runCatching {
                val pcm = cache.getOrPut(sfx) { synth(sfx) }
                val track = AudioTrack.Builder()
                    .setAudioAttributes(attributes)
                    .setAudioFormat(
                        AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(RATE).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()
                    )
                    .setBufferSizeInBytes(pcm.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                track.write(pcm, 0, pcm.size)
                track.play()
                // Libère la piste une fois le son terminé (sans bloquer les sons suivants)
                executor.schedule({ runCatching { track.release() } }, pcm.size * 1000L / RATE + 100, TimeUnit.MILLISECONDS)
            }
        }
    }

    /** Une note : fréquence (Hz), début et durée (s), volume. */
    private data class Note(val freq: Double, val start: Double, val dur: Double, val vol: Double = 0.5)

    private fun midi(n: Int) = 440.0 * 2.0.pow((n - 69) / 12.0)

    private fun synth(sfx: Sfx): ShortArray {
        val notes = when (sfx) {
            // Deux notes montantes, claires (do – sol)
            Sfx.CORRECT -> listOf(Note(midi(79), 0.0, 0.16, 0.45), Note(midi(86), 0.08, 0.32, 0.45))
            // Deux notes graves descendantes, douces (pas agressif)
            Sfx.WRONG -> listOf(Note(midi(57), 0.0, 0.18, 0.40), Note(midi(52), 0.13, 0.30, 0.40))
            // Petit arpège de victoire (do – mi – sol – do)
            Sfx.LEVEL_UP -> listOf(72, 76, 79, 84).mapIndexed { i, n ->
                Note(midi(n), i * 0.09, if (i == 3) 0.55 else 0.2, 0.38)
            } + Note(midi(88), 0.27, 0.5, 0.18)
            // « Tic » très court pendant que la barre d'XP se remplit
            Sfx.XP_TICK -> listOf(Note(midi(96), 0.0, 0.05, 0.18))
        }
        val total = notes.maxOf { it.start + it.dur } + 0.05
        val out = DoubleArray((total * RATE).toInt())
        for (n in notes) {
            val from = (n.start * RATE).toInt()
            val len = (n.dur * RATE).toInt()
            for (i in 0 until len) {
                val t = i.toDouble() / RATE
                // Attaque rapide puis décroissance exponentielle : son de clochette
                val env = min(1.0, t / 0.006) * exp(-t * 5.5 / n.dur)
                val w = 2 * PI * n.freq * t
                val v = sin(w) + 0.35 * sin(2 * w) + 0.12 * sin(3 * w)
                val idx = from + i
                if (idx < out.size) out[idx] += v * env * n.vol * 0.6
            }
        }
        return ShortArray(out.size) { i -> (out[i].coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort() }
    }
}
