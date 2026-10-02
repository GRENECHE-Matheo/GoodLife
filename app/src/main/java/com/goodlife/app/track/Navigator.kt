package com.goodlife.app.track

import com.goodlife.app.data.OutingType
import com.goodlife.app.data.Repo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Guidage pendant une sortie avec un itinéraire : distance restante, écart au tracé, et recalcul automatique (comme un
 * GPS) quand on quitte l'itinéraire vers une destination. Le recalcul part de la position actuelle : si le chemin
 * pris est plus court, c'est lui qui devient l'itinéraire. Tout est calculé sur le téléphone.
 */
object Navigator {
    data class State(
        val remainingM: Double,
        val offM: Double,
        val rerouting: Boolean = false,
        val reroutedAt: Long = 0L
    )

    private val _state = MutableStateFlow<State?>(null)
    val state: StateFlow<State?> = _state

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var route: PlannedRoute? = null
    private var hint = -1
    private var offCount = 0
    private var lastReroute = 0L
    private var job: Job? = null
    private var reroutedAt = 0L

    /** Au-delà de cette distance du tracé, on est « hors itinéraire ». */
    const val OFF_ROUTE_M = 40.0

    fun reset() {
        job?.cancel(); job = null
        route = null; hint = -1; offCount = 0; lastReroute = 0L; reroutedAt = 0L
        _state.value = null
    }

    @Synchronized
    fun onLocation(lat: Double, lng: Double, type: OutingType) {
        val r = Tracker.planned.value
        if (r == null) { _state.value = null; route = null; return }
        if (r !== route) { route = r; hint = -1; offCount = 0 }
        val (remaining, off, index) = Routing.progress(r.points, lat, lng, hint)
        hint = index
        offCount = if (off > OFF_ROUTE_M) offCount + 1 else 0
        val now = System.currentTimeMillis()
        // 3 positions d'affilée hors du tracé, et au plus un recalcul toutes les 20 s
        if (r.hasDestination && offCount >= 3 && remaining > 80 && now - lastReroute > 20_000 && job?.isActive != true) {
            lastReroute = now
            job = scope.launch {
                runCatching { Routing.toDestination(lat, lng, r.destLat, r.destLng, type, r.style) }.getOrNull()?.let { fresh ->
                    // Seulement si personne n'a changé d'itinéraire entre-temps
                    if (Tracker.planned.value === r) {
                        Tracker.setPlanned(fresh.copy(label = r.label))
                        reroutedAt = System.currentTimeMillis()
                    }
                }
                _state.value = _state.value?.copy(rerouting = false, reroutedAt = reroutedAt)
            }
        }
        _state.value = State(remaining, off, job?.isActive == true, reroutedAt)
    }

    /** Vitesse habituelle (m/s) par défaut, avant d'avoir des sorties enregistrées. */
    fun typicalSpeed(t: OutingType) = when (t) { OutingType.RUN -> 2.8; OutingType.WALK -> 1.35; OutingType.BIKE -> 4.5 }

    /**
     * Ta vitesse moyenne (m/s) pour ce type de sortie, calculée sur tes 20 dernières sorties de plus de 5 minutes
     * (distance totale ÷ temps en mouvement). Rien n'est envoyé : tout vient des sorties gardées sur le téléphone.
     */
    fun personalSpeed(t: OutingType): Double {
        val list = Repo.outings.value.filter { it.type == t && it.movingMs > 5 * 60_000L && it.distanceM > 300 }.take(20)
        val ms = list.sumOf { it.movingMs }
        if (list.size < 2 || ms <= 0) return typicalSpeed(t)
        val v = list.sumOf { it.distanceM } / (ms / 1000.0)
        return v.coerceIn(typicalSpeed(t) * 0.4, typicalSpeed(t) * 2.5)
    }

    /** Vitesse utilisée pour l'heure d'arrivée : ta moyenne habituelle, mélangée avec celle de la sortie en cours. */
    fun etaSpeed(live: LiveOuting): Double {
        val usual = personalSpeed(live.type)
        return if (live.movingMs > 3 * 60_000L && live.avgSpeed > 0.3) usual * 0.5 + live.avgSpeed * 0.5 else usual
    }
}
