package pl.zse.bydgoszcz.elektron.domain.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Notatka należy do terminu lekcji w konkretnej klasie, niezależnie od zmian planu. */
data class LessonNote(
    val classId: String, val date: LocalDate, val number: Int,
    val subject: String, val text: String, val revision: Long,
    val reminded: Boolean = false
) {
    val key: String get() = key(classId, date, number)
    companion object {
        const val MAX_LENGTH = 2000
        fun key(classId: String, date: LocalDate, number: Int) = "$classId/${date.toEpochDay()}/$number"
        fun key(lesson: Lesson) = key(lesson.classId, lesson.date, lesson.number)
        fun subject(lesson: Lesson) = lesson.groups.mapNotNull { it.subject }.distinct().sorted().joinToString(" / ")
        fun canEdit(lesson: Lesson, now: LocalDateTime) = LocalDateTime.of(lesson.date, lesson.timeFrom) > now
    }
}

data class NoteReminderSettings(
    val previousDay: Boolean = true,
    val time: LocalTime = LocalTime.of(18, 0),
    val minutesBefore: Int = 60
) {
    init { require(minutesBefore in MINUTE_OPTIONS) }
    companion object { val MINUTE_OPTIONS = listOf(5, 10, 15, 30, 60, 120) }
}

object NoteReminders {
    data class Reminder(val note: LessonNote, val lesson: Lesson, val at: LocalDateTime, val plannedAt: LocalDateTime = at)
    fun matchesGroups(note: LessonNote, lesson: Lesson): Boolean {
        val original = note.subject.split(" / ").mapNotNull(LessonGroups::parse).filter { it.label != null }.groupBy { it.base }
        val current = lesson.groups.mapNotNull { LessonGroups.parse(it.subject) }
        return original.all { (base, groups) ->
            val sameSubject = current.filter { it.base == base }
            sameSubject.isEmpty() || sameSubject.any { shown -> groups.any { it.label == shown.label } }
        }
    }
    fun plannedAt(note: LessonNote, lesson: Lesson, settings: NoteReminderSettings = NoteReminderSettings()): LocalDateTime {
        val start = LocalDateTime.of(lesson.date, lesson.timeFrom)
        val created = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(note.revision), java.time.ZoneId.systemDefault())
        val evening = lesson.date.minusDays(1).atTime(settings.time)
        val before = start.minusMinutes(settings.minutesBefore.toLong())
        return when {
            settings.previousDay && created <= evening -> evening
            created <= before -> before
            else -> created.plusMinutes(1).coerceAtMost(start.minusSeconds(1))
        }
    }
    fun next(notes: List<LessonNote>, lessons: List<Lesson>, now: LocalDateTime, settings: NoteReminderSettings = NoteReminderSettings()): Reminder? {
        val byKey = lessons.associateBy(LessonNote::key)
        return notes.asSequence().filter { !it.reminded }.mapNotNull { note ->
            val lesson = byKey[note.key] ?: return@mapNotNull null
            if (!matchesGroups(note, lesson) || lesson.substitution?.let(SubstitutionDisplay::freesLesson) == true) return@mapNotNull null
            val start = LocalDateTime.of(lesson.date, lesson.timeFrom)
            if (start <= now) return@mapNotNull null
            val planned = plannedAt(note, lesson, settings)
            Reminder(note, lesson, planned.coerceAtLeast(now), planned)
        }.minWithOrNull(compareBy<Reminder> { it.at }.thenBy { it.note.date }.thenBy { it.note.number })
    }
}
