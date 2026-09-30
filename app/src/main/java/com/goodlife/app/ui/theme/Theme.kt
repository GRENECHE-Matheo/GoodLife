package com.goodlife.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext

// Couleurs Google utilisées pour les macros
val GoogleBlue = Color(0xFF1A73E8)
val GoogleGreen = Color(0xFF1E8E3E)
val GoogleYellow = Color(0xFFF9AB00)
val GoogleRed = Color(0xFFD93025)

/** Vert « réussi », lisible sur fond clair comme sur fond sombre. */
val successColor: Color
    @Composable get() = if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color(0xFF81C995) else GoogleGreen

/** Rouge « raté », cohérent avec la couleur d'erreur du thème. */
val failColor: Color
    @Composable get() = MaterialTheme.colorScheme.error

/** Palette d'accent : couleurs claires et sombres. */
private data class Accent(
    val primary: Color, val onPrimary: Color, val container: Color, val onContainer: Color,
    val darkPrimary: Color, val darkOnPrimary: Color, val darkContainer: Color, val darkOnContainer: Color
)

private val accents = mapOf(
    "blue" to Accent(
        Color(0xFF1A73E8), Color.White, Color(0xFFD3E3FD), Color(0xFF041E49),
        Color(0xFFA8C7FA), Color(0xFF062E6F), Color(0xFF0842A0), Color(0xFFD3E3FD)
    ),
    "green" to Accent(
        Color(0xFF146C2E), Color.White, Color(0xFFC4EED0), Color(0xFF072711),
        Color(0xFF6DD58C), Color(0xFF0A3818), Color(0xFF0F5223), Color(0xFFC4EED0)
    ),
    "purple" to Accent(
        Color(0xFF6750A4), Color.White, Color(0xFFEADDFF), Color(0xFF21005D),
        Color(0xFFD0BCFF), Color(0xFF381E72), Color(0xFF4F378B), Color(0xFFEADDFF)
    ),
    "orange" to Accent(
        Color(0xFF9C4400), Color.White, Color(0xFFFFDBCA), Color(0xFF331200),
        Color(0xFFFFB68F), Color(0xFF532200), Color(0xFF773300), Color(0xFFFFDBCA)
    ),
    "pink" to Accent(
        Color(0xFFB0265E), Color.White, Color(0xFFFFD9E2), Color(0xFF3F001B),
        Color(0xFFFFB1C8), Color(0xFF650030), Color(0xFF8E0F47), Color(0xFFFFD9E2)
    )
)

/** Choix affichés dans les paramètres. */
val THEME_COLORS = listOf(
    "auto" to "Couleurs du téléphone",
    "blue" to "Bleu",
    "green" to "Vert",
    "purple" to "Violet",
    "orange" to "Orange",
    "pink" to "Rose"
)
val THEME_MODES = listOf("system" to "Système", "light" to "Clair", "dark" to "Sombre")

/** Material You (couleurs du fond d'écran) dispo à partir d'Android 12. */
val dynamicColorSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@Composable
fun isAppInDarkTheme(mode: String): Boolean = when (mode) {
    "light" -> false
    "dark" -> true
    else -> isSystemInDarkTheme()
}

private fun schemeFor(a: Accent, dark: Boolean): ColorScheme =
    if (dark) darkColorScheme(
        primary = a.darkPrimary, onPrimary = a.darkOnPrimary,
        primaryContainer = a.darkContainer, onPrimaryContainer = a.darkOnContainer,
        secondary = a.darkPrimary, secondaryContainer = a.darkContainer,
        onSecondaryContainer = a.darkOnContainer,
        tertiary = Color(0xFFFDD663),
        background = Color(0xFF131314), surface = Color(0xFF131314)
    ) else lightColorScheme(
        primary = a.primary, onPrimary = a.onPrimary,
        primaryContainer = a.container, onPrimaryContainer = a.onContainer,
        secondary = a.primary, secondaryContainer = a.container,
        onSecondaryContainer = a.onContainer,
        tertiary = GoogleYellow, error = GoogleRed,
        background = Color(0xFFF8FAFD), surface = Color(0xFFF8FAFD)
    )

@Composable
fun GoodLifeTheme(
    themeMode: String = "system",
    themeColor: String = "auto",
    content: @Composable () -> Unit
) {
    val dark = isAppInDarkTheme(themeMode)
    val context = LocalContext.current
    val scheme = if (themeColor == "auto" && dynamicColorSupported) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        schemeFor(accents[themeColor] ?: accents.getValue("blue"), dark)
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
