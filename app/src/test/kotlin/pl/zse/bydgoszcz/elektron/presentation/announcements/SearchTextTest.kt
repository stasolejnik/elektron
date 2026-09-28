package pl.zse.bydgoszcz.elektron.presentation.announcements

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTextTest {
    @Test
    fun ignoresCaseAndPolishCharacters() {
        assertEquals("zlot mlodziezy", SearchText.normalize("ZŁOT  Młodzieży"))
        assertEquals("wycieczka do gdanska", SearchText.normalize(" Wycieczka do Gdańska "))
        assertTrue(SearchText.normalize("Egzamin zawodowy – sesja zimowa").contains(SearchText.normalize("EGZAMIN")))
    }
}
