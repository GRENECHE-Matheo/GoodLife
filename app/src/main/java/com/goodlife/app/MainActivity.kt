package com.goodlife.app

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
import com.goodlife.app.ui.screens.ScanScreen
import com.goodlife.app.ui.screens.SleepScreen
import com.goodlife.app.ui.theme.GoodLifeTheme
import com.goodlife.app.ui.theme.isAppInDarkTheme

class MainActivity : FragmentActivity() {

    private val locked = mutableStateOf(false)
    private var backgroundAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Repo.init(this)
        UpdateInstaller.cleanup(this, onlyInstalled = true)
        val s = Repo.settings.value
        applyScreenshotBlock(s.blockScreenshots)
        // Rotation / changement de thème : pas de re-verrouillage ; retour après plus d'1 min : verrouillage.
        locked.value = s.appLock && (
            savedInstanceState == null ||
                savedInstanceState.getBoolean(KEY_LOCKED, true) ||
                SystemClock.elapsedRealtime() - savedInstanceState.getLong(KEY_SAVED_AT, 0L) > AppLock.GRACE_MS
            )
        enableEdgeToEdge()
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
            GoodLifeTheme(themeMode = settings.themeMode, themeColor = settings.themeColor) {
                if (locked.value && settings.appLock) {
                    LockScreen(onUnlocked = { locked.value = false })
                } else {
                    GoodLifeApp()
                }
            }
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
    }

    override fun onStart() {
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
    Tab("Accueil", Icons.Filled.Home),
    Tab("Scanner", Icons.Filled.PhotoCamera),
    Tab("Planning", Icons.Filled.DateRange),
    Tab("Sommeil", Icons.Filled.NightsStay),
    Tab("Profil", Icons.Filled.Person)
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GoodLifeApp() {
    val profile by Repo.profile.collectAsState()
    val settings by Repo.settings.collectAsState()
    val stage = when {
        profile == null -> 0
        !settings.aiConsentAsked -> 1
        else -> 2
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
            else -> MainTabs()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MainTabs() {
    var tab by rememberSaveable { mutableIntStateOf(0) }
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
                3 -> SleepScreen()
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
                        label = { Text(t.label) }
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
                                label = { Text(t.label, maxLines = 1) }
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
