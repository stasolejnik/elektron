package pl.zse.bydgoszcz.elektron.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class SubstitutionsFreshnessTest {
    private val now = LocalDateTime.of(2026, 10, 7, 9, 0)

    @Test fun freshDataHasNoLabelAndRefreshesWhenItBecomesStale() {
        val r = SubstitutionsFreshness.of(now.minusMinutes(20), now)
        assertNull(r.label)
        assertEquals(now.minusMinutes(20).plusHours(3), r.staleAt)
    }

    @Test fun oldOrMissingDataIsLabelled() {
        assertEquals("Sprawdzono: dziś 05:30", SubstitutionsFreshness.of(now.withHour(5).withMinute(30), now).label)
        assertEquals("Sprawdzono: wczoraj 21:40", SubstitutionsFreshness.of(now.minusDays(1).withHour(21).withMinute(40), now).label)
        assertEquals("Sprawdzono: 04.10 21:40", SubstitutionsFreshness.of(LocalDateTime.of(2026, 10, 4, 21, 40), now).label)
        assertEquals("Zastępstw jeszcze nie sprawdzono", SubstitutionsFreshness.of(null, now).label)
        // Tuż po północy wczorajszy odczyt nie jest już "z dziś", nawet gdy ma mniej niż 3 h.
        val night = LocalDateTime.of(2026, 10, 8, 0, 30)
        assertEquals("Sprawdzono: wczoraj 23:00", SubstitutionsFreshness.of(LocalDateTime.of(2026, 10, 7, 23, 0), night).label)
        assertNull(SubstitutionsFreshness.of(now.minusDays(1), now).staleAt)
    }

    @Test fun staleLabelIsKeptWhenAReadFails() {
        val previous = SubsWidgetState.Ready("1D", emptyList(), checked = "Sprawdzono: wczoraj 21:40")
        assertEquals(previous.copy(readError = true), retainSubsWidgetData(previous, SubsWidgetState.Failed))
    }
}
