package pl.zse.bydgoszcz.elektron.work

import pl.zse.bydgoszcz.elektron.domain.util.runCatchingCancellable
import pl.zse.bydgoszcz.elektron.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
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
import pl.zse.bydgoszcz.elektron.ElektronActivity
import pl.zse.bydgoszcz.elektron.R
import pl.zse.bydgoszcz.elektron.presentation.common.SafeUrls
import pl.zse.bydgoszcz.elektron.domain.model.Announcement
import pl.zse.bydgoszcz.elektron.domain.model.Substitution
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocalNotificationSink @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository
) : NotificationSink {

    init { ensureChannels() }

    private fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val mgr = context.getSystemService(NotificationManager::class.java) ?: return

        mgr.createNotificationChannelGroup(
            NotificationChannelGroup(GROUP_ELEKTRON, "eLektron").apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    description = "Powiadomienia z aplikacji eLektron"
                }
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
        mgr.createNotificationChannel(NotificationChannel(CHANNEL_NOTES, "Notatki do lekcji", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Przypomnienia o Twoich notatkach do przyszłych lekcji."
            group = GROUP_ELEKTRON
        })
        mgr.createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDERS, "Przypomnienia o lekcjach", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Przypomnienia przed lekcją (Ustawienia -> Powiadomienia)."
                group = GROUP_ELEKTRON
            }
        )
    }

    override suspend fun postSubstitution(sub: Substitution, originalSubject: String?) {
        if (!canNotify()) return
        // Treść jak w aplikacji (SubstitutionNotice). Nazwa przedmiotu po personalizacji.
        val styles = runCatchingCancellable { settings.subjectStyles.first() }.getOrDefault(emptyMap())
        val subject = pl.zse.bydgoszcz.elektron.domain.model.SubjectStyles.displayName(originalSubject, styles)
        val content = pl.zse.bydgoszcz.elektron.domain.model.SubstitutionNotice.content(sub, subject, java.time.LocalDate.now())
        // Jeden wpis w panelu na lekcję: poprawione zastępstwo (nowe ID) zastępuje poprzednią wersję.
        val tag = pl.zse.bydgoszcz.elektron.domain.model.SubstitutionNotice.tag(sub)
        val pi = PendingIntent.getActivity(
            context, tag.hashCode(), deepLinkIntent("substitutions"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val showInPlan = PendingIntent.getActivity(
            context, tag.hashCode() xor 0x5A5A, pl.zse.bydgoszcz.elektron.widget.lessonIntent(context,
                pl.zse.bydgoszcz.elektron.domain.model.LessonTarget(sub.date, sub.lessonNumber)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(context, CHANNEL_SUBSTITUTIONS)
            .setSmallIcon(R.drawable.ic_stat_elektron)
            .setContentTitle(content.title)
            .setContentText(content.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content.expanded))
            .setContentIntent(pi)
            .addAction(0, "Pokaż w planie", showInPlan)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setAutoCancel(true)
            .setSilent(isQuietNow())
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        try { NotificationManagerCompat.from(context).notify(tag, SUBSTITUTION_ID, n) }
        catch (e: SecurityException) { Log.w(TAG, "Brak uprawnień", e) }
    }

    override suspend fun postAnnouncement(ann: Announcement) {
        if (!canNotify()) return
        val body = ann.excerpt ?: ann.title
        // Bezpośrednio do przeglądarki — dawniej przez ElektronActivity, która najpierw
        // uruchamiała appkę, a potem przekierowywała (mignięcie ekranu appki).
        // Tylko bezpieczne adresy http(s); inaczej otwieramy sekcję Ogłoszeń w aplikacji.
        val target = if (SafeUrls.isWebUrl(ann.url)) {
            Intent(Intent.ACTION_VIEW, android.net.Uri.parse(ann.url.trim()))
                .addCategory(Intent.CATEGORY_BROWSABLE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } else deepLinkIntent("announcements")
        val pi = PendingIntent.getActivity(
            context, ann.id.hashCode(), target,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(context, CHANNEL_ANNOUNCEMENTS)
            .setSmallIcon(R.drawable.ic_stat_elektron)
            .setContentTitle(ann.title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setSilent(isQuietNow())
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
            .setSilent(isQuietNow())
            .build()
        safeNotify(title.hashCode(), n)
    }

    fun canPostNotes(): Boolean = canNotify() && NotificationManagerCompat.from(context).areNotificationsEnabled() && (Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
        context.getSystemService(NotificationManager::class.java)?.getNotificationChannel(CHANNEL_NOTES)?.importance != NotificationManager.IMPORTANCE_NONE)

    suspend fun postNote(note: pl.zse.bydgoszcz.elektron.domain.model.LessonNote, subject: String, changed: Boolean = false): Boolean {
        if (!canPostNotes()) return false
        val target = pl.zse.bydgoszcz.elektron.domain.model.LessonTarget(note.date, note.number)
        val intent = deepLinkIntent("timetable").apply {
            removeExtra(EXTRA_DEEP_LINK)
            putExtra(pl.zse.bydgoszcz.elektron.domain.model.LessonLinks.EXTRA_LESSON,
                pl.zse.bydgoszcz.elektron.domain.model.LessonLinks.deepLink(target))
            data = android.net.Uri.parse(pl.zse.bydgoszcz.elektron.domain.model.LessonLinks.uri(target))
        }
        val pi = PendingIntent.getActivity(context, note.key.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val body = "${note.date.format(java.time.format.DateTimeFormatter.ofPattern("d.MM"))}, ${note.number}. lekcja\n" +
            (if (changed) "Plan się zmienił. Notatka zapisana dla: ${note.subject}.\n" else "") + note.text
        val n = NotificationCompat.Builder(context, CHANNEL_NOTES).setSmallIcon(R.drawable.ic_stat_elektron)
            .setContentTitle("Notatka: $subject").setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body)).setContentIntent(pi)
            .setCategory(NotificationCompat.CATEGORY_REMINDER).setAutoCancel(true).setSilent(isQuietNow())
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build()
        return try {
            NotificationManagerCompat.from(context).notify("note:${note.key}", 1, n)
            true
        } catch (e: SecurityException) { Log.w(TAG, "Brak uprawnień", e); false }
    }

    /** Przypomnienie przed lekcją (LessonReminderReceiver). Znika samo po końcu lekcji. */
    suspend fun postReminder(id: Int, title: String, body: String, timeoutMs: Long) {
        if (!canNotify()) return
        val pi = PendingIntent.getActivity(
            context, id xor 0x3C3C, deepLinkIntent("timetable"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_stat_elektron)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pi)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setSilent(isQuietNow())
            .setTimeoutAfter(timeoutMs)
            .build()
        safeNotify(REMINDER_ID_BASE + (id and 0xFFFF), n)
    }

    /** Ciche godziny z Ustawień: powiadomienie bez dźwięku i wibracji. */
    private suspend fun isQuietNow(): Boolean =
        runCatchingCancellable { settings.quietHours.first().isQuiet(java.time.LocalTime.now()) }.getOrDefault(false)

    private fun deepLinkIntent(route: String): Intent =
        Intent(context, ElektronActivity::class.java).apply {
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
        const val CHANNEL_NOTES = "elektron_lesson_notes"
        const val CHANNEL_REMINDERS = "elektron_reminders"
        private const val REMINDER_ID_BASE = 0x7E000000
        /** Zastępstwa: rozróżnia je znacznik (SubstitutionNotice.tag), ID jest stałe. */
        private const val SUBSTITUTION_ID = 0x5B000001
        const val EXTRA_DEEP_LINK = "elektron_deep_link"
        private const val TAG = "LocalNotificationSink"
    }
}
