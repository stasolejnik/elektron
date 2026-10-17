package pl.zse.bydgoszcz.elektron.widget

/** RemoteViews cannot measure text before sending it to the launcher. Reserve line
 * heights (including system font scaling) and let Text ellipsize the last line. */
internal object WidgetNoteLayout {
    fun lines(heightDp: Float, reservedDp: Float, fontScale: Float = 1f): Int =
        ((heightDp - reservedDp) / (18f * fontScale.coerceAtLeast(1f))).toInt().coerceIn(0, 2000)

    /** Co zmieści się w widżecie Następna lekcja (pod nagłówkiem, tytułem i godzinami). */
    data class NextLessonPlan(
        val timeRow: Boolean, val followingLines: Int, val detail: Boolean,
        val titleLines: Int, val noteLines: Int, val bigNumber: Boolean
    )

    /**
     * Przydział wysokości widżetu Następna lekcja według faktycznych wysokości linii (rozmiar czcionki
     * x [fontScale]), po kolei od najważniejszego: kolejna lekcja (1 linia), czas z paskiem, nauczyciel,
     * druga linia kolejnej lekcji, druga linia tytułu, notatka, duży numer lekcji. Dawniej stałe progi
     * (150/180/210 dp x skala) zostawiały w średnim widżecie dużą pustą przerwę.
     */
    fun nextLesson(
        heightDp: Float, fontScale: Float, hasStatus: Boolean, hasProgress: Boolean, hasDetail: Boolean,
        hasSchoolNote: Boolean, hasFollowing: Boolean, hasUserNote: Boolean
    ): NextLessonPlan {
        val scale = fontScale.coerceAtLeast(1f)
        fun line(sp: Float) = sp * LINE_HEIGHT * scale
        var free = heightDp - PADDING_DP - maxOf(16f, line(11f)) - line(15f) - 2f - line(12f) -
            (if (hasSchoolNote) line(12f) else 0f) - SAFETY_DP
        fun take(dp: Float): Boolean = (free >= dp).also { if (it) free -= dp }
        // Kolejna lekcja przed paskiem: bez wiersza czasu odliczanie i tak jest w nagłówku.
        var followingLines = if (hasFollowing && take(4f + line(11f))) 1 else 0
        val timeRow = hasStatus && take((if (hasProgress) 10f else 0f) + line(11f))
        val detail = hasDetail && take(line(12f))
        if (followingLines == 1 && take(line(12f))) followingLines = 2
        val titleLines = if (take(line(15f))) 2 else 1
        val noteLines = if (hasUserNote) (free / line(12f)).toInt().coerceAtLeast(0).also { free -= it * line(12f) } else 0
        val bigNumber = take(line(30f))
        return NextLessonPlan(timeRow, followingLines, detail, titleLines, noteLines, bigNumber)
    }

    private const val LINE_HEIGHT = 1.2f
    private const val PADDING_DP = 28f
    private const val SAFETY_DP = 2f

    // Dziś zawsze od bieżącej lekcji, także w dzień z notatką (dawniej notatka do dowolnej
    // lekcji pokazywała minione i spychała trwającą poza mały widżet). Notatki minionych
    // lekcji są w szczegółach lekcji i w "Moich notatkach".
    fun dayPlanStart(state: WidgetState.Ready): Int = if (state.isToday) state.firstNotPastIndex else 0

    fun dayPlanLines(heightDp: Float, lessonCount: Int, schoolNote: Boolean, fontScale: Float): Int {
        val perRow = (heightDp - 54f * fontScale.coerceAtLeast(1f)).coerceAtLeast(0f) / lessonCount.coerceIn(1, 4)
        return lines(perRow, 44f * fontScale.coerceAtLeast(1f) + if (schoolNote) 18f * fontScale else 0f, fontScale).coerceIn(1, 4)
    }

    data class Subs(val shown: Int, val more: Int, val noteLines: List<Int>)

    fun substitutions(heightDp: Float, items: List<WidgetSubstitution>, fontScale: Float = 1f): Subs {
        if (items.isEmpty()) return Subs(0, 0, emptyList())
        val scale = fontScale.coerceAtLeast(1f)
        val line = 18f * scale
        val budget = (heightDp - SubstitutionRows.CHROME_DP).coerceAtLeast(0f)
        fun base(index: Int) = 18f + 34f * scale + if (items[index].note != null) line else 0f
        var shown = 0
        var used = 0f
        for (i in items.indices.take(SubstitutionRows.MAX_ROWS)) {
            val row = base(i) + if (items[i].userNote != null) line else 0f
            val more = if (i + 1 < items.size) 32f * scale else 0f
            if (used + row + more > budget) break
            used += row
            shown++
        }
        // Preserve the first substitution even at the launcher's minimum size.
        shown = shown.coerceAtLeast(1)
        var remaining = budget - (0 until shown).sumOf { base(it).toDouble() }.toFloat() -
            if (shown < items.size) 32f * scale else 0f
        val noteLines = MutableList(shown) { 0 }
        // Give every visible note a line before expanding any of them.
        while (remaining >= line && (0 until shown).any { items[it].userNote != null }) {
            for (i in 0 until shown) {
                if (items[i].userNote != null && remaining >= line) {
                    noteLines[i]++
                    remaining -= line
                }
            }
        }
        return Subs(shown, items.size - shown, noteLines)
    }
}
