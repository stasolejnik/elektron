package pl.zse.bydgoszcz.elektron.widget

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import pl.zse.bydgoszcz.elektron.domain.model.Lesson
import java.time.Duration
import pl.zse.bydgoszcz.elektron.domain.model.ClassNames
import pl.zse.bydgoszcz.elektron.domain.model.SubstitutionDisplay
import pl.zse.bydgoszcz.elektron.domain.model.SubstitutionRelevance
import pl.zse.bydgoszcz.elektron.domain.repository.SubstitutionsRepository
import pl.zse.bydgoszcz.elektron.domain.model.LessonGroups
import pl.zse.bydgoszcz.elektron.domain.repository.SettingsRepository
import pl.zse.bydgoszcz.elektron.domain.repository.ThemeMode
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
    fun substitutions(): SubstitutionsRepository
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
    val isSubstitution: Boolean,
    /** Zastępstwo: informacja zamiast sali i uwagi ze strony ("za ostatnią lekcję"). */
    val note: String? = null
) {
    val timeRange: String get() = "$timeFrom–$timeTo"
}

/** Zastępstwo przygotowane do widżetu. */
data class WidgetSubstitution(
    val dayLabel: String,
    val lessonNumber: Int,
    val title: String,
    val detail: String,
    val note: String? = null
)

sealed interface SubsWidgetState {
    data object NoClass : SubsWidgetState
    data class Ready(val className: String?, val items: List<WidgetSubstitution>) : SubsWidgetState
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
        /** Trwa przerwa: koniec poprzedniej lekcji (do paska postępu przerwy), inaczej null. */
        val breakFrom: LocalTime?,
        /** Kiedy stan widżetu się zmieni (dzwonek / północ). */
        val nextChangeAt: LocalDateTime,
        /**
         * Kiedy odświeżyć widżet: przy zmianie stanu, a w trakcie lekcji i na godzinę przed
         * zmianą co 5 min (pasek postępu i "Zostało X min" pozostają aktualne).
         */
        val refreshAt: LocalDateTime
    ) : WidgetState {
        val focus: WidgetLesson get() = lessons[focusIndex]
        val following: WidgetLesson? get() = lessons.getOrNull(focusIndex + 1)
    }
}

object WidgetDataLoader {

    private val PL = Locale("pl", "PL")

    fun entryPoint(context: Context): WidgetEntryPoint =
        EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)

    /** Kolory widżetów według ustawień aplikacji (motyw, kolory z tapety). */
    suspend fun palette(context: Context): WidgetPalette {
        val ep = entryPoint(context)
        val mode = runCatching { ep.settings().themeMode.first() }.getOrDefault(ThemeMode.SYSTEM)
        val dynamic = runCatching { ep.settings().dynamicColor.first() }.getOrDefault(false)
        return WidgetPalettes.create(context, mode, dynamic)
    }

    suspend fun load(context: Context): WidgetState {
        val ep = entryPoint(context)
        val classId = ep.settings().selectedClassId.first() ?: return WidgetState.NoClass
        val groups = ep.settings().groupSelections(classId).first()
        val nowDt = LocalDateTime.now()
        val today = nowDt.toLocalDate()
        val lessons = runCatching {
            LessonGroups.filter(ep.timetable().getLessonsOnce(classId, today, today.plusDays(7)), groups)
        }.getOrDefault(emptyList())
        return buildState(lessons, nowDt)
    }

    /**
     * Czysta logika stanu widżetu (bez bazy i Androida — testowalna): który dzień pokazać,
     * trwająca/najbliższa lekcja, przerwa, kiedy stan się zmieni i kiedy odświeżyć.
     * Jeden odczyt czasu ([nowDt]) — dawniej data i godzina były pobierane osobno.
     * [lessons] muszą być już po filtrze grup.
     */
    internal fun buildState(lessons: List<Lesson>, nowDt: LocalDateTime): WidgetState {
        val today = nowDt.toLocalDate()
        val now = nowDt.toLocalTime()
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

        val refreshAt = if (Duration.between(nowDt, nextChangeAt) <= Duration.ofMinutes(60))
            minOf(nextChangeAt, nowDt.plusMinutes(5)) else nextChangeAt

        return WidgetState.Ready(
            className = className,
            dayLabel = dayLabel(day, today),
            lessons = dayLessons.map(::toWidgetLesson),
            focusIndex = focusIndex,
            focusIsNow = focusIsNow,
            isToday = isToday,
            breakFrom = if (isToday && !focusIsNow && focusIndex > 0)
                dayLessons[focusIndex - 1].timeTo.takeIf { it <= now } else null,
            nextChangeAt = nextChangeAt,
            refreshAt = refreshAt
        )
    }

    /**
     * Nadchodzące zastępstwa dla klasy i grup użytkownika (do widżetu "Zastępstwa").
     * Dzisiejsze po zakończeniu lekcji znikają, jak w aplikacji.
     */
    suspend fun loadSubstitutions(context: Context): SubsWidgetState {
        val ep = entryPoint(context)
        val classId = ep.settings().selectedClassId.first() ?: return SubsWidgetState.NoClass
        val short = ep.timetable().observeClasses().first().firstOrNull { it.id == classId }?.shortName
            ?: return SubsWidgetState.NoClass
        val groups = ep.settings().groupSelections(classId).first()
        val today = LocalDate.now()
        val now = LocalTime.now()
        val lessons = runCatching { ep.timetable().getLessonsOnce(classId, today, today.plusDays(14)) }
            .getOrDefault(emptyList())
        val endByToday = lessons.filter { it.date == today }.associate { it.number to it.timeTo }
        val subs = runCatching { ep.substitutions().getAllFrom(today) }.getOrDefault(emptyList())
            .asSequence()
            .filter { SubstitutionRelevance.matchesClass(it, short) }
            .filter { LessonGroups.substitutionRelevant(it, lessons, groups) }
            .filter { it.date > today || endByToday[it.lessonNumber]?.let { end -> now < end } ?: true }
            .sortedWith(compareBy({ it.date }, { it.lessonNumber }))
            .map { s ->
                WidgetSubstitution(
                    dayLabel = dayLabel(s.date, today),
                    lessonNumber = s.lessonNumber,
                    title = SubstitutionDisplay.headline(s),
                    detail = SubstitutionDisplay.place(s) ?: "za ${s.originalTeacher}",
                    note = SubstitutionDisplay.notes(s)
                )
            }.toList()
        return SubsWidgetState.Ready(ClassNames.clean(short), subs)
    }

    private fun dayLabel(day: LocalDate, today: LocalDate): String = when (day) {
        today -> "Dziś"
        today.plusDays(1) -> "Jutro"
        else -> day.dayOfWeek.getDisplayName(TextStyle.FULL, PL).replaceFirstChar { it.titlecase(PL) }
    }

    private fun toWidgetLesson(l: Lesson): WidgetLesson {
        val sub = l.substitution
        if (sub != null) {
            // Na pierwszym planie nauczyciel zastępujący (nie sala) — jak w aplikacji.
            val hasSubstitute = !sub.substituteTeacher.isNullOrBlank()
            val info = sub.roomOrInfo.trim()
            val isRoom = hasSubstitute && SubstitutionDisplay.isRoom(info)
            return WidgetLesson(
                number = l.number,
                title = SubstitutionDisplay.headline(sub),
                detail = "Zastępstwo za ${sub.originalTeacher}",
                room = info.takeIf { isRoom },
                timeFrom = l.timeFrom, timeTo = l.timeTo,
                isSubstitution = true,
                note = listOfNotNull(info.takeIf { hasSubstitute && !isRoom && it.isNotEmpty() }, SubstitutionDisplay.notes(sub))
                    .joinToString(" · ").ifBlank { null }
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
