package pl.zse.bydgoszcz.elektron.domain.model

import org.junit.Assert.assertEquals
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
}
