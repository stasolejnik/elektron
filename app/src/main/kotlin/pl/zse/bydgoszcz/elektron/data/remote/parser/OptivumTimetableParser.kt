package pl.zse.bydgoszcz.elektron.data.remote.parser

import android.util.Log
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode
import pl.zse.bydgoszcz.elektron.data.remote.dto.LessonCellDto
import pl.zse.bydgoszcz.elektron.data.remote.dto.LessonGroupDto
import pl.zse.bydgoszcz.elektron.data.remote.dto.TimetableDto

/**
 * Parser planu oddziału z plan.zse.bydgoszcz.pl/plany/o*.html (UTF-8, Optivum VULCAN).
 *
 * Przykład HTML (komórka td.l z dwiema podgrupami):
 *   <td class="l">
 *     <span style="font-size:85%">
 *       <span class="p">wf-j1</span>
 *       <a href="n12.html" class="n">Bo</a>
 *       <a href="s65.html" class="s">Hala2</a>
 *     </span><br>
 *     <span class="p">wf</span>-j2
 *     <span class="p">#1AF</span>
 *     <a href="s67.html" class="s">Hala4</a>
 *   </td>
 *
 * UWAGA: w pliku oddziału są 4 tabele — parsujemy WYŁĄCZNIE table.tabela.
 * Ignorujemy tabtytul (tytuł), artefakt VULCAN i stopkę.
 *
 * Kolejność tokenów zależy od widoku (o*, n*, s*), dlatego zbieramy je
 * w strumień i redukujemy po <br> do osobnych grup. Nie polegamy na
 * kolejności pól w grupie.
 */
object OptivumTimetableParser {
    private const val TAG = "OptivumTimetableParser"
    private val TIME_RE = Regex("(\\d{1,2}):(\\d{2})\\s*-\\s*(\\d{1,2}):(\\d{2})")

    fun parse(doc: Document, classId: String): TimetableDto {
        val titleRaw = doc.title()
        val className = titleRaw.substringAfter("Plan lekcji oddziału - ", "").trim()
            .ifBlank { titleRaw.trim() }

        val tables = doc.select("table.tabela")
        if (tables.isEmpty()) {
            Log.w(TAG, "Brak table.tabela — struktura planu mogła się zmienić (classId=$classId)")
            return TimetableDto(classId, className, null, null, emptyList(), layoutOk = false)
        }
        if (tables.size > 1) {
            Log.w(TAG, "Znaleziono ${tables.size} table.tabela — oczekiwano 1, biorę pierwszą")
        }
        val table = tables.firstOrNull() ?: return TimetableDto(classId, className, null, null, emptyList())

        val docText = doc.text()
        val generatedAt = Regex("wygenerowano\\s+(\\d{2}\\.\\d{2}\\.\\d{4})")
            .find(docText)?.groupValues?.get(1)
        val validFrom = Regex("Obowiązuje od:\\s*(\\d{2}\\.\\d{2}\\.\\d{4})")
            .find(docText)?.groupValues?.get(1)

        val lessons = mutableListOf<LessonCellDto>()
        var dataRows = 0
        var warnings = 0
        for (row in table.select("tr")) {
            val nrCell = row.selectFirst("td.nr") ?: continue
            val number = nrCell.text().trim().toIntOrNull()
            if (number == null || number !in 0..12) { warnings++; continue }
            val gCell = row.selectFirst("td.g")
            val (from, to) = parseTime(gCell?.text() ?: "")
            if (from.isEmpty()) {
                Log.w(TAG, "Wiersz nr=$number: brak poprawnej godziny w td.g")
                warnings++
            }
            val dayCells = row.select("td.l")
            if (dayCells.size != 5) {
                Log.w(TAG, "Wiersz nr=$number: ${dayCells.size} komórek td.l, oczekiwano 5")
                warnings++
            }
            dayCells.forEachIndexed { idx, td ->
                if (idx >= 5) return@forEachIndexed
                val (groups, note) = parseCell(td)
                // Diagnostyka: jeśli grupa istnieje, ale nie ma ani subjectu, ani nauczyciela, ani sali
                // — prawdopodobnie zmienił się format HTML i parser gubi dane.
                for (g in groups) {
                    if (g.subject.isNullOrBlank() && g.teacherCode.isNullOrBlank() && g.room.isNullOrBlank()) {
                        Log.w(TAG, "Pusta grupa w $classId, dzień=${idx + 1}, nr=$number — możliwa zmiana HTML")
                        warnings++
                    }
                }
                lessons += LessonCellDto(
                    number = number,
                    timeFrom = from,
                    timeTo = to,
                    dayIndex = idx + 1,
                    groups = groups,
                    note = note
                )
            }
            dataRows++
        }
        Log.i(TAG, "Sparsowano $classId: wierszy=$dataRows, komórek=${lessons.size}, ostrzeżeń=$warnings")
        // Tabela bez ani jednego wiersza z godzinami lekcji = inny układ niż znany.
        // Stopka "wygenerowano" jest za tabelą: bez niej odpowiedź mogła zostać ucięta (zerwane
        // połączenie bez Content-Length) - tabela bez ostatnich lekcji skasowałaby je z planu.
        val footer = docText.contains("wygenerowano", ignoreCase = true)
        return TimetableDto(classId, className, generatedAt, validFrom, lessons, layoutOk = dataRows > 0 && warnings == 0 && tables.size == 1 && footer)
    }

    private fun parseTime(raw: String): Pair<String, String> {
        val m = TIME_RE.find(raw) ?: return "" to ""
        val f = "%02d:%02d".format(m.groupValues[1].toInt(), m.groupValues[2].toInt())
        val t = "%02d:%02d".format(m.groupValues[3].toInt(), m.groupValues[4].toInt())
        return f to t
    }

    private sealed interface Token {
        data class Subject(val text: String) : Token
        data class Teacher(val code: String, val url: String?) : Token
        data class Room(val name: String, val url: String?) : Token
        data class ClassRef(val name: String, val url: String?) : Token
        data class RawText(val text: String) : Token
        object Break : Token
    }

    private fun parseCell(td: Element): Pair<List<LessonGroupDto>, String?> {
        val tokens = mutableListOf<Token>()
        td.childNodes().forEach { tokenize(it, tokens) }
        if (tokens.isEmpty()) return emptyList<LessonGroupDto>() to null

        // Komórka wpisana w Optivum zwykłym tekstem, bez przedmiotu/nauczyciela/sali jako
        // pól, np. "St WP1 zaj wojskowe" (klasy F, środa) albo "5B,5D zaj uni". Dawniej
        // takie lekcje znikały z planu.
        if (tokens.all { it is Token.RawText }) {
            val text = tokens.joinToString(" ") { (it as Token.RawText).text }
            if (!isAbsentMark(text)) return listOfNotNull(parseTextCell(text)) to null
        }

        val groups = mutableListOf<LessonGroupDto>()
        var cur: MutableGroup? = null
        var absent = false

        fun flush() { cur?.let { groups += it.build() }; cur = null }
        // Bieżąca grupa albo nowa — bez operatora !! (dawniej cur!! w każdej gałęzi).
        fun group(): MutableGroup = cur ?: MutableGroup().also { cur = it }

        // Znacznik grupy łączonej z kilku oddziałów, np. "#1AF" w "wf-j2 #1AF Hala4" - osobne
        // pole przedmiotu na stronie. Dawniej nadpisywał przedmiot ("#1AF" zamiast "wf-j2"),
        // przez co grupa WF znikała z wyboru grup. Nie jest przedmiotem - trafia do classRef.
        fun jointTag(text: String) {
            val g = group()
            if (g.classRef == null) g.classRef = text
        }

        for (tok in tokens) {
            when (tok) {
                is Token.Break -> flush()
                is Token.Subject -> if (tok.text.startsWith("#")) jointTag(tok.text) else group().subject = tok.text
                is Token.Teacher -> group().apply {
                    teacherCode = tok.code
                    teacherUrl = tok.url
                }
                is Token.Room -> group().apply {
                    room = tok.name
                    roomUrl = tok.url
                }
                is Token.ClassRef -> group().classRef = tok.name
                is Token.RawText -> {
                    val t = tok.text.trim()
                    val g = cur
                    if (isAbsentMark(t)) {
                        absent = true
                    } else if (t.isNotEmpty()) {
                        // Np. "-j2 #1AF" po <span class="p">wf</span>: dopisek do przedmiotu
                        // ("wf" + "-j2") i znacznik grupy łączonej osobno.
                        val (tags, rest) = t.split(Regex("\\s+")).partition { it.startsWith("#") }
                        tags.forEach(::jointTag)
                        val text = rest.joinToString(" ")
                        if (text.isNotEmpty() && g != null) g.subject = (g.subject ?: "") + text
                    }
                }
            }
        }
        flush()

        val note = if (absent) "Nauczyciel nieobecny" else null
        return groups to note
    }

    private fun tokenize(node: Node, out: MutableList<Token>) {
        when (node) {
            is TextNode -> {
                val t = node.text()
                if (t.isNotBlank()) out += Token.RawText(t)
            }
            is Element -> {
                when (node.tagName().lowercase()) {
                    "br" -> out += Token.Break
                    "span" -> {
                        val txt = node.text().trim()
                        when {
                            node.hasClass("p") -> if (txt.isNotEmpty()) out += Token.Subject(txt)
                            // Nauczyciel / sala / oddział bez linku (np. <span class="s">@</span>).
                            // "@" = brak przypisanej sali - dawniej doklejał się do przedmiotu
                            // ("zaj.woj.@").
                            node.hasClass("n") -> if (txt.isNotEmpty()) out += Token.Teacher(txt, null)
                            node.hasClass("s") -> if (txt.isNotEmpty() && txt != NO_ROOM) out += Token.Room(txt, null)
                            node.hasClass("o") -> if (txt.isNotEmpty()) out += Token.ClassRef(txt, null)
                            else -> node.childNodes().forEach { tokenize(it, out) }
                        }
                    }
                    "a" -> {
                        val href = node.attr("href").trim().ifBlank { null }
                        val text = node.text().trim()
                        if (text.isNotEmpty()) {
                            val cls = node.className().split(" ").toSet()
                            val kind = when {
                                "n" in cls -> 0
                                "s" in cls -> 1
                                "o" in cls -> 2
                                href?.startsWith("n") == true -> 0
                                href?.startsWith("s") == true -> 1
                                href?.startsWith("o") == true -> 2
                                else -> -1
                            }
                            when (kind) {
                                0 -> out += Token.Teacher(text, href)
                                1 -> out += Token.Room(text, href)
                                2 -> out += Token.ClassRef(text, href)
                                else -> out += Token.RawText(text)
                            }
                        }
                    }
                    else -> node.childNodes().forEach { tokenize(it, out) }
                }
            }
            else -> {}
        }
    }

    private const val NO_ROOM = "@"
    // Kod nauczyciela: zawsze 2 litery ("St", "KŁ", "dA", "Pń").
    private val TEACHER_CODE = Regex("^\\p{L}{2}$")
    // Sala w komórce tekstowej: krótka i z cyfrą ("WP1", "211", "Hala2").
    private val ROOM_CODE = Regex("^(?=.*\\d)[\\p{L}\\d-]{1,6}$")
    // Lista oddziałów: "5B,5D".
    private val CLASS_LIST = Regex("^\\d\\p{L}{1,2}(,\\d\\p{L}{1,2})+$")

    private fun isAbsentMark(text: String) = text.trim().equals("N", ignoreCase = true)

    /**
     * Lekcja zapisana samym tekstem. "---" (i puste) = brak lekcji. Rozpoznajemy dwa wzory
     * spotykane na stronie ZSE, a resztę pokazujemy w całości jako nazwę zajęć:
     *  - "St WP1 zaj wojskowe" -> nauczyciel St, sala WP1, przedmiot "zaj wojskowe",
     *  - "5B,5D zaj uni"       -> oddziały 5B,5D (classRef), przedmiot "zaj uni".
     */
    internal fun parseTextCell(raw: String): LessonGroupDto? {
        val text = raw.replace('\u00A0', ' ').trim().replace(Regex("\\s+"), " ")
        if (text.isEmpty() || text.all { it == '-' }) return null
        val w = text.split(' ')
        return when {
            w.size >= 3 && TEACHER_CODE.matches(w[0]) && ROOM_CODE.matches(w[1]) ->
                LessonGroupDto(w.drop(2).joinToString(" "), w[0], null, w[1], null, null, null)
            w.size >= 2 && CLASS_LIST.matches(w[0]) ->
                LessonGroupDto(w.drop(1).joinToString(" "), null, null, null, null, w[0], null)
            else -> LessonGroupDto(text, null, null, null, null, null, null)
        }
    }

    private class MutableGroup {
        var subject: String? = null
        var teacherCode: String? = null
        var teacherUrl: String? = null
        var room: String? = null
        var roomUrl: String? = null
        var classRef: String? = null
        fun build() = LessonGroupDto(subject, teacherCode, teacherUrl, room, roomUrl, classRef, null)
    }
}
