package com.goodlife.app.net

import android.content.Context

/**
 * Version Google Play : pas de mise à jour intégrée (interdit par le règlement Play).
 * Les mises à jour passent par le Play Store ; ces fonctions ne font donc rien.
 */
data class AvailableUpdate(val tag: String, val pageUrl: String, val apkUrl: String, val digest: String)

object Updater {
    @Suppress("UNUSED_PARAMETER")
    suspend fun check(force: Boolean = false): String? = null
    fun availableUpdate(): AvailableUpdate? = null
}

object UpdateInstaller {
    @Suppress("UNUSED_PARAMETER")
    fun cleanup(context: Context, onlyInstalled: Boolean = false) = Unit
}
