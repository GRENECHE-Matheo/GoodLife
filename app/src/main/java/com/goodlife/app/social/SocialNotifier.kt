package com.goodlife.app.social

import com.goodlife.app.i18n.t

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.goodlife.app.MainActivity
import com.goodlife.app.R
import com.goodlife.app.data.Repo
import kotlinx.coroutines.flow.MutableStateFlow

/** Écran demandé par une notification (« friends » : l'écran Amis). */
object AppNav {
    val request = MutableStateFlow<String?>(null)
    const val EXTRA = "goodlife_open"
}

/**
 * Notifications des amis, préparées sur le téléphone (aucun serveur) :
 * - un encouragement reçu (il arrive avec la carte de l'ami, à la synchro) ;
 * - une personne croisée pour la première fois (avec « Ajouter en ami ») ou recroisée (sa carte est à jour).
 * Le nom n'apparaît pas sur l'écran verrouillé.
 */
object SocialNotifier {
    const val CH_FRIENDS = "friends"
    /** Notification imposée par Android tant que les croisements tournent : la plus discrète possible. */
    const val CH_ACTIVE = "crossings_active"
    private const val ACTION_ADD = "com.goodlife.app.ADD_FRIEND"
    private const val EXTRA_ID = "person"
    private const val CHEERS_ID = 3100

    fun channels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_FRIENDS, t("Amis et rencontres"), NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = t("Encouragements reçus et personnes croisées.")
        })
        nm.createNotificationChannel(NotificationChannel(CH_ACTIVE, t("Croisements actifs"), NotificationManager.IMPORTANCE_MIN).apply {
            description = t("Notification discrète imposée par Android tant que les croisements sont actifs.")
            setShowBadge(false)
        })
        nm.deleteNotificationChannel("streetpass")   // ancienne notification, trop visible
    }

    private fun allowed(context: Context) = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Ouvre l'écran Amis. */
    fun openFriends(context: Context, code: Int): PendingIntent = PendingIntent.getActivity(
        context, code,
        Intent(context, MainActivity::class.java).putExtra(AppNav.EXTRA, "friends").addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun post(context: Context, id: Int, title: String, text: String, actions: NotificationCompat.Builder.() -> Unit = {}) {
        if (!allowed(context)) return
        channels(context)
        val public = NotificationCompat.Builder(context, CH_FRIENDS)
            .setSmallIcon(R.drawable.ic_notif_leaf)
            .setContentTitle("Lifoody")
            .setContentText(t("Du nouveau chez tes amis"))
            .build()
        val n = NotificationCompat.Builder(context, CH_FRIENDS)
            .setSmallIcon(R.drawable.ic_notif_leaf)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openFriends(context, id))
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(public)
            .apply(actions)
            .build()
        runCatching { context.getSystemService(NotificationManager::class.java).notify(id, n) }
    }

    /** Encouragements reçus d'un ami. */
    fun cheers(context: Context, from: String, messages: List<Int>) {
        if (messages.isEmpty()) return
        val text = messages.distinct().joinToString("\n") { CHEERS.getOrElse(it) { "" } }
        post(context, CHEERS_ID + (from.hashCode() and 0xff), t("💪 %1\$s t'encourage", from), text)
    }

    fun cancelCheers(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        runCatching { nm.activeNotifications.filter { it.id in CHEERS_ID..CHEERS_ID + 0xff }.forEach { nm.cancel(it.id) } }
    }

    private fun encounterId(personId: String) = 3400 + (personId.hashCode() and 0xfff)

    /** Personne croisée : première fois (proposer de l'ajouter) ou de nouveau (sa carte est à jour). */
    fun encounter(context: Context, personId: String, pseudo: String, first: Boolean, friend: Boolean) {
        val id = encounterId(personId)
        val title = if (first) t("Nouvelle rencontre : %1\$s", pseudo) else t("Tu as recroisé %1\$s", pseudo)
        val text = when {
            first -> t("Tu as croisé un joueur Lifoody. Ajoute-le en ami pour le suivre dans ton classement.")
            friend -> t("Sa carte est à jour (niveau, série, défi de la semaine).")
            else -> t("Sa carte est à jour. Tu peux l'ajouter en ami.")
        }
        post(context, id, title, text) {
            if (!friend) {
                val add = PendingIntent.getBroadcast(
                    context, id,
                    Intent(context, FriendActionReceiver::class.java).setAction(ACTION_ADD).putExtra(EXTRA_ID, personId),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                addAction(0, t("Ajouter en ami"), add)
            }
        }
    }

    /** Joueur inconnu croisé : il reste anonyme (identifiant qui change toutes les 15 min). */
    fun stranger(context: Context, level: Int?) {
        post(context, 3399, t("Tu as croisé un joueur Lifoody"),
            if (level != null) t("Niveau %1\$s. Les inconnus restent anonymes : ni pseudo, ni suivi possible.", level)
            else t("Les inconnus restent anonymes : ni pseudo, ni suivi possible.")) {}
    }

    internal fun handleAdd(context: Context, intent: Intent) {
        if (intent.action != ACTION_ADD) return
        val id = intent.getStringExtra(EXTRA_ID) ?: return
        Repo.init(context.applicationContext)
        if (Repo.social.value.people.any { it.id == id }) Repo.setFriend(id, true)
        context.getSystemService(NotificationManager::class.java).cancel(encounterId(id))
    }
}

/** Bouton « Ajouter en ami » d'une notification de rencontre. */
class FriendActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = SocialNotifier.handleAdd(context, intent)
}
