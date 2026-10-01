package com.goodlife.app.ai

import com.goodlife.app.i18n.t
import java.text.Normalizer

/**
 * Filtre côté app (indépendant de l'IA) : avant d'afficher une réponse libre (chat, coach), on vérifie qu'elle ne
 * recopie pas les consignes internes. Les consignes sont découpées en suites de 7 mots ; si la réponse en reprend
 * au moins 2, elle n'est pas affichée et le chef répond à la place.
 * Seules les consignes fixes sont comparées (jamais les chiffres de la personne), pour ne pas bloquer une réponse
 * qui cite ses propres repas ou son objectif.
 */
object PromptShield {
    private const val WINDOW = 7
    private const val MIN_HITS = 2

    @Volatile private var shingles: Set<String> = emptySet()

    /** Consignes à protéger (appelé une fois par les écrans qui les définissent). */
    @Synchronized
    fun protect(vararg texts: String) {
        shingles = shingles + texts.flatMap { grams(it) }
    }

    private fun words(text: String): List<String> =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .split(Regex("[^a-z0-9]+")).filter { it.isNotEmpty() }

    private fun grams(text: String): Set<String> {
        val w = words(text)
        return if (w.size < WINDOW) emptySet() else (0..w.size - WINDOW).map { w.subList(it, it + WINDOW).joinToString(" ") }.toSet()
    }

    /** La réponse recopie-t-elle les consignes ? */
    fun leaks(reply: String): Boolean {
        val s = shingles
        if (s.isEmpty()) return false
        return grams(reply).count { it in s } >= MIN_HITS
    }

    /** Réponse affichable : celle de l'IA, ou un mot du chef si elle recopiait ses consignes. */
    fun clean(reply: String): String =
        if (leaks(reply)) t("Mes consignes de cuisine restent secrètes 😉 Je suis le chef de GoodLife : pose-moi plutôt une question sur ton alimentation, ton sport ou ta motivation !")
        else reply
}
