package pl.zse.bydgoszcz.elektron.work

import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.first
import pl.zse.bydgoszcz.elektron.domain.model.Announcement
import pl.zse.bydgoszcz.elektron.domain.model.AnnouncementSource
import pl.zse.bydgoszcz.elektron.domain.model.LessonGroups
import pl.zse.bydgoszcz.elektron.domain.model.Substitution
import pl.zse.bydgoszcz.elektron.domain.repository.AnnouncementsRepository
import pl.zse.bydgoszcz.elektron.domain.repository.NotificationsRepository
import pl.zse.bydgoszcz.elektron.domain.repository.SettingsRepository
import pl.zse.bydgoszcz.elektron.domain.repository.SubstitutionsRepository
import pl.zse.bydgoszcz.elektron.domain.repository.TimetableRepository
import pl.zse.bydgoszcz.elektron.widget.WidgetUpdater
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tryb dewelopera: symulacje do sprawdzania aplikacji bez
 * czekania na prawdziwe dane ze szkoły. Symulowane wpisy są oznaczone (id "dev|" / "dev_ann_")
 * i istnieją tylko na tym telefonie - usuwa je "Usuń symulacje" (ogłoszenia także najbliższy sync).
 */
@Singleton
class DeveloperTools @Inject constructor(
    private val settings: SettingsRepository,
    private val timetableRepo: TimetableRepository,
    private val substitutionsRepo: SubstitutionsRepository,
    private val announcementsRepo: AnnouncementsRepository,
    private val sink: NotificationSink,
    private val widgetUpdater: WidgetUpdater,
    private val notificationsRepo: NotificationsRepository
) {

    /**
     * Zastępstwo (albo zwolnienie, [freed]) na najbliższej lekcji użytkownika: trafia do planu,
     * zakładki Zastępstwa, strony głównej i widżetów oraz wysyła powiadomienie.
     */
    suspend fun simulateSubstitution(freed: Boolean): String {
        val classId = settings.selectedClassId.first() ?: return "Najpierw wybierz klasę."
        val short = timetableRepo.observeClasses().first().firstOrNull { it.id == classId }?.shortName
            ?: return "Brak listy klas - odśwież dane."
        val now = LocalDateTime.now()
        val today = now.toLocalDate()
        val lessons = LessonGroups.filter(
            timetableRepo.getLessonsOnce(classId, today, today.plusDays(7)),
            settings.groupSelections(classId).first()
        )
        val target = lessons.filter { LocalDateTime.of(it.date, it.timeTo) > now }
            .minWithOrNull(compareBy({ it.date }, { it.timeFrom }))
            ?: return "Brak nadchodzących lekcji w planie."
        val group = target.groups.firstOrNull()
        val sub = Substitution(
            id = "dev|${System.currentTimeMillis()}|${target.number}",
            date = target.date,
            lessonNumber = target.number,
            classShortName = short,
            groupNumber = null,
            roomOrInfo = if (freed) "Uczniowie zwolnieni do domu" else (group?.room ?: "108"),
            substituteTeacher = if (freed) null else "J. Testowy",
            notes = "Symulacja - tryb dewelopera",
            originalTeacher = group?.teacherFullName ?: group?.teacherCode ?: "Tryb dewelopera",
            originalSubject = group?.subject
        )
        substitutionsRepo.upsertOne(sub)
        // Do "widzianych" (ta sama blokada co SyncWorker) - inaczej najbliższy sync uznałby
        // symulację za nowe zastępstwo i powiadomił drugi raz.
        notificationsRepo.withNotifyLock {
            notificationsRepo.setSeenSubstitutionIds(notificationsRepo.getSeenSubstitutionIds() + sub.id)
            sink.postSubstitution(sub, sub.originalSubject)
        }
        widgetUpdater.requestUpdate()
        val day = target.date.format(DateTimeFormatter.ofPattern("dd.MM"))
        return if (freed) "Symulowane zwolnienie: $day, ${target.number}. lekcja."
            else "Symulowane zastępstwo: $day, ${target.number}. lekcja."
    }

    /** Ogłoszenie testowe na górze listy ogłoszeń + powiadomienie. */
    suspend fun simulateAnnouncement(): String {
        val ann = Announcement(
            id = "dev_ann_${System.currentTimeMillis()}",
            title = "Symulowane ogłoszenie (tryb dewelopera)",
            url = "https://zse.bydgoszcz.pl/",
            publishedAt = Instant.now(),
            excerpt = "To ogłoszenie testowe z trybu dewelopera eLektronu. Zniknie przy najbliższej synchronizacji.",
            coverImageUrl = null,
            fullHtml = "<p>To ogłoszenie testowe z trybu dewelopera eLektronu.</p>",
            source = AnnouncementSource.RSS_NEWS
        )
        announcementsRepo.upsertOne(ann)
        notificationsRepo.withNotifyLock {
            notificationsRepo.setSeenAnnouncementIds(notificationsRepo.getSeenAnnouncementIds() + ann.id)
            sink.postAnnouncement(ann)
        }
        return "Dodano symulowane ogłoszenie."
    }

    suspend fun clearSimulations(): String {
        val subs = substitutionsRepo.deleteSimulated()
        val anns = announcementsRepo.deleteSimulated()
        widgetUpdater.requestUpdate()
        return if (subs + anns == 0) "Brak symulacji do usunięcia."
            else "Usunięto symulacje: zastępstwa $subs, ogłoszenia $anns."
    }

    /**
     * Awaria aplikacji: wyjątek na wątku głównym - zadziała zwykła obsługa awarii, a po ponownym
     * uruchomieniu pojawi się okno raportu (kopiowanie, e-mail, GitHub).
     */
    fun crash() {
        Handler(Looper.getMainLooper()).postDelayed({
            throw IllegalStateException("Symulowana awaria aplikacji (tryb dewelopera) - ${LocalDate.now()}")
        }, 300)
    }
}
