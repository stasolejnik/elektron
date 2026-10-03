package pl.zse.bydgoszcz.elektron.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class LessonLinksTest {

    private val today = LocalDate.of(2026, 10, 5)
    private val day = LocalDate.of(2026, 10, 7)

    private fun group(subject: String) = LessonGroup(subject, null, null, null, null, null, null, null)

    private fun substitution(number: Int, group: Int?) = Substitution(
        id = "s$number-$group", date = day, lessonNumber = number, classShortName = "2D",
        groupNumber = group, roomOrInfo = "316", substituteTeacher = "X", notes = null,
        originalTeacher = "Y"
    )

    private fun lesson(number: Int, vararg subjects: String, date: LocalDate = day, subs: List<Substitution> = emptyList()) = Lesson(
        id = "l$number-$date", classId = "o1", className = "2D", date = date,
        dayOfWeek = DayOfWeek.SRODA, number = number,
        timeFrom = LocalTime.of(8, 0), timeTo = LocalTime.of(8, 45),
        groups = subjects.map(::group), note = null, substitution = subs.firstOrNull(), substitutions = subs
    )

    @Test
    fun deepLinkRoundTrip() {
        val target = LessonTarget(day, 3)
        val link = LessonLinks.deepLink(target)
        assertTrue(LessonLinks.isLessonDeepLink(link))
        assertEquals(target, LessonLinks.parseDeepLink(link, today))
        // Inne lekcje - inne linki (widżet: każdy wiersz ma swój cel).
        assertNotEquals(link, LessonLinks.deepLink(LessonTarget(day, 4)))
        assertNotEquals(link, LessonLinks.deepLink(LessonTarget(day.plusDays(1), 3)))
    }

    @Test
    fun lessonNumberRange() {
        assertEquals(0, LessonLinks.parseDeepLink("lesson/${day.toEpochDay()}/0", today)?.lessonNumber)
        assertEquals(12, LessonLinks.parseDeepLink("lesson/${day.toEpochDay()}/12", today)?.lessonNumber)
        assertNull(LessonLinks.parseDeepLink("lesson/${day.toEpochDay()}/13", today))
        assertNull(LessonLinks.parseDeepLink("lesson/${day.toEpochDay()}/-1", today))
    }

    @Test
    fun rejectsDatesFarFromToday() {
        assertEquals(today.plusDays(366), LessonLinks.parseDeepLink("lesson/${today.plusDays(366).toEpochDay()}/1", today)?.date)
        assertEquals(today.minusDays(366), LessonLinks.parseDeepLink("lesson/${today.minusDays(366).toEpochDay()}/1", today)?.date)
        assertNull(LessonLinks.parseDeepLink("lesson/${today.plusDays(367).toEpochDay()}/1", today))
        assertNull(LessonLinks.parseDeepLink("lesson/${today.minusDays(367).toEpochDay()}/1", today))
        // Skrajne liczby nie wywracają LocalDate.ofEpochDay.
        assertNull(LessonLinks.parseDeepLink("lesson/${Long.MAX_VALUE}/1", today))
        assertNull(LessonLinks.parseDeepLink("lesson/${Long.MIN_VALUE}/1", today))
    }

    @Test
    fun rejectsMalformedLinks() {
        listOf(
            null, "", "lesson/", "lesson/abc/1", "lesson/${day.toEpochDay()}", "lesson/${day.toEpochDay()}/x",
            "lesson/${day.toEpochDay()}/1/2", "lesson//1", "substitutions", "https://zse.bydgoszcz.pl",
            "lesson/${day.toEpochDay()}/99999999999"
        ).forEach { assertNull(it, LessonLinks.parseDeepLink(it, today)) }
        assertFalse(LessonLinks.isLessonDeepLink("timetable"))
        assertFalse(LessonLinks.isLessonDeepLink(null))
    }

    @Test
    fun findsLessonByDateAndNumber() {
        val lessons = listOf(lesson(1, "mat"), lesson(2, "pol"), lesson(2, "pol", date = day.plusDays(1)))
        assertEquals("l2-$day", LessonLinks.findLesson(lessons, LessonTarget(day, 2), emptyMap())?.id)
        assertEquals("l2-${day.plusDays(1)}", LessonLinks.findLesson(lessons, LessonTarget(day.plusDays(1), 2), emptyMap())?.id)
    }

    @Test
    fun missingLessonGivesNull() {
        val lessons = listOf(lesson(1, "mat"))
        assertNull(LessonLinks.findLesson(lessons, LessonTarget(day, 5), emptyMap()))
        assertNull(LessonLinks.findLesson(lessons, LessonTarget(day.plusDays(7), 1), emptyMap()))
        assertNull(LessonLinks.findLesson(emptyList(), LessonTarget(day, 1), emptyMap()))
    }

    @Test
    fun respectsGroupSelectionAndPicksOwnSubstitution() {
        val subs = listOf(substitution(3, 1), substitution(3, 2))
        val lessons = listOf(lesson(3, "ang-1/2", "ang-2/2", subs = subs))
        val found = LessonLinks.findLesson(lessons, LessonTarget(day, 3), mapOf("ang" to "2/2"))
        assertEquals(listOf("ang-2/2"), found?.groups?.map { it.subject })
        assertEquals(2, found?.substitution?.groupNumber)
    }

    @Test
    fun otherGroupsLessonIsNotOpened() {
        // Zastępstwo za religię, a użytkownik na religię nie chodzi - lekcji nie ma po filtrze.
        val lessons = listOf(lesson(1, "mat"), lesson(7, "rel-1/1", subs = listOf(substitution(7, null))))
        assertNull(LessonLinks.findLesson(lessons, LessonTarget(day, 7), mapOf("rel" to LessonGroups.NONE)))
        assertEquals("l7-$day", LessonLinks.findLesson(lessons, LessonTarget(day, 7), emptyMap())?.id)
    }
}
