package com.goodlife.app.ui.screens

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.goodlife.app.data.Repo

/**
 * Saisie de la clé API, protégée :
 * - clavier en mode « mot de passe » : le clavier n'apprend pas la clé et ne la proposera jamais en suggestion ;
 * - captures et enregistrement d'écran bloqués tant que le champ est affiché ;
 * - après l'enregistrement, la clé est retirée du presse-papiers si elle y était (collée).
 */
val KEY_KEYBOARD = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false, imeAction = ImeAction.Done)

private fun Context.activity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) { if (c is Activity) return c; c = c.baseContext }
    return null
}

/** Bloque les captures d'écran tant que ce composant est affiché (rétablit ensuite le réglage de l'utilisateur). */
@Composable
fun NoScreenshotsWhileVisible() {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val window = context.activity()?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose {
            if (!Repo.settings.value.blockScreenshots) window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}

/** Enregistre la clé (coffre chiffré) et l'efface du presse-papiers si elle y est. */
fun saveApiKey(context: Context, key: String) {
    Repo.updateSettings { it.copy(apiKey = key) }
    runCatching {
        val cm = context.getSystemService(ClipboardManager::class.java) ?: return
        val clip = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()?.trim()
        if (clip == key && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) cm.clearPrimaryClip()
    }
}
