package pl.zse.bydgoszcz.elektron.work

import pl.zse.bydgoszcz.elektron.domain.model.SyncErrors
import java.time.LocalDateTime
import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import pl.zse.bydgoszcz.elektron.domain.model.LessonGroups
import pl.zse.bydgoszcz.elektron.domain.model.SubstitutionRelevance
import pl.zse.bydgoszcz.elektron.domain.repository.AnnouncementsRepository
import pl.zse.bydgoszcz.elektron.domain.repository.NotificationsRepository
import pl.zse.bydgoszcz.elektron.domain.repository.SettingsRepository
import pl.zse.bydgoszcz.elektron.domain.repository.SubstitutionsRepository
import pl.zse.bydgoszcz.elektron.domain.repository.TimetableRepository
import pl.zse.bydgoszcz.elektron.domain.model.SchoolPageChangedException
import pl.zse.bydgoszcz.elektron.domain.usecase.SyncAllUseCase
import pl.zse.bydgoszcz.elektron.widget.WidgetUpdater
import java.time.Instant
import java.time.LocalDate

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters,
    private val syncAll: SyncAllUseCase,
    private val settings: SettingsRepository,
    private val substitutionsRepo: SubstitutionsRepository,
    private val announcementsRepo: AnnouncementsRepository,
    private val notificationsRepo: NotificationsRepository,
    private val sink: NotificationSink,
    private val timetableRepo: TimetableRepository,
    private val widgetUpdater: WidgetUpdater,
    private val reminders: LessonReminderScheduler
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        notificationsRepo.setIsSyncing(true)
        try {
            val classId = settings.selectedClassId.first()
            val notifSubs = settings.notificationsSubstitutions.first()
            val notifAnns = settings.notificationsAnnouncements.first()

            val ok = syncAll(classId)
            if (ok == 0) {
                notificationsRepo.setLastSyncError("Nie udało się pobrać danych ze szkoły.")
                // Bug audytu #9: bez limitu Result.retry() ponawiał się w nieskończoność
                // przy dłuższej awarii strony szkoły. Po MAX_RETRY_ATTEMPTS oddajemy
                // kontrolę cyklicznemu harmonogramowi (za 15 min) zamiast dobijać się dalej.
                return if (runAttemptCount >= MAX_RETRY_ATTEMPTS) Result.failure() else Result.retry()
            }

            // Reszta danych się pobrała, ale plan ma nieznany układ - komunikat na stronie głównej.
            notificationsRepo.setLastSyncError(
                if (syncAll.pageChanged) SchoolPageChangedException.USER_MESSAGE else null
            )
            notificationsRepo.setLastSyncAt(Instant.now())

            // Blokada dzielona z ElektronFirebaseMessagingService — patrz komentarz przy
            // withNotifyLock. Bez niej push i ten cykliczny sync mogą zdublować powiadomienie.
            notificationsRepo.withNotifyLock {
                // Powiadamiamy tylko gdy mamy wiarygodny punkt odniesienia: po pierwszym
                // syncu klasy ORAZ po zbudowaniu zbioru "widzianych" w nowym formacie v2.
                val initialDone = notificationsRepo.isInitialSyncDone() && notificationsRepo.isSeenStoreReady()
                val seenSubs = notificationsRepo.getSeenSubstitutionIds()
                val seenAnns = notificationsRepo.getSeenAnnouncementIds()

                val currentSubs = substitutionsRepo.getAllFrom(LocalDate.now().minusDays(1))
                val currentAnns = announcementsRepo.getAll()
                val freshSubs = currentSubs.filter { it.id !in seenSubs }
                val freshAnns = currentAnns.filter { it.id !in seenAnns }

                if (initialDone) {
                    if (notifSubs && classId != null && freshSubs.isNotEmpty()) {
                        val short = timetableRepo.observeClasses().first()
                            .firstOrNull { it.id == classId }?.shortName
                        val forClass = if (short != null) {
                            freshSubs.filter { SubstitutionRelevance.matchesClass(it, short) }
                        } else emptyList()
                        // Grupy zajęciowe: pomijamy zastępstwa dla grupy, do której użytkownik nie należy.
                        val groups = settings.groupSelections(classId).first()
                        val lessonsForSubs = if (forClass.isEmpty()) emptyList() else runCatching {
                            timetableRepo.getLessonsOnce(classId, forClass.minOf { it.date }, forClass.maxOf { it.date })
                        }.getOrDefault(emptyList())
                        // Bez powiadomień o zastępstwach, których lekcja już minęła (np. wczorajsze
                        // albo poranne odczytane dopiero po południu - po poprawce parsera 0.6.4
                        // pojawiły się naraz wszystkie dotąd gubione wpisy).
                        val ends = SubstitutionRelevance.lessonEnds(lessonsForSubs)
                        val now = LocalDateTime.now()
                        val relevant = forClass
                            .filter { LessonGroups.substitutionRelevant(it, lessonsForSubs, groups) }
                            .filterNot { SubstitutionRelevance.isOver(it, now, ends) }
                        val visibleLessons = LessonGroups.filter(lessonsForSubs, groups)  // raz, nie w pętli
                        relevant.forEach { sub ->
                            val subject = visibleLessons
                                .firstOrNull { it.date == sub.date && it.number == sub.lessonNumber }
                                ?.groups?.firstOrNull()?.subject
                            sink.postSubstitution(sub, subject)
                        }
                    }
                    if (notifAnns && freshAnns.isNotEmpty()) {
                        freshAnns.take(5).forEach { ann ->
                            sink.postAnnouncement(ann)
                        }
                    }
                }

                notificationsRepo.setSeenSubstitutionIds(currentSubs.map { it.id }.toSet())
                notificationsRepo.setSeenAnnouncementIds(currentAnns.map { it.id }.toSet())
                if (!notificationsRepo.isInitialSyncDone()) notificationsRepo.setInitialSyncDone()
                if (!notificationsRepo.isSeenStoreReady()) notificationsRepo.markSeenStoreReady()
            }

            notificationsRepo.pruneHistory()
            return Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "SyncWorker wyjątek", e)
            notificationsRepo.setLastSyncError(SyncErrors.userMessage(e))
            return if (runAttemptCount >= MAX_RETRY_ATTEMPTS) Result.failure() else Result.retry()
        } finally {
            // NonCancellable: anulowany worker (np. utrata sieci) dawniej zostawiał
            // "Synchronizacja…" na Starcie, bo sprzątanie też było anulowane.
            withContext(NonCancellable) {
                notificationsRepo.setIsSyncing(false)
                notificationsRepo.setInitialSyncPending(false)
            }
            widgetUpdater.requestUpdate()
            // Przypomnienia przed lekcją: nowe zastępstwa/plan, a po restarcie telefonu
            // (system kasuje alarmy) ponowne ustawienie najpóźniej przy następnym syncu.
            reminders.requestReschedule()
        }
    }

    companion object {
        private const val TAG = "SyncWorker"
        private const val MAX_RETRY_ATTEMPTS = 5
    }
}
