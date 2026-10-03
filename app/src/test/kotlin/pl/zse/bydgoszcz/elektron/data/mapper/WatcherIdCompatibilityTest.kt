package pl.zse.bydgoszcz.elektron.data.mapper

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Test
import pl.zse.bydgoszcz.elektron.data.remote.parser.ZastepstwaParser

/**
 * ID zastępstw z aplikacji muszą być identyczne z ID z watch.py (repozytorium
 * elektron-push-watcher) - inaczej to samo zastępstwo przychodzi dwa razy: z pusha
 * i z synchronizacji. Plik *.watcher.ids wygenerował watch.py (fetch_substitutions)
 * z tej samej zapisanej strony; po zmianie parsera lub formatu ID w którymkolwiek
 * repozytorium wygeneruj go ponownie i porównaj.
 */
class WatcherIdCompatibilityTest {

    @Test
    fun appIdsMatchPushWatcherIds() {
        val doc = javaClass.getResourceAsStream("/zastepstwa/2026-10-01.html")!!
            .use { Jsoup.parse(it, "UTF-8", "https://zastepstwa.zse.bydgoszcz.pl/") }
        val appIds = ZastepstwaParser.parse(doc).mapNotNull(SubstitutionMapper::toEntity).map { it.id }
        val watcherIds = javaClass.getResourceAsStream("/zastepstwa/2026-10-01.watcher.ids")!!
            .use { it.readBytes().toString(Charsets.UTF_8) }.lines().filter { it.isNotBlank() }
        assertEquals(32, watcherIds.size)
        assertEquals(watcherIds, appIds)
    }
}
