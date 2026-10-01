package pl.zse.bydgoszcz.elektron.work

import java.time.LocalDateTime
import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import pl.zse.bydgoszcz.elektron.domain.model.Announcement
import pl.zse.bydgoszcz.elektron.domain.model.AnnouncementSource
import pl.zse.bydgoszcz.elektron.domain.model.LessonGroups
import pl.zse.bydgoszcz.elektron.domain.model.Substitution
import pl.zse.bydgoszcz.elektron.domain.model.SubstitutionRelevance
import pl.zse.bydgoszcz.elektron.domain.repository.AnnouncementsRepository
import pl.zse.bydgoszcz.elektron.domain.repository.NotificationsRepository
import pl.zse.bydgoszcz.elektron.domain.repository.SettingsRepository
import pl.zse.bydgoszcz.elektron.domain.repository.SubstitutionsRepository
import pl.zse.bydgoszcz.elektron.domain.repository.TimetableRepository
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
    @Inject lateinit var timetableRepo: TimetableRepository
    @Inject lateinit var substitutionsRepo: SubstitutionsRepository
    @Inject lateinit var announcementsRepo: AnnouncementsRepository
    @Inject lateinit var notificationsRepo: NotificationsRepository
    @Inject lateinit var sink: NotificationSink
    @Inject lateinit var widgetUpdater: WidgetUpdater

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
        substitutionsRepo.upsertOne(sub)
        widgetUpdater.requestUpdate() // zastępstwo może zmienić lekcję na widżecie

        val notifSubs = settings.notificationsSubstitutions.first()
        if (!notifSubs) return

        val classId = settings.selectedClassId.first() ?: return
        val short = timetableRepo.observeClasses().first().firstOrNull { it.id == classId }?.shortName ?: return
        if (!SubstitutionRelevance.matchesClass(sub, short)) return

        // Grupy zajęciowe: zastępstwo dla grupy, do której użytkownik nie należy, pomijamy.
        val groups = settings.groupSelections(classId).first()
        val lessonsThatDay = runCatching { timetableRepo.getLessonsOnce(classId, sub.date, sub.date) }
            .getOrDefault(emptyList())
        if (!LessonGroups.substitutionRelevant(sub, lessonsThatDay, groups)) return
        // Zastępstwo, którego lekcja już minęła - bez powiadomienia (SyncWorker oznaczy je jako widziane).
        if (SubstitutionRelevance.isOver(sub, LocalDateTime.now(), SubstitutionRelevance.lessonEnds(lessonsThatDay))) return

        // Ta sama blokada co SyncWorker — patrz dokumentacja withNotifyLock.
        notificationsRepo.withNotifyLock {
            val seen = notificationsRepo.getSeenSubstitutionIds()
            if (id in seen) return@withNotifyLock // SyncWorker już to obsłużył — bez duplikatu

            val subject = LessonGroups.filter(lessonsThatDay, groups)
                .firstOrNull { it.number == sub.lessonNumber }
                ?.groups?.firstOrNull()?.subject
            sink.postSubstitution(sub, subject)
            notificationsRepo.setSeenSubstitutionIds(seen + id)
        }
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
