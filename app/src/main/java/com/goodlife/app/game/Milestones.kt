package com.goodlife.app.game

import com.goodlife.app.data.Repo
import com.goodlife.app.data.localDay
import org.json.JSONArray

/** Une mission de la première semaine (récompensée une seule fois). */
data class Mission(val id: String, val emoji: String, val title: String, val xp: Int, val done: Boolean)

/** Un badge (succès), débloqué d'après les vraies données de l'app. */
data class Badge(val id: String, val emoji: String, val title: String, val how: String, val unlocked: Boolean)

/**
 * Missions de démarrage, badges et garde-fou bienveillant. Rien n'est inventé : tout est calculé à partir de ce que
 * la personne a réellement fait dans l'app.
 */
object Milestones {
    /** Les missions s'affichent pendant les 14 premiers jours (ou jusqu'à ce qu'elles soient toutes faites). */
    const val MISSION_DAYS = 14

    fun missions(s: GameSummary?): List<Mission> {
        val st = Repo.settings.value
        val meals = Repo.meals.value
        val goal = st.waterGoalMl
        val list = mutableListOf(
            Mission("repas", "🍽️", "Note ton premier repas", 20, meals.isNotEmpty()),
            Mission("scan", "📸", "Ajoute un repas en photo ou par code-barres", 20, meals.any { it.source == "photo" || it.source == "code-barres" }),
            Mission("quiz", "🧠", "Réponds au quiz du chef", 15, Repo.game.value.quizResults.isNotEmpty()),
            Mission("eau", "💧", "Bois ${goal / 1000.0} L dans une journée".replace(".0 L", " L").replace('.', ','), 15, Repo.water.value.values.any { it >= goal }),
            Mission("ressenti", "🙂", "Dis au chef comment tu te sens", 10, Repo.feelings.value.isNotEmpty()),
            Mission("sport", "🏃", "Fais une sortie ou une séance de sport", 20, Repo.outings.value.isNotEmpty() || Repo.sport.value.done.isNotEmpty()),
            Mission("ami", "👥", "Ajoute un ami", 20, Repo.social.value.people.any { it.friend }),
            Mission("jour", "✅", "Valide une journée (score de 80 ou plus)", 30,
                s != null && (s.today.status == DayStatus.REUSSI || s.history.any { it.status == DayStatus.REUSSI }))
        )
        if (st.stepsEnabled) list.add(5, Mission("pas", "👟", "Fais 5 000 pas dans une journée", 20, Repo.steps.value.days.values.any { it.steps >= 5000 }))
        return list
    }

    /** Doit-on encore montrer les missions ? */
    fun showMissions(missions: List<Mission>): Boolean {
        val start = Repo.settings.value.privacyAcceptedAt
        val young = start == 0L || System.currentTimeMillis() - start < MISSION_DAYS * 86_400_000L
        return young && missions.any { !it.done }
    }

    /** Donne l'XP des missions accomplies qui ne l'ont pas encore été (une seule fois chacune). */
    fun rewardMissions(missions: List<Mission>) {
        val todo = missions.filter { it.done && it.id !in Repo.game.value.missionXp }
        if (todo.isNotEmpty()) Repo.updateGame { g -> g.copy(missionXp = g.missionXp + todo.associate { it.id to it.xp }) }
    }

    fun badges(s: GameSummary?): List<Badge> {
        val best = maxOf(s?.bestStreak ?: 0, s?.streak ?: 0)
        val dex = Repo.dex.value.unlocked.size
        val quiz = Repo.game.value.quizResults
        val steps = Repo.steps.value.days.values
        val outs = Repo.outings.value.filter { it.valid }
        val friends = Repo.social.value.people.count { it.friend }
        val waterDays = Repo.water.value.values.count { it >= Repo.settings.value.waterGoalMl }
        val list = mutableListOf<Badge>()
        listOf(3, 7, 14, 30, 60, 100).forEach { n -> list += Badge("serie$n", "🔥", "Série de $n jours", "Valide $n jours d'affilée", best >= n) }
        listOf(10, 25, 50, 100, 161).forEach { n -> list += Badge("dex$n", "📖", if (n == 161) "Nutridex complet" else "$n aliments au Nutridex", "Débloque $n aliments en photo", dex >= n) }
        list += Badge("quiz10", "🧠", "10 quiz", "Réponds à 10 quiz du chef", quiz.size >= 10)
        list += Badge("quiz5", "⭐", "5 bonnes réponses", "Fais au moins 5 bonnes réponses à un quiz", quiz.values.any { it >= 5 })
        list += Badge("pas10k", "👟", "10 000 pas", "Fais 10 000 pas dans une journée", steps.any { it.steps >= 10_000 })
        list += Badge("sortie1", "🏃", "Première sortie", "Enregistre une sortie GPS", outs.isNotEmpty())
        list += Badge("km5", "🎽", "5 km d'un coup", "Fais une sortie de 5 km ou plus", outs.any { it.distanceM >= 5000 })
        list += Badge("km50", "🗺️", "50 km au total", "Cumule 50 km de sorties", outs.sumOf { it.distanceM } >= 50_000)
        list += Badge("ami1", "🤝", "Premier ami", "Ajoute un ami", friends >= 1)
        list += Badge("ami5", "👥", "5 amis", "Ajoute 5 amis", friends >= 5)
        list += Badge("eau7", "💧", "Bien hydraté", "Atteins ton objectif d'eau 7 jours", waterDays >= 7)
        list += Badge("gel", "❄️", "Sauvé par le gel", "Utilise un gel pour sauver ta série", Repo.game.value.freezeUsed.isNotEmpty())
        return list
    }

    /** Badges débloqués que la personne n'a pas encore vus (pour la petite annonce sur l'accueil). */
    fun newBadges(all: List<Badge>): List<Badge> {
        val seen = runCatching { JSONArray(Repo.getExtra("badges_seen") ?: "[]") }.getOrElse { JSONArray() }
            .let { a -> (0 until a.length()).map { a.optString(it) }.toSet() }
        return all.filter { it.unlocked && it.id !in seen }
    }

    fun markBadgesSeen(all: List<Badge>) {
        Repo.putExtra("badges_seen", JSONArray(all.filter { it.unlocked }.map { it.id }).toString())
    }

    /**
     * Garde-fou bienveillant : les 3 derniers jours terminés, des repas ont été notés mais moins de la moitié des
     * besoins estimés. On propose alors gentiment de l'aide (jamais de reproche), sauf si c'est mis en pause.
     */
    fun undereating(s: GameSummary?): Boolean {
        if (s == null) return false
        if (Repo.settings.value.guardSnoozeUntil > System.currentTimeMillis()) return false
        val target = Repo.profile.value?.targetKcal ?: return false
        val last3 = s.history.filter { it.date >= localDay(-3) }
        return last3.size == 3 && last3.all { it.kcal > 0 && it.kcal < target * 0.5 }
    }
}
