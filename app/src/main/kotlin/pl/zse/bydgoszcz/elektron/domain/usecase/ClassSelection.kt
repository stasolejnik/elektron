package pl.zse.bydgoszcz.elektron.domain.usecase

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pl.zse.bydgoszcz.elektron.domain.repository.AnnouncementsRepository
import pl.zse.bydgoszcz.elektron.domain.repository.NotificationsRepository
import pl.zse.bydgoszcz.elektron.domain.repository.SettingsRepository
import pl.zse.bydgoszcz.elektron.domain.repository.SubstitutionsRepository
import pl.zse.bydgoszcz.elektron.domain.repository.TimetableRepository
import pl.zse.bydgoszcz.elektron.domain.model.SyncErrors
import pl.zse.bydgoszcz.elektron.domain.model.SyncOutcome
import pl.zse.bydgoszcz.elektron.widget.WidgetUpdater
import pl.zse.bydgoszcz.elektron.work.LessonReminderScheduler
import java.util.concurrent.ConcurrentHashMap
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wybór klasy + pierwszy sync jej danych.
 *
 * Działa w zasięgu całej aplikacji, a nie ViewModelu ekranu. Dawniej sync po zmianie klasy
 * żył w viewModelScope Ustawień — zaraz po zmianie klasy appka przełącza się na ekran grup,
 * a anulowany scope zostawiał flagę "trwa sync" na zawsze (sprzątanie w finally też było
 * anulowane). Ekran grup kręcił się wtedy bez końca.
 *
 * Wcześniej ten sam kod był skopiowany w SetupViewModel i SettingsViewModel.
 */
@Singleton
class ClassSelection @Inject constructor(
    private val settings: SettingsRepository,
    private val timetableRepo: TimetableRepository,
    private val substitutionsRepo: SubstitutionsRepository,
    private val announcementsRepo: AnnouncementsRepository,
    private val notificationsRepo: NotificationsRepository,
    private val widgetUpdater: WidgetUpdater,
    private val reminders: LessonReminderScheduler,
    private val db: pl.zse.bydgoszcz.elektron.data.local.AppDatabase
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    /** Wybiera klasę i synchronizuje jej dane. Szybka ponowna zmiana anuluje poprzedni sync. */
    @Synchronized
    fun select(classId: String) {
        val previous = job
        job = scope.launch {
            // Czekamy na pełne zakończenie poprzedniego (łącznie z finally), żeby jego
            // "pending = false" nie nadpisało "pending = true" nowego syncu.
            previous?.cancelAndJoin()
            run(classId)
        }
    }

    /**
     * Czyści lokalną bazę (cache ze strony szkoły + stan synchronizacji) i pobiera dane
     * od nowa. Ustawienia (DataStore: klasa, grupy, motyw) zostają. Wyczyszczony stan
     * synchronizacji oznacza, że pierwszy sync tylko go odbuduje — bez lawiny powiadomień.
     */
    @Synchronized
    fun resetCacheAndResync(classId: String) {
        val previous = job
        job = scope.launch {
            previous?.cancelAndJoin()
            db.clearAllTables()
            timetableRepo.syncSidebar()
            run(classId)
        }
    }

    private suspend fun run(id: String) {
        // Najpierw "trwa sync", potem klasa — ekran grup (pokazywany po zmianie klasy)
        // nie może zobaczyć chwilowego stanu "plan nie pobrany".
        notificationsRepo.clearLoaded()
        notificationsRepo.setInitialSyncPending(true)
        notificationsRepo.setLastSyncError(null)
        settings.setSelectedClassId(id)
        try {
            val failures = ConcurrentHashMap<String, Throwable>()
            val succeeded = ConcurrentHashMap.newKeySet<String>()
            coroutineScope {
                launch {
                    timetableRepo.syncTimetable(id, LocalDate.now())
                        .onSuccess { succeeded += SyncOutcome.TIMETABLE; notificationsRepo.markLoaded("timetable") }
                        .onFailure { Log.w(TAG, "timetable FAILED", it); failures[SyncOutcome.TIMETABLE] = it }
                }
                launch {
                    substitutionsRepo.syncAll()
                        .onSuccess { succeeded += SyncOutcome.SUBSTITUTIONS; notificationsRepo.markLoaded("subs") }
                        .onFailure { Log.w(TAG, "subs FAILED", it); failures[SyncOutcome.SUBSTITUTIONS] = it }
                }
                launch {
                    announcementsRepo.syncAll()
                        .onSuccess { notificationsRepo.markLoaded("anns") }
                        .onFailure { Log.w(TAG, "anns FAILED", it) }
                }
            }
            // Komunikat po polsku (dawniej surowe e.message, np. "Plan: Unable to resolve host").
            notificationsRepo.setLastSyncError(SyncOutcome.errorMessage(failures))
            if (SyncOutcome.freshDataLoaded(succeeded)) notificationsRepo.setLastSyncAt(Instant.now())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "sync po wyborze klasy nie powiódł się", e)
            notificationsRepo.setLastSyncError(SyncErrors.userMessage(e))
        } finally {
            // NonCancellable: po anulowaniu każde wywołanie suspend rzuca od razu wyjątek,
            // więc bez tego flaga zostawała na "true".
            withContext(NonCancellable) {
                notificationsRepo.setInitialSyncPending(false)
                // Przypomnienia dla NOWEJ klasy: dopiero teraz jej plan jest w bazie. Dawniej
                // przeliczenie szło tylko przy zmianie klasy (planu jeszcze nie było - alarm
                // kasowany) i do najbliższego syncu w tle przypomnień nie było wcale.
                reminders.reschedule()
            }
            widgetUpdater.requestUpdate()
        }
    }

    companion object { private const val TAG = "ClassSelection" }
}
