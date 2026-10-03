package pl.zse.bydgoszcz.elektron.widget

/**
 * Ile wierszy zastępstw zmieści widżet o wysokości [heightDp] (dp). Wiersz ok. [ROW_DP] dp,
 * nagłówek i marginesy [CHROME_DP] dp; od 1 do [MAX_ROWS] wierszy. Gdy zastępstw jest więcej,
 * ostatni wiersz to "+N więcej" (otwiera zakładkę Zastępstwa).
 */
object SubstitutionRows {
    const val ROW_DP = 52f
    /** Marginesy widżetu (2 x 14 dp) + nagłówek (ok. 20 dp) + odstęp (8 dp). */
    const val CHROME_DP = 56f
    const val MAX_ROWS = 6

    /** [shown] zastępstw i [more] w wierszu "+N więcej" (0 - bez niego). */
    data class Layout(val shown: Int, val more: Int)

    fun capacity(heightDp: Float): Int {
        if (heightDp.isNaN() || heightDp <= CHROME_DP) return 1
        return ((heightDp - CHROME_DP) / ROW_DP).toInt().coerceIn(1, MAX_ROWS)
    }

    fun layout(heightDp: Float, count: Int): Layout {
        if (count <= 0) return Layout(0, 0)
        val rows = capacity(heightDp)
        if (count <= rows) return Layout(count, 0)
        // Jedno miejsce zajmuje "+N więcej"; co najmniej jedno zastępstwo zawsze widać.
        val shown = (rows - 1).coerceAtLeast(1)
        return Layout(shown, count - shown)
    }
}
