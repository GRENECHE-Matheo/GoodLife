package com.goodlife.app.ui.screens

import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.goodlife.app.data.Repo

/**
 * Saisie de la clé API, protégée :
 * - clavier en mode « mot de passe » : le clavier n'apprend pas la clé et ne la proposera jamais en suggestion ;
 * - après l'enregistrement, la clé est retirée du presse-papiers si elle y était (collée).
 */
val KEY_KEYBOARD = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false, imeAction = ImeAction.Done)

/** Enregistre la clé (coffre chiffré) et l'efface du presse-papiers si elle y est. */
fun saveApiKey(context: Context, key: String) {
    Repo.updateSettings { it.copy(apiKey = key) }
    runCatching {
        val cm = context.getSystemService(ClipboardManager::class.java) ?: return
        val clip = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()?.trim()
        if (clip == key && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) cm.clearPrimaryClip()
    }
}
