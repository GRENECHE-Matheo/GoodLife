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

    /** Ma clé de croisement (créée la première fois) : donnée à mes amis avec ma carte, jamais diffusée en Bluetooth. */
    @Synchronized
    fun crossSecret(): ByteArray {
        Repo.settings.value.crossSecret.takeIf { it.isNotBlank() }
            ?.let { s -> runCatching { android.util.Base64.decode(s, android.util.Base64.NO_WRAP) }.getOrNull()?.takeIf { it.size == 32 } }
            ?.let { return it }
        val fresh = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
        Repo.updateSettings { it.copy(crossSecret = android.util.Base64.encodeToString(fresh, android.util.Base64.NO_WRAP)) }
        return fresh
    }

    /**
     * Ma carte, avec seulement ce que j'ai choisi de partager. [withSecret] : avec ma clé de croisement (carte donnée
     * à un ami par QR, NFC ou lien) ; sans (carte chiffrée diffusée en Bluetooth).
     */
    fun myCard(withSecret: Boolean = true): PlayerCard? {
        if (!canShare()) return null
        val s = Repo.settings.value
        val p = Repo.profile.value ?: return null
        val summary = Game.summarize(Repo.meals.value, p, Repo.game.value, Repo.steps.value.days, newRulesFrom = s.scoreRulesFrom, foodOnlyFrom = s.foodOnlyFrom, richFrom = s.richScoreFrom)
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
            week = if (s.shareWeek) com.goodlife.app.game.Weekly.myStats() else null,
            crossSecret = if (withSecret) crossSecret() else null
        )
    }

    fun mySignedCard(withSecret: Boolean = true): ByteArray? = runCatching { myCard(withSecret)?.let { Identity.sign(it) } }.getOrNull()

    /** Carte d'un ami reconnu lors d'un croisement (déjà déchiffrée et vérifiée). */
    fun receiveCrossing(card: PlayerCard): SyncEvent = record(card, "street")

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

    /**
     * Invitation arrivée d'une AUTRE app (lien ouvert dans le navigateur, message partagé) : elle attend l'accord de
     * l'utilisateur dans l'app. Sans ça, n'importe quelle page web pourrait ajouter des « amis » en ouvrant un lien.
     */
    val pendingInvite = kotlinx.coroutines.flow.MutableStateFlow<PlayerCard?>(null)

    /** Carte valide (signature vérifiée) trouvée dans un message, sauf la mienne et celles des personnes bloquées. */
    fun cardInMessage(message: String): PlayerCard? {
        val code = Regex("""GL1:[A-Za-z0-9_-]{40,1900}""").find(message)?.value ?: return null
        val card = Identity.fromText(code) ?: return null
        if (card.id == Identity.myId() || card.id in Repo.social.value.blocked) return null
        return card
    }

    /** Déjà dans mes amis : la carte est signée par sa clé, on peut juste la mettre à jour. */
    fun isFriend(card: PlayerCard): Boolean = Repo.social.value.people.any { it.id == card.id && it.friend }

    fun accept(card: PlayerCard): SyncEvent = record(card, "code")

    /**
     * Lien d'invitation cliquable : une page statique (GitHub Pages) qui ouvre GoodLife. La carte est après le « # » :
     * le navigateur ne l'envoie jamais au serveur, et l'app vérifie sa signature.
     */
    fun inviteLink(code: String): String {
        val (owner, repo) = com.goodlife.app.BuildConfig.UPDATE_REPO.split("/").let { it[0] to it.getOrElse(1) { "GoodLife" } }
        return "https://${owner.lowercase()}.github.io/$repo/ami/#$code"
    }

    /** Message à envoyer pour qu'un ami m'ajoute à distance (sans aucun serveur GoodLife) : un lien cliquable. */
    fun shareText(): String? = mySignedCard()?.let {
        t("Ajoute-moi en ami sur GoodLife 🍏 Touche ce lien depuis ton téléphone Android :") + "\n" + inviteLink(Identity.toText(it))
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
