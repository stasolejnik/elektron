package pl.zse.bydgoszcz.elektron.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import pl.zse.bydgoszcz.elektron.MainActivity
import pl.zse.bydgoszcz.elektron.R
import pl.zse.bydgoszcz.elektron.domain.model.Announcement
import pl.zse.bydgoszcz.elektron.domain.model.Substitution
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalNotificationSink @Inject constructor(
    @ApplicationContext private val context: Context
) : NotificationSink {

    init { ensureChannels() }

    private fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = context.getSystemService(NotificationManager::class.java) ?: return

        mgr.createNotificationChannelGroup(
            NotificationChannelGroup(GROUP_ELEKTRON, "eLektron").apply {
                description = "Powiadomienia z aplikacji eLektron"
            }
        )

        mgr.createNotificationChannel(
            NotificationChannel(CHANNEL_SUBSTITUTIONS, "Zastępstwa", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Powiadomienia o nowych zastępstwach w Twojej klasie."
                group = GROUP_ELEKTRON
            }
        )
        mgr.createNotificationChannel(
            NotificationChannel(CHANNEL_ANNOUNCEMENTS, "Ogłoszenia", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Powiadomienia o nowych ogłoszeniach z RSS szkoły."
                group = GROUP_ELEKTRON
            }
        )
    }

    override suspend fun postSubstitution(sub: Substitution, originalSubject: String?) {
        if (!canNotify()) return
        val roomShort = sub.roomOrInfo.take(40)
        val subName = sub.substituteTeacher ?: "brak zastępcy"
        val title = "Nowe zastępstwo - ${sub.lessonNumber} lekcja, $subName, $roomShort"
        val detail = buildString {
            append("Zastępuje: ${sub.originalTeacher}")
            originalSubject?.let { append(" • $it") }
            sub.notes?.let { append(" • $it") }
        }
        val pi = PendingIntent.getActivity(
            context, sub.id.hashCode(), deepLinkIntent("substitutions"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(context, CHANNEL_SUBSTITUTIONS)
            .setSmallIcon(R.drawable.ic_stat_elektron)
            .setContentTitle(title)
            .setContentText(detail)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        safeNotify(sub.id.hashCode(), n)
    }

    override suspend fun postAnnouncement(ann: Announcement) {
        if (!canNotify()) return
        val body = ann.excerpt ?: ann.title
        // Bezpośrednio do przeglądarki — dawniej przez MainActivity, która najpierw
        // uruchamiała appkę, a potem przekierowywała (mignięcie ekranu appki).
        val browser = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(ann.url))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pi = PendingIntent.getActivity(
            context, ann.id.hashCode(), browser,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(context, CHANNEL_ANNOUNCEMENTS)
            .setSmallIcon(R.drawable.ic_stat_elektron)
            .setContentTitle(ann.title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        safeNotify(ann.id.hashCode(), n)
    }

    override suspend fun postGeneric(title: String, body: String, deepLink: String?) {
        if (!canNotify()) return
        val pi = deepLink?.let { link ->
            PendingIntent.getActivity(
                context, link.hashCode(), deepLinkIntent(link),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
        val n = NotificationCompat.Builder(context, CHANNEL_ANNOUNCEMENTS)
            .setSmallIcon(R.drawable.ic_stat_elektron)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        safeNotify(title.hashCode(), n)
    }

    private fun deepLinkIntent(route: String): Intent =
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_DEEP_LINK, route)
        }

    private fun safeNotify(id: Int, n: android.app.Notification) {
        try { NotificationManagerCompat.from(context).notify(id, n) }
        catch (e: SecurityException) { Log.w(TAG, "Brak uprawnień", e) }
    }

    private fun canNotify(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    companion object {
        const val GROUP_ELEKTRON = "elektron"
        const val CHANNEL_SUBSTITUTIONS = "elektron_substitutions"
        const val CHANNEL_ANNOUNCEMENTS = "elektron_announcements"
        const val EXTRA_DEEP_LINK = "elektron_deep_link"
        private const val TAG = "LocalNotificationSink"
    }
}
