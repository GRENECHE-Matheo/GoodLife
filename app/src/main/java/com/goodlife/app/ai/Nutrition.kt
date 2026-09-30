package com.goodlife.app.ai

import com.goodlife.app.data.Goal
import com.goodlife.app.data.Profile
import com.goodlife.app.data.Sex
import kotlin.math.roundToInt

/** Calculs de base, utilisés hors-ligne et comme garde-fou pour l'IA. */
object Nutrition {

    /** Métabolisme de base, équation de Mifflin-St Jeor. */
    fun bmr(p: Profile): Double {
        val base = 10 * p.weightKg + 6.25 * p.heightCm - 5 * p.age
        return if (p.sex == Sex.HOMME) base + 5 else base - 161
    }

    fun tdee(p: Profile): Double = bmr(p) * p.activity.factor

    /** Apport minimal de sécurité : on ne descend jamais sous ce seuil. */
    fun safetyFloor(p: Profile): Int = if (p.sex == Sex.HOMME) 1500 else 1200

    fun clampTarget(p: Profile, kcal: Int): Int =
        kcal.coerceIn(safetyFloor(p), 4500)

    fun formulaTarget(profile: Profile): Profile {
        val p = if (profile.goal == Goal.PERTE && !weightLossAllowed(profile)) profile.copy(goal = Goal.MAINTIEN) else profile
        val kcal = clampTarget(p, (tdee(p) + p.goal.deltaKcal).roundToInt())
        val protein = (p.weightKg * if (p.goal == Goal.PRISE) 1.8 else 1.6).roundToInt()
        val fat = (kcal * 0.28 / 9).roundToInt()
        val carbs = ((kcal - protein * 4 - fat * 9) / 4.0).roundToInt().coerceAtLeast(0)
        return p.copy(
            targetKcal = kcal, proteinG = protein, carbsG = carbs, fatG = fat,
            targetSource = "formule",
            targetExplanation = "Calcul Mifflin-St Jeor : métabolisme de base ${bmr(p).roundToInt()} kcal × activité " +
                "(${p.activity.label}) = ${tdee(p).roundToInt()} kcal/jour, ajusté pour l'objectif « ${p.goal.label} »."
        )
    }

    /** Âge minimum pour utiliser l'app seul (majorité numérique en France, loi Informatique et Libertés art. 45). */
    const val MIN_AGE = 15

    /**
     * Garde-fou santé : pas d'objectif de perte de poids pour les mineurs (croissance) ni quand l'IMC
     * est déjà sous 18,5 (maigreur), pour ne pas encourager des troubles du comportement alimentaire.
     */
    fun weightLossAllowed(p: Profile): Boolean = p.age >= 18 && bmi(p) >= 18.5

    fun bmi(p: Profile): Double {
        val m = p.heightCm / 100.0
        return if (m > 0) p.weightKg / (m * m) else 0.0
    }
}
