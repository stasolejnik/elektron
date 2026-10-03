package pl.zse.bydgoszcz.elektron.data.remote.parser

import pl.zse.bydgoszcz.elektron.domain.model.ClassNames
import android.util.Log
import org.jsoup.nodes.Document
import pl.zse.bydgoszcz.elektron.data.remote.dto.ClassListItemDto

/**
 * Parser sidebaru lista.html z plan.zse.bydgoszcz.pl (UTF-8).
 * Sanityzuje zdublowane nazwy ("1A 1A Ivy" -> "1A Ivy").
 */
object OptivumListaParser {
    private const val TAG = "OptivumListaParser"
    private val HREF_RE = Regex("plany/([ons]\\d+)\\.html")
    private val DUP_RE = Regex("^(\\S+)\\s+\\1(?:\\s+(.+))?$")

    fun parse(doc: Document, baseUrl: String): List<ClassListItemDto> {
        val result = mutableListOf<ClassListItemDto>()
        val anchors = doc.select("a[href*=plany/]")
        if (anchors.isEmpty()) {
            Log.w(TAG, "Brak linków 'plany/' w lista.html")
            return emptyList()
        }
        val base = baseUrl.trimEnd('/')
        for (a in anchors) {
            val href = a.attr("href").trim()
            val id = HREF_RE.find(href)?.groupValues?.get(1) ?: continue
            val kind = when (id.first()) {
                'o' -> ClassListItemDto.Kind.CLASS
                'n' -> ClassListItemDto.Kind.TEACHER
                's' -> ClassListItemDto.Kind.ROOM
                else -> continue
            }
            val rawDisplay = a.text().trim()
            if (rawDisplay.isBlank()) continue
            val display = sanitizeDisplay(rawDisplay)
            val short = ClassNames.shortName(display)
            val absolute = if (href.startsWith("http")) href else "$base/$href"
            result += ClassListItemDto(id, kind, display, short, absolute)
        }
        Log.i(TAG, "Sparsowano ${result.size} pozycji")
        return result
    }

    private fun sanitizeDisplay(raw: String): String {
        val m = DUP_RE.find(raw) ?: return raw
        val first = m.groupValues[1]
        val rest = m.groupValues[2]
        return if (rest.isBlank()) first else "$first $rest"
    }
}
