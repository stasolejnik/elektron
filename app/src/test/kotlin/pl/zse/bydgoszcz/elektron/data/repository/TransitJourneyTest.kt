package pl.zse.bydgoszcz.elektron.data.repository

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pl.zse.bydgoszcz.elektron.domain.model.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TransitJourneyTest {
    private val now = 1_791_219_000_000L
    private val destination = TransitDestination("Rondo Jagiellonów", listOf("9", "26"), 53.1236, 18.007)
    private val platforms = mapOf("24" to TransitDestination("Jagiellońska - Łużycka", listOf("24"), 53.12194, 18.02764))
    private fun fixture() = JSONObject(javaClass.getResource("/transit/planner.json")!!.readText())
    private fun parse(response: JSONObject = fixture(), target: TransitDestination = destination, at: Long = now) =
        TransitJourneyParser.parse(response, target, platforms, at)

    @Test fun actualPlannerResponseReachesExactDestinationWithoutFinalWalkingLeg() {
        val journey = parse().single()
        assertEquals("24", journey.rides.first().fromId)
        assertEquals("26", journey.rides.last().toId)
        assertEquals("6", journey.rides.first().line)
        assertEquals("Bielawy", journey.rides.first().direction)
        assertEquals(1_791_219_720_000L, journey.arrivalMs)
        assertTrue(journey.walkMinutes >= 3)
    }
    @Test fun stopPassedOnRouteTruncatesTheJourneyAtThatStop() {
        val target = TransitDestination("Dworzec Autobusowy", listOf("25"), 53.123, 18.018)
        val journey = parse(target = target).single()
        assertEquals("25", journey.rides.single().toId)
        assertEquals(1_791_219_600_000L, journey.arrivalMs)
    }
    @Test fun nearbyStopAndOppositeDirectionDoNotCountAsDestination() {
        assertTrue(parse(target = destination.copy(stopIds = listOf("9"))).isEmpty())
        val reverse = fixture()
        val leg = reverse.getJSONArray("polaczenia").getJSONObject(0).getJSONArray("odcinki").getJSONObject(1)
        leg.put("doPrzystanku", JSONObject().put("id", "11").put("nazwa", "Inny kierunek"))
        leg.remove("poDrodze")
        assertTrue(parse(reverse).isEmpty())
    }
    @Test fun departuresRemainVisibleAfterWalkingDeadlineUntilTheyLeave() {
        val first = parse().single()
        val at = first.departureMs - (first.walkMinutes + 2) * 60_000L + 1
        assertEquals(1, parse(at = at).size)
        assertEquals(1, TransitJourneys.rank(listOf(first), at).size)
        assertTrue(TransitJourneys.rank(listOf(first), first.departureMs).isEmpty())
        assertEquals(1, parse(at = first.departureMs - (first.walkMinutes + 2) * 60_000L).size)
    }
    @Test fun malformedTimesAndUnknownBoardingStopAreRejected() {
        val invalid = fixture()
        invalid.getJSONArray("polaczenia").getJSONObject(0).getJSONArray("odcinki").getJSONObject(1)
            .getJSONArray("poDrodze").getJSONObject(2).put("at", now - 1)
        assertTrue(parse(invalid).isEmpty())
        assertTrue(TransitJourneyParser.parse(fixture(), destination, emptyMap(), now).isEmpty())
    }
    @Test fun departuresStayChronologicalRegardlessOfDistanceOrReachability() {
        val base = parse().single()
        val laterNearest = base.copy(rides = base.rides.map { it.copy(departureMs = it.departureMs + 600_000L, arrivalMs = it.arrivalMs + 600_000L) }, distanceMeters = 100.0)
        val earlierDistant = base.copy(rides = base.rides.map { it.copy(fromId = "25") }, distanceMeters = 800.0, walkMinutes = 1)
        val ranked = TransitJourneys.rank(listOf(earlierDistant, laterNearest), now)
        assertEquals(earlierDistant.key, ranked.first().key)
        assertTrue("A faster alternative remains visible", ranked.any { it.key == earlierDistant.key })
        assertTrue(TransitJourneys.rank(listOf(laterNearest), laterNearest.departureMs).isEmpty())
    }
    @Test fun cacheSurvivesWalkingDeadlineButExpiresAfterOneMinuteOrForAnotherGoal() {
        val journey = parse().single()
        val fetched = journey.departureMs - (journey.walkMinutes + 2) * 60_000L
        val cache = TransitJourneyRepository.Result(destination.key, listOf(journey), fetched)
        assertTrue(cache.canReuse(destination.key, fetched))
        assertTrue(cache.canReuse(destination.key, fetched + 1))
        assertFalse(cache.canReuse(destination.key, fetched + 60_000))
        assertFalse(cache.canReuse("other-goal", fetched))
        val empty = cache.copy(journeys = emptyList())
        assertTrue(empty.canReuse(destination.key, fetched + 59_999L))
        assertFalse(empty.canReuse(destination.key, fetched + 60_000L))
        assertFalse(empty.canReuse(destination.key, fetched - 1))
    }
    @Test fun distantTransferNeedsWalkingTimeAndKnownPlatforms() {
        val response = fixture()
        val legs = response.getJSONArray("polaczenia").getJSONObject(0).getJSONArray("odcinki")
        val first = legs.getJSONObject(1)
        first.put("doPrzystanku", JSONObject().put("id", "27").put("nazwa", "Przesiadka"))
        first.remove("poDrodze")
        val second = JSONObject(first.toString()).put("odjazdMs", first.getLong("przyjazdMs") + 60_000L)
            .put("przyjazdMs", first.getLong("przyjazdMs") + 600_000L)
            .put("zPrzystanku", JSONObject().put("id", "28").put("nazwa", "Drugi przystanek"))
            .put("doPrzystanku", JSONObject().put("id", "26").put("nazwa", "Rondo Jagiellonów"))
        legs.put(second)
        assertTrue(parse(response).isEmpty())
        val known = platforms + mapOf(
            "27" to TransitDestination("Przesiadka", listOf("27"), 53.123, 18.020),
            "28" to TransitDestination("Drugi przystanek", listOf("28"), 53.123, 18.024))
        assertTrue(TransitJourneyParser.parse(response, destination, known, now).isEmpty())
        second.put("odjazdMs", first.getLong("przyjazdMs") + 360_000L)
        assertEquals(1, TransitJourneyParser.parse(response, destination, known, now).size)
        val far = known + ("28" to TransitDestination("Daleko", listOf("28"), 53.2, 18.2))
        assertTrue(TransitJourneyParser.parse(response, destination, far, now).isEmpty())
    }
    @Test fun transferArrivingAfterDepartureOfSecondLegIsRejected() {
        val response = fixture()
        val option = response.getJSONArray("polaczenia").getJSONObject(0)
        val legs = option.getJSONArray("odcinki")
        val first = legs.getJSONObject(1)
        first.put("doPrzystanku", JSONObject().put("id", "27").put("nazwa", "Przesiadka"))
        first.remove("poDrodze")
        val second = JSONObject(first.toString()).put("odjazdMs", first.getLong("przyjazdMs") - 60_000L)
            .put("przyjazdMs", first.getLong("przyjazdMs") + 600_000L)
            .put("zPrzystanku", JSONObject().put("id", "27").put("nazwa", "Przesiadka"))
            .put("doPrzystanku", JSONObject().put("id", "26").put("nazwa", "Rondo Jagiellonów"))
        legs.put(second)
        assertTrue(parse(response).isEmpty())
        second.put("odjazdMs", first.getLong("przyjazdMs") + 180_000L)
        assertEquals(1, parse(response).single().transfers)
        assertTrue(TransitJourneyParser.parse(response, destination, platforms, now, allowTransfers = false).isEmpty())
        assertTrue(TransitJourneys.rank(parse(response), now, allowTransfers = false).isEmpty())
        assertEquals(1, TransitJourneys.rank(parse(response), now, allowTransfers = true).size)
    }
    @Test fun variantsOfTheSameDepartureAreCombinedAndThereIsNoSixDepartureLimit() {
        val base = parse().single()
        val slower = base.copy(rides = base.rides.map { it.copy(toId = "25", arrivalMs = it.arrivalMs + 60_000L) })
        val later = (1..9).map { offset -> base.copy(rides = base.rides.map { it.copy(departureMs = it.departureMs + offset * 600_000L, arrivalMs = it.arrivalMs + offset * 600_000L) }) }
        val results = TransitJourneys.rank(listOf(slower, base) + later, now)
        assertEquals(10, results.size)
        assertEquals(base.arrivalMs, results.first { it.key == base.key }.arrivalMs)
        assertEquals(results.size, results.map { it.key }.distinct().size)
    }

    @Test fun selectedStopExcludesOtherDepartures() {
        val base = parse().single()
        val other = base.copy(rides = base.rides.map { it.copy(fromId = "25") }, distanceMeters = 400.0)
        val origin = TransitDestination("Preferowany", listOf("25"), 53.123, 18.027)
        val ranked = TransitJourneys.rank(listOf(base, other), now, origin)
        assertEquals("25", ranked.first().rides.first().fromId)
        assertEquals(1, ranked.size)
        assertFalse(TransitJourneyRepository.Result(destination.key, listOf(base), now, originKey = "24").canReuse(destination.key, now, "25"))
    }
    @Test fun walkingUpdatesDoNotReorderOrHideCoursesBeforeDeparture() {
        val base = parse().single()
        val first = base.copy(rides = base.rides.map { it.copy(departureMs = now + 60_000L, arrivalMs = now + 600_000L) }, walkAvailable = false, walkPending = true)
        val later = base.copy(rides = base.rides.map { it.copy(departureMs = now + 420_000L, arrivalMs = now + 900_000L) })
        val input = listOf(later, first)
        assertEquals(listOf(first.key, later.key), TransitJourneys.rank(input, now).map { it.key })
        assertEquals(listOf(first.key, later.key), TransitJourneys.rank(input.map { it.copy(walkMinutes = 8, walkAvailable = true, walkPending = false) }, now + 59_999L).map { it.key })
        assertEquals(listOf(later.key), TransitJourneys.rank(input, first.departureMs).map { it.key })
    }

    @Test fun firstThreeJourneysArriveSoonestRegardlessOfDistanceOrDepartureOrder() {
        val base = parse().single()
        fun trip(line: String, departure: Long, arrival: Long, distance: Double) = base.copy(
            rides = base.rides.map { it.copy(line = line, departureMs = now + departure, arrivalMs = now + arrival) }, distanceMeters = distance)
        val earlyDepartureSlowArrival = trip("1", 60_000L, 1_800_000L, 20.0)
        val fastest = trip("2", 300_000L, 600_000L, 900.0)
        val second = trip("3", 120_000L, 900_000L, 700.0)
        val third = trip("4", 180_000L, 1_200_000L, 500.0)
        assertEquals(listOf(fastest.key, second.key, third.key),
            TransitJourneys.rank(listOf(earlyDepartureSlowArrival, third, second, fastest), now).take(3).map { it.key })
    }

}
