package com.goodlife.app.sleep

import com.goodlife.app.i18n.t

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.goodlife.app.data.Repo
import com.goodlife.app.data.SleepSession
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.SleepClassifyEvent
import com.google.android.gms.location.SleepSegmentEvent
import com.google.android.gms.location.SleepSegmentRequest

/**
 * Détection automatique du sommeil via la Sleep API de Google Play Services
 * (la même technologie que Google Fit / de nombreuses apps de santé) :
 * le téléphone combine accéléromètre, luminosité ambiante et usage de l'écran
 * pour estimer quand tu dors, sans que l'app tourne en permanence.
 */
object SleepTracker {

    fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) ==
            PackageManager.PERMISSION_GRANTED

    private fun pendingIntent(context: Context): PendingIntent {
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
        return PendingIntent.getBroadcast(
            context, 42, Intent(context, SleepReceiver::class.java), flags
        )
    }

    @SuppressLint("MissingPermission")
    fun subscribe(context: Context, onResult: (Boolean, String?) -> Unit = { _, _ -> }) {
        if (!hasPermission(context)) {
            onResult(false, t("Autorisation « Activité physique » refusée."))
            return
        }
        runCatching {
            ActivityRecognition.getClient(context)
                .requestSleepSegmentUpdates(
                    pendingIntent(context),
                    SleepSegmentRequest.getDefaultSleepSegmentRequest()
                )
                .addOnSuccessListener {
                    Repo.updateSettings { it.copy(sleepAuto = true) }
                    onResult(true, null)
                }
                .addOnFailureListener { e ->
                    onResult(false, e.message ?: t("Google Play Services indisponible."))
                }
        }.onFailure { onResult(false, it.message) }
    }

    fun unsubscribe(context: Context) {
        runCatching { ActivityRecognition.getClient(context).removeSleepSegmentUpdates(pendingIntent(context)) }
        Repo.updateSettings { it.copy(sleepAuto = false) }
    }
}

class SleepReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Repo.init(context)
        if (SleepSegmentEvent.hasEvents(intent)) {
            SleepSegmentEvent.extractEvents(intent)
                .filter { it.status == SleepSegmentEvent.STATUS_SUCCESSFUL }
                .forEach { e ->
                    Repo.addSleep(
                        SleepSession(start = e.startTimeMillis, end = e.endTimeMillis, source = "auto")
                    )
                }
        }
        if (SleepClassifyEvent.hasEvents(intent)) {
            SleepClassifyEvent.extractEvents(intent).maxByOrNull { it.timestampMillis }?.let { e ->
                Repo.updateSettings {
                    it.copy(lastSleepConfidence = e.confidence, lastSleepConfidenceAt = e.timestampMillis)
                }
            }
        }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        Repo.init(context)
        if (Repo.settings.value.sleepAuto) SleepTracker.subscribe(context)
        com.goodlife.app.social.StreetPass.sync(context)
        com.goodlife.app.coach.CoachNotifier.schedule(context)
    }
}
