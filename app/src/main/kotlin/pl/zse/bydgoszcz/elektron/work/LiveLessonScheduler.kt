package pl.zse.bydgoszcz.elektron.work

import android.Manifest
import android.app.AlarmManager
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import pl.zse.bydgoszcz.elektron.R
import pl.zse.bydgoszcz.elektron.data.repository.LessonNotesRepository
import pl.zse.bydgoszcz.elektron.domain.model.LessonGroups
import pl.zse.bydgoszcz.elektron.domain.model.LiveLesson
import pl.zse.bydgoszcz.elektron.domain.repository.SettingsRepository
import pl.zse.bydgoszcz.elektron.domain.repository.TimetableRepository
import pl.zse.bydgoszcz.elektron.domain.util.AppClock
import pl.zse.bydgoszcz.elektron.domain.util.runCatchingCancellable
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Powiadomienie na żywo (treść: [LiveLesson]). Bez usługi pierwszoplanowej i bez stałego procesu:
 * jak przypomnienia o lekcjach - jeden alarm na najbliższą granicę (początek okna, dzwonek), po nim
 * przeliczenie. Odliczanie robi chronometr powiadomienia. Pasek postępu odświeża osobny alarm
 * BEZ budzenia telefonu (co [PROGRESS_MINUTES] min) - system dostarcza go, gdy telefon i tak działa
 * (np. włączony ekran), więc uśpiony telefon nie budzi się co kilka minut.
 *
 * Przeliczane przy każdym przeliczeniu przypomnień ([LessonReminderScheduler.reschedule]: synchronizacja,
 * restart, zmiana czasu i strefy, zmiana klasy, grup i nazw przedmiotów), po zmianie notatki
 * i po zmianie ustawienia.
 */
@Singleton
class LiveLessonScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val timetableRepo: TimetableRepository,
    private val notes: LessonNotesRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()
    private val stateLog = pl.zse.bydgoszcz.elektron.domain.util.LogOnChange()

    fun requestReschedule() {
        scope.launch { reschedule() }
    }

    /** [now] - czas aplikacji (także symulowany w trybie dewelopera, [AppClock]). */
    suspend fun reschedule(now: LocalDateTime = AppClock.now()) = mutex.withLock {
        runCatchingCancellable { rescheduleLocked(now) }
            .onFailure { Log.w(TAG, "Nie udało się przeliczyć powiadomienia na żywo", it) }
        Unit
    }

    /** "Ukryj na dziś" albo odrzucenie (Android 14+): do jutra bez powiadomienia. */
    suspend fun hideToday(now: LocalDateTime = AppClock.now()) {
        runCatchingCancellable { settings.setLiveHiddenDay(now.toLocalDate().toEpochDay()) }
            .onFailure { Log.w(TAG, "Nie udało się zapisać ukrycia powiadomienia", it) }
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        reschedule(now)
    }

    /**
     * Powiadomienie zniknęło bez "Ukryj na dziś": odsunięcie przez użytkownika (Android 14+) ukrywa je
     * do jutra. System wysyła ten sam sygnał po upływie czasu ([NotificationCompat.Builder.setTimeoutAfter],
     * po końcu lekcji) - wtedy nic nie ukrywamy, inaczej lekcja dopisana później tego dnia (zastępstwo)
     * nie miałaby już powiadomienia.
     */
    suspend fun dismissed(now: LocalDateTime = AppClock.now()) {
        val showing = runCatchingCancellable { mutex.withLock { contentAt(now) } != null }.getOrDefault(true)
        if (showing) { Log.i(TAG, "Odsunięte - ukryte do jutra"); hideToday(now) }
        else { Log.i(TAG, "Wygasło po lekcjach"); reschedule(now) }
    }

    private suspend fun lessonsFor(classId: String, today: java.time.LocalDate) =
        LessonGroups.filter(timetableRepo.getLessonsOnce(classId, today, today.plusDays(7)), settings.groupSelections(classId).first())

    /** Co powiadomienie pokazuje teraz (bez ukrycia na dziś); null - poza oknem lekcji. */
    private suspend fun contentAt(now: LocalDateTime): LiveLesson.Content? {
        val classId = settings.selectedClassId.first() ?: return null
        return LiveLesson.content(lessonsFor(classId, now.toLocalDate()), now)
    }

    private suspend fun rescheduleLocked(now: LocalDateTime) {
        val classId = settings.selectedClassId.first()
        if (!settings.liveNotifications.first() || classId == null || !canPost(context)) {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
            cancelAlarms()
            if (settings.liveNotifications.first()) Log.i(TAG, "Wyłączone: ${if (classId == null) "brak klasy" else "brak zgody na powiadomienia"}")
            return
        }
        val today = now.toLocalDate()
        val lessons = lessonsFor(classId, today)
        val hidden = settings.liveHiddenDay.first() == today.toEpochDay()
        val styles = runCatchingCancellable { settings.subjectStyles.first() }.getOrDefault(emptyMap())
        val noteMap = runCatchingCancellable { notes.notes.first() }.getOrDefault(emptyList()).associateBy { it.key }
        val content = if (hidden) null else LiveLesson.content(lessons, now, styles, noteMap)
        if (content == null) NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        else post(content, now)
        // Ukryte dziś - następna granica dopiero jutro (bez budzenia na dzisiejsze dzwonki).
        val from = if (hidden) today.plusDays(1).atStartOfDay().minusNanos(1) else now
        val next = LiveLesson.nextChange(lessons, from)
        setAlarm(BOUNDARY_REQUEST, ACTION_BOUNDARY, next, wakeUp = true)
        val progressAt = content?.let { minOf(next ?: it.windowEnd, now.plusMinutes(PROGRESS_MINUTES)) }
        setAlarm(PROGRESS_REQUEST, ACTION_PROGRESS, progressAt?.takeIf { next == null || it < next }, wakeUp = false)
        // Diagnostyka ("Zgłoś problem"): co pokazano i kiedy następna zmiana - tylko przy zmianie stanu,
        // nie przy każdym odświeżeniu paska co minutę.
        if (stateLog.changed(listOf(content?.phase, content?.countdownTo, hidden, next, AppClock.simulated)))
        Log.i(TAG, "Teraz ${now.withNano(0)}${if (AppClock.simulated) " (symulacja)" else ""}: " +
            (content?.let { "${it.phase}, do ${it.countdownTo.toLocalTime()}" } ?: if (hidden) "ukryte na dziś" else "bez powiadomienia") +
            ", następna zmiana ${next?.withNano(0) ?: "brak"}, dokładne alarmy: ${LessonReminderScheduler.canUseExactAlarms(context)}" +
            (canPostPromoted(context)?.let { ", Live Updates: ${if (it) "tak" else "wyłączone w systemie"}" } ?: ""))
    }

    private fun post(content: LiveLesson.Content, now: LocalDateTime) {
        val target = pl.zse.bydgoszcz.elektron.widget.lessonIntent(context, content.target).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val open = PendingIntent.getActivity(context, OPEN_REQUEST, target,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val hide = broadcast(HIDE_REQUEST, ACTION_HIDE)
        val dismissed = broadcast(DISMISS_REQUEST, ACTION_DISMISSED)
        val countdownMs = realMillis(content.countdownTo)
        val progress = (content.progress(now) * PROGRESS_MAX).toInt()
        // Ekran blokady (gdy system ukrywa poufne treści): bez przedmiotów, sal i notatek.
        val public = NotificationCompat.Builder(context, LocalNotificationSink.CHANNEL_LIVE)
            .setSmallIcon(R.drawable.ic_stat_elektron)
            .setContentTitle(PUBLIC_TITLES.getValue(content.phase))
            .setUsesChronometer(true).setChronometerCountDown(true).setWhen(countdownMs).setShowWhen(true)
            .build()
        val n = NotificationCompat.Builder(context, LocalNotificationSink.CHANNEL_LIVE)
            .setSmallIcon(R.drawable.ic_stat_elektron)
            .setContentTitle(content.title)
            .setContentText(content.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content.lines.joinToString("\n")).setBigContentTitle(content.title))
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setWhen(countdownMs)
            .setShowWhen(true)
            .setProgress(PROGRESS_MAX, progress, false)
            // Mały napis: kiedy koniec lekcji całego dnia i ile jeszcze (odświeżany co minutę przy włączonym ekranie).
            .setSubText(LiveLesson.dayLeft(content, now))
            .setContentIntent(open)
            .setDeleteIntent(dismissed)
            .addAction(0, "Ukryj na dziś", hide)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setLocalOnly(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(public)
            // Zabezpieczenie: po końcu ostatniej lekcji znika samo, nawet gdy alarm się spóźni.
            .setTimeoutAfter((java.time.Duration.between(now, content.windowEnd).toMillis() + 60_000L).coerceAtLeast(60_000L))
            .apply {
                // Android 16: prośba o "Live Update" (promowane powiadomienie na pasku i ekranie blokady).
                // Starsze systemy ignorują ten klucz; nie wymaga compileSdk 36.
                extras.putBoolean(EXTRA_REQUEST_PROMOTED_ONGOING, true)
            }
            .build()
        // "Wyczyść wszystko" nie usuwa powiadomienia (dawniej znikało i ukrywało się do jutra);
        // pojedyncze odsunięcie na Androidzie 14+ nadal działa i ukrywa je do jutra.
        n.flags = n.flags or android.app.Notification.FLAG_NO_CLEAR
        try { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, n) }
        catch (e: SecurityException) { Log.w(TAG, "Brak uprawnień do powiadomień", e) }
    }

    private fun broadcast(request: Int, action: String): PendingIntent =
        PendingIntent.getBroadcast(context, request, Intent(context, LiveLessonReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun setAlarm(request: Int, action: String, at: LocalDateTime?, wakeUp: Boolean) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = broadcast(request, action)
        if (at == null) { am.cancel(pi); return }
        val ms = realMillis(at) + 1_000L
        if (!wakeUp) {
            // Bez budzenia, ale co do sekundy, gdy telefon działa (zwykłe set() system przesuwał o minuty).
            try {
                if (LessonReminderScheduler.canUseExactAlarms(context)) am.setExact(AlarmManager.RTC, ms, pi) else am.set(AlarmManager.RTC, ms, pi)
            } catch (e: SecurityException) { am.set(AlarmManager.RTC, ms, pi) }
            return
        }
        try {
            if (LessonReminderScheduler.canUseExactAlarms(context)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
        }
    }

    /**
     * Chwila czasu aplikacji jako prawdziwy czas telefonu: alarmy i chronometr działają w czasie
     * systemu, a przy symulacji (tryb dewelopera) czas aplikacji jest przesunięty o [AppClock.offsetMs].
     */
    private fun realMillis(at: LocalDateTime): Long =
        at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() - AppClock.offsetMs.value

    private fun cancelAlarms() {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        am.cancel(broadcast(BOUNDARY_REQUEST, ACTION_BOUNDARY))
        am.cancel(broadcast(PROGRESS_REQUEST, ACTION_PROGRESS))
    }

    companion object {
        private const val TAG = "LiveLesson"
        const val NOTIFICATION_ID = 0x4C495645
        private const val PROGRESS_MAX = 1000
        /** Pasek postępu i napis o końcu dnia: co minutę, alarmem bez budzenia (tylko przy działającym telefonie). */
        private const val PROGRESS_MINUTES = 1L
        private const val BOUNDARY_REQUEST = 4201
        private const val PROGRESS_REQUEST = 4202
        private const val HIDE_REQUEST = 4203
        private const val DISMISS_REQUEST = 4204
        private const val OPEN_REQUEST = 4205
        const val ACTION_BOUNDARY = "pl.zse.bydgoszcz.elektron.live.BOUNDARY"
        const val ACTION_PROGRESS = "pl.zse.bydgoszcz.elektron.live.PROGRESS"
        const val ACTION_HIDE = "pl.zse.bydgoszcz.elektron.live.HIDE"
        const val ACTION_DISMISSED = "pl.zse.bydgoszcz.elektron.live.DISMISSED"
        /** Notification.EXTRA_REQUEST_PROMOTED_ONGOING (API 36). */
        private const val EXTRA_REQUEST_PROMOTED_ONGOING = "android.requestPromotedOngoing"

        /** Ekran blokady: tylko rodzaj odliczania, bez przedmiotów i sal. */
        private val PUBLIC_TITLES = mapOf(
            LiveLesson.Phase.BEFORE to "Do lekcji",
            LiveLesson.Phase.LESSON to "Do końca lekcji",
            LiveLesson.Phase.BREAK to "Do lekcji",
            LiveLesson.Phase.GAP to "Do lekcji"
        )

        /**
         * Android 16+: czy system pozwala promować powiadomienia eLektronu (Live Updates - odliczanie
         * na pasku stanu). Użytkownik może to wyłączyć w ustawieniach aplikacji; null - starszy Android.
         * Przez refleksję: NotificationManager.canPostPromotedNotifications() jest od API 36.
         */
        fun canPostPromoted(context: Context): Boolean? {
            if (Build.VERSION.SDK_INT < 36) return null
            val nm = context.getSystemService(NotificationManager::class.java) ?: return null
            return runCatching { nm.javaClass.getMethod("canPostPromotedNotifications").invoke(nm) as Boolean }.getOrNull()
        }

        /** Ustawienia Live Updates dla eLektronu (Android 16+), a gdy ich brak - ustawienia powiadomień. */
        fun promotedSettingsIntent(context: Context): Intent =
            Intent("android.settings.MANAGE_APP_PROMOTED_NOTIFICATIONS")
                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        /** Zgoda na powiadomienia i włączony kanał "Na żywo". */
        fun canPost(context: Context): Boolean {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
            if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
            return Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                context.getSystemService(NotificationManager::class.java)
                    ?.getNotificationChannel(LocalNotificationSink.CHANNEL_LIVE)?.importance != NotificationManager.IMPORTANCE_NONE
        }
    }
}
