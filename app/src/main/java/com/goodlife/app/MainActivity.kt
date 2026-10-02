package com.goodlife.app

import com.goodlife.app.i18n.t

import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import android.graphics.Color
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.ui.platform.LocalConfiguration
import com.goodlife.app.ui.Motion
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import com.goodlife.app.data.Backup
import com.goodlife.app.data.Repo
import com.goodlife.app.net.UpdateInstaller
import com.goodlife.app.security.AppLock
import com.goodlife.app.ui.screens.AiChoiceScreen
import com.goodlife.app.ui.screens.HomeScreen
import com.goodlife.app.ui.screens.PlanningScreen
import com.goodlife.app.ui.screens.LockScreen
import com.goodlife.app.ui.screens.OnboardingScreen
import com.goodlife.app.ui.screens.ProfileScreen
import com.goodlife.app.ui.screens.PrivacyScreen
import androidx.compose.foundation.layout.safeDrawingPadding
import com.goodlife.app.ui.screens.ScanScreen
import com.goodlife.app.ui.screens.FormeScreen
import androidx.compose.material.icons.filled.FitnessCenter
import com.goodlife.app.ui.theme.GoodLifeTheme
import com.goodlife.app.ui.theme.isAppInDarkTheme

class MainActivity : FragmentActivity() {

    private val locked = mutableStateOf(false)
    private var backgroundAt = 0L
    /** Mini-fenêtre (Picture-in-Picture) pendant une sortie : seulement la carte et les chiffres du guidage. */
    private val pip = mutableStateOf(false)
    private var pipArmed = false

    private fun pipParams(): android.app.PictureInPictureParams? {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) return null
        val b = android.app.PictureInPictureParams.Builder().setAspectRatio(android.util.Rational(3, 4))
        // Android 12+ : la mini-fenêtre s'ouvre toute seule en quittant l'app, seulement pendant une sortie
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) b.setAutoEnterEnabled(com.goodlife.app.track.Tracker.live.value != null)
        return b.build()
    }

    /** Quitter l'app (bouton Accueil) pendant une sortie : la carte passe en mini-fenêtre, comme un GPS. */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (android.os.Build.VERSION.SDK_INT in android.os.Build.VERSION_CODES.O until android.os.Build.VERSION_CODES.S &&
            com.goodlife.app.track.Tracker.live.value != null && !locked.value) {
            runCatching { pipParams()?.let { enterPictureInPictureMode(it) } }
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        pip.value = isInPictureInPictureMode
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Repo.init(this)
        Repo.reloadSecretsIfNeeded()
        // Nouveau lancement : le bandeau de mise à jour masqué la dernière fois réapparaît
        if (savedInstanceState == null && Repo.settings.value.dismissedTag.isNotEmpty()) Repo.updateSettings { it.copy(dismissedTag = "") }
        com.goodlife.app.net.appContext = applicationContext
        UpdateInstaller.cleanup(this, onlyInstalled = true)
        com.goodlife.app.steps.Steps.schedule(this)
        com.goodlife.app.social.StreetPass.sync(this)
        com.goodlife.app.coach.CoachNotifier.schedule(this)
        // Objectif de pas conseillé par l'IA : recalculé une fois par jour, à la première ouverture
        val ctx = applicationContext
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            com.goodlife.app.steps.StepGoalAi.refreshIfNeeded(ctx)
            com.goodlife.app.ai.WaterGoalAi.refreshIfNeeded(ctx)   // objectif d'eau du jour (IA)
        }
        val s = Repo.settings.value
        applyScreenshotBlock(s.blockScreenshots)
        // Rotation / changement de thème : pas de re-verrouillage ; retour après plus d'1 min : verrouillage.
        locked.value = s.appLock && (
            savedInstanceState == null ||
                savedInstanceState.getBoolean(KEY_LOCKED, true) ||
                SystemClock.elapsedRealtime() - savedInstanceState.getLong(KEY_SAVED_AT, 0L) > AppLock.GRACE_MS
            )
        enableEdgeToEdge()
        // Mini-fenêtre automatique : activée seulement pendant une sortie GPS
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) lifecycleScope.launch {
            com.goodlife.app.track.Tracker.live.collect { l ->
                if ((l != null) != pipArmed) { pipArmed = l != null; runCatching { pipParams()?.let { setPictureInPictureParams(it) } } }
            }
        }
        handleSharedCard(intent)
        handleOpenRequest(intent)
        // Ouverte par Health Connect pour expliquer l'usage des données : on montre la politique de confidentialité
        val rationale = intent?.action in setOf(
            "androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE", "android.intent.action.VIEW_PERMISSION_USAGE"
        )
        setContent {
            val settings by Repo.settings.collectAsState()
            val dark = isAppInDarkTheme(settings.themeMode)
            // Icônes de la barre d'état claires/sombres selon le thème choisi dans l'app
            LaunchedEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                            else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            LaunchedEffect(settings.blockScreenshots) { applyScreenshotBlock(settings.blockScreenshots) }
            // L'état des écrans (onglet ouvert…) est gardé pendant la mini-fenêtre, pour revenir exactement au même endroit
            val saved = androidx.compose.runtime.saveable.rememberSaveableStateHolder()
            GoodLifeTheme(themeMode = settings.themeMode, themeColor = settings.themeColor) {
                if (rationale) {
                    androidx.compose.foundation.layout.Box(Modifier.safeDrawingPadding()) {
                        PrivacyScreen(onBack = { finish() })
                    }
                } else if (locked.value && settings.appLock) {
                    LockScreen(onUnlocked = { locked.value = false })
                } else if (pip.value) {
                    com.goodlife.app.ui.screens.PipNavigation()
                } else {
                    saved.SaveableStateProvider("app") { GoodLifeApp() }
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleSharedCard(intent)
        handleOpenRequest(intent)
    }

    /** Ouverte depuis une notification d'amis : on affiche l'écran Amis. */
    private fun handleOpenRequest(intent: android.content.Intent?) {
        intent?.getStringExtra(com.goodlife.app.social.AppNav.EXTRA)?.let { com.goodlife.app.social.AppNav.request.value = it }
    }

    /** Carte d'ami reçue par message (texte partagé ou lien d'invitation) : vérifiée (signature) puis ajoutée. */
    private fun handleSharedCard(intent: android.content.Intent?) {
        val text = when (intent?.action) {
            android.content.Intent.ACTION_SEND -> intent.getStringExtra(android.content.Intent.EXTRA_TEXT)
            android.content.Intent.ACTION_VIEW -> intent.dataString?.let { android.net.Uri.decode(it) }
            else -> null
        } ?: return
        if (!text.contains("GL1:")) return
        if (Repo.profile.value == null) return
        val social = com.goodlife.app.social.Social
        val card = social.cardInMessage(text)
        when {
            card == null -> android.widget.Toast.makeText(this, t("Ce message ne contient pas de carte GoodLife valide."), android.widget.Toast.LENGTH_LONG).show()
            // Ami déjà connu : simple mise à jour de sa carte
            social.isFriend(card) -> {
                social.accept(card)
                android.widget.Toast.makeText(this, t("Carte de %1\$s mise à jour.", card.pseudo), android.widget.Toast.LENGTH_LONG).show()
            }
            // Nouvelle personne : on demande d'abord (fenêtre dans l'app, après le déverrouillage)
            else -> social.pendingInvite.value = card
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_LOCKED, locked.value)
        outState.putLong(KEY_SAVED_AT, SystemClock.elapsedRealtime())
    }

    override fun onStop() {
        super.onStop()
        backgroundAt = SystemClock.elapsedRealtime()
        // Sauvegarde chiffrée automatique (seulement si activée et si des données ont changé)
        val app = applicationContext
        Thread { Backup.autoBackup(app) }.start()
        com.goodlife.app.widget.ChefWidgets.updateAll(app)
    }

    override fun onStart() {
        Repo.reloadSecretsIfNeeded()
        // Mise à jour disponible ? Vérifié à chaque ouverture de l'app (une petite requête), affiché sur l'accueil
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch { com.goodlife.app.net.Updater.check() }
        super.onStart()
        val away = SystemClock.elapsedRealtime() - backgroundAt
        if (Repo.settings.value.appLock && backgroundAt > 0 && away > AppLock.GRACE_MS) {
            locked.value = true
        }
    }

    /**
     * Bloque captures d'écran, enregistrement d'écran et aperçu dans les apps récentes.
     * Jamais en version debug : l'écran de l'émulateur doit rester visible pendant le développement.
     */
    private fun applyScreenshotBlock(block: Boolean) {
        if (block && !BuildConfig.DEBUG) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}

private const val KEY_LOCKED = "goodlife_locked"
private const val KEY_SAVED_AT = "goodlife_saved_at"

private data class Tab(val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(t("Accueil"), Icons.Filled.Home),
    Tab(t("Scanner"), Icons.Filled.PhotoCamera),
    Tab(t("Planning"), Icons.Filled.DateRange),
    Tab(t("Forme"), Icons.Filled.FitnessCenter),
    Tab(t("Profil"), Icons.Filled.Person)
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GoodLifeApp() {
    val profile by Repo.profile.collectAsState()
    val settings by Repo.settings.collectAsState()
    val stage = when {
        profile == null -> 0
        !settings.aiConsentAsked -> 1
        // Options désactivées par défaut : proposées une fois, pour que personne ne passe à côté
        !settings.featuresAsked -> 2
        else -> 3
    }
    // Passage animé : accueil → choix de l'IA → application
    AnimatedContent(
        targetState = stage,
        transitionSpec = { Motion.sharedAxisX(forward = targetState >= initialState) },
        label = "stage"
    ) { st ->
        when (st) {
            0 -> OnboardingScreen()
            1 -> AiChoiceScreen()
            2 -> com.goodlife.app.ui.screens.OptionsOnboardingScreen()
            else -> MainTabs()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MainTabs() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    // Sortie GPS en cours (app rouverte depuis la notification ou la mini-fenêtre) : directement sur la carte
    LaunchedEffect(Unit) { if (com.goodlife.app.track.Tracker.live.value != null) tab = 3 }
    // Demande venue d'une notification (écran Amis, dans l'onglet Profil)
    val navRequest by com.goodlife.app.social.AppNav.request.collectAsState()
    LaunchedEffect(navRequest) {
        when (navRequest) {
            "friends" -> tab = 4
            // Bouton « Photo » du widget : onglet Scanner
            "scan" -> { tab = 1; com.goodlife.app.social.AppNav.request.value = null }
        }
    }
    // Retour du téléphone : depuis Scanner, Planning, Forme ou Profil, on revient à l'accueil (seul l'accueil ferme l'app).
    // Les écrans ouverts par-dessus (paramètres, amis, quiz…) gèrent leur propre retour en premier.
    androidx.activity.compose.BackHandler(enabled = tab != 0) { tab = 0 }
    InviteDialog()
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    val keyboardOpen = WindowInsets.isImeVisible

    val content: @Composable (Modifier) -> Unit = { modifier ->
        // « Fade through » Material entre les onglets
        AnimatedContent(
            targetState = tab,
            transitionSpec = { Motion.fadeThrough() },
            modifier = modifier,
            label = "tabs"
        ) { t ->
            when (t) {
                0 -> HomeScreen(onScan = { tab = 1 }, onOpenProfile = { tab = 4 })
                1 -> ScanScreen(onDone = { tab = 0 })
                2 -> PlanningScreen()
                3 -> FormeScreen()
                else -> ProfileScreen()
            }
        }
    }

    if (wide) {
        // Tablettes / paysage : rail de navigation à gauche, toujours visible
        Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            NavigationRail(Modifier.fillMaxHeight().windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Vertical + WindowInsetsSides.Start))) {
                Spacer(Modifier.weight(1f))
                tabs.forEachIndexed { i, t ->
                    NavigationRailItem(
                        selected = tab == i,
                        onClick = { tab = i },
                        icon = { Icon(t.icon, null) },
                        label = { com.goodlife.app.ui.FitText(t.label) }
                    )
                }
                Spacer(Modifier.weight(1f))
            }
            content(Modifier.weight(1f).fillMaxHeight().windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Vertical + WindowInsetsSides.End)))
        }
    } else {
        // Téléphones : barre du bas fixe (masquée seulement quand le clavier est ouvert, comme les apps Google)
        Scaffold(
            bottomBar = {
                AnimatedVisibility(
                    visible = !keyboardOpen,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut()
                ) {
                    NavigationBar {
                        tabs.forEachIndexed { i, t ->
                            NavigationBarItem(
                                selected = tab == i,
                                onClick = { tab = i },
                                icon = { Icon(t.icon, null) },
                                label = { com.goodlife.app.ui.FitText(t.label) }
                            )
                        }
                    }
                }
            }
        ) { padding ->
            content(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding))
        }
    }
}

/** Invitation reçue par lien ou message : on demande avant d'ajouter la personne. */
@Composable
private fun InviteDialog() {
    val social = com.goodlife.app.social.Social
    val card by social.pendingInvite.collectAsState()
    val c = card ?: return
    val context = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { social.pendingInvite.value = null },
        title = { Text(t("Ajouter %1\$s en ami ?", c.pseudo)) },
        text = { Text(t("Cette invitation vient d'un lien ou d'un message. Accepte seulement si tu connais cette personne.")) },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = {
                social.pendingInvite.value = null
                val e = social.accept(c)
                android.widget.Toast.makeText(context, t("%1\$s est dans tes amis !", e.pseudo), android.widget.Toast.LENGTH_LONG).show()
            }) { Text(t("Ajouter")) }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = { social.pendingInvite.value = null }) { Text(t("Ignorer")) }
        }
    )
}
