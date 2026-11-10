package pl.zse.bydgoszcz.elektron.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.zse.bydgoszcz.elektron.data.repository.TransitJourneyRepository
import pl.zse.bydgoszcz.elektron.domain.model.DayOfWeek
import pl.zse.bydgoszcz.elektron.domain.model.Lesson
import pl.zse.bydgoszcz.elektron.domain.model.TransitDestination
import pl.zse.bydgoszcz.elektron.domain.model.TransitDirection
import pl.zse.bydgoszcz.elektron.domain.model.TransitJourney
import pl.zse.bydgoszcz.elektron.domain.model.TransitPreferences
import pl.zse.bydgoszcz.elektron.domain.model.TransitRide
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/** Widżet Przejazdy: kierunek według pory, tylko pasujący wynik, od najbliższego kursu albo z zapasem przed lekcją. */
class TransitWidgetDataTest {
    private val zone = ZoneId.of("Europe/Warsaw")
    private val day = LocalDate.of(2026, 11, 3)
    private val home = TransitDestination("Dworzec Główny", listOf("100", "101"), 53.13, 18.0)
    private val prefs = TransitPreferences(destination = home, enabled = true)
    private val lessons = listOf(lesson(1, "08:00", "08:45"), lesson(7, "13:40", "14:25"))

    private fun lesson(n: Int, from: String, to: String) = Lesson("$day-$n", "o3", "1D", day, DayOfWeek.WTOREK, n,
        LocalTime.parse(from), LocalTime.parse(to), emptyList(), null, null)
    private fun at(time: String) = LocalDateTime.of(day, LocalTime.parse(time))
    private fun ms(time: String) = at(time).atZone(zone).toInstant().toEpochMilli()
    private fun trip(line: String, dep: String, atSchool: String? = null, from: String = "Rycerska", to: String = "Dworzec Główny",
        walk: Int = 0, arrival: String? = null) = TransitJourney(
        listOf(TransitRide(line, "Kierunek", "41", from, "100", to, ms(dep), arrival?.let(::ms) ?: (ms(dep) + 10 * 60_000L))), walk, 0.0,
        schoolArrivalMs = atSchool?.let(::ms))
    private fun result(direction: TransitDirection, journeys: List<TransitJourney>, fetched: String) =
        TransitJourneyRepository.Result(home.key, journeys, ms(fetched), direction = direction)
    private fun ready(state: TransitWidgetData.State) = state as TransitWidgetData.State.Ready

    @Test fun withoutYourStopTheWidgetExplainsWhatToDo() {
        val state = TransitWidgetData.build(TransitPreferences(enabled = true), lessons, null, at("07:00"), zone)
        assertEquals(TransitWidgetData.State.Message("Wybierz swój przystanek w Ustawieniach → Przejazdy.", "transit"), state)
        val off = TransitWidgetData.build(TransitPreferences(destination = home), lessons, null, at("07:00"), zone)
        assertEquals(TransitWidgetData.State.Message("Włącz Przejazdy w Ustawieniach → Przejazdy.", "dashboard"), off)
    }

    @Test fun directionFollowsTheTimeOfDayAndOnlyAMatchingResultIsShown() {
        val fromSchool = result(TransitDirection.FROM_SCHOOL, listOf(trip("5", "15:00", walk = 4)), "14:30")
        // Rano dojazd do szkoły - wynik powrotu z wczoraj się nie liczy.
        val morning = ready(TransitWidgetData.build(prefs, lessons, fromSchool, at("07:00"), zone))
        assertTrue(morning.toSchool)
        assertEquals("Do szkoły", morning.title)
        assertEquals("Z: Dworzec Główny", morning.subtitle)
        assertEquals("Dotknij, aby sprawdzić połączenia.", morning.empty)
        // Po połowie dnia lekcji - powrót: przystanek, na którym wsiadasz, i kiedy wyjść (dojście 4 min + 2 min zapasu).
        val afternoon = ready(TransitWidgetData.build(prefs, lessons, fromSchool, at("14:40"), zone))
        assertEquals("Ze szkoły", afternoon.title)
        assertEquals("Do: Dworzec Główny · sprawdzono 14:30", afternoon.subtitle)
        val row = afternoon.rows.single()
        assertEquals(listOf("5", "Rycerska", "za 20 min", "15:00", "wyjdź o 14:54"),
            listOf(row.line, row.stop, row.until, row.time, row.detail))
        // Czas na wyjście minął, ale do odjazdu jeszcze da się dojść - pogrubione "wyjdź teraz".
        val late = ready(TransitWidgetData.build(prefs, lessons, fromSchool, at("14:54:30"), zone)).rows.single()
        assertEquals("wyjdź teraz", late.detail)
        assertTrue(late.urgent)
    }

    @Test fun toSchoolStartsWithTheNearestRideOrTheOneWithTheChosenMargin() {
        val trips = listOf(trip("8", "07:00", "07:23", to = "Jagiellońska - Łużycka", arrival = "07:20"), trip("8", "07:15", "07:38"),
            trip("5", "07:30", "07:48"), trip("5", "07:40", "07:58"))
        val saved = result(TransitDirection.TO_SCHOOL, trips, "06:50")
        val nearest = ready(TransitWidgetData.build(prefs, lessons, saved, at("06:55"), zone))
        assertEquals(listOf("za 5 min", "za 20 min", "za 35 min", "za 45 min"), nearest.rows.map { it.until })
        // Przystanek przy szkole, godzina wysiadki i zapas do lekcji o 8:00 (z dojściem) - jak w zakładce.
        assertEquals("Jagiellońska - Łużycka", nearest.rows.first().stop)
        assertEquals("na przystanku 07:20 · zapas 37 min", nearest.rows.first().detail)
        // "Kursy na czas przed lekcją" 10 min: od kursu, który jest na miejscu o 07:48.
        val margin = ready(TransitWidgetData.build(prefs.copy(arrivalMarginMinutes = 10), lessons, saved, at("06:55"), zone))
        assertEquals(listOf("07:30", "07:40"), margin.rows.map { it.time })
    }

    /** Ze szkoły wynik jest według przyjazdu do celu - widżet pokazuje od najbliższego odjazdu. */
    @Test fun ridesFromSchoolAreOrderedByDeparture() {
        val later = trip("77", "15:16", arrival = "15:24")
        val sooner = trip("8", "15:13", arrival = "15:30")
        val saved = result(TransitDirection.FROM_SCHOOL, listOf(later, sooner), "15:02")
        assertEquals(listOf("8", "77"), ready(TransitWidgetData.build(prefs, lessons, saved, at("15:02"), zone)).rows.map { it.line })
    }

    @Test fun refreshingAndFailureAreShownInTheSubtitle() {
        val saved = result(TransitDirection.FROM_SCHOOL, listOf(trip("5", "15:00")), "14:30")
        assertEquals("Do: Dworzec Główny · sprawdzam…", ready(TransitWidgetData.build(prefs, lessons, saved, at("14:40"), zone,
            TransitWidgetData.Status(refreshing = true))).subtitle)
        val failed = ready(TransitWidgetData.build(prefs, lessons, null, at("14:40"), zone, TransitWidgetData.Status(failed = true)))
        assertEquals("Do: Dworzec Główny · nie udało się odświeżyć", failed.subtitle)
        assertEquals("Dotknij, aby sprawdzić połączenia.", failed.empty)
        // W trakcie sprawdzania bez wezwania do dotknięcia.
        assertNull(ready(TransitWidgetData.build(prefs, lessons, null, at("14:40"), zone, TransitWidgetData.Status(refreshing = true))).empty)
    }

    @Test fun departedRidesDisappearAndTheWidgetRedrawsEveryMinuteWhileItShowsRides() {
        val saved = result(TransitDirection.FROM_SCHOOL, listOf(trip("5", "15:00"), trip("67", "16:30")), "14:30")
        val state = ready(TransitWidgetData.build(prefs, lessons, saved, at("15:05"), zone))
        assertEquals(listOf("67"), state.rows.map { it.line })
        assertEquals("za 1 h 25 min", state.rows.single().until)
        assertEquals(at("15:06"), TransitWidgetData.nextTick(state, at("15:05:30")))
        val empty = ready(TransitWidgetData.build(prefs, lessons, saved, at("16:40"), zone))
        assertTrue(empty.rows.isEmpty())
        assertEquals(at("16:55"), TransitWidgetData.nextTick(empty, at("16:40")))
    }
}
