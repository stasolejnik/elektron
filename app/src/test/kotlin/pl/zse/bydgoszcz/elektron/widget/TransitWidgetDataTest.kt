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
    private fun trip(line: String, dep: String, atSchool: String? = null, from: String = "Rycerska") = TransitJourney(
        listOf(TransitRide(line, "Kierunek", "41", from, "100", "Dworzec Główny", ms(dep), ms(dep) + 10 * 60_000L)), 0, 0.0,
        schoolArrivalMs = atSchool?.let(::ms))
    private fun result(direction: TransitDirection, journeys: List<TransitJourney>, fetched: String) =
        TransitJourneyRepository.Result(home.key, journeys, ms(fetched), direction = direction)

    @Test fun withoutYourStopTheWidgetExplainsWhatToDo() {
        val state = TransitWidgetData.build(TransitPreferences(enabled = true), lessons, null, at("07:00"), zone)
        assertEquals(TransitWidgetData.State.Message("Wybierz swój przystanek w Ustawieniach → Przejazdy.", "transit"), state)
        val off = TransitWidgetData.build(TransitPreferences(destination = home), lessons, null, at("07:00"), zone)
        assertEquals(TransitWidgetData.State.Message("Włącz Przejazdy w Ustawieniach → Przejazdy.", "dashboard"), off)
    }

    @Test fun directionFollowsTheTimeOfDayAndOnlyAMatchingResultIsShown() {
        val fromSchool = result(TransitDirection.FROM_SCHOOL, listOf(trip("5", "15:00")), "14:30")
        // Rano dojazd do szkoły - wynik powrotu z wczoraj się nie liczy.
        val morning = TransitWidgetData.build(prefs, lessons, fromSchool, at("07:00"), zone) as TransitWidgetData.State.Ready
        assertTrue(morning.toSchool)
        assertEquals("Do szkoły", morning.title)
        assertNull(morning.checked)
        assertEquals("Dotknij, aby sprawdzić połączenia.", morning.empty)
        // Po połowie dnia lekcji (10:50) - powrót, od najbliższego.
        val afternoon = TransitWidgetData.build(prefs, lessons, fromSchool, at("14:40"), zone) as TransitWidgetData.State.Ready
        assertEquals("Ze szkoły", afternoon.title)
        assertEquals("Sprawdzono 14:30", afternoon.checked)
        assertEquals(listOf("5"), afternoon.rows.map { it.line })
        assertEquals("za 20 min", afternoon.rows.single().until)
        assertEquals("Rycerska · 15:00", afternoon.rows.single().detail)
    }

    @Test fun toSchoolStartsWithTheNearestRideOrTheOneWithTheChosenMargin() {
        val trips = listOf(trip("8", "07:00", "07:20"), trip("8", "07:15", "07:35"), trip("5", "07:30", "07:48"), trip("5", "07:40", "07:58"))
        val saved = result(TransitDirection.TO_SCHOOL, trips, "06:50")
        val nearest = TransitWidgetData.build(prefs, lessons, saved, at("06:55"), zone) as TransitWidgetData.State.Ready
        assertEquals(listOf("za 5 min", "za 20 min", "za 35 min", "za 45 min"), nearest.rows.map { it.until })
        assertEquals("Odjazd 07:00 · na przystanku 07:10", nearest.rows.first().detail)
        // "Kursy na czas przed lekcją" 10 min: od kursu, który jest na miejscu o 07:48 (lekcja 08:00).
        val margin = TransitWidgetData.build(prefs.copy(arrivalMarginMinutes = 10), lessons, saved, at("06:55"), zone) as TransitWidgetData.State.Ready
        assertEquals(listOf("07:30", "07:40"), margin.rows.map { it.detail.substringAfter("Odjazd ").take(5) })
    }

    /** Ze szkoły wynik jest według przyjazdu do celu - widżet pokazuje od najbliższego odjazdu (prawdziwy przypadek 03.11, 15:02). */
    @Test fun ridesFromSchoolAreOrderedByDeparture() {
        val later = trip("77", "15:16").let { it.copy(rides = listOf(it.rides.single().copy(arrivalMs = ms("15:24")))) }
        val sooner = trip("8", "15:13").let { it.copy(rides = listOf(it.rides.single().copy(arrivalMs = ms("15:30")))) }
        val saved = result(TransitDirection.FROM_SCHOOL, listOf(later, sooner), "15:02")
        val state = TransitWidgetData.build(prefs, lessons, saved, at("15:02"), zone) as TransitWidgetData.State.Ready
        assertEquals(listOf("8", "77"), state.rows.map { it.line })
    }

    @Test fun departedRidesDisappearAndTheWidgetRedrawsEveryMinuteWhileItShowsRides() {
        val saved = result(TransitDirection.FROM_SCHOOL, listOf(trip("5", "15:00"), trip("67", "15:10")), "14:30")
        val state = TransitWidgetData.build(prefs, lessons, saved, at("15:05"), zone) as TransitWidgetData.State.Ready
        assertEquals(listOf("67"), state.rows.map { it.line })
        assertEquals(at("15:06"), TransitWidgetData.nextTick(state, at("15:05:30")))
        val empty = TransitWidgetData.build(prefs, lessons, saved, at("15:20"), zone) as TransitWidgetData.State.Ready
        assertTrue(empty.rows.isEmpty())
        assertEquals(at("15:35"), TransitWidgetData.nextTick(empty, at("15:20")))
    }
}
