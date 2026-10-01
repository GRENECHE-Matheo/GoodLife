package com.goodlife.app.social

import com.goodlife.app.i18n.t

import com.goodlife.app.data.Repo
import com.goodlife.app.game.Game
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/** Événement d'échange (pour afficher « Ami ajouté ! » à l'écran). */
data class SyncEvent(val pseudo: String, val result: Repo.Received, val id: String = "")

object Social {
    private val _events = MutableSharedFlow<SyncEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<SyncEvent> = _events

    /** On ne partage rien tant que le profil n'est pas public et qu'il n'a pas de pseudo. */
    fun canShare(): Boolean {
        val s = Repo.settings.value
        return s.publicProfile && Identity.cleanPseudo(s.pseudo).isNotBlank() && Repo.profile.value != null
    }

    /** Ma carte, avec seulement ce que j'ai choisi de partager. */
    fun myCard(): PlayerCard? {
        if (!canShare()) return null
        val s = Repo.settings.value
        val p = Repo.profile.value ?: return null
        val summary = Game.summarize(Repo.meals.value, p, Repo.game.value, Repo.steps.value.days, newRulesFrom = s.scoreRulesFrom, foodOnlyFrom = s.foodOnlyFrom)
        val today = (System.currentTimeMillis() / 86_400_000L).toInt()
        val cheers = Repo.social.value.cheersOut.filter { today - it.day <= 7 }
            .sortedByDescending { it.at }.take(5).map { Cheer(it.to, it.message, it.day) }
        return PlayerCard(
            publicKey = Identity.myPublicKey(),
            pseudo = s.pseudo,
            level = if (s.shareLevel) summary.level.level else null,
            streak = if (s.shareStreak) summary.streak else null,
            bestStreak = if (s.shareStreak) summary.bestStreak else null,
            dex = if (s.shareDex) Repo.dex.value.unlocked.keys else null,
            timestamp = System.currentTimeMillis(),
            cheers = cheers,
            week = if (s.shareWeek) com.goodlife.app.game.Weekly.myStats() else null
        )
    }

    fun mySignedCard(): ByteArray? = runCatching { myCard()?.let { Identity.sign(it) } }.getOrNull()

    /** Carte reçue (octets bruts) : vérifiée, puis enregistrée. */
    fun receive(bytes: ByteArray, via: String): SyncEvent? {
        val card = Identity.verify(bytes) ?: return null
        return record(card, via)
    }

    fun receiveText(text: String, via: String): SyncEvent? {
        val card = Identity.fromText(text.trim()) ?: return null
        return record(card, via)
    }

    /** Retrouve une carte « GL1:… » dans un message (partagée par WhatsApp, SMS…) et l'enregistre. */
    fun receiveFromMessage(message: String): SyncEvent? {
        val code = Regex("""GL1:[A-Za-z0-9_-]{40,1900}""").find(message)?.value ?: return null
        return receiveText(code, "code")
    }

    /** Texte à envoyer par message pour qu'un ami m'ajoute à distance (sans aucun serveur). */
    fun shareText(): String? = mySignedCard()?.let {
        t("Ajoute-moi sur GoodLife 🍏 Ouvre ce message avec l'app GoodLife (Partager › GoodLife), ou copie-le dans Amis › Coller un code :\n") + Identity.toText(it)
    }

    private fun record(card: PlayerCard, via: String): SyncEvent {
        fun key(c: com.goodlife.app.data.CheerRecord) = "${c.from}|${c.message}|${c.day}"
        val before = Repo.social.value.cheersIn.map { key(it) }.toSet()
        val result = Repo.receiveCard(card, via, Identity.myId())
        // Encouragements arrivés avec cette carte : une notification
        val fresh = Repo.social.value.cheersIn.filter { it.from == card.id && key(it) !in before }
        if (fresh.isNotEmpty()) Repo.appContext?.let { SocialNotifier.cheers(it, card.pseudo, fresh.map { c -> c.message }) }
        val e = SyncEvent(card.pseudo, result, card.id)
        _events.tryEmit(e)
        return e
    }
}
