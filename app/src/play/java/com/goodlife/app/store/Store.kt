package com.goodlife.app.store

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import com.goodlife.app.BuildConfig
import com.goodlife.app.i18n.t
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.StandardIntegrityManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Version Google Play : abonnement Premium (Google Play Billing) et preuve d'authenticité pour le relais IA
 * (Play Integrity). Le paiement, l'essai gratuit, la résiliation et le remboursement sont gérés par Google Play.
 */
object Store {
    const val available = true

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var client: BillingClient? = null
    @Volatile private var appContext: Context? = null
    private var details: ProductDetails? = null

    private val _offers = MutableStateFlow<List<Offer>>(emptyList())
    /** Formules proposées (mensuelle, annuelle), avec le prix et l'essai gratuit auxquels la personne a droit. */
    val offers: StateFlow<List<Offer>> = _offers

    private val _premium = MutableStateFlow(false)
    /** Abonnement actif d'après Google Play sur ce téléphone (le relais revérifie de son côté). */
    val premium: StateFlow<Boolean> = _premium

    private val _message = MutableStateFlow<String?>(null)
    /** Dernier message à afficher après un achat (succès, attente de paiement, erreur). */
    val message: StateFlow<String?> = _message
    fun clearMessage() { _message.value = null }

    @Volatile private var token: String? = null
    /** Jeton d'achat de l'abonnement actif, envoyé au relais pour qu'il le vérifie auprès de Google. */
    fun purchaseToken(): String? = token

    /** Appelé quand l'abonnement change (achat, fin), pour rafraîchir le statut auprès du relais. */
    var onChange: (() -> Unit)? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        if (client != null) return
        client = BillingClient.newBuilder(context.applicationContext)
            .setListener { result, purchases -> scope.launch { onPurchases(result, purchases) } }
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .enableAutoServiceReconnection()
            .build()
        scope.launch { refresh() }
    }

    private suspend fun connected(): BillingClient? {
        val c = client ?: return null
        if (c.isReady) return c
        val done = CompletableDeferred<Boolean>()
        c.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) { done.complete(result.responseCode == BillingClient.BillingResponseCode.OK) }
            override fun onBillingServiceDisconnected() { done.complete(false) }
        })
        return if (withTimeoutOrNull(10_000) { done.await() } == true) c else null
    }

    /** Relit l'abonnement et les formules (ouverture de l'app, retour dans l'app, « Restaurer mes achats »). */
    suspend fun refresh(): Boolean {
        val c = connected() ?: return false
        val owned = c.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build())
        if (owned.billingResult.responseCode == BillingClient.BillingResponseCode.OK) handle(owned.purchasesList)
        val r = c.queryProductDetails(
            QueryProductDetailsParams.newBuilder().setProductList(listOf(
                QueryProductDetailsParams.Product.newBuilder().setProductId(PREMIUM_PRODUCT_ID).setProductType(BillingClient.ProductType.SUBS).build()
            )).build()
        )
        details = r.productDetailsList?.firstOrNull()
        _offers.value = details?.let { offersOf(it) } ?: emptyList()
        return true
    }

    /** Pour chaque formule (mensuelle, annuelle) : l'offre avec essai gratuit si la personne y a droit, sinon le prix normal. */
    private fun offersOf(d: ProductDetails): List<Offer> {
        val all = d.subscriptionOfferDetails ?: return emptyList()
        return all.groupBy { it.basePlanId }.mapNotNull { (basePlan, list) ->
            val best = list.firstOrNull { o -> o.pricingPhases.pricingPhaseList.any { it.priceAmountMicros == 0L } } ?: list.firstOrNull { it.offerId == null } ?: list.first()
            val phases = best.pricingPhases.pricingPhaseList
            val regular = phases.lastOrNull() ?: return@mapNotNull null
            val trial = phases.firstOrNull { it.priceAmountMicros == 0L }
            Offer(
                basePlanId = basePlan,
                yearly = regular.billingPeriod.endsWith("Y"),
                price = regular.formattedPrice,
                priceMicros = regular.priceAmountMicros,
                currency = regular.priceCurrencyCode,
                trialDays = trial?.let { days(it.billingPeriod) } ?: 0,
                offerToken = best.offerToken
            )
        }.sortedBy { it.yearly }
    }

    /** Durée ISO 8601 (P7D, P1W, P1M…) en jours. */
    private fun days(period: String): Int {
        val m = Regex("""P(\d+)([DWMY])""").find(period) ?: return 0
        val n = m.groupValues[1].toInt()
        return when (m.groupValues[2]) { "D" -> n; "W" -> n * 7; "M" -> n * 30; else -> n * 365 }
    }

    /** Ouvre la fenêtre de paiement de Google Play. Renvoie un message d'erreur, ou null si la fenêtre s'est ouverte. */
    fun buy(activity: Activity, offer: Offer): String? {
        val c = client?.takeIf { it.isReady } ?: return t("Google Play n'est pas disponible. Vérifie ta connexion et que tu es connecté au Play Store.")
        val d = details ?: return t("Les formules Premium ne sont pas encore disponibles.")
        val params = BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(
            BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(d).setOfferToken(offer.offerToken).build()
        )).build()
        val r = c.launchBillingFlow(activity, params)
        return if (r.responseCode == BillingClient.BillingResponseCode.OK) null else t("Achat impossible pour le moment (%1\$s).", r.responseCode)
    }

    private suspend fun onPurchases(result: BillingResult, purchases: List<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> handle(purchases.orEmpty(), fresh = true)
            BillingClient.BillingResponseCode.USER_CANCELED -> Unit
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> { refresh(); _message.value = t("Tu as déjà Lifoody Premium : abonnement restauré.") }
            else -> _message.value = t("L'achat n'a pas abouti (%1\$s). Aucun montant n'a été prélevé.", result.responseCode)
        }
    }

    private suspend fun handle(purchases: List<Purchase>, fresh: Boolean = false) {
        val mine = purchases.filter { PREMIUM_PRODUCT_ID in it.products }
        val active = mine.firstOrNull { it.purchaseState == Purchase.PurchaseState.PURCHASED }
        if (active != null && !active.isAcknowledged) {
            // Obligatoire sous 3 jours, sinon Google rembourse automatiquement l'achat
            connected()?.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(active.purchaseToken).build())
        }
        val changed = token != active?.purchaseToken
        token = active?.purchaseToken
        _premium.value = active != null
        if (fresh) _message.value = when {
            active != null -> t("Bienvenue dans Lifoody Premium ! L'IA est prête.")
            mine.any { it.purchaseState == Purchase.PurchaseState.PENDING } -> t("Paiement en attente : Premium s'activera dès qu'il sera confirmé par Google Play.")
            else -> null
        }
        if (changed) onChange?.invoke()
    }

    /** Page Google Play de gestion de l'abonnement (changer de formule, résilier). */
    fun manageUrl(packageName: String): String =
        "https://play.google.com/store/account/subscriptions?sku=$PREMIUM_PRODUCT_ID&package=$packageName"

    // ---------------------------------------------------------------- Play Integrity

    @Volatile private var provider: StandardIntegrityManager.StandardIntegrityTokenProvider? = null

    private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitOrNull(): T? = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { cont.resume(it) }
        addOnFailureListener { cont.resume(null) }
    }

    /** Jeton Play Integrity lié à [requestHash] (empreinte de la demande) ; null si indisponible. */
    suspend fun integrityToken(requestHash: String): String? {
        val ctx = appContext ?: return null
        if (BuildConfig.CLOUD_PROJECT == 0L) return null
        val p = provider ?: IntegrityManagerFactory.createStandard(ctx)
            .prepareIntegrityToken(StandardIntegrityManager.PrepareIntegrityTokenRequest.builder().setCloudProjectNumber(BuildConfig.CLOUD_PROJECT).build())
            .awaitOrNull()?.also { provider = it } ?: return null
        val tok = p.request(StandardIntegrityManager.StandardIntegrityTokenRequest.builder().setRequestHash(requestHash).build()).awaitOrNull()
        if (tok == null) provider = null   // fournisseur périmé : il sera recréé à la prochaine demande
        return tok?.token()
    }
}
