package com.goodlife.app.i18n

import java.util.Locale

/**
 * Langue de l'app : français, ou anglais pour tous les téléphones qui ne sont pas en français
 * (réglable dans Paramètres › Langue). Les textes sont écrits en français dans le code et traduits à l'affichage
 * par [t] grâce au dictionnaire [EN] : en français, [t] renvoie le texte tel quel.
 */
object Lang {
    @Volatile var en: Boolean = Locale.getDefault().language != "fr"
        private set

    /** « system » (langue du téléphone), « fr » ou « en ». */
    fun apply(choice: String) {
        en = when (choice) {
            "fr" -> false
            "en" -> true
            else -> Locale.getDefault().language != "fr"
        }
    }

    /** Locale des dates et des nombres (virgule décimale en français, point en anglais). */
    val locale: Locale get() = if (en) Locale.UK else Locale.FRANCE

    /** Nom de la langue pour demander à l'IA de répondre dans la bonne langue. */
    val aiLanguage: String get() = if (en) "anglais (English)" else "français"
}

/** Texte traduit (le texte français sert de clé). */
fun t(fr: String): String = if (Lang.en) EN[fr] ?: fr else fr

/**
 * Singulier ou pluriel selon [n] (règle française : 0 et 1 au singulier ; anglaise : seul 1 au singulier).
 * Les deux textes contiennent %1$s pour le nombre.
 */
fun tp(n: Number, one: String, many: String): String {
    val plural = if (Lang.en) n.toDouble() != 1.0 else n.toDouble() >= 2.0
    return t(if (plural) many else one, n)
}

/** Texte traduit avec des valeurs (%1$s, %2$s…), dans la langue de l'app. */
fun t(fr: String, vararg args: Any?): String {
    val pattern = if (Lang.en) EN[fr] ?: fr else fr
    return runCatching { String.format(Lang.locale, pattern, *args) }.getOrDefault(pattern)
}
