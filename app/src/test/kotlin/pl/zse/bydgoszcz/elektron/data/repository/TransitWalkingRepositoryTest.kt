package pl.zse.bydgoszcz.elektron.data.repository

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.zse.bydgoszcz.elektron.domain.model.SchoolTransit
import pl.zse.bydgoszcz.elektron.domain.model.TransitDestination

/** Dojście ze szkoły szacowane na telefonie z odległości (bez planera tras pieszych w sieci). */
class TransitWalkingRepositoryTest {
    @Test fun walkIsEstimatedFromDistanceWithoutNetwork() = runBlocking {
        val repo = TransitWalkingRepository()
        val near = TransitDestination("Przy szkole", listOf("1"), SchoolTransit.LATITUDE, SchoolTransit.LONGITUDE)
        // Ok. 1 km od szkoły na północ (0,009° szerokości).
        val far = TransitDestination("Dalej", listOf("2", "3"), SchoolTransit.LATITUDE + 0.009, SchoolTransit.LONGITUDE)
        val paths = repo.fromSchool(listOf(near, far))
        assertEquals(1, paths.getValue("1").minutes)
        // 1 km x 1,3 (ulice) / 75 m/min = ok. 18 min; to samo dla obu stanowisk przystanku.
        assertTrue(paths.getValue("2").minutes in 16..20)
        assertEquals(paths.getValue("2"), paths.getValue("3").copy(savedAt = paths.getValue("2").savedAt))
        assertFalse(repo.retryDue())
    }
}
