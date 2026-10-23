package pl.zse.bydgoszcz.elektron.presentation.transit

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/** Zdanie nad listą dojazdów: dzień przed godziną, z przyimkiem ("Na 2. lekcję w poniedziałek (26.10) o 08:55"). */
class LessonWhenTest {
    private fun ms(at: LocalDateTime) = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test fun dayComesBeforeTheTime() {
        val friday = LocalDateTime.of(2026, 10, 23, 19, 0)
        assertEquals("w poniedziałek (26.10) o 08:55", lessonWhen(LocalDateTime.of(2026, 10, 26, 8, 55), ms(friday)))
        assertEquals("we wtorek (27.10) o 08:00", lessonWhen(LocalDateTime.of(2026, 10, 27, 8, 0), ms(friday)))
        assertEquals("jutro o 08:00", lessonWhen(LocalDateTime.of(2026, 10, 24, 8, 0), ms(friday)))
        assertEquals("o 19:50", lessonWhen(LocalDateTime.of(2026, 10, 23, 19, 50), ms(friday)))
    }
}
