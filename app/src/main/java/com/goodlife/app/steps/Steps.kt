package com.goodlife.app.steps

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.goodlife.app.data.Repo
import com.goodlife.app.data.StepDay
import com.goodlife.app.data.localDay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.math.roundToInt

/**
 * Nombre de pas, calculé 100 % sur le téléphone (rien n'est envoyé) :
 * - capteur de pas du téléphone (par défaut) : le compteur matériel est relevé à l'ouverture de l'app
 *   et toutes les 15 min en arrière-plan (lecture d'un nombre, très économe) ;
 * - ou Health Connect (Android) : reprend les pas de Samsung Health, Google Fit, d'une montre…
 */
object Steps {
    const val DEFAULT_GOAL = 6000
    private const val WORK = "goodlife_steps"

    val HC_PERMISSIONS = setOf(HealthPermission.getReadPermission(StepsRecord::class))

    fun sensorAvailable(context: Context): Boolean =
        (context.getSystemService(Context.SENSOR_SERVICE) as SensorManager).getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null

    fun hasActivityPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED

    fun healthConnectAvailable(context: Context): Boolean =
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    suspend fun healthConnectGranted(context: Context): Boolean = runCatching {
        HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions().containsAll(HC_PERMISSIONS)
    }.getOrDefault(false)

    /**
     * Objectif du jour. Auto : moyenne des 7 derniers jours + 10 %, arrondie à 500, entre 5 000 et 12 000
     * (atteignable, mais on progresse un peu chaque semaine).
     */
    fun goal(): Int {
        val s = Repo.settings.value
        return when (s.stepsGoalMode) {
            "manual" -> s.stepsGoalManual.coerceIn(1000, 40_000)
            "ia" -> s.stepsGoalIa.takeIf { it > 0 } ?: autoGoal()
            else -> autoGoal()
        }
    }

    fun autoGoal(): Int {
        val days = Repo.steps.value.days
        val recent = (1..7).mapNotNull { days[localDay(-it)]?.steps?.takeIf { s -> s > 0 } }
        if (recent.size < 3) return DEFAULT_GOAL
        val target = recent.average() * 1.10
        return ((target / 500).roundToInt() * 500).coerceIn(5000, 12_000)
    }

    /** Moyenne des 7 derniers jours (pour l'IA et les écrans). */
    fun weekAverage(): Int {
        val days = Repo.steps.value.days
        val recent = (1..7).mapNotNull { days[localDay(-it)]?.steps?.takeIf { s -> s > 0 } }
        return if (recent.isEmpty()) 0 else recent.average().roundToInt()
    }

    /** Relève les pas et met à jour l'historique. Sans effet si le suivi est désactivé. */
    suspend fun refresh(context: Context) {
        val s = Repo.settings.value
        if (!s.stepsEnabled) return
        if (s.stepsSource == "hc" && healthConnectAvailable(context) && healthConnectGranted(context)) {
            refreshFromHealthConnect(context)
        } else if (hasActivityPermission(context)) {
            refreshFromSensor(context)
        }
    }

    private suspend fun refreshFromSensor(context: Context) {
        val counter = readCounter(context) ?: return
        val today = localDay(0)
        val goal = goal()
        Repo.updateSteps { d ->
            val delta = when {
                d.lastCounter < 0 -> 0L                    // premier relevé : point de départ
                counter >= d.lastCounter -> counter - d.lastCounter
                else -> counter                            // le téléphone a redémarré : le compteur repart de 0
            }.coerceIn(0L, 60_000L)                         // garde-fou contre une valeur aberrante
            val cur = d.days[today]?.steps ?: 0
            d.copy(days = d.days + (today to StepDay(cur + delta.toInt(), goal)), lastCounter = counter)
        }
    }

    /** Lit la valeur actuelle du compteur matériel (pas depuis le démarrage), puis coupe le capteur. */
    private suspend fun readCounter(context: Context): Long? {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return null
        return withTimeoutOrNull(3000) {
            suspendCancellableCoroutine { cont ->
                val listener = object : SensorEventListener {
                    override fun onSensorChanged(e: SensorEvent) {
                        sm.unregisterListener(this)
                        if (cont.isActive) cont.resume(e.values[0].toLong())
                    }
                    override fun onAccuracyChanged(s: Sensor?, a: Int) = Unit
                }
                sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
                cont.invokeOnCancellation { sm.unregisterListener(listener) }
            }
        }
    }

    private suspend fun refreshFromHealthConnect(context: Context) {
        val client = HealthConnectClient.getOrCreate(context)
        val goal = goal()
        val updates = mutableMapOf<String, StepDay>()
        for (offset in 0 downTo -6) {
            val (from, to) = Repo.dayBounds(offset)
            val total = runCatching {
                client.aggregate(
                    AggregateRequest(
                        setOf(StepsRecord.COUNT_TOTAL),
                        TimeRangeFilter.between(Instant.ofEpochMilli(from), Instant.ofEpochMilli(to))
                    )
                )[StepsRecord.COUNT_TOTAL]
            }.getOrNull() ?: continue
            val day = localDay(offset)
            val keepGoal = Repo.steps.value.days[day]?.goal?.takeIf { offset < 0 && it > 0 } ?: goal
            updates[day] = StepDay(total.toInt().coerceIn(0, 200_000), keepGoal)
        }
        if (updates.isNotEmpty()) Repo.updateSteps { it.copy(days = it.days + updates) }
    }

    /** Relevé périodique (capteur) : toutes les 15 min, seulement si le suivi est activé. */
    fun schedule(context: Context) {
        val wm = WorkManager.getInstance(context)
        val s = Repo.settings.value
        if (s.stepsEnabled && s.stepsSource == "sensor") {
            wm.enqueueUniquePeriodicWork(
                WORK, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<StepsWorker>(15, TimeUnit.MINUTES).build()
            )
        } else {
            wm.cancelUniqueWork(WORK)
        }
    }
}

class StepsWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        Repo.init(applicationContext)
        runCatching { Steps.refresh(applicationContext) }
        runCatching { StepGoalAi.refreshIfNeeded(applicationContext) }
        runCatching { com.goodlife.app.widget.ChefWidgets.updateAll(applicationContext) }   // pas à jour sur le widget
        return Result.success()
    }
}
