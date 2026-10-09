package com.goodlife.app.store

/**
 * Formule d'abonnement Premium telle que Google Play la propose à cette personne (prix dans sa devise,
 * essai gratuit seulement s'il y a droit). Version GitHub : aucune formule (tout est gratuit, avec sa propre clé).
 */
data class Offer(
    val basePlanId: String,
    val yearly: Boolean,
    val price: String,          // prix après l'essai, mis en forme par Google Play (« 3,99 € »)
    val priceMicros: Long,
    val currency: String,
    val trialDays: Int,         // 0 = pas d'essai gratuit pour cette personne
    val offerToken: String
)

/** Identifiant de l'abonnement dans la Play Console (le même que côté serveur : PRODUCT_IDS). */
const val PREMIUM_PRODUCT_ID = "lifoody_premium"
