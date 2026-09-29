package pl.zse.bydgoszcz.elektron.domain.model

/** Wygląd planu lekcji (Ustawienia -> Wygląd planu). */
data class TimetableLook(
    /** Kompaktowy: mniejsze odstępy, godziny w jednej linii - więcej lekcji na ekranie. */
    val compact: Boolean = false,
    val showRoom: Boolean = true,
    val showTeacher: Boolean = true
)
