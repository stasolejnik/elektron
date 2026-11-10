package pl.zse.bydgoszcz.elektron.data.remote.sources.zse

/**
 * Centralny rejestr endpointów ZSE Bydgoszcz.
 *
 * ⚠️ Zmiana struktury strony szkoły → edytuj TYLKO tutaj.
 *
 * Encoding per źródło (KRYTYCZNE):
 *  - plan.zse.bydgoszcz.pl        → UTF-8
 *  - zastepstwa.zse.bydgoszcz.pl  → ISO-8859-2 (WYMUSZONE)
 *  - zse.bydgoszcz.pl             → UTF-8 (RSS + strona)
 */
object SchoolEndpoints {

    /** Adres https/http na serwerach szkoły (zse.bydgoszcz.pl, zse.edu.bydgoszcz.pl i ich subdomeny). Bez Androida - testy JVM. */
    fun isSchoolUrl(url: String?): Boolean {
        val uri = runCatching { java.net.URI(url?.trim().orEmpty()) }.getOrNull() ?: return false
        if (uri.scheme?.lowercase() !in setOf("http", "https")) return false
        val host = uri.host?.lowercase() ?: return false
        return listOf("zse.bydgoszcz.pl", "zse.edu.bydgoszcz.pl").any { host == it || host.endsWith(".$it") }
    }

    object Timetable {
        const val BASE = "https://plan.zse.bydgoszcz.pl/"
        const val SIDEBAR = "${BASE}lista.html"
        fun classPlan(classId: String): String = "${BASE}plany/$classId.html"
        fun teacherPlan(teacherId: String): String = "${BASE}plany/$teacherId.html"
        fun roomPlan(roomId: String): String = "${BASE}plany/$roomId.html"
    }

    object Substitutions {
        const val BASE = "https://zastepstwa.zse.bydgoszcz.pl/"
        const val INDEX = "${BASE}index.html"
        // Uwaga: /lista.html na tym serwerze zwraca 404.
    }

    object School {
        const val BASE = "https://zse.bydgoszcz.pl/"
        const val RSS_NEWS = "${BASE}rss.xml"
        const val RSS_LATEST = "${BASE}rsslatest.xml"
        const val NEWS_CATEGORY = "${BASE}aktualnosci-m1,10.html"
        const val OTHER_NEWS_CATEGORY = "${BASE}informacje-spoza-szkoly-m1,290.html"
        fun homePage(nnr: Int): String = "${BASE}?menu=1&item=0&nnr=$nnr"
    }

    object Alt {
        // TODO: https://altplan.zse.bydgoszcz.pl — nieprobingowany. Na później.
        const val BASE = "https://altplan.zse.bydgoszcz.pl/"
    }
}
