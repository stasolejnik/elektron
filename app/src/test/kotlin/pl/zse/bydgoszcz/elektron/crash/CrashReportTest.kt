package pl.zse.bydgoszcz.elektron.crash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashReportTest {
    private val stack = "java.lang.IllegalStateException: Brak lekcji\n\tat pl.zse.A.b(A.kt:10)\n"
    private fun report(s: String = stack) =
        CrashReport.format("0.6.0-alpha", 13, "gms", "15", 35, "Xiaomi 2201117TY", "2026-10-01 07:40", s)

    @Test fun headerAndStack() {
        val r = report()
        assertTrue(r.startsWith("eLektron 0.6.0-alpha (13, gms)\nAndroid 15 (API 35), Xiaomi 2201117TY"))
        assertTrue(r.contains("at pl.zse.A.b(A.kt:10)"))
    }

    @Test fun titleIsSimpleExceptionName() {
        assertEquals("Awaria: IllegalStateException: Brak lekcji", CrashReport.title(report()))
    }

    @Test fun longStackIsTrimmed() {
        val r = report("java.lang.Error\n" + "\tat x.Y.z(Y.kt:1)\n".repeat(2000))
        assertTrue(r.length < 5_300)
        assertTrue(r.endsWith("(przycięto)"))
    }

    @Test fun issueUrlIsEncodedAndBounded() {
        val url = CrashReport.issueUrl(report("java.lang.Error: zażółć\n" + "\tat x.Y.z(Y.kt:1)\n".repeat(2000)))
        assertTrue(url.startsWith("https://github.com/stasolejnik/elektron/issues/new?title=Awaria"))
        assertTrue(!url.contains(' ') && !url.contains('\n'))
        assertTrue(url.length < 8_000)   // GitHub odrzuca dłuższe adresy
    }

    @Test fun feedbackUrlContainsDeviceInfo() {
        val url = CrashReport.feedbackUrl("0.6.0-alpha", 13, "foss", "14", 34, "Google Pixel 7")
        assertTrue(url.startsWith("https://github.com/stasolejnik/elektron/issues/new?body="))
        assertTrue(url.contains("Pixel%207"))
        assertTrue(url.contains("foss"))
    }
}
