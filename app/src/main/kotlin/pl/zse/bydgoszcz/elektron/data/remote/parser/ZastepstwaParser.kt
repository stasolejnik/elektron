package pl.zse.bydgoszcz.elektron.data.remote.parser

import android.util.Log
import org.jsoup.nodes.Document
import pl.zse.bydgoszcz.elektron.data.remote.dto.SubstitutionDto

/**
 * Parser zastępstw z zastepstwa.zse.bydgoszcz.pl (ISO-8859-2 — wymuszone na poziomie HTTP).
 *
 * Przykład HTML (eksport Optivum):
 *   <TR><TD class=st0 COLSPAN=4>Zastępstwa w dniu 01.10.2026 czwartek</TD></TR>
 *   <TR><TD class=st1 COLSPAN=4>Izabela Kowalska</TD></TR>
 *   <TR><TD class=st4>lekcja</TD><TD class=st5>opis</TD><TD class=st5>zastępca</TD><TD class=st6>uwagi</TD></TR>
 *   <TR><TD class=st14>7</TD><TD class=st15>1 D(2) - Zajęcia Świetlicowe</TD><TD>&nbsp;</TD><TD>&nbsp;</TD></TR>
 *   <TR><TD class=st10>&nbsp;</TD>...</TR>      <- pusty odstęp między nauczycielami
 *
 * WAŻNE: nazwy klas CSS (st0, st7, st14...) Optivum nadaje przy KAŻDYM eksporcie od nowa,
 * numerując kolejne style strony - zmieniają się z dnia na dzień. Dawniej parser rozpoznawał
 * wiersze po "st7"/"st10" i 30.09.2026 odczytał 1 zastępstwo z kilkunastu (zgubił m.in. 1D).
 * Teraz wyłącznie struktura tabeli:
 *  - 1 komórka + "Zastępstwa w dniu dd.mm.rrrr" -> data,
 *  - 1 komórka tuż przed nagłówkami kolumn (albo tuż przed pierwszym wpisem, gdy
 *    nagłówków brak) -> nieobecny nauczyciel; wcześniejsza jednokomórkowa informacja
 *    zostaje nadpisana przez nazwisko stojące bezpośrednio nad wpisami,
 *  - 4 komórki, pierwsza to numer lekcji -> wpis; puste wiersze - pomijane.
 */
object ZastepstwaParser {
    private const val TAG = "ZastepstwaParser"
    private val DATE_RE = Regex("Zastępstwa w dniu\\s+(\\d{2}\\.\\d{2}\\.\\d{4})")
    private val DESC_RE = Regex("^(\\d+)\\s*([A-Z])(?:\\((\\d+)\\))?\\s*-\\s*(.+)$")
    private val COLUMN_HEADERS = setOf("lekcja", "opis", "zastępca", "uwagi")

    /**
     * Daty ("dd.mm.rrrr") z nagłówków "Zastępstwa w dniu ..." - także dni BEZ wpisów (szkoła
     * odwołała wszystkie zastępstwa). Repozytorium zastępuje tylko te dni.
     */
    fun pageDates(doc: Document): Set<String> =
        doc.select("table tr").mapNotNull { row ->
            val cells = row.select("> td, > th")
            if (cells.size != 1) null else DATE_RE.find(cells[0].text())?.groupValues?.get(1)
        }.toSet()

    fun parse(doc: Document): List<SubstitutionDto> {
        val tables = doc.select("table")
        if (tables.isEmpty()) {
            Log.w(TAG, "Brak <table> w dokumencie zastępstw")
            return emptyList()
        }
        val out = mutableListOf<SubstitutionDto>()
        var currentDate: String? = null
        var currentTeacher: String? = null
        var candidateTeacher: String? = null   // ostatni jednokomórkowy wiersz (nie data)
        var warnings = 0

        // Wszystkie tabele po kolei (dotąd: tylko pierwsza) - eksport bywa dzielony.
        for (row in tables.select("tr")) {
            val cells = row.select("> td, > th")
            if (cells.isEmpty()) continue
            val texts = cells.map { it.text().replace('\u00A0', ' ').trim() }

            if (cells.size == 1) {
                val text = texts[0]
                val date = DATE_RE.find(text)?.groupValues?.get(1)
                when {
                    date != null -> {
                        currentDate = date
                        currentTeacher = null
                        candidateTeacher = null
                    }
                    text.isNotBlank() -> candidateTeacher = text
                }
                continue
            }

            if (cells.size != 4) continue
            if (texts.all { it.isEmpty() }) continue                 // odstęp między nauczycielami

            // Nagłówki kolumn zaczynają blok nauczyciela z wiersza tuż nad nimi.
            if (texts.map { it.lowercase() }.toSet() == COLUMN_HEADERS) {
                currentTeacher = candidateTeacher
                candidateTeacher = null
                continue
            }

            val lessonNo = texts[0].toIntOrNull()
            // Blok nauczyciela bez wiersza nagłówków kolumn - nazwisko tuż nad wpisem.
            if (lessonNo != null && candidateTeacher != null) {
                currentTeacher = candidateTeacher
                candidateTeacher = null
            }
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
