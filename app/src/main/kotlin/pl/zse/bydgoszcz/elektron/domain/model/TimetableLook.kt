package pl.zse.bydgoszcz.elektron.domain.model

/**
 * Wygląd planu lekcji (Ustawienia -> Wygląd planu). Karty lekcji są zawsze kompaktowe
 * (od 0.6 bez przełącznika - godziny w jednej linii, mniejsze odstępy).
 */
data class TimetableLook(
    val showRoom: Boolean = true,
    val showTeacher: Boolean = true,
    /** Ostatnio wybrany tryb planu: tydzień (true) albo dzień (false). */
    val weekView: Boolean = false
)
