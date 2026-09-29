package pl.zse.bydgoszcz.elektron.domain.model

/** Wygląd widżetów (Ustawienia -> Widżety), wspólny dla wszystkich widżetów. */
data class WidgetLook(
    /** Krycie tła w procentach (100 = pełne). */
    val opacity: Int = 100,
    val showTeacher: Boolean = true,
    val showRoom: Boolean = true
) {
    companion object {
        val OPACITY_OPTIONS = listOf(100, 85, 70, 50)
    }
}
