package com.goodlife.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.goodlife.app.BuildConfig
import com.goodlife.app.ai.Relay
import com.goodlife.app.i18n.t
import com.goodlife.app.store.Offer
import com.goodlife.app.store.Store
import com.goodlife.app.ui.ScreenColumn
import com.goodlife.app.ui.SubScreenHeader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/** Ouverture de l'écran Premium depuis n'importe où (essais épuisés, paramètres, scanner…). */
object Paywall {
    val open = MutableStateFlow(false)
    fun show() { if (Store.available) open.value = true }
}

/** Abonnés : limites du jour indiquées par le relais (15 photos, 40 messages par défaut). */
private const val PHOTOS_PER_DAY = 15
private const val MESSAGES_PER_DAY = 40

@Composable
fun PremiumScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val uri = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val offers by Store.offers.collectAsState()
    val premium by Store.premium.collectAsState()
    val status by Relay.status.collectAsState()
    val message by Store.message.collectAsState()
    var chosen by remember(offers) { mutableStateOf(offers.firstOrNull { it.yearly } ?: offers.firstOrNull()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }   // Google Play a répondu (ou pas) : on arrête le chargement

    BackHandler(onBack = onClose)
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        // Ouvert depuis le coach (essais épuisés) : on referme le clavier resté ouvert
        focus.clearFocus(force = true); keyboard?.hide()
        Store.refresh(); loaded = true
        Relay.refreshStatus()
    }
    LaunchedEffect(premium) { if (premium) Relay.refreshStatus() }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        ScreenColumn {
            SubScreenHeader(t("Lifoody Premium"), onBack = onClose)

            // En-tête : la promesse en une phrase
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                val pulse by animateFloatAsState(if (premium) 1.08f else 1f, label = "pulse")
                Box(
                    Modifier.size(84.dp).scale(pulse).background(
                        Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary)), CircleShape
                    ),
                    contentAlignment = Alignment.Center
                ) { Icon(if (premium) Icons.Filled.CheckCircle else Icons.Filled.AutoAwesome, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(42.dp)) }
                Spacer(Modifier.height(12.dp))
                Text(
                    if (premium) t("Premium est actif. Merci !") else t("Toute l'IA de Lifoody, sans clé à créer."),
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold
                )
                if (!premium) status?.let { s ->
                    if (s.trialsLeft > 0) Text(tp3(s.trialsLeft), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // Ce qu'on gagne
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Benefit(Icons.Filled.PhotoCamera, t("Une photo, tes calories"), t("Jusqu'à %1\$s analyses de repas par jour, corrigeables avant de valider.", PHOTOS_PER_DAY))
                Benefit(Icons.Filled.RestaurantMenu, t("Le coach chef"), t("Jusqu'à %1\$s messages par jour : idées de repas, recettes, conseils.", MESSAGES_PER_DAY))
                Benefit(Icons.Filled.CalendarMonth, t("Planning et courses"), t("Ta semaine de repas selon ton budget, et la liste de courses qui va avec."))
                Benefit(Icons.Filled.WaterDrop, t("Objectifs du jour"), t("Eau et pas ajustés chaque jour à ton activité."))
                Benefit(Icons.Filled.Lock, t("Ta vie privée"), t("Tes données restent chiffrées sur ton téléphone. Le service IA n'enregistre ni tes photos ni tes messages."))
            }

            if (!premium) {
                // Formules (prix de Google Play, dans ta devise)
                if (offers.isEmpty() && !loaded) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(t("Chargement des formules depuis Google Play…"), style = MaterialTheme.typography.bodyMedium)
                    }
                } else if (offers.isEmpty()) {
                    Text(
                        t("Les formules ne s'affichent pas : vérifie ta connexion et que tu es connecté au Play Store, puis reviens ici."),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    val monthly = offers.firstOrNull { !it.yearly }
                    offers.forEach { o -> Plan(o, selected = chosen == o, monthly = monthly) { chosen = o } }
                    chosen?.let { o ->
                        Button(
                            enabled = !busy,
                            onClick = {
                                error = null
                                val activity = context.findActivity()
                                error = if (activity == null) t("Achat impossible depuis cet écran.") else Store.buy(activity, o)
                            },
                            modifier = Modifier.fillMaxWidth().height(54.dp)
                        ) {
                            Text(
                                if (o.trialDays > 0) t("Essayer gratuitement %1\$s jours", o.trialDays) else t("S'abonner"),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Text(
                            (if (o.trialDays > 0) t("%1\$s jours gratuits, puis %2\$s par %3\$s. ", o.trialDays, o.price, if (o.yearly) t("an") else t("mois")) else "") +
                                t("Abonnement à renouvellement automatique, payé via Google Play. Annulable à tout moment dans Google Play, au plus tard 24 h avant le renouvellement : Premium reste actif jusqu'à la fin de la période payée. Si tu annules pendant l'essai gratuit, rien n'est facturé."),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            AnimatedVisibility(visible = error != null || message != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
                Text(error ?: message.orEmpty(), color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(enabled = !busy, onClick = {
                    busy = true; error = null
                    scope.launch {
                        val ok = Store.refresh()
                        Relay.refreshStatus()
                        busy = false
                        error = if (!ok) t("Google Play n'est pas disponible. Vérifie ta connexion.")
                                else if (!Store.premium.value) t("Aucun abonnement actif trouvé sur ce compte Google.") else null
                    }
                }) { Text(t("Restaurer mes achats")) }
                if (premium) TextButton(onClick = { uri.openUri(Store.manageUrl(context.packageName)) }) { Text(t("Gérer mon abonnement")) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { uri.openUri("${BuildConfig.SITE_URL}/conditions") }) { Text(t("Conditions d'abonnement")) }
                TextButton(onClick = { uri.openUri("${BuildConfig.SITE_URL}/confidentialite") }) { Text(t("Confidentialité")) }
            }
            Text(
                t("Sans Premium, toutes les fonctions sans IA restent gratuites : suivi des repas, code-barres, planning, sport, GPS, jeu et amis."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun tp3(n: Int) = com.goodlife.app.i18n.tp(n, "Il te reste %1\$s essai IA gratuit.", "Il te reste %1\$s essais IA gratuits.")

@Composable
private fun Benefit(icon: ImageVector, title: String, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.size(40.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Plan(o: Offer, selected: Boolean, monthly: Offer?, onSelect: () -> Unit) {
    val border by animateColorAsState(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, label = "planBorder")
    OutlinedCard(
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(if (selected) 2.dp else 1.dp, border),
        modifier = Modifier.fillMaxWidth().selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (o.yearly) t("Annuel") else t("Mensuel"), style = MaterialTheme.typography.titleMedium)
                    // Économie de la formule annuelle par rapport à 12 mois
                    if (o.yearly && monthly != null && monthly.currency == o.currency && monthly.priceMicros > 0) {
                        val saving = (100 - o.priceMicros * 100 / (monthly.priceMicros * 12)).toInt()
                        if (saving > 0) Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(50)) {
                            Text(t("−%1\$s %%", saving), Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                if (o.trialDays > 0) Text(t("%1\$s jours gratuits", o.trialDays), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(o.price, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(if (o.yearly) t("par an") else t("par mois"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

internal fun android.content.Context.findActivity(): android.app.Activity? {
    var c: android.content.Context? = this
    while (c is android.content.ContextWrapper) { if (c is android.app.Activity) return c; c = c.baseContext }
    return null
}
