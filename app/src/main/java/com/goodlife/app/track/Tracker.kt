package com.goodlife.app.track

import android.location.Location
import com.goodlife.app.data.OutingType
import com.goodlife.app.data.TrackPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** État d'une sortie en cours (lu par l'écran et la notification). */
data class LiveOuting(
    val type: OutingType,
    val startedAt: Long,
    val movingMs: Long = 0L,
    val distanceM: Double = 0.0,
    val elevGainM: Double = 0.0,
    val speed: Double = 0.0,          // m/s, instantanée (lissée)
    val maxSpeed: Double = 0.0,
    val paused: Boolean = false,      // pause manuelle
    val autoPaused: Boolean = false,  // à l'arrêt
    val gpsOk: Boolean = false,       // précision suffisante reçue
    val points: List<TrackPoint> = emptyList(),
    val routeId: Long = 0L            // parcours refait (0 = nouveau)
) {
    val avgSpeed: Double get() = if (movingMs > 0) distanceM / (movingMs / 1000.0) else 0.0
}

/**
 * Calcul de la sortie à partir des positions GPS : filtre les points imprécis et les sauts impossibles,
 * met en pause automatiquement à l'arrêt, et calcule un dénivelé positif lissé (baromètre si disponible).
 */
object Tracker {
    private val _live = MutableStateFlow<LiveOuting?>(null)
    val live: StateFlow<LiveOuting?> = _live

    /** Itinéraire à suivre (boucle, destination ou parcours à refaire), affiché et utilisé pour le guidage. */
    private val _planned = MutableStateFlow<PlannedRoute?>(null)
    val planned: StateFlow<PlannedRoute?> = _planned
    fun setPlanned(route: PlannedRoute?) { _planned.value = route }

    private var last: Location? = null
    private var lastTick = 0L
    private var stillSince = 0L
    private var smoothAlt = Double.NaN
    private var altRef = Double.NaN
    private var rejected = 0
    @Volatile var baroAltitude = Double.NaN   // renseignée par le service si le téléphone a un baromètre

    fun start(type: OutingType, routeId: Long) {
        last = null; lastTick = 0L; stillSince = 0L; smoothAlt = Double.NaN; altRef = Double.NaN; baroAltitude = Double.NaN; rejected = 0
        _live.value = LiveOuting(type, System.currentTimeMillis(), routeId = routeId)
    }

    fun togglePause() { _live.value = _live.value?.let { it.copy(paused = !it.paused) } }

    fun clear() { _live.value = null; last = null }

    private fun maxPlausibleSpeed(t: OutingType) = when (t) { OutingType.BIKE -> 25.0; OutingType.RUN -> 9.0; OutingType.WALK -> 4.0 }
    private fun stopSpeed(t: OutingType) = when (t) { OutingType.BIKE -> 1.2; OutingType.RUN -> 0.8; OutingType.WALK -> 0.35 }

    /** Le temps avance chaque seconde (appelé par le service), même sans nouvelle position. */
    @Synchronized
    fun tick(now: Long = System.currentTimeMillis()) {
        val cur = _live.value ?: return
        if (lastTick == 0L) { lastTick = now; return }
        val dt = (now - lastTick).coerceIn(0, 5000)
        lastTick = now
        val stopped = stillSince > 0 && now - stillSince > 8000
        if (!cur.paused && !stopped && cur.gpsOk) _live.value = cur.copy(movingMs = cur.movingMs + dt, autoPaused = false)
        else if (stopped != cur.autoPaused) _live.value = cur.copy(autoPaused = stopped)
    }

    @Synchronized
    fun onLocation(loc: Location) {
        val cur = _live.value ?: return
        if (!loc.hasAccuracy() || loc.accuracy > 30f) return      // point trop imprécis
        // Position périmée (gardée en cache par le téléphone) : ignorée
        val ageMs = (android.os.SystemClock.elapsedRealtimeNanos() - loc.elapsedRealtimeNanos) / 1_000_000
        if (ageMs > 10_000) return
        if (cur.paused) { last = loc; return }
        val prev = last
        var distance = cur.distanceM
        var speed = cur.speed
        var maxSpeed = cur.maxSpeed
        val points = cur.points
        var newPoints = points
        if (prev != null) {
            val d = haversine(prev.latitude, prev.longitude, loc.latitude, loc.longitude)
            val dt = ((loc.time - prev.time) / 1000.0).coerceAtLeast(0.5)
            val v = d / dt
            if (v > maxPlausibleSpeed(cur.type)) {
                // Saut impossible : ignoré… sauf si plusieurs bons points d'affilée le confirment
                // (c'était alors l'ancien point qui était faux) : on repart du nouveau, sans compter de distance.
                if (++rejected >= 3) { last = loc; rejected = 0 }
                return
            }
            rejected = 0
            // Petits déplacements dans la marge d'erreur du GPS : on ne compte pas (évite de « marcher » à l'arrêt)
            val moved = d > (loc.accuracy / 2).coerceIn(3f, 10f)
            // Vitesse du GPS si elle est fournie et non nulle, sinon celle calculée à partir du déplacement
            val instant = if (loc.hasSpeed() && loc.speed > 0.1f) loc.speed.toDouble() else v
            speed = speed * 0.6 + instant * 0.4
            if (speed < stopSpeed(cur.type)) { if (stillSince == 0L) stillSince = System.currentTimeMillis() } else stillSince = 0L
            if (!moved) { _live.value = cur.copy(speed = speed, gpsOk = true); return }
            distance += d
            if (speed > maxSpeed) maxSpeed = speed
        }
        // Dénivelé : baromètre si présent, sinon altitude GPS ; lissé, et seules les montées de plus de 3 m comptent
        var gain = cur.elevGainM
        val rawAlt = when {
            !baroAltitude.isNaN() -> baroAltitude
            loc.hasAltitude() && (!loc.hasVerticalAccuracy() || loc.verticalAccuracyMeters <= 15f) -> loc.altitude
            else -> Double.NaN
        }
        if (!rawAlt.isNaN()) {
            smoothAlt = if (smoothAlt.isNaN()) rawAlt else smoothAlt * 0.8 + rawAlt * 0.2
            if (altRef.isNaN()) altRef = smoothAlt
            val diff = smoothAlt - altRef
            if (diff > 3.0) { gain += diff; altRef = smoothAlt } else if (diff < -3.0) altRef = smoothAlt
        }
        // On garde un point tous les 5 m environ (tracé léger)
        val lastKept = points.lastOrNull()
        if (lastKept == null || haversine(lastKept.lat, lastKept.lng, loc.latitude, loc.longitude) >= 5.0) {
            newPoints = points + TrackPoint(loc.latitude, loc.longitude, if (rawAlt.isNaN()) Double.NaN else smoothAlt, loc.time)
        }
        last = loc
        _live.value = cur.copy(
            distanceM = distance, elevGainM = gain, speed = speed, maxSpeed = maxSpeed,
            gpsOk = true, points = newPoints
        )
    }

    /** Distance entre deux points GPS, en mètres. */
    fun haversine(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * atan2(sqrt(a), sqrt(1 - a))
    }

    fun length(points: List<TrackPoint>): Double =
        points.zipWithNext().sumOf { (a, b) -> haversine(a.lat, a.lng, b.lat, b.lng) }

    /**
     * Le parcours de référence a-t-il bien été suivi ? Au moins 85 % de ses points à moins de 40 m du tracé,
     * et au moins 90 % de sa distance parcourue.
     */
    fun followed(route: List<TrackPoint>, attempt: List<TrackPoint>): Boolean {
        if (route.size < 2 || attempt.size < 2) return false
        if (length(attempt) < length(route) * 0.9) return false
        val sample = if (route.size > 200) route.filterIndexed { i, _ -> i % (route.size / 200 + 1) == 0 } else route
        val near = sample.count { r -> attempt.any { a -> abs(a.lat - r.lat) < 0.001 && haversine(a.lat, a.lng, r.lat, r.lng) <= 40.0 } }
        return near >= sample.size * 0.85
    }
}
