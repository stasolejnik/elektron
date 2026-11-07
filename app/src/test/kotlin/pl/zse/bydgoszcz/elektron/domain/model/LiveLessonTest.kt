package pl.zse.bydgoszcz.elektron.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Treść powiadomienia na żywo w każdym stanie dnia i harmonogram granic. */
class LiveLessonTest {

    private val day = LocalDate.of(2026, 10, 16)          // piątek
    private val monday = LocalDate.of(2026, 10, 19)

    private fun lesson(n: Int, from: String, to: String, subject: String = "mat", room: String? = "105",
        teacher: String? = "J.Kowalski", date: LocalDate = day) =
        Lesson("$date-$n", "o3", "1D", date, DayOfWeek.PIATEK, n, LocalTime.parse(from), LocalTime.parse(to),
            listOf(LessonGroup(subject, "Ko", null, teacher, room, null, null, null)), null, null)

    private fun sub(n: Int, info: String, substitute: String? = null, notes: String? = null) =
        Substitution("s$n", day, n, "1D", null, info, substitute, notes, "J.Kowalski")

    private val plan = listOf(
        lesson(1, "08:00", "08:45"),
        lesson(2, "08:55", "09:40", "fiz", "12", "A.Nowak"),
        lesson(4, "10:45", "11:30", "pol", "7", "B.Lis")
    )

    private fun at(t: String, lessons: List<Lesson> = plan, notes: Map<String, LessonNote> = emptyMap()) =
        LiveLesson.content(lessons, LocalDateTime.of(day, LocalTime.parse(t)), notes = notes)

    @Test fun beforeLessonsOnlyFrom15MinutesBefore() {
        assertNull(at("07:44"))
        val c = at("07:45")!!
        assertEquals(LiveLesson.Phase.BEFORE, c.phase)
        assertEquals("Do lekcji", c.title)
        assertEquals("Pierwsza lekcja: 1. mat · s.\u2060\u00A0\u2060105", c.text)
        assertTrue(c.lines.contains("Pierwsza lekcja: 1. mat · s.\u2060\u00A0\u2060105 · J.Kowalski, od 08:00"))
        assertEquals(day.atTime(8, 0), c.countdownTo)
        assertEquals(LessonTarget(day, 1), c.target)
        assertEquals(0f, c.progress(day.atTime(7, 45)), 0.001f)
    }

    @Test fun duringLessonShowsRoomTimeLeftAndWhatIsNext() {
        val c = at("08:33")!!
        assertEquals(LiveLesson.Phase.LESSON, c.phase)
        assertEquals("odliczanie do przerwy", "Do przerwy", c.title)
        assertEquals("Teraz: 1. mat · s.\u2060\u00A0\u2060105", c.text)
        assertEquals(day.atTime(8, 45), c.countdownTo)
        assertTrue(c.lines.contains("Potem: 2. fiz · s.\u2060\u00A0\u206012 · A.Nowak"))
        assertEquals(LessonTarget(day, 1), c.target)
        assertTrue(c.progress(day.atTime(8, 33)) in 0.7f..0.75f)
    }

    @Test fun shortBreakCountsDownToNextLesson() {
        val c = at("08:50")!!
        assertEquals(LiveLesson.Phase.BREAK, c.phase)
        assertEquals("odliczanie do lekcji", "Do lekcji", c.title)
        assertEquals(day.atTime(8, 55), c.countdownTo)
        assertEquals(day.atTime(8, 45), c.progressFrom)
        assertEquals("Przerwa, potem: 2. fiz · s.\u2060\u00A0\u206012", c.text)
        assertEquals(LessonTarget(day, 2), c.target)
    }

    @Test fun longGapIsWindowNotBreak() {
        val c = at("10:00")!!
        assertEquals(LiveLesson.Phase.GAP, c.phase)
        assertEquals("Do lekcji", c.title)
        assertEquals("Okienko, potem: 4. pol · s.\u2060\u00A0\u20607", c.text)
        assertEquals(day.atTime(9, 40), c.progressFrom)
        assertEquals(day.atTime(10, 45), c.countdownTo)
    }

    @Test fun lastLessonAndAfterLessons() {
        val last = at("11:00")!!
        assertEquals("Do końca lekcji", last.title)
        assertEquals("Teraz: 4. pol · s.\u2060\u00A0\u20607", last.text)
        assertTrue(last.lines.contains("To ostatnia lekcja dzisiaj"))
        assertEquals(day.atTime(11, 30), last.windowEnd)
        assertNull("po lekcjach powiadomienie znika", at("11:30"))
        assertNull(at("15:00"))
    }

    @Test fun freedLessonsAreSkipped() {
        // 4. lekcja zwolniona: po 2. lekcji to koniec dnia; ostatnia lekcja to 2.
        val freed = plan.map { if (it.number == 4) it.copy(substitution = sub(4, "Uczniowie zwolnieni do domu")) else it }
        assertEquals("Do końca lekcji", at("09:00", freed)!!.title)
        assertNull(at("10:50", freed))
        // 1. lekcja zwolniona ("przychodzą później"): okno od 15 min przed 2. lekcją.
        val late = plan.map { if (it.number == 1) it.copy(substitution = sub(1, "Uczniowie przychodzą później")) else it }
        assertNull(at("08:00", late))
        assertEquals("Pierwsza lekcja: 2. fiz · s.\u2060\u00A0\u206012", at("08:40", late)!!.text)
    }

    @Test fun substitutionShowsTeacherFirstThenRoom() {
        val withSub = plan.map { if (it.number == 2) it.copy(substitution = sub(2, "211", "M.Zelek", "za ostatnią lekcję")) else it }
        val during = at("09:00", withSub)!!
        assertEquals("Teraz: 2. fiz · s.\u2060\u00A0\u2060211", during.text)
        assertTrue(during.lines.contains("Zastępstwo: M.Zelek · s.\u2060\u00A0\u2060211 · za ostatnią lekcję"))
        // Przed nią (w trakcie 1. lekcji) - "dalej" z zastępcą zamiast nauczyciela planu.
        val before = at("08:30", withSub)!!
        assertTrue(before.lines.contains("Potem: 2. fiz · M.Zelek · s.\u2060\u00A0\u2060211"))
        assertTrue(before.lines.contains("Potem zastępstwo: M.Zelek · s.\u2060\u00A0\u2060211 · za ostatnią lekcję"))
        // Informacja szkoły bez zastępcy (np. zajęcia w auli) - w treści.
        val info = plan.map { if (it.number == 2) it.copy(substitution = sub(2, "Zajęcia w auli")) else it }
        assertTrue(at("09:00", info)!!.lines.contains("Zastępstwo: Zajęcia w auli"))
    }

    /** Prawdziwy wpis z 03.11.2026: 1D, 6. lekcja j.polski, zastępca D. Musierowicz, uwaga "edukacja zdrowotna". */
    @Test fun lessonFromNotesReplacesThePlannedSubjectLikeOnTheStartScreen() {
        val withSub = plan.map { if (it.number == 2) it.copy(substitution = sub(2, "111", "D. Musierowicz", "edukacja zdrowotna")) else it }
        val during = at("09:00", withSub)!!
        assertEquals("Teraz: 2. edukacja zdrowotna · s.\u2060\u00A0\u2060111", during.text)
        assertTrue(during.lines.toString(), during.lines.contains("Teraz: 2. edukacja zdrowotna · D. Musierowicz · s.\u2060\u00A0\u2060111, do 09:40"))
        // Bez powtórzenia lekcji, zastępcy i sali - tylko za co i za kogo.
        assertTrue(during.lines.toString(), during.lines.contains("Zastępstwo za: fiz · J.Kowalski"))
        val before = at("08:30", withSub)!!
        assertTrue(before.lines.toString(), before.lines.contains("Potem: 2. edukacja zdrowotna · D. Musierowicz · s.\u2060\u00A0\u2060111"))
        assertTrue(before.lines.toString(), before.lines.contains("Potem zastępstwo za: fiz · J.Kowalski"))
    }

    @Test fun noteOfCurrentOrNextLessonIsPreviewed() {
        val note = LessonNote("o3", day, 2, "fiz", "Zabrać kalkulator\ni zeszyt", 1)
        val notes = mapOf(note.key to note)
        val before = at("08:30", notes = notes)!!
        assertTrue(before.hasNote)
        assertTrue(before.text.endsWith("✎"))
        assertTrue(before.lines.contains("Notatka do następnej: Zabrać kalkulator i zeszyt"))
        val during = at("09:00", notes = notes)!!
        assertTrue(during.lines.contains("Notatka: Zabrać kalkulator i zeszyt"))
        assertFalse(at("10:00", notes = notes)!!.hasNote)
        // Długa notatka - skrót.
        val long = note.copy(text = "a".repeat(200))
        assertTrue(at("09:00", notes = mapOf(long.key to long))!!.lines.first { it.startsWith("Notatka") }.endsWith("…"))
    }

    @Test fun boundariesAreLessonStartsEndsAndWindowStart() {
        fun next(t: String) = LiveLesson.nextChange(plan, LocalDateTime.of(day, LocalTime.parse(t)))
        assertEquals(day.atTime(7, 45), next("06:00"))
        assertEquals(day.atTime(8, 0), next("07:45"))
        assertEquals(day.atTime(8, 45), next("08:10"))
        assertEquals(day.atTime(8, 55), next("08:45"))
        assertEquals(day.atTime(11, 30), next("10:50"))
        // Po lekcjach - początek okna następnego dnia z lekcjami (poniedziałek).
        val week = plan + lesson(1, "08:00", "08:45", date = monday)
        assertEquals(monday.atTime(7, 45), LiveLesson.nextChange(week, day.atTime(12, 0)))
        assertNull(LiveLesson.nextChange(plan, day.atTime(12, 0)))
    }

    @Test fun boundaryAcrossDaylightSavingChangeKeepsWallClockTime() {
        // Zmiana czasu w nocy z 24 na 25.10.2026 (niedziela): poniedziałkowy alarm dalej o 7:45 czasu lokalnego.
        val zone = ZoneId.of("Europe/Warsaw")
        val week = plan + lesson(1, "08:00", "08:45", date = LocalDate.of(2026, 10, 26))
        val friday = LocalDate.of(2026, 10, 23).atTime(12, 0)
        val boundary = LiveLesson.nextChange(week.map { it.copy(date = if (it.date == day) LocalDate.of(2026, 10, 23) else it.date) }, friday)!!
        assertEquals(LocalDate.of(2026, 10, 26).atTime(7, 45), boundary)
        val hours = java.time.Duration.between(friday.atZone(zone), boundary.atZone(zone)).toMinutes() / 60.0
        assertEquals("o godzinę dłużej niż różnica zegarów (cofnięcie czasu)", 68.75, hours, 0.001)
    }

    @Test fun noLessonsNoNotification() {
        assertNull(LiveLesson.content(emptyList(), day.atTime(9, 0)))
        assertNull(LiveLesson.nextChange(emptyList(), day.atTime(9, 0)))
    }

    @Test fun lessonBeforeAWindowCountsDownToTheWindow() {
        // Po 2. lekcji (do 09:40) okienko do 10:45 - "Do okienka", nie "Do przerwy".
        val c = at("09:00")!!
        assertEquals("Do okienka", c.title)
        assertEquals(day.atTime(9, 40), c.countdownTo)
    }

    @Test fun smallTextSaysWhenTheSchoolDayEnds() {
        // Plan kończy się o 11:30.
        assertEquals("Lekcje do 11:30 · 3 h 30 min", LiveLesson.dayLeft(at("08:00")!!, day.atTime(8, 0)))
        assertEquals("Lekcje do 11:30 · 2 h", LiveLesson.dayLeft(at("09:30")!!, day.atTime(9, 30)))
        assertEquals("Lekcje do 11:30 · 1 h 30 min", LiveLesson.dayLeft(at("10:00")!!, day.atTime(10, 0)))
        assertEquals("Lekcje do 11:30 · 50 min", LiveLesson.dayLeft(at("10:40")!!, day.atTime(10, 40)))
        // Ostatnia lekcja - odliczanie i tak pokazuje koniec, bez powtórzenia.
        assertEquals("Lekcje do 11:30", LiveLesson.dayLeft(at("11:10")!!, day.atTime(11, 10)))
    }

    @Test fun progressIsRightInEveryPhaseIncludingLateFirstLesson() {
        // Przerwa 08:45-08:55: w połowie - pół paska (dawniej pasek stał na wartości z początku przerwy).
        assertEquals(0.5f, at("08:50")!!.progress(day.atTime(8, 50)), 0.01f)
        // Okienko 09:40-10:45.
        assertEquals(0.5f, at("10:12:30")!!.progress(day.atTime(10, 12, 30)), 0.01f)
        // Pierwsza lekcja później niż zwykle (1. lekcja zwolniona): kwadrans przed 2. lekcją, w połowie.
        val late = plan.map { if (it.number == 1) it.copy(substitution = sub(1, "Uczniowie przychodzą później")) else it }
        val c = at("08:47:30", late)!!
        assertEquals(day.atTime(8, 40), c.progressFrom)
        assertEquals(0.5f, c.progress(day.atTime(8, 47, 30)), 0.01f)
    }

    @Test fun shortCountdownForTheStatusBarCapsuleRoundsMinutesUp() {
        val c = at("08:20")!!                                   // lekcja 1 do 08:45
        assertEquals("25 min", LiveLesson.shortCountdown(c, LocalDateTime.of(day, LocalTime.of(8, 20))))
        assertEquals("13 min", LiveLesson.shortCountdown(c, LocalDateTime.of(day, LocalTime.of(8, 32, 30))))
        assertEquals("1 min", LiveLesson.shortCountdown(c, LocalDateTime.of(day, LocalTime.of(8, 44, 59))))
        assertEquals("1 min", LiveLesson.shortCountdown(c, LocalDateTime.of(day, LocalTime.of(8, 45))))
        val long = c.copy(countdownTo = LocalDateTime.of(day, LocalTime.of(11, 0)))
        assertEquals("3 h", LiveLesson.shortCountdown(long, LocalDateTime.of(day, LocalTime.of(8, 20))))
    }

    @Test fun nextMinuteTickIsWhenTheShortTextChanges() {
        val c = at("08:20")!!
        // 12 min 30 s do końca = "13 min"; "12 min" od 08:33:00.
        assertEquals(LocalDateTime.of(day, LocalTime.of(8, 33)), LiveLesson.nextMinuteTick(c, LocalDateTime.of(day, LocalTime.of(8, 32, 30))))
        // Równo 12 min do końca: następna zmiana za minutę.
        assertEquals(LocalDateTime.of(day, LocalTime.of(8, 34)), LiveLesson.nextMinuteTick(c, LocalDateTime.of(day, LocalTime.of(8, 33))))
        // Po czasie odliczania - za minutę, nigdy w przeszłości.
        assertEquals(LocalDateTime.of(day, LocalTime.of(8, 46)), LiveLesson.nextMinuteTick(c, LocalDateTime.of(day, LocalTime.of(8, 45))))
    }

    @Test fun shortCriticalTextOnlyOnColorOsPhones() {
        assertTrue(pl.zse.bydgoszcz.elektron.work.LiveLessonScheduler.usesShortCriticalText("realme"))
        assertTrue(pl.zse.bydgoszcz.elektron.work.LiveLessonScheduler.usesShortCriticalText("OPPO"))
        assertTrue(pl.zse.bydgoszcz.elektron.work.LiveLessonScheduler.usesShortCriticalText("OnePlus"))
        assertFalse(pl.zse.bydgoszcz.elektron.work.LiveLessonScheduler.usesShortCriticalText("Google"))
        assertFalse(pl.zse.bydgoszcz.elektron.work.LiveLessonScheduler.usesShortCriticalText(null))
    }
}
