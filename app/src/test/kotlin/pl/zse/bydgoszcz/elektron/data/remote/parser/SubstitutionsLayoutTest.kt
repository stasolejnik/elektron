package pl.zse.bydgoszcz.elektron.data.remote.parser

import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test

class SubstitutionsLayoutTest {
    @Test fun emptySchoolExportIsValid() {
        assertTrue(ZastepstwaParser.hasRecognizedLayout(Jsoup.parse("<title>Inf. o zast.</title><body>Brak zastępstw</body>")))
    }
    @Test fun maintenancePageIsNotValid() {
        assertFalse(ZastepstwaParser.hasRecognizedLayout(Jsoup.parse("<title>Przerwa techniczna</title>Spróbuj później")))
    }
    @Test fun datedEmptyDayIsValid() {
        assertTrue(ZastepstwaParser.hasRecognizedLayout(Jsoup.parse("<table><tr><td>Zastępstwa w dniu 01.10.2026</td></tr></table>")))
    }
    @Test fun undatedLessonRowIsNotAnEmptyExport() {
        assertFalse(ZastepstwaParser.hasRecognizedLayout(Jsoup.parse("<title>Inf. o zast.</title><table><tr><td>3</td><td>2D - 105</td><td>AB</td><td></td></tr></table>")))
    }
}
