package pl.zse.bydgoszcz.elektron.work

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import pl.zse.bydgoszcz.elektron.domain.model.Announcement
import pl.zse.bydgoszcz.elektron.domain.model.AnnouncementSource
import pl.zse.bydgoszcz.elektron.domain.model.Substitution
import pl.zse.bydgoszcz.elektron.domain.repository.AnnouncementsRepository
import pl.zse.bydgoszcz.elektron.domain.repository.NotificationsRepository
import pl.zse.bydgoszcz.elektron.domain.repository.SettingsRepository
import pl.zse.bydgoszcz.elektron.domain.repository.SubstitutionsRepository
import pl.zse.bydgoszcz.elektron.widget.WidgetUpdater
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

/**
 * Funkcja #7: odbiornik pushy z zewnętrznego "watchera" (GitHub Actions + FCM Topics —
 * patrz PORADNIK_PUSH.md). Appka NIE ma własnego backendu — to jedyny element sieciowy,
 * który przychodzi z zewnątrz zamiast być przez appkę pobierany.
 *
 * Watcher publikuje na temat "elektron-subs-zse-bydgoszcz" / "elektron-anns-zse-bydgoszcz"
 * z pełnymi danymi w payloadzie (data message, nie "notification") — appka sama decyduje,
 * czy to jej dotyczy (klasa) i czy w ogóle pokazać powiadomienie (przełączniki w
 * Ustawieniach), tak samo jak przy zwykłym SyncWorker.
 *
 * Idempotentność: sprawdzamy "seen" set (pod tą samą blokadą co SyncWorker — patrz
 * NotificationsRepository.withNotifyLock) zanim pokażemy powiadomienie — jeśli periodyczny
 * SyncWorker (15 min) już wykrył to samo zastępstwo niezależnie, push go nie zduplikuje.
 */
@AndroidEntryPoint
class ElektronFirebaseMessagingService : FirebaseMessagingService() {

    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var substitutionsRepo: SubstitutionsRepository
    @Inject lateinit var announcementsRepo: AnnouncementsRepository
    @Inject lateinit var notificationsRepo: NotificationsRepository
    @Inject lateinit var timetableRepo: pl.zse.bydgoszcz.elektron.domain.repository.TimetableRepository
    @Inject lateinit var reminders: LessonReminderScheduler
    @Inject lateinit var sink: NotificationSink
    @Inject lateinit var widgetUpdater: WidgetUpdater
    @Inject lateinit var substitutionNotifier: SubstitutionNotifier

    // Google zaleca runBlocking (nie goAsync — to metoda BroadcastReceivera, nie tej klasy)
    // dla krótkiej pracy w onMessageReceived: to wywołanie leci na wątku w tle biblioteki
    // FCM, nie na głównym, więc blokowanie go jest bezpieczne. Nasza praca (kilka operacji
    // na Room + ewentualne powiadomienie) mieści się z zapasem w oknie ~20s, jakie ma
    // onMessageReceived — dłuższe zadania Google każe zlecać do WorkManagera.
    override fun onMessageReceived(message: RemoteMessage) = runBlocking(Dispatchers.IO) {
        val data = message.data
        val type = data["type"] ?: return@runBlocking
        try {
            when (type) {
                "substitution" -> handleSubstitution(data)
                "announcement" -> handleAnnouncement(data)
                else -> Log.w(TAG, "Nieznany typ push: $type")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Błąd przetwarzania push", e)
        }
    }

    private suspend fun handleSubstitution(data: Map<String, String>) {
        val id = data["id"] ?: return
        val date = runCatching { LocalDate.parse(data["date"]) }.getOrNull() ?: return
        val lessonNumber = data["lessonNumber"]?.toIntOrNull() ?: return
        val classShortName = data["classShortName"] ?: return
        val sub = Substitution(
            id = id,
            date = date,
            lessonNumber = lessonNumber,
            classShortName = classShortName,
            groupNumber = data["groupNumber"]?.toIntOrNull(),
            roomOrInfo = data["roomOrInfo"] ?: "",
            substituteTeacher = data["substituteTeacher"],
            notes = data["notes"],
            originalTeacher = data["originalTeacher"] ?: "",
            originalSubject = data["originalSubject"]
        )
        val previous = substitutionsRepo.getForClassAndDay(classShortName, date).firstOrNull { it.id == id }
        if (previous != sub) {
            substitutionsRepo.upsertOne(sub)
            val cid = settings.selectedClassId.first()
            val short = timetableRepo.observeClasses().first().firstOrNull { it.id == cid }?.shortName
            if (short != null && pl.zse.bydgoszcz.elektron.domain.model.SubstitutionRelevance.matchesClass(sub, short)) {
                reminders.reschedule()
                widgetUpdater.requestDurableUpdate()
            }
        }

        // Klasa, grupy, minione lekcje, "widziane" - ta sama logika co po synchronizacji.
        substitutionNotifier.notifyPushed(sub)
    }

    private suspend fun handleAnnouncement(data: Map<String, String>) {
        val id = data["id"] ?: return
        val title = data["title"] ?: return
        val url = data["url"] ?: return
        val publishedAt = runCatching { Instant.parse(data["publishedAt"]) }.getOrDefault(Instant.now())
        val source = runCatching { AnnouncementSource.valueOf(data["source"] ?: "") }.getOrDefault(AnnouncementSource.RSS_NEWS)
        val ann = Announcement(
            id = id, title = title, url = url, publishedAt = publishedAt,
            excerpt = data["excerpt"], coverImageUrl = data["coverImageUrl"],
            fullHtml = null, source = source
        )
        announcementsRepo.upsertOne(ann)

        if (!settings.notificationsAnnouncements.first()) return

        notificationsRepo.withNotifyLock {
            val seen = notificationsRepo.getSeenAnnouncementIds()
            if (id in seen) return@withNotifyLock
            sink.postAnnouncement(ann)
            notificationsRepo.setSeenAnnouncementIds(seen + id)
        }
    }

    companion object { private const val TAG = "ElektronFcmService" }
}
