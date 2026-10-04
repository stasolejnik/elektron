package pl.zse.bydgoszcz.elektron.domain.model

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class LessonNoteTest {
    private val day = LocalDate.of(2026, 10, 6)
    private fun lesson(n: Int = 1) = Lesson("l$n", "o3", "1D", day, DayOfWeek.WTOREK,
        n, LocalTime.of(8, 0), LocalTime.of(8, 45), listOf(LessonGroup("mat", null, null, null, "211", null, null, null)), null, null)
    private fun note(created: LocalDateTime = day.minusDays(2).atTime(12, 0)) = LessonNote("o3", day, 1, "mat", "Powtórzyć", created.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())

    @Test fun identityBelongsToClassDateAndLessonNumberInsteadOfWeeklySubject() {
        assertEquals(note().key, LessonNote.key(lesson()))
        assertNotEquals(note().key, LessonNote.key(lesson().copy(date = day.plusWeeks(1))))
        assertNotEquals(note().key, LessonNote.key(lesson().copy(classId = "o4")))
        assertEquals(note().key, LessonNote.key(lesson().copy(id = "changed", groups = emptyList())))
    }
    @Test fun regularReminderIsPreviousEvening() {
        assertEquals(day.minusDays(1).atTime(18, 0), NoteReminders.next(listOf(note()), listOf(lesson()), day.minusDays(2).atStartOfDay())!!.at)
    }
    @Test fun lateNoteRemindsOneHourBeforeLesson() {
        assertEquals(day.atTime(7, 0), NoteReminders.plannedAt(note(day.minusDays(1).atTime(23, 0)), lesson()))
    }
    @Test fun lastMinuteNoteHasStableTargetAndMissedReminderCanRunImmediately() {
        val n = note(day.atTime(7, 30))
        assertEquals(day.atTime(7, 31), NoteReminders.plannedAt(n, lesson()))
        val now = day.atTime(7, 32)
        assertEquals(now, NoteReminders.next(listOf(n), listOf(lesson()), now)!!.at)
    }
    @Test fun skipsPastNotifiedMissingOtherClassesAndCanceledLessons() {
        val now = day.atTime(8, 0)
        assertFalse(LessonNote.canEdit(lesson(), now))
        assertNull(NoteReminders.next(listOf(note()), listOf(lesson()), now))
        assertNull(NoteReminders.next(listOf(note().copy(reminded = true)), listOf(lesson()), day.minusDays(1).atStartOfDay()))
        assertNull(NoteReminders.next(listOf(note().copy(classId = "o4")), listOf(lesson()), day.minusDays(1).atStartOfDay()))
        assertNull(NoteReminders.next(listOf(note()), emptyList(), day.minusDays(1).atStartOfDay()))
        val sub = Substitution("s", day, 1, "1D", null, "Uczniowie zwolnieni", null, null, "Kowalski")
        assertNull(NoteReminders.next(listOf(note()), listOf(lesson().copy(substitution = sub)), day.minusDays(1).atStartOfDay()))
    }
    @Test fun noteForOneGroupDoesNotRemindAfterSwitchToAnotherGroupOfSameSubject() {
        val n = note().copy(subject = "ang-1/2")
        val other = lesson().copy(groups = lesson().groups.map { it.copy(subject = "ang-2/2") })
        assertFalse(NoteReminders.matchesGroups(n, other))
        assertNull(NoteReminders.next(listOf(n), listOf(other), day.minusDays(2).atStartOfDay()))
        val own = other.copy(groups = other.groups.map { it.copy(subject = "ang-1/2") })
        assertTrue(NoteReminders.matchesGroups(n, own))
        assertTrue(NoteReminders.matchesGroups(n.copy(subject = "ang-1/2 / ang-2/2"), other))
        // Zmiana przedmiotu w tym terminie zachowuje notatkę; UI pokazuje ostrzeżenie.
        assertTrue(NoteReminders.matchesGroups(n, lesson()))
    }
    @Test fun configurablePreviousDayTimeIsUsed() {
        val settings = NoteReminderSettings(time = LocalTime.of(20, 35))
        assertEquals(day.minusDays(1).atTime(20, 35), NoteReminders.plannedAt(note(), lesson(), settings))
    }
    @Test fun eachLeadTimeIsMeasuredFromLessonStartWithoutPreviousEvening() {
        NoteReminderSettings.MINUTE_OPTIONS.forEach { minutes ->
            val settings = NoteReminderSettings(previousDay = false, minutesBefore = minutes)
            assertEquals(day.atTime(8, 0).minusMinutes(minutes.toLong()), NoteReminders.plannedAt(note(), lesson(), settings))
        }
    }
    @Test fun changingTimeDoesNotResendDeliveredNoteAndLateEditsUseFallback() {
        val settings = NoteReminderSettings(previousDay = false, minutesBefore = 30)
        assertNull(NoteReminders.next(listOf(note().copy(reminded = true)), listOf(lesson()), day.minusDays(2).atStartOfDay(), settings))
        assertEquals(day.atTime(7, 51), NoteReminders.plannedAt(note(day.atTime(7, 50)), lesson(), settings))
    }
    @Test fun closestNoteWinsRegardlessOfInputOrder() {
        val second = note().copy(date = day.plusDays(1))
        assertEquals(note().key, NoteReminders.next(listOf(second, note()), listOf(lesson().copy(date = day.plusDays(1)), lesson()), day.minusDays(2).atStartOfDay())!!.note.key)
    }
}
