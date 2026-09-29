package pl.zse.bydgoszcz.elektron.crash

import java.net.URLEncoder

/**
 * Treść raportu awarii - czysta logika (bez Androida), testowana w CrashReportTest.
 * Raport zawiera wyłącznie dane techniczne: wersję, wariant, model telefonu i ślad stosu.
 * Nic nie jest wysyłane automatycznie - użytkownik sam decyduje, czy go udostępnić.
 */
object CrashReport {

    const val ISSUES_URL = "https://github.com/stasolejnik/elektron/issues/new"

    /** Dłuższe ślady stosu są przycinane - adres zgłoszenia na GitHubie ma limit długości. */
    private const val MAX_STACK_CHARS = 5_000

    fun format(
        versionName: String,
        versionCode: Int,
        flavor: String,
        androidVersion: String,
        sdkInt: Int,
        device: String,
        time: String,
        stackTrace: String
    ): String = buildString {
        appendLine("eLektron $versionName ($versionCode, $flavor)")
        appendLine("Android $androidVersion (API $sdkInt), $device")
        appendLine("Czas: $time")
        appendLine()
        append(trimStack(stackTrace))
    }

    fun trimStack(stack: String): String =
        if (stack.length <= MAX_STACK_CHARS) stack.trimEnd()
        else stack.take(MAX_STACK_CHARS).trimEnd() + "\n… (przycięto)"

    /** Pierwsza linia śladu stosu, np. "java.lang.IllegalStateException: …" - tytuł zgłoszenia. */
    fun title(report: String): String {
        val line = report.lineSequence()
            .dropWhile { it.isNotBlank() }   // pomijamy nagłówek z wersją
            .firstOrNull { it.isNotBlank() }
            ?.trim()
            ?: return "Awaria aplikacji"
        val simple = line.substringBefore(':').substringAfterLast('.')
        val message = if (':' in line) ":" + line.substringAfter(':') else ""
        return ("Awaria: $simple$message").take(110)
    }

    /**
     * Adres nowego zgłoszenia na GitHubie z wypełnioną treścią. GitHub odrzuca zbyt długie
     * adresy, a po zakodowaniu każdy znak nowej linii zajmuje 3 znaki - dlatego raport jest
     * skracany, aż adres zmieści się w limicie. Pełny raport zostaje do "Udostępnij".
     */
    fun issueUrl(report: String, maxLength: Int = 7_500): String {
        var text = report
        while (true) {
            val body = "Co robiłeś/aś tuż przed awarią?\n\n\n---\n```\n$text\n```"
            val url = "$ISSUES_URL?title=${enc(title(report))}&body=${enc(body)}&labels=crash"
            if (url.length <= maxLength || text.length < 200) return url
            text = text.take(text.length * 3 / 4).trimEnd() + "\n… (przycięto)"
        }
    }

    /** "Zgłoś problem" z Ustawień: zgłoszenie bez awarii, z danymi technicznymi na dole. */
    fun feedbackUrl(
        versionName: String, versionCode: Int, flavor: String,
        androidVersion: String, sdkInt: Int, device: String
    ): String {
        val body = "Opisz problem - co się stało i co spodziewałeś/aś się zobaczyć?\n\n\n---\n" +
            "eLektron $versionName ($versionCode, $flavor)\nAndroid $androidVersion (API $sdkInt), $device"
        return "$ISSUES_URL?body=${enc(body)}"
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
}
