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
import pl.zse.bydgoszcz.elektron.widget.WidgetUpdater
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
    private val widgetUpdater: WidgetUpdater
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

    private suspend fun run(id: String) {
        // Najpierw "trwa sync", potem klasa — ekran grup (pokazywany po zmianie klasy)
        // nie może zobaczyć chwilowego stanu "plan nie pobrany".
        notificationsRepo.clearLoaded()
        notificationsRepo.setInitialSyncPending(true)
        notificationsRepo.setLastSyncError(null)
        settings.setSelectedClassId(id)
        try {
            coroutineScope {
                launch {
                    timetableRepo.syncTimetable(id, LocalDate.now())
                        .onSuccess { notificationsRepo.markLoaded("timetable") }
                        .onFailure {
                            Log.w(TAG, "timetable FAILED", it)
                            notificationsRepo.setLastSyncError("Plan: ${it.message}")
                        }
                }
                launch {
                    substitutionsRepo.syncAll()
                        .onSuccess { notificationsRepo.markLoaded("subs") }
                        .onFailure { Log.w(TAG, "subs FAILED", it) }
                }
                launch {
                    announcementsRepo.syncAll()
                        .onSuccess { notificationsRepo.markLoaded("anns") }
                        .onFailure { Log.w(TAG, "anns FAILED", it) }
                }
            }
            notificationsRepo.setLastSyncAt(Instant.now())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "sync po wyborze klasy nie powiódł się", e)
            notificationsRepo.setLastSyncError(e.message ?: "Błąd synchronizacji")
        } finally {
            // NonCancellable: po anulowaniu każde wywołanie suspend rzuca od razu wyjątek,
            // więc bez tego flaga zostawała na "true".
            withContext(NonCancellable) { notificationsRepo.setInitialSyncPending(false) }
            widgetUpdater.requestUpdate()
        }
    }

    companion object { private const val TAG = "ClassSelection" }
}
