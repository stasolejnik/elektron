package pl.zse.bydgoszcz.elektron.data.mapper

/**
 * Wersja odczytu treści ogłoszenia, zapisywana razem z treścią (komentarz HTML na początku).
 * Treść bez znacznika albo ze starszą wersją pochodzi z wcześniejszej wersji aplikacji - np.
 * przed RC7 zamiast artykułu bywał zapisany skrócony podgląd z bocznej kolumny strony - i przy
 * otwarciu jest pobierana ponownie. Zwiększ [VERSION] po każdej zmianie NewsArticleParser,
 * która zmienia zakres odczytanej treści.
 */
internal object ArticleContent {
    const val VERSION = 2
    private val MARKER = Regex("^<!--eLektron-article:(\\d+)-->")

    fun mark(html: String): String = "<!--eLektron-article:$VERSION-->" + strip(html)

    fun isCurrent(stored: String?): Boolean =
        stored != null && MARKER.find(stored)?.groupValues?.get(1)?.toIntOrNull() == VERSION

    fun strip(stored: String?): String? = stored?.replaceFirst(MARKER, "")
}
