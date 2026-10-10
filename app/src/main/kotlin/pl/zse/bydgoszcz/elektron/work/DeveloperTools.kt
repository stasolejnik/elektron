package pl.zse.bydgoszcz.elektron.work

import pl.zse.bydgoszcz.elektron.domain.util.AppClock

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

    /** Treść symulowanego zastępstwa: własna albo skopiowana z zapisanego (także minionego) wpisu. */
    data class SubstitutionDraft(val substituteTeacher: String?, val roomOrInfo: String?, val notes: String?) {
        companion object {
            /** Zwolnienie z lekcji (bez zastępcy). */
            val FREED = SubstitutionDraft(null, "Uczniowie zwolnieni do domu", null)
        }
    }

    /**
     * Zastępstwo na najbliższej lekcji użytkownika (według zegara aplikacji, także symulowanego):
     * trafia do planu, zakładki Zastępstwa, strony głównej i widżetów oraz wysyła powiadomienie.
     */
    suspend fun simulateSubstitution(draft: SubstitutionDraft): String {
        val classId = settings.selectedClassId.first() ?: return "Najpierw wybierz klasę."
        val short = timetableRepo.observeClasses().first().firstOrNull { it.id == classId }?.shortName
            ?: return "Brak listy klas - odśwież dane."
        val now = AppClock.now()
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
            roomOrInfo = draft.roomOrInfo?.takeIf { it.isNotBlank() } ?: group?.room ?: "108",
            substituteTeacher = draft.substituteTeacher?.takeIf { it.isNotBlank() },
            notes = draft.notes?.takeIf { it.isNotBlank() },
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
        return "Symulowane zastępstwo: $day, ${target.number}. lekcja."
    }

    /** Zastępstwa wybranej klasy z ostatnich 60 dni (także minione) - wzory do symulacji. */
    suspend fun pastSubstitutions(): List<Pair<SubstitutionDraft, String>> {
        val classId = settings.selectedClassId.first() ?: return emptyList()
        val short = timetableRepo.observeClasses().first().firstOrNull { it.id == classId }?.shortName ?: return emptyList()
        return substitutionsRepo.getAllFrom(AppClock.today().minusDays(60))
            .filter { it.classShortName.equals(short, ignoreCase = true) && !it.id.startsWith("dev|") }
            .sortedWith(compareByDescending<Substitution> { it.date }.thenBy { it.lessonNumber })
            .take(40)
            .map { sub ->
                SubstitutionDraft(sub.substituteTeacher, sub.roomOrInfo, sub.notes) to
                    "${sub.date.format(DateTimeFormatter.ofPattern("dd.MM"))}, ${sub.lessonNumber}. lekcja · ${sub.substituteTeacher ?: sub.roomOrInfo ?: sub.originalTeacher}"
            }
    }

    /** Ogłoszenie testowe na górze listy ogłoszeń + powiadomienie; [templateId] - kopia zapisanego ogłoszenia. */
    suspend fun simulateAnnouncement(title: String, text: String, templateId: String? = null): String {
        // Pełna treść tylko z pojedynczego odczytu (listy jej nie zawierają).
        val template = templateId?.let { announcementsRepo.observeById(it).first() }
        val ann = Announcement(
            id = "dev_ann_${System.currentTimeMillis()}",
            title = template?.title ?: title.ifBlank { "Symulowane ogłoszenie" },
            url = template?.url ?: "https://zse.bydgoszcz.pl/",
            publishedAt = AppClock.now().atZone(java.time.ZoneId.systemDefault()).toInstant(),
            excerpt = template?.excerpt ?: text.ifBlank { null },
            coverImageUrl = template?.coverImageUrl,
            // Ze znacznikiem bieżącej wersji - inaczej otwarcie pobierałoby "nowszą" treść ze strony szkoły.
            fullHtml = (template?.fullHtml ?: text.takeIf { it.isNotBlank() }?.let { "<p>" + org.jsoup.nodes.Entities.escape(it) + "</p>" })
                ?.let(pl.zse.bydgoszcz.elektron.data.mapper.ArticleContent::mark),
            source = AnnouncementSource.RSS_NEWS
        )
        announcementsRepo.upsertOne(ann)
        notificationsRepo.withNotifyLock {
            notificationsRepo.setSeenAnnouncementIds(notificationsRepo.getSeenAnnouncementIds() + ann.id)
            sink.postAnnouncement(ann)
        }
        return "Dodano symulowane ogłoszenie."
    }

    /** Zapisane ogłoszenia (najnowsze) - wzory do symulacji. */
    suspend fun pastAnnouncements(): List<Announcement> =
        announcementsRepo.getAll().filterNot { it.id.startsWith("dev_ann_") }.sortedByDescending { it.publishedAt }.take(30)

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
            throw IllegalStateException("Symulowana awaria aplikacji (tryb dewelopera) - ${AppClock.today()}")
        }, 300)
    }
}
