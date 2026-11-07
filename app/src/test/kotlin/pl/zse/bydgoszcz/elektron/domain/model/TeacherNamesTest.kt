package pl.zse.bydgoszcz.elektron.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** Nazwiska z listy nauczycieli planu ("M.Kucharska") zapisane jak na stronie zastępstw ("M. Kucharska"). */
class TeacherNamesTest {
    @Test fun initialGetsASpace() {
        assertEquals("M. Kucharska", TeacherNames.display("M.Kucharska"))
        assertEquals("J. Siepniewska-Stańczyk", TeacherNames.display("J.Siepniewska-Stańczyk"))
        assertEquals("Ł. Żak", TeacherNames.display("Ł.Żak"))
    }

    @Test fun alreadySpacedOrFullNamesStayTheSame() {
        assertEquals("D. Musierowicz", TeacherNames.display("D. Musierowicz"))
        assertEquals("Dagmara Wróblewska", TeacherNames.display(" Dagmara Wróblewska "))
        // Skrót w środku nazwy (np. "ks.") nie jest inicjałem imienia.
        assertEquals("ks.Nowak", TeacherNames.display("ks.Nowak"))
    }
}
