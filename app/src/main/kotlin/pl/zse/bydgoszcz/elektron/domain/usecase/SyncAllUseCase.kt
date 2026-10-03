package pl.zse.bydgoszcz.elektron.domain.usecase

import pl.zse.bydgoszcz.elektron.domain.model.SyncOutcome
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pl.zse.bydgoszcz.elektron.domain.model.SchoolPageChangedException
import pl.zse.bydgoszcz.elektron.domain.repository.AnnouncementsRepository
import pl.zse.bydgoszcz.elektron.domain.repository.NotificationsRepository
import pl.zse.bydgoszcz.elektron.domain.repository.SubstitutionsRepository
import pl.zse.bydgoszcz.elektron.domain.repository.TimetableRepository
import java.time.LocalDate
import javax.inject.Inject

/**
 * Jeden sync = sidebar + plan oddziału + zastępstwa + RSS.
 * Odporny: jeśli jedno źródło padnie, pozostałe się zapiszą.
 *
 * Każde udane źródło jest oznaczane jako "loaded" — dawniej robiły to tylko
 * Setup/Ustawienia/Start, więc gdy pierwszy sync się nie udał, a później udał się
 * dopiero w tle (SyncWorker), Start wisiał na "Ładowanie…" w nieskończoność.
 *
 * @return ile źródeł zakończyło się sukcesem (0..4)
 */
class SyncAllUseCase @Inject constructor(
    private val timetableRepo: TimetableRepository,
    private val substitutionsRepo: SubstitutionsRepository,
    private val announcementsRepo: AnnouncementsRepository,
    private val notificationsRepo: NotificationsRepository
) {
    /** Czy w ostatnim przebiegu strona z planem miała nieznany układ (patrz SchoolPageChangedException). */
    var pageChanged: Boolean = false
        private set

    /** Źródła, które zawiodły w ostatnim przebiegu (klucze jak w SyncOutcome). */
    var failures: Map<String, Throwable> = emptyMap()
        private set

    /** Źródła, które się pobrały w ostatnim przebiegu (klucze jak w SyncOutcome). */
    var succeeded: Set<String> = emptySet()
        private set

    suspend operator fun invoke(classId: String?, anchorDate: LocalDate = LocalDate.now()): Int =
        withContext(Dispatchers.IO) {
            pageChanged = false
            val failed = mutableMapOf<String, Throwable>()
            val ok = mutableSetOf<String>()
            timetableRepo.syncSidebar()
                .onSuccess { ok += SyncOutcome.SIDEBAR }
                .onFailure { Log.w(TAG, "sidebar fail", it); failed[SyncOutcome.SIDEBAR] = it }
            if (classId != null) {
                timetableRepo.syncTimetable(classId, anchorDate)
                    .onSuccess { ok += SyncOutcome.TIMETABLE; notificationsRepo.markLoaded("timetable") }
                    .onFailure {
                        Log.w(TAG, "timetable fail", it)
                        failed[SyncOutcome.TIMETABLE] = it
                        if (it is SchoolPageChangedException) pageChanged = true
                    }
            }
            substitutionsRepo.syncAll()
                .onSuccess { ok += SyncOutcome.SUBSTITUTIONS; notificationsRepo.markLoaded("subs") }
                .onFailure { Log.w(TAG, "substitutions fail", it); failed[SyncOutcome.SUBSTITUTIONS] = it }
            announcementsRepo.syncAll()
                .onSuccess { ok += SyncOutcome.ANNOUNCEMENTS; notificationsRepo.markLoaded("anns") }
                .onFailure { Log.w(TAG, "announcements fail", it); failed[SyncOutcome.ANNOUNCEMENTS] = it }
            failures = failed
            succeeded = ok
            ok.size
        }

    companion object { private const val TAG = "SyncAllUseCase" }
}
