package pl.zse.bydgoszcz.elektron.presentation.common

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import pl.zse.bydgoszcz.elektron.domain.model.SubjectStyle
import pl.zse.bydgoszcz.elektron.domain.model.SubjectStyles
import pl.zse.bydgoszcz.elektron.domain.model.TimetableLook

/** Personalizacja dostępna w całym UI (podawana w ElektronActivity). */
data class Personalization(
    val styles: Map<String, SubjectStyle> = emptyMap(),
    val look: TimetableLook = TimetableLook()
) {
    /** Nazwa przedmiotu do wyświetlenia (własna albo ze strony szkoły, bez sufiksu grupy). */
    fun subjectName(subject: String?): String? = SubjectStyles.displayName(subject, styles)

    fun subjectColor(subject: String?): Color? = SubjectStyles.colorOf(subject, styles)?.let { Color(it) }
}

val LocalPersonalization = compositionLocalOf { Personalization() }
