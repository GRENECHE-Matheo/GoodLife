package com.goodlife.app.track

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.goodlife.app.MainActivity
import com.goodlife.app.R
import com.goodlife.app.data.Outing
import com.goodlife.app.data.OutingType
import com.goodlife.app.data.Repo
import com.goodlife.app.game.Game
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

/** Résultat d'une sortie terminée (pour l'écran de fin). */
data class FinishedOuting(val outing: Outing, val xp: Int, val record: Boolean, val previousBest: Outing?)

/**
 * Suivi GPS d'une sortie (course, marche, vélo) : service de premier plan « localisation », démarré par
 * l'utilisateur depuis l'écran ; il continue écran éteint et affiche une notification. Rien n'est envoyé :
 * le tracé reste sur le téléphone.
 */
class TrackingService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val fused by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private var sensorManager: SensorManager? = null
    private var ticks = 0

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.locations.forEach { Tracker.onLocation(it) }
        }
    }

    private val baro = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            Tracker.baroAltitude = SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, e.values[0]).toDouble()
        }
        override fun onAccuracyChanged(s: Sensor?, a: Int) = Unit
    }

    private val ticker = object : Runnable {
        override fun run() {
            Tracker.tick()
            if (++ticks % 5 == 0) notifyProgress()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { stopTracking(); return START_NOT_STICKY }
            ACTION_START -> startTracking(
                runCatching { OutingType.valueOf(intent.getStringExtra(EXTRA_TYPE) ?: "") }.getOrDefault(OutingType.RUN),
                intent.getLongExtra(EXTRA_ROUTE, 0L)
            )
        }
        return START_NOT_STICKY
    }

    @SuppressLint("MissingPermission") // vérifiée par TrackingService.start
    private fun startTracking(type: OutingType, routeId: Long) {
        Repo.init(applicationContext)
        startForegroundCompat(notification("Sortie en cours", "Recherche du signal GPS…"))
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            stopSelf(); return
        }
        Tracker.start(type, routeId)
        fused.requestLocationUpdates(
            LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L)
                .setMinUpdateIntervalMillis(1000L)
                .setWaitForAccurateLocation(false)
                .build(),
            callback, Looper.getMainLooper()
        )
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        sensorManager?.getDefaultSensor(Sensor.TYPE_PRESSURE)?.let {
            sensorManager?.registerListener(baro, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    private fun stopTracking() {
        handler.removeCallbacks(ticker)
        fused.removeLocationUpdates(callback)
        sensorManager?.unregisterListener(baro)
        finished = save()
        Tracker.clear()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** Enregistre la sortie si elle a vraiment eu lieu (au moins 50 m). */
    private fun save(): FinishedOuting? {
        val live = Tracker.live.value ?: return null
        if (live.points.size < 2 || live.distanceM < 50) return null
        val id = System.currentTimeMillis()
        val routeId = if (live.routeId != 0L) live.routeId else id
        val valid = live.routeId == 0L || Tracker.followed(Repo.routeTrack(live.routeId), live.points)
        val previous = if (live.routeId != 0L) Repo.bestOn(live.routeId) else null
        val name = if (live.routeId != 0L) Repo.outings.value.firstOrNull { it.routeId == live.routeId }?.name.orEmpty() else ""
        val o = Outing(
            id = id, type = live.type, start = live.startedAt, end = id, movingMs = live.movingMs,
            distanceM = live.distanceM, elevGainM = live.elevGainM, maxSpeed = live.maxSpeed,
            routeId = routeId, valid = valid, name = name
        )
        Repo.addOuting(o, live.points)
        val xp = Repo.addSportXp(Game.outingXp(live.movingMs / 60_000))
        val record = valid && previous != null && o.movingMs < previous.movingMs
        return FinishedOuting(o, xp, record, previous)
    }

    private fun notifyProgress() {
        val l = Tracker.live.value ?: return
        val text = "${formatClock(l.movingMs)} · ${"%.2f".format(l.distanceM / 1000)} km" +
            if (l.paused) " · en pause" else if (l.autoPaused) " · à l'arrêt" else ""
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, notification("${l.type.emoji} ${l.type.label} en cours", text))
    }

    private fun notification(title: String, text: String) = NotificationCompat.Builder(this, CHANNEL)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle(title)
        .setContentText(text)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setContentIntent(
            PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        )
        .build()

    private fun startForegroundCompat(n: android.app.Notification) {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Sorties GPS", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Temps et distance pendant une sortie"
            }
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        else startForeground(NOTIF_ID, n)
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        runCatching { fused.removeLocationUpdates(callback) }
        sensorManager?.unregisterListener(baro)
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "outings"
        private const val NOTIF_ID = 2
        private const val ACTION_START = "start"
        private const val ACTION_STOP = "stop"
        private const val EXTRA_TYPE = "type"
        private const val EXTRA_ROUTE = "route"

        /** Dernière sortie terminée, pour afficher le récapitulatif. */
        @Volatile var finished: FinishedOuting? = null

        fun hasPermission(context: Context) =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

        fun start(context: Context, type: OutingType, routeId: Long = 0L) {
            finished = null
            ContextCompat.startForegroundService(
                context,
                Intent(context, TrackingService::class.java).setAction(ACTION_START)
                    .putExtra(EXTRA_TYPE, type.name).putExtra(EXTRA_ROUTE, routeId)
            )
        }

        fun stop(context: Context) {
            context.startService(Intent(context, TrackingService::class.java).setAction(ACTION_STOP))
        }
    }
}

/** 1 h 05 min 09 s → « 1:05:09 », 5 min 09 s → « 5:09 ». */
fun formatClock(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, (s % 3600) / 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

/** Allure en min/km (course, marche). */
fun formatPace(speedMs: Double): String {
    if (speedMs < 0.3) return "--:--"
    val secPerKm = (1000 / speedMs).toLong()
    return if (secPerKm >= 3600) "--:--" else "%d:%02d".format(secPerKm / 60, secPerKm % 60)
}

fun formatKmh(speedMs: Double): String = "%.1f".format(speedMs * 3.6)
