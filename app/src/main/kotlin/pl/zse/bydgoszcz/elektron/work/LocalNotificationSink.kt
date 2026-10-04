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
import pl.zse.bydgoszcz.elektron.MainActivity
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
        val roomShort = sub.roomOrInfo.take(40)
        val subName = sub.substituteTeacher ?: "brak zastępcy"
        val title = "Nowe zastępstwo - ${sub.lessonNumber} lekcja, $subName, $roomShort"
        val day = dayLabel(sub.date)
        // Zwinięte: jedna linia z dniem. Rozwinięte: pełne szczegóły w osobnych liniach.
        val detail = buildString {
            append("$day • Zastępuje: ${sub.originalTeacher}")
            originalSubject?.let { append(" • $it") }
            sub.notes?.let { append(" • $it") }
        }
        val expanded = buildString {
            append("Kiedy: $day, ${sub.lessonNumber} lekcja")
            sub.groupNumber?.let { append(" (grupa $it)") }
            append("\nZastępuje: ${sub.originalTeacher}")
            originalSubject?.let { append(" • $it") }
            append("\nZastępca: $subName")
            append("\nSala / informacja: ${sub.roomOrInfo}")
            sub.notes?.let { append("\nUwagi: $it") }
        }
        val pi = PendingIntent.getActivity(
            context, sub.id.hashCode(), deepLinkIntent("substitutions"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val showInPlan = PendingIntent.getActivity(
            context, sub.id.hashCode() xor 0x5A5A, deepLinkIntent("timetable"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(context, CHANNEL_SUBSTITUTIONS)
            .setSmallIcon(R.drawable.ic_stat_elektron)
            .setContentTitle(title)
            .setContentText(detail)
            .setStyle(NotificationCompat.BigTextStyle().bigText(expanded))
            .setContentIntent(pi)
            .addAction(0, "Pokaż w planie", showInPlan)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setAutoCancel(true)
            .setSilent(isQuietNow())
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        safeNotify(sub.id.hashCode(), n)
    }

    override suspend fun postAnnouncement(ann: Announcement) {
        if (!canNotify()) return
        val body = ann.excerpt ?: ann.title
        // Bezpośrednio do przeglądarki — dawniej przez MainActivity, która najpierw
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

    suspend fun postNote(note: pl.zse.bydgoszcz.elektron.domain.model.LessonNote, subject: String, changed: Boolean = false) {
        if (!canPostNotes()) return
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
        try { NotificationManagerCompat.from(context).notify("note:${note.key}", 1, n) }
        catch (e: SecurityException) { Log.w(TAG, "Brak uprawnień", e) }
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

    private fun dayLabel(date: java.time.LocalDate): String {
        val today = java.time.LocalDate.now()
        val pl = java.util.Locale("pl", "PL")
        return when (date) {
            today -> "Dziś"
            today.plusDays(1) -> "Jutro"
            else -> date.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, pl)
                .replaceFirstChar { it.titlecase(pl) } + " " + date.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM"))
        }
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
        const val CHANNEL_NOTES = "elektron_lesson_notes"
        const val CHANNEL_REMINDERS = "elektron_reminders"
        private const val REMINDER_ID_BASE = 0x7E000000
        const val EXTRA_DEEP_LINK = "elektron_deep_link"
        private const val TAG = "LocalNotificationSink"
    }
}
