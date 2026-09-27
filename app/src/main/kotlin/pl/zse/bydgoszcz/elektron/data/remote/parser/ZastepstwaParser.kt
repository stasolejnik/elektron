package pl.zse.bydgoszcz.elektron.data.remote.parser

import android.util.Log
import org.jsoup.nodes.Document
import pl.zse.bydgoszcz.elektron.data.remote.dto.SubstitutionDto

/**
 * Parser zastępstw z zastepstwa.zse.bydgoszcz.pl (ISO-8859-2 — wymuszone na poziomie HTTP).
 *
 * Przykład HTML:
 *   <TR><TD class="st0">Zastępstwa w dniu 25.09.2026 piątek</TD></TR>
 *   <TR><TD class="st1">Piotr Augustyn</TD></TR>
 *   <TR>
 *     <TD class="st4">lekcja</TD><TD class="st5">opis</TD>
 *     <TD class="st5">zastępca</TD><TD class="st6">uwagi</TD>
 *   </TR>
 *   <TR>
 *     <TD class="st7">0</TD>
 *     <TD class="st8">2 D(1) - 316</TD>
 *     <TD class="st8">M. Baranowska</TD>
 *     <TD class="st9">złączenie grup</TD>
 *   </TR>
 *   <TR>
 *     <TD class="st10">1</TD>
 *     <TD class="st11">2 D(1) - 316</TD>
 *     <TD class="st11">M. Baranowska</TD>
 *     <TD class="st12">złączenie grup</TD>
 *   </TR>
 *
 * Klasy st7/st8/st9 oraz st10/st11/st12 to naprzemienne wiersze zebry —
 * rozpoznajemy tylko pierwszą komórkę (st7 lub st10) jako sygnał wiersza danych.
 * Kontekst (data, nauczyciel) jest wstrzykiwany z nagłówków st0/st1.
 */
object ZastepstwaParser {
    private const val TAG = "ZastepstwaParser"
    private val DATE_RE = Regex("Zastępstwa w dniu\\s+(\\d{2}\\.\\d{2}\\.\\d{4})")
    private val DESC_RE = Regex("^(\\d+)\\s*([A-Z])(?:\\((\\d+)\\))?\\s*-\\s*(.+)$")
    private val COLUMN_HEADERS = setOf("lekcja", "opis", "zastępca", "uwagi")

    fun parse(doc: Document): List<SubstitutionDto> {
        val tables = doc.select("table")
        if (tables.isEmpty()) {
            Log.w(TAG, "Brak <table> w dokumencie zastępstw")
            return emptyList()
        }
        val table = tables.firstOrNull() ?: return emptyList()
        val out = mutableListOf<SubstitutionDto>()
        var currentDate: String? = null
        var currentTeacher: String? = null
        var warnings = 0

        for (row in table.select("tr")) {
            val cells = row.select("td")
            if (cells.isEmpty()) continue

            if (cells.size == 1) {
                val cell = cells[0]
                val text = cell.text().trim()
                val cls = cell.className()
                when {
                    "st0" in cls.split(" ") -> {
                        val m = DATE_RE.find(text)
                        if (m != null) currentDate = m.groupValues[1]
                        else Log.w(TAG, "Nie udało się sparsować daty: '$text'")
                    }
                    "st1" in cls.split(" ") -> {
                        currentTeacher = text.takeIf { it.isNotBlank() }
                    }
                }
                continue
            }

            if (cells.size != 4) continue

            val texts = cells.map { it.text().trim() }
            if (texts.map { it.lowercase() }.toSet() == COLUMN_HEADERS) continue

            val firstCls = cells[0].className().split(" ").toSet()
            if ("st7" !in firstCls && "st10" !in firstCls) continue

            val lessonNo = texts[0].toIntOrNull()
            if (lessonNo == null || lessonNo !in 0..12) {
                Log.w(TAG, "Nieprawidłowy numer lekcji: '${texts[0]}'")
                warnings++
                continue
            }
            val date = currentDate
            val teacher = currentTeacher
            if (date == null || teacher == null) {
                Log.w(TAG, "Wiersz danych bez kontekstu daty/nauczyciela — pomijam")
                warnings++
                continue
            }
            val m = DESC_RE.find(texts[1])
            if (m == null) {
                Log.w(TAG, "Nie udało się sparsować opisu: '${texts[1]}' (nauczyciel=$teacher)")
                warnings++
                continue
            }
            val classShort = "${m.groupValues[1]}${m.groupValues[2]}"
            val group = m.groupValues[3].takeIf { it.isNotBlank() }?.toIntOrNull()
            val roomOrInfo = m.groupValues[4].trim()
            if (roomOrInfo.isEmpty()) {
                Log.w(TAG, "Pusty opis sali/info dla $classShort lekcja $lessonNo")
                warnings++
            }
            out += SubstitutionDto(
                dateRaw = date,
                originalTeacher = teacher,
                lessonNumber = lessonNo,
                classShortName = classShort,
                groupNumber = group,
                roomOrInfo = roomOrInfo,
                substituteTeacher = texts[2].takeIf { it.isNotBlank() },
                notes = texts[3].takeIf { it.isNotBlank() }
            )
        }
        Log.i(TAG, "Sparsowano ${out.size} zastępstw (ostrzeżeń=$warnings)")
        return out
    }
}
