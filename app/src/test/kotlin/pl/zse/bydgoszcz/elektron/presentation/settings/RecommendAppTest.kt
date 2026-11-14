package pl.zse.bydgoszcz.elektron.presentation.settings

import io.nayuki.qrcodegen.QrCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.zse.bydgoszcz.elektron.presentation.common.tourSteps
import pl.zse.bydgoszcz.elektron.presentation.navigation.ElektronRoutes

class RecommendAppTest {
    @Test fun qrCodeLinksToTheRepositoryWithHighErrorCorrection() {
        val qr = recommendQr()
        assertEquals(QrCode.Ecc.HIGH, qr.errorCorrectionLevel)
        // Mały kod (duże moduły) - łatwo zeskanować z ekranu drugiego telefonu.
        assertTrue("wersja ${qr.version}", qr.version <= 5)
        assertEquals("https://github.com/stasolejnik/elektron", RECOMMEND_URL)
        assertTrue(RECOMMEND_TEXT.endsWith(RECOMMEND_URL))
    }

    @Test fun logoHoleIsCenteredAndFarBelowWhatTheCodeCanRecover() {
        val qr = recommendQr()
        val hole = logoHole(qr.size)
        val side = hole.last - hole.first + 1
        assertEquals(1, side % 2)
        assertEquals(hole.first, qr.size - 1 - hole.last)
        // Pusty środek pod logo: poniżej 10% modułów (poziom H odtwarza do 30%).
        assertTrue(side * side < qr.size * qr.size * 0.10)
    }

    @Test fun tourStepsAreShortAndFollowTheBarAndTransit() {
        val all = tourSteps(moreEnabled = true, transitVisible = true)
        assertTrue(all.any { it.openMore })
        assertTrue(all.any { it.route == ElektronRoutes.TRANSIT })
        val plain = tourSteps(moreEnabled = false, transitVisible = false)
        assertTrue(plain.none { it.openMore || it.route == ElektronRoutes.TRANSIT })
        assertEquals(all.size - 2, plain.size)
        all.forEach {
            assertTrue(it.title, it.text.length <= 180)
            assertTrue(it.title, '\u2014' !in it.text + it.title && '\u2013' !in it.text + it.title)
        }
        assertEquals(all.size, all.map { it.target }.toSet().size)
        // Start i Zastępstwa schowane w "Więcej" - te kroki bez wskazywanej ikony, reszta bez zmian.
        val hidden = tourSteps(moreEnabled = true, transitVisible = true) { it != ElektronRoutes.DASHBOARD && it != ElektronRoutes.SUBSTITUTIONS }
        assertEquals(all.size, hidden.size)
        assertEquals(2, hidden.count { it.target == null })
    }
}
