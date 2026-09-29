package pl.zse.bydgoszcz.elektron.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class LessonRemindersTest {

    private val mon = LocalDate.of(2026, 10, 5)
    private val tue = mon.plusDays(1)
    private fun lesson(date: LocalDate, n: Int, from: String, to: String) =
        Lesson("$date-$n", "o3", "1D", date, DayOfWeek.PONIEDZIALEK, n, LocalTime.parse(from), LocalTime.parse(to), emptyList(), null, null)

    private val lessons = listOf(
        lesson(mon, 1, "08:00", "08:45"),
        lesson(mon, 2, "08:50", "09:35"),     // przerwa 5 min
        lesson(mon, 3, "09:50", "10:35"),     // przerwa 15 min
        lesson(tue, 2, "08:50", "09:35")
    )
    private fun at(date: LocalDate, t: String) = LocalDateTime.of(date, LocalTime.parse(t))

    @Test fun offGivesNothing() {
        assertNull(LessonReminders.next(lessons, at(mon, "06:00"), ReminderSettings(ReminderMode.OFF)))
    }

    @Test fun firstLessonOnly() {
        val s = ReminderSettings(ReminderMode.FIRST, 10)
        assertEquals(at(mon, "07:50"), LessonReminders.next(lessons, at(mon, "06:00"), s)!!.at)
        // Po pierwszej lekcji poniedziałku - następne jest przed pierwszą lekcją wtorku.
        val r = LessonReminders.next(lessons, at(mon, "07:55"), s)!!
        assertEquals(at(tue, "08:40"), r.at)
        assertEquals("$tue-2", r.lesson.id)
    }

    @Test fun everyLessonMovesToBreakStart() {
        val s = ReminderSettings(ReminderMode.EVERY, 10)
        // Przerwa 5 min: przypomnienie o 2. lekcji na początku przerwy, nie w trakcie 1. lekcji.
        val r2 = LessonReminders.next(lessons, at(mon, "08:00"), s)!!
        assertEquals("$mon-2", r2.lesson.id)
        assertEquals(at(mon, "08:45"), r2.at)
        // Przerwa 15 min: normalnie 10 min przed.
        val r3 = LessonReminders.next(lessons, at(mon, "08:46"), s)!!
        assertEquals(at(mon, "09:40"), r3.at)
    }

    @Test fun nothingAfterLastLesson() {
        assertNull(LessonReminders.next(lessons, at(tue, "09:00"), ReminderSettings(ReminderMode.EVERY, 10)))
    }

    @Test fun titleCountsRealMinutes() {
        val start = at(mon, "08:00")
        assertEquals("Za 10 min: mat", LessonReminders.title("mat", start, at(mon, "07:50")))
        assertEquals("Za 3 min: mat", LessonReminders.title("mat", start, at(mon, "07:57:30")))
        assertEquals("Teraz: mat", LessonReminders.title("mat", start, at(mon, "08:00")))
    }

    @Test fun describeUsesCustomNameRoomAndTeacher() {
        val l = lesson(mon, 1, "08:00", "08:45").copy(
            groups = listOf(LessonGroup("wf-j1", "Ko", null, "J. Kowalski", "sg", null, null, null)))
        val (what, body) = LessonReminders.describe(l, mapOf("wf" to SubjectStyle(name = "WF")))
        assertEquals("WF", what)
        assertEquals("1. lekcja, 08:00–08:45 · s. sg · J. Kowalski", body)
    }
}
