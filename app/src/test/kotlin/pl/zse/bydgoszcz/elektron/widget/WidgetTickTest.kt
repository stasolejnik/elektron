package pl.zse.bydgoszcz.elektron.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

/** Każdy stan widżetu (poza brakiem klasy) planuje kolejne przeładowanie. */
class WidgetTickTest {
    private val now = LocalDateTime.of(2026, 10, 7, 22, 40)

    @Test fun failedReadIsRetriedInsteadOfWaitingForTheSystemFallback() {
        assertEquals(now.plusMinutes(WIDGET_RETRY_MINUTES), widgetTickAt(WidgetState.Failed, now))
        assertEquals(now.plusMinutes(WIDGET_RETRY_MINUTES), subsWidgetTickAt(SubsWidgetState.Failed, now))
    }

    @Test fun noLessonsChecksAgainJustAfterMidnight() {
        assertEquals(LocalDateTime.of(2026, 10, 8, 0, 1), widgetTickAt(WidgetState.NoLessons("1D"), now))
    }

    @Test fun noClassNeedsNoTicks() {
        assertNull(widgetTickAt(WidgetState.NoClass, now))
        assertNull(subsWidgetTickAt(SubsWidgetState.NoClass, now))
        assertEquals(now.plusHours(1), subsWidgetTickAt(SubsWidgetState.Ready("1D", emptyList(), now.plusHours(1)), now))
    }
}
