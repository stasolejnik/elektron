package pl.zse.bydgoszcz.elektron.widget

import org.junit.Assert.assertEquals
import org.junit.Test

/** Polska odmiana w widżecie Plan dnia: "N lekcja/lekcje/lekcji za Tobą". */
class LessonsWordTest {
    @Test
    fun polishPlural() {
        assertEquals("lekcja", lessonsWord(1))
        assertEquals("lekcje", lessonsWord(2))
        assertEquals("lekcje", lessonsWord(4))
        assertEquals("lekcji", lessonsWord(5))
        assertEquals("lekcji", lessonsWord(12))
        assertEquals("lekcji", lessonsWord(14))
        assertEquals("lekcje", lessonsWord(22))
    }
}
