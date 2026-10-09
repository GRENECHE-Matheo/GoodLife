package com.goodlife.app.ui.screens

import com.goodlife.app.i18n.t
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.goodlife.app.data.Repo
import com.goodlife.app.security.AppLock
import com.goodlife.app.security.findFragmentActivity
import com.goodlife.app.sleep.SleepTracker
import com.goodlife.app.ui.ChefMascot
import com.goodlife.app.ui.ChefMood

/**
 * À l'inscription (et une fois pour les comptes déjà créés) : toutes les options désactivées par défaut sont
 * proposées, pour que personne ne passe à côté. Rien n'est activé sans un appui, et tout se change ensuite dans
 * Profil › Paramètres.
 */
@Composable
fun OptionsOnboardingScreen() {
    val context = LocalContext.current
    val settings by Repo.settings.collectAsState()
    var message by remember { mutableStateOf<String?>(null) }
    val enableSteps = rememberEnableSteps { message = it }
    val askNotif = rememberNotifPermission { granted ->
        if (granted) saveNotifPrefs(context, NotifPrefs.ALL) else message = t("Sans l'autorisation des notifications, le chef ne peut pas t'écrire.")
    }
    val askNotifWater = rememberNotifPermission { granted ->
        if (granted) { Repo.updateSettings { it.copy(notifWater = true) }; com.goodlife.app.coach.CoachNotifier.schedule(context) }
        else message = t("Sans l'autorisation des notifications, le chef ne peut pas t'écrire.")
    }
    val askActivity = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) SleepTracker.subscribe(context) { ok, err -> message = if (ok) null else err }
        else message = t("Sans l'autorisation « Activité physique », la détection automatique ne peut pas fonctionner.")
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ChefMascot(size = 96.dp, mood = ChefMood.BRAVO)
            Text(t("Tes options"), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                t("Tout est désactivé par défaut. Active ce qui te plaît maintenant : tu pourras tout changer plus tard dans Moi › Paramètres."),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.widthIn(max = 520.dp)
            )
            Column(Modifier.widthIn(max = 520.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Pas et notifications : déjà demandés à l'inscription ; montrés seulement aux comptes plus anciens
                val firstTime = !settings.notifAsked
                if (firstTime) OptionRow("👣", t("Compter mes pas"), t("Avec le capteur du téléphone (ou Health Connect). Envoyés seulement à l'IA si tu l'actives, et le total de la semaine à tes amis si ton profil est public."),
                    settings.stepsEnabled) { on -> if (on) enableSteps() else Repo.updateSettings { it.copy(stepsEnabled = false) } }
                OptionRow("😴", t("Détecter mon sommeil"), t("Estimé par ton téléphone pendant la nuit (Google Play Services), sans rien envoyer."),
                    settings.sleepAuto) { on ->
                    if (!on) { SleepTracker.unsubscribe(context); Repo.updateSettings { it.copy(sleepAuto = false) } }
                    else if (SleepTracker.hasPermission(context)) SleepTracker.subscribe(context) { ok, err -> message = if (ok) null else err }
                    else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) askActivity.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                }
                if (firstTime) OptionRow("🔔", t("Les petits mots du chef"), t("Un bilan le matin, un mot à midi, un rappel le soir si ta série est en danger, un bilan le dimanche."),
                    settings.anyNotif) { on -> if (on) askNotif() else saveNotifPrefs(context, NotifPrefs(false, false, false, false)) }
                OptionRow("💧", t("Rappel pour boire de l'eau"), t("Un petit rappel l'après-midi si tu n'as pas assez bu."),
                    settings.notifWater) { on ->
                    if (on) askNotifWater() else { Repo.updateSettings { it.copy(notifWater = false) }; com.goodlife.app.coach.CoachNotifier.schedule(context) }
                }
                OptionRow("🔒", t("Verrouiller l'app"), t("Empreinte, visage ou code du téléphone à l'ouverture."), settings.appLock) { wanted ->
                    val activity = context.findFragmentActivity()
                    when {
                        activity == null -> message = t("Verrouillage indisponible.")
                        wanted && !AppLock.isAvailable(context) -> message = t("Ajoute d'abord une empreinte ou un code de verrouillage dans les réglages du téléphone.")
                        else -> AppLock.authenticate(
                            activity, if (wanted) t("Activer le verrouillage") else t("Désactiver le verrouillage"),
                            onSuccess = { Repo.updateSettings { it.copy(appLock = wanted) } }, onError = { message = it }
                        )
                    }
                }
                if (settings.aiEnabled) OptionRow("🧊", t("Retirer du frigo après une photo de repas"),
                    t("Le chef propose de retirer de « Mon frigo » ce que tu as cuisiné ; tu valides à chaque fois."),
                    settings.fridgeAutoRemove) { on -> Repo.updateSettings { it.copy(fridgeAutoRemove = on) } }
            }
            Text(
                t("Aussi à découvrir : les amis et les croisements (Moi › Amis), la sauvegarde chiffrée (Paramètres), les cartes hors ligne (Forme › Carte)."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.widthIn(max = 520.dp)
            )
            if (message != null) Text(message!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(4.dp))
            Button(onClick = { Repo.updateSettings { it.copy(featuresAsked = true, notifAsked = true) } }, modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth()) {
                Text(t("C'est parti !"))
            }
        }
    }
}

@Composable
private fun OptionRow(emoji: String, title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(8.dp))
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}
