package pl.zse.bydgoszcz.elektron.widget

/** RemoteViews cannot measure text before sending it to the launcher. Reserve line
 * heights (including system font scaling) and let Text ellipsize the last line. */
internal object WidgetNoteLayout {
    fun lines(heightDp: Float, reservedDp: Float, fontScale: Float = 1f): Int =
        ((heightDp - reservedDp) / (18f * fontScale.coerceAtLeast(1f))).toInt().coerceIn(0, 2000)

    fun dayPlanStart(state: WidgetState.Ready): Int =
        if (state.lessons.any { !it.userNote.isNullOrBlank() }) 0 else if (state.isToday) state.focusIndex else 0

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
