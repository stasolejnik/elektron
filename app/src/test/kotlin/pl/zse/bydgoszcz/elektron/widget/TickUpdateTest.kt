package pl.zse.bydgoszcz.elektron.widget

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Collections

class TickUpdateTest {

    @Test
    fun cancelledTickStillRefreshesAllWidgets() = runBlocking {
        // provideGlance pierwszego widżetu planuje następny tick z REPLACE, co anuluje działającego
        // WidgetTickWorker. Dawniej przerywało to odświeżanie pozostałych widżetów i kafelka.
        val refreshed = Collections.synchronizedList(mutableListOf<String>())
        val job = launch(Dispatchers.Default) {
            runTickUpdate {
                refreshed += "Następna lekcja"
                delay(100)                         // tu przychodzi anulowanie (REPLACE)
                refreshed += "Plan dnia"
                refreshed += "Zastępstwa"
                refreshed += "Kafelek"
            }
        }
        delay(30)
        job.cancel()
        job.join()
        assertEquals(listOf("Następna lekcja", "Plan dnia", "Zastępstwa", "Kafelek"), refreshed.toList())
    }
    @Test fun substitutionsAloneRefreshAtLessonEndAndMidnight() {
        val now = java.time.LocalDateTime.of(2026, 10, 6, 10, 0)
        val target = pl.zse.bydgoszcz.elektron.domain.model.LessonTarget(now.toLocalDate(), 3)
        val end = java.time.LocalTime.of(10, 45)
        assertEquals(now.toLocalDate().atTime(end), nextSubstitutionChange(now, listOf(target), mapOf(3 to end)))
        assertEquals(now.toLocalDate().plusDays(1).atStartOfDay(), nextSubstitutionChange(now.plusHours(1), listOf(target), mapOf(3 to end)))
    }

}
