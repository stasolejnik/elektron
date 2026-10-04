package pl.zse.bydgoszcz.elektron.domain.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

object TimetableShare {
    private val dateFormat = DateTimeFormatter.ofPattern("EEEE, d.MM.yyyy", Locale("pl", "PL"))
    private val shortDateFormat = DateTimeFormatter.ofPattern("d.MM.yyyy")

    fun text(date: LocalDate, lessons: List<Lesson>, styles: Map<String, SubjectStyle> = emptyMap()): String {
        val day = lessons.filter { it.date == date }.sortedBy { it.number }
        return buildString {
            append("Plan dnia")
            day.firstOrNull()?.className?.let { append(" · $it") }
            append("\n${date.format(dateFormat)}\n")
            appendLessons(day, styles)
        }.trimEnd()
    }

    fun weekText(anchor: LocalDate, lessons: List<Lesson>, styles: Map<String, SubjectStyle> = emptyMap()): String {
        val monday = anchor.with(DayOfWeek.MONDAY)
        val week = lessons.filter { it.date >= monday && it.date <= monday.plusDays(4) }
            .sortedWith(compareBy({ it.date }, { it.number }))
        return buildString {
            append("Plan tygodnia")
            week.firstOrNull()?.className?.let { append(" · $it") }
            append("\n${monday.format(shortDateFormat)}–${monday.plusDays(4).format(shortDateFormat)}\n")
            (0L..4L).forEach { offset ->
                val date = monday.plusDays(offset)
                append("\n${date.format(dateFormat)}\n")
                appendLessons(week.filter { it.date == date }, styles)
            }
        }.trimEnd()
    }

    private fun StringBuilder.appendLessons(lessons: List<Lesson>, styles: Map<String, SubjectStyle>) {
        if (lessons.isEmpty()) append("\nBrak zapisanych lekcji w tym dniu.\n")
        lessons.forEach { lesson ->
            append("\n${lesson.number}. ${lesson.timeFrom}–${lesson.timeTo}\n")
            lesson.groups.forEach { group ->
                append(SubjectStyles.displayName(group.subject, styles) ?: "Lekcja")
                LessonGroups.parse(group.subject)?.label?.let { append(" · ${LessonGroups.displayLabel(it)}") }
                group.room?.let { append(" · s. $it") }
                (group.teacherFullName ?: group.teacherCode ?: JointGroups.describe(group.classRef, lesson.className))
                    ?.let { append(" · $it") }
                append('\n')
            }
            lesson.substitution?.let { sub ->
                append("Zmiana: ${SubstitutionDisplay.headline(sub)}")
                SubstitutionDisplay.place(sub)?.let { append(" · $it") }
                append('\n')
                SubstitutionDisplay.notes(sub)?.let { append("$it\n") }
            }
            lesson.note?.let { append("$it\n") }
        }
    }
}
