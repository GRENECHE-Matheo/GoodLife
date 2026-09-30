package com.goodlife.app

import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import android.graphics.Color
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
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
        locked.value = s.appLock
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

    override fun onStop() {
        super.onStop()
        backgroundAt = SystemClock.elapsedRealtime()
    }

    override fun onStart() {
        super.onStart()
        val away = SystemClock.elapsedRealtime() - backgroundAt
        if (Repo.settings.value.appLock && backgroundAt > 0 && away > AppLock.GRACE_MS) {
            locked.value = true
        }
    }

    /** Bloque captures d'écran, enregistrement d'écran et aperçu dans les apps récentes. */
    private fun applyScreenshotBlock(block: Boolean) {
        if (block) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}

private data class Tab(val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("Accueil", Icons.Filled.Home),
    Tab("Scanner", Icons.Filled.PhotoCamera),
    Tab("Planning", Icons.Filled.DateRange),
    Tab("Sommeil", Icons.Filled.NightsStay),
    Tab("Profil", Icons.Filled.Person)
)

@Composable
fun GoodLifeApp() {
    val profile by Repo.profile.collectAsState()
    val settings by Repo.settings.collectAsState()
    if (profile == null) {
        OnboardingScreen()
    } else if (!settings.aiConsentAsked) {
        // Choix explicite de l'IA, demandé une fois (y compris après mise à jour depuis la v0.2)
        AiChoiceScreen()
    } else {
        var tab by rememberSaveable { mutableIntStateOf(0) }
        Scaffold(
            bottomBar = {
                NavigationBar {
                    tabs.forEachIndexed { i, t ->
                        NavigationBarItem(
                            selected = tab == i,
                            onClick = { tab = i },
                            icon = { Icon(t.icon, null) },
                            label = { Text(t.label) }
                        )
                    }
                }
            }
        ) { padding ->
            Box(Modifier.padding(padding)) {
                when (tab) {
                    0 -> HomeScreen(onScan = { tab = 1 })
                    1 -> ScanScreen(onDone = { tab = 0 })
                    2 -> PlanningScreen()
                    3 -> SleepScreen()
                    else -> ProfileScreen()
                }
            }
        }
    }
}
