package pl.zse.bydgoszcz.elektron.widget

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import pl.zse.bydgoszcz.elektron.domain.model.Lesson
import pl.zse.bydgoszcz.elektron.domain.model.LessonGroups
import pl.zse.bydgoszcz.elektron.domain.repository.SettingsRepository
import pl.zse.bydgoszcz.elektron.domain.repository.TimetableRepository
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

/** Dostęp do repozytoriów z widżetów (Glance nie wspiera wstrzykiwania przez konstruktor). */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun settings(): SettingsRepository
    fun timetable(): TimetableRepository
    fun widgetUpdater(): WidgetUpdater
}

/** Lekcja przygotowana do wyświetlenia w widżecie. */
data class WidgetLesson(
    val number: Int,
    val title: String,
    val detail: String?,
    val room: String?,
    val timeFrom: LocalTime,
    val timeTo: LocalTime,
    val isSubstitution: Boolean
) {
    val timeRange: String get() = "$timeFrom–$timeTo"
}

sealed interface WidgetState {
    /** Nie wybrano klasy — widżet zaprasza do otwarcia aplikacji. */
    data object NoClass : WidgetState

    /** Brak lekcji w najbliższym tygodniu (np. wakacje albo plan jeszcze nie pobrany). */
    data class NoLessons(val className: String?) : WidgetState

    data class Ready(
        val className: String?,
        /** "Dziś", "Jutro" albo nazwa dnia tygodnia. */
        val dayLabel: String,
        /** Wszystkie lekcje wyświetlanego dnia (po filtrze grup). */
        val lessons: List<WidgetLesson>,
        /** Indeks lekcji trwającej lub najbliższej w [lessons]. */
        val focusIndex: Int,
        /** true = lekcja [focusIndex] właśnie trwa. */
        val focusIsNow: Boolean,
        /** true = wyświetlany dzień to dziś (minione lekcje można przygaszać). */
        val isToday: Boolean,
        /** Kiedy stan widżetu się zmieni (dzwonek / północ) — do zaplanowania odświeżenia. */
        val nextChangeAt: LocalDateTime
    ) : WidgetState {
        val focus: WidgetLesson get() = lessons[focusIndex]
        val following: WidgetLesson? get() = lessons.getOrNull(focusIndex + 1)
    }
}

object WidgetDataLoader {

    private val PL = Locale("pl", "PL")

    fun entryPoint(context: Context): WidgetEntryPoint =
        EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)

    suspend fun load(context: Context): WidgetState {
        val ep = entryPoint(context)
        val classId = ep.settings().selectedClassId.first() ?: return WidgetState.NoClass
        val groups = ep.settings().groupSelections(classId).first()
        val today = LocalDate.now()
        val now = LocalTime.now()
        val lessons = runCatching {
            LessonGroups.filter(ep.timetable().getLessonsOnce(classId, today, today.plusDays(7)), groups)
        }.getOrDefault(emptyList())
        val className = lessons.firstOrNull()?.className

        // Dzień do pokazania: dziś, jeśli zostały jeszcze lekcje; inaczej najbliższy dzień z lekcjami.
        val byDay = lessons.groupBy { it.date }.toSortedMap()
        val todayRemaining = byDay[today].orEmpty().any { it.timeTo > now }
        val day = if (todayRemaining) today else byDay.keys.firstOrNull { it > today }
            ?: return WidgetState.NoLessons(className)

        val dayLessons = byDay.getValue(day).sortedBy { it.number }
        val isToday = day == today
        val focusIndex = if (isToday) dayLessons.indexOfFirst { it.timeTo > now }.coerceAtLeast(0) else 0
        val focusLesson = dayLessons[focusIndex]
        val focusIsNow = isToday && now >= focusLesson.timeFrom

        val nextChangeAt = when {
            !isToday -> today.plusDays(1).atTime(0, 1)           // "Jutro" -> "Dziś" o północy
            focusIsNow -> day.atTime(focusLesson.timeTo)          // koniec trwającej lekcji
            else -> day.atTime(focusLesson.timeFrom)              // początek najbliższej
        }

        return WidgetState.Ready(
            className = className,
            dayLabel = dayLabel(day, today),
            lessons = dayLessons.map(::toWidgetLesson),
            focusIndex = focusIndex,
            focusIsNow = focusIsNow,
            isToday = isToday,
            nextChangeAt = nextChangeAt
        )
    }

    private fun dayLabel(day: LocalDate, today: LocalDate): String = when (day) {
        today -> "Dziś"
        today.plusDays(1) -> "Jutro"
        else -> day.dayOfWeek.getDisplayName(TextStyle.FULL, PL).replaceFirstChar { it.titlecase(PL) }
    }

    private fun toWidgetLesson(l: Lesson): WidgetLesson {
        val sub = l.substitution
        if (sub != null) {
            return WidgetLesson(
                number = l.number,
                title = sub.roomOrInfo.ifBlank { "Zastępstwo" },
                detail = "Zastępstwo · ${sub.substituteTeacher ?: "bez zastępcy"}",
                room = null,
                timeFrom = l.timeFrom, timeTo = l.timeTo,
                isSubstitution = true
            )
        }
        // Nazwa bez sufiksu grupy ("zaj.prakt-2/3" -> "zaj.prakt") — grupy są już odfiltrowane.
        val title = l.groups.mapNotNull { g -> LessonGroups.parse(g.subject)?.base }
            .distinct().joinToString(" / ").ifBlank { l.note ?: "Lekcja" }
        val teacher = l.groups.firstOrNull()?.let { it.teacherFullName ?: it.teacherCode }
        return WidgetLesson(
            number = l.number,
            title = title,
            detail = teacher,
            room = l.groups.mapNotNull { it.room }.distinct().joinToString(", ").ifBlank { null },
            timeFrom = l.timeFrom, timeTo = l.timeTo,
            isSubstitution = false
        )
    }
}
