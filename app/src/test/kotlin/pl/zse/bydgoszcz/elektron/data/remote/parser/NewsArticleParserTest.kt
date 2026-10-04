package pl.zse.bydgoszcz.elektron.data.remote.parser

import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test
import pl.zse.bydgoszcz.elektron.domain.model.ArticleContentException
import pl.zse.bydgoszcz.elektron.domain.model.SyncErrors

class NewsArticleParserTest {
    @Test fun schoolMainContentIsReadWithoutTitleMetadataOrNavigation() {
        val doc = Jsoup.parse("""
            <nav>Menu szkoły</nav><div class="main-content">
            <div class="row"><div class="col-12"><h1>Tytuł ogłoszenia</h1>
            <div class="page-subtitle">Opublikowano: 2026-09-11 09:54, Autor: Nauczyciel</div></div></div>
            <div class="row"><div class="col-12 baguettebox"><p>Pierwszy akapit.</p><p>Drugi akapit.</p></div></div>
            </div><footer>Adres szkoły</footer>
        """)
        val html = NewsArticleParser.parse(doc)!!
        assertTrue(html.contains("Pierwszy akapit."))
        assertTrue(html.contains("Drugi akapit."))
        assertFalse(html.contains("Tytuł ogłoszenia"))
        assertFalse(html.contains("Opublikowano"))
        assertFalse(html.contains("Menu szkoły"))
        assertFalse(html.contains("Adres szkoły"))
    }

    @Test fun maintenancePageIsNotSavedAsArticle() {
        assertNull(NewsArticleParser.parse(Jsoup.parse("<div class='main-content'><h1>Przerwa techniczna</h1><p>Spróbuj później</p></div>")))
    }

    @Test fun unrecognizedArticleIsNotReportedAsNoInternet() {
        assertEquals(ArticleContentException.USER_MESSAGE, SyncErrors.userMessage(ArticleContentException()))
        assertNotEquals(SyncErrors.OFFLINE, SyncErrors.userMessage(ArticleContentException()))
    }

    @Test fun semanticArticleIsAlsoSupported() {
        assertTrue(NewsArticleParser.parse(Jsoup.parse("<article><p>Treść.</p></article>"))!!.contains("Treść."))
    }
    @Test fun articleSectionHeadingsAreNotRemovedWithPageTitle() {
        val html = NewsArticleParser.parse(Jsoup.parse("<article><h1>Tytuł strony</h1><p>Wstęp.</p><h1>Sekcja</h1><p>Treść sekcji.</p></article>"))!!
        assertFalse(html.contains("Tytuł strony"))
        assertTrue(html.contains("<h1>Sekcja</h1>"))
    }

}
