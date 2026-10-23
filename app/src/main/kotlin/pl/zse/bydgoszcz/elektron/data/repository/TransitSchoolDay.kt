package pl.zse.bydgoszcz.elektron.data.repository

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import pl.zse.bydgoszcz.elektron.domain.model.Lesson
import pl.zse.bydgoszcz.elektron.domain.model.LessonGroups
import pl.zse.bydgoszcz.elektron.domain.repository.SettingsRepository
import pl.zse.bydgoszcz.elektron.domain.repository.TimetableRepository
import pl.zse.bydgoszcz.elektron.domain.util.AppClock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lekcje Twojej klasy i grup na najbliższy tydzień (z zastępstwami) - Przejazdy wybierają z nich
 * kierunek (do szkoły / ze szkoły) i lekcję, na którą liczy się dojazd.
 */
@Singleton
class TransitSchoolDay @Inject constructor(
    private val settings: SettingsRepository,
    private val timetable: TimetableRepository
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    // Także po zmianie symulowanego czasu (tryb dewelopera) - plan od nowego "dziś", nie z chwili startu.
    val lessons: Flow<List<Lesson>> = combine(settings.selectedClassId, AppClock.offsetMs) { classId, _ -> classId to AppClock.today() }
        .distinctUntilChanged().flatMapLatest { (classId, today) ->
        if (classId == null) flowOf(emptyList())
        else {
            combine(timetable.observeLessons(classId, today, today.plusDays(7)), settings.groupSelections(classId)) { lessons, groups ->
                LessonGroups.filter(lessons, groups)
            }
        }
    }.catch { emit(emptyList()) }
}
