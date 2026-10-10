package pl.zse.bydgoszcz.elektron.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException
import java.net.UnknownHostException

class SyncOutcomeTest {
    @Test fun onlyImportantSourcesProduceMessage() {
        assertNull(SyncOutcome.errorMessage(emptyMap()))
        assertNull(SyncOutcome.errorMessage(mapOf("anns" to IOException("x"))))
    }

    @Test fun substitutionsFailure() {
        assertEquals("Nie udało się odświeżyć zastępstw. ${SyncErrors.SERVER}",
            SyncOutcome.errorMessage(mapOf(SyncOutcome.SUBSTITUTIONS to IOException("Zastępstwa: HTTP 503 dla x"))))
        assertEquals("Nie udało się odświeżyć planu i zastępstw. ${SyncErrors.OFFLINE}",
            SyncOutcome.errorMessage(mapOf(SyncOutcome.SUBSTITUTIONS to UnknownHostException("h"), SyncOutcome.TIMETABLE to UnknownHostException("h"))))
    }

    @Test fun pageChanged() {
        assertEquals(SchoolPageChangedException.USER_MESSAGE,
            SyncOutcome.errorMessage(mapOf(SyncOutcome.TIMETABLE to SchoolPageChangedException("plan lekcji"))))
    }

    @Test fun onlyFailuresAreRetried() {
        assertFalse("nic do sprawdzenia (noc) to nie błąd", SyncOutcome().shouldRetry)
        assertFalse(SyncOutcome.INTERRUPTED.shouldRetry)
        assertFalse(SyncOutcome(setOf(SyncOutcome.SIDEBAR), mapOf(SyncOutcome.TIMETABLE to IOException("x"))).shouldRetry)
        assertTrue(SyncOutcome(failures = mapOf(SyncOutcome.SUBSTITUTIONS to IOException("x"))).shouldRetry)
    }

    @Test fun incompleteSubstitutionsAreASingleWarningSentence() {
        val warning = SyncOutcome.errorMessage(mapOf(SyncOutcome.SUBSTITUTIONS to IncompleteSchoolDataException()))
        assertEquals(IncompleteSchoolDataException.USER_MESSAGE, warning)
        assertTrue(IncompleteSchoolDataException.isWarning(warning))
        // Razem z błędem planu - zwykły komunikat o błędzie.
        val both = SyncOutcome.errorMessage(mapOf(SyncOutcome.SUBSTITUTIONS to IncompleteSchoolDataException(),
            SyncOutcome.TIMETABLE to UnknownHostException("h")))
        assertFalse(IncompleteSchoolDataException.isWarning(both))
        assertTrue(both!!.startsWith("Nie udało się odświeżyć planu i zastępstw."))
        // Komunikat zapisany przez rc7 (widoczny po aktualizacji do pierwszej synchronizacji).
        val rc7 = "Nie udało się odświeżyć zastępstw. Nie udało się odczytać wszystkich zastępstw. Lista może być niepełna; zachowano wcześniejsze wpisy."
        assertTrue(IncompleteSchoolDataException.isWarning(IncompleteSchoolDataException.normalize(rc7)))
        assertEquals("Brak sieci", IncompleteSchoolDataException.normalize("Brak sieci"))
        assertNull(IncompleteSchoolDataException.normalize(null))
    }
}
