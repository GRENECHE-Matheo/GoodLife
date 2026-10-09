package com.goodlife.app.store

import android.app.Activity
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Version GitHub : 100 % gratuite. Pas d'abonnement ni de serveur Lifoody : l'IA passe uniquement par la clé
 * Gemini personnelle de la personne, directement chez Google. Ces fonctions ne font donc rien.
 */
object Store {
    const val available = false
    val offers: StateFlow<List<Offer>> = MutableStateFlow(emptyList())
    val premium: StateFlow<Boolean> = MutableStateFlow(false)
    val message: StateFlow<String?> = MutableStateFlow(null)
    fun clearMessage() = Unit
    var onChange: (() -> Unit)? = null
    fun init(context: Context) = Unit
    suspend fun refresh(): Boolean = false
    @Suppress("UNUSED_PARAMETER")
    fun buy(activity: Activity, offer: Offer): String? = null
    fun purchaseToken(): String? = null
    @Suppress("UNUSED_PARAMETER")
    fun manageUrl(packageName: String): String = ""
    @Suppress("UNUSED_PARAMETER")
    suspend fun integrityToken(requestHash: String): String? = null
}
