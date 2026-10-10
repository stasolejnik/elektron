package pl.zse.bydgoszcz.elektron.data.mapper

import org.junit.Assert.*
import org.junit.Test

class ArticleContentTest {
    @Test fun markedContentIsCurrentAndStripsBackToTheOriginal() {
        val marked = ArticleContent.mark("<p>Treść</p>")
        assertTrue(ArticleContent.isCurrent(marked))
        assertEquals("<p>Treść</p>", ArticleContent.strip(marked))
        // Ponowne oznaczenie nie dokleja drugiego znacznika.
        assertEquals(marked, ArticleContent.mark(marked))
    }

    @Test fun contentFromOlderVersionsIsOutdated() {
        assertFalse(ArticleContent.isCurrent("<p>Zapisane przed 1.0.0</p>"))
        assertFalse(ArticleContent.isCurrent("<!--eLektron-article:1--><p>Stara wersja odczytu</p>"))
        assertFalse(ArticleContent.isCurrent(null))
        assertEquals("<p>Stara wersja odczytu</p>", ArticleContent.strip("<!--eLektron-article:1--><p>Stara wersja odczytu</p>"))
        assertEquals("<p>Bez znacznika</p>", ArticleContent.strip("<p>Bez znacznika</p>"))
    }
}
