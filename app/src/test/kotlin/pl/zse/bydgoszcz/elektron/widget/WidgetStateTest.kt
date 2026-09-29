package pl.zse.bydgoszcz.elektron.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.zse.bydgoszcz.elektron.domain.model.DayOfWeek
import pl.zse.bydgoszcz.elektron.domain.model.Lesson
import pl.zse.bydgoszcz.elektron.domain.model.LessonGroup
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Stan widżetów (i kafelka) w konkretnych chwilach tygodnia — bez zależności od zegara. */
class WidgetStateTest {

    private val friday = LocalDate.of(2026, 10, 2)          // piątek
    private val monday = LocalDate.of(2026, 10, 5)

    private fun lesson(date: LocalDate, n: Int, from: String, to: String, subject: String = "mat") =
        Lesson("$date-$n", "o3", "1D", date, DayOfWeek.PIATEK, n, LocalTime.parse(from), LocalTime.parse(to),
            listOf(LessonGroup(subject, "Ch", null, null, "105", null, null, null)), null, null)

    private val week = listOf(
        lesson(friday, 3, "09:50", "10:35"),
        lesson(friday, 4, "10:45", "11:30"),
        lesson(friday, 5, "11:40", "12:25"),
        lesson(monday, 1, "08:00", "08:45")
    )

    private fun at(date: LocalDate, time: String) = WidgetDataLoader.buildState(week, LocalDateTime.of(date, LocalTime.parse(time)))

    @Test
    fun duringLesson() {
        val st = at(friday, "11:00") as WidgetState.Ready
        assertEquals(4, st.focus.number)
        assertTrue(st.focusIsNow)
        assertNull(st.breakFrom)
        assertEquals(LocalDateTime.of(friday, LocalTime.of(11, 30)), st.nextChangeAt)
        assertEquals(LocalDateTime.of(friday, LocalTime.of(11, 5)), st.refreshAt)   // co 5 min w trakcie lekcji
    }

    @Test
    fun duringBreak() {
        val st = at(friday, "11:35") as WidgetState.Ready
        assertEquals(5, st.focus.number)
        assertFalse(st.focusIsNow)
        assertEquals(LocalTime.of(11, 30), st.breakFrom)
        assertEquals(LocalDateTime.of(friday, LocalTime.of(11, 40)), st.refreshAt)  // dzwonek wcześniej niż +5 min
    }

    @Test
    fun beforeFirstLessonIsNotBreak() {
        val st = at(friday, "09:00") as WidgetState.Ready
        assertEquals(3, st.focus.number)
        assertNull("przed pierwszą lekcją to nie przerwa", st.breakFrom)
    }

    @Test
    fun afterSchoolShowsNextSchoolDay() {
        val st = at(friday, "15:00") as WidgetState.Ready
        assertFalse(st.isToday)
        assertEquals(1, st.focus.number)
        assertEquals("Poniedziałek", st.dayLabel)
        assertEquals(LocalDateTime.of(friday.plusDays(1), LocalTime.of(0, 1)), st.nextChangeAt)
    }

    @Test
    fun sundayEveningShowsTomorrow() {
        val st = at(monday.minusDays(1), "20:00") as WidgetState.Ready
        assertEquals("Jutro", st.dayLabel)
    }

    @Test
    fun noLessonsInRange() {
        assertTrue(WidgetDataLoader.buildState(emptyList(), LocalDateTime.of(friday, LocalTime.NOON)) is WidgetState.NoLessons)
    }

    @Test
    fun substitutionShowsTeacherRoomAndNotes() {
        // Zgłoszenie beta testera: przy zastępstwie na pierwszym planie nauczyciel, nie sala,
        // a uwagi ze strony ("za ostatnią lekcję") mają być widoczne.
        val sub = pl.zse.bydgoszcz.elektron.domain.model.Substitution(
            "s", friday, 3, "1D", null, "211", "M. Zelek", "za ostatnią lekcję", "J.Siepniewska-Stańczyk")
        val lessons = listOf(lesson(friday, 3, "09:50", "10:35").copy(substitution = sub))
        val st = WidgetDataLoader.buildState(lessons, LocalDateTime.of(friday, LocalTime.of(9, 0))) as WidgetState.Ready
        assertEquals("M. Zelek", st.focus.title)
        assertEquals("211", st.focus.room)
        assertEquals("za ostatnią lekcję", st.focus.note)
    }

    @Test
    fun customSubjectNameIsUsed() {
        // Własna nazwa z Ustawień -> Przedmioty trafia też do widżetów (dla każdej grupy przedmiotu).
        val lessons = listOf(lesson(friday, 3, "09:50", "10:35", subject = "wf-j1"))
        val styles = mapOf("wf" to pl.zse.bydgoszcz.elektron.domain.model.SubjectStyle(name = "WF"))
        val st = WidgetDataLoader.buildState(lessons, LocalDateTime.of(friday, LocalTime.of(9, 0)), styles) as WidgetState.Ready
        assertEquals("WF", st.focus.title)
    }

    @Test
    fun widgetLookHidesTeacherAndRoom() {
        val lessons = listOf(lesson(friday, 3, "09:50", "10:35"))
        val look = pl.zse.bydgoszcz.elektron.domain.model.WidgetLook(showTeacher = false, showRoom = false)
        val st = WidgetDataLoader.buildState(lessons, LocalDateTime.of(friday, LocalTime.of(9, 0)), emptyMap(), look) as WidgetState.Ready
        assertEquals(null, st.focus.detail)
        assertEquals(null, st.focus.room)
    }

    @Test
    fun customSubjectColorReachesWidget() {
        // Kolor z Ustawień -> Przedmioty trafia do widżetów; zastępstwo zostaje w kolorze zastępstw.
        val lessons = listOf(lesson(friday, 3, "09:50", "10:35", subject = "wf-j1"))
        val styles = mapOf("wf" to pl.zse.bydgoszcz.elektron.domain.model.SubjectStyle(color = 0xFF7CB342))
        val st = WidgetDataLoader.buildState(lessons, LocalDateTime.of(friday, LocalTime.of(9, 0)), styles) as WidgetState.Ready
        assertEquals(0xFF7CB342, st.focus.color)
        assertEquals(null, (WidgetDataLoader.buildState(listOf(lesson(friday, 3, "09:50", "10:35")),
            LocalDateTime.of(friday, LocalTime.of(9, 0)), styles) as WidgetState.Ready).focus.color)
    }
}
