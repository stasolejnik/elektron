package pl.zse.bydgoszcz.elektron.presentation.common.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import pl.zse.bydgoszcz.elektron.domain.model.AccentColor

/** Odcienie akcentu dla jednego trybu (jasny/ciemny). */
data class AccentTones(
    val primary: Color,
    val onPrimary: Color,
    val container: Color,
    val onContainer: Color
)

/**
 * Palety akcentu. Kolor "primary" w trybie jasnym ma kontrast z białym >= 4.5:1 (tekst,
 * ikony), w ciemnym - z czarnym. "container" to delikatne tło wyróżnień (karta najbliższej
 * lekcji, zaznaczony przycisk, podświetlona lekcja w widżecie).
 */
object Accents {

    fun light(accent: AccentColor): AccentTones = when (accent) {
        AccentColor.BLUE -> AccentTones(Color(0xFF007C98), Color.White, Color(0xFFD3F1F8), Color(0xFF00343F))
        AccentColor.INDIGO -> AccentTones(Color(0xFF4A55C8), Color.White, Color(0xFFE2E5FF), Color(0xFF151B5C))
        AccentColor.PURPLE -> AccentTones(Color(0xFF8036C0), Color.White, Color(0xFFF1E4FD), Color(0xFF30104F))
        AccentColor.GREEN -> AccentTones(Color(0xFF237A45), Color.White, Color(0xFFD9F2E1), Color(0xFF0A3A1E))
        AccentColor.PINK -> AccentTones(Color(0xFFC0185A), Color.White, Color(0xFFFCE1EB), Color(0xFF4A0722))
        AccentColor.GRAPHITE -> AccentTones(Color(0xFF3A3A3C), Color.White, Color(0xFFE3E3E8), Color(0xFF1C1C1E))
    }

    fun dark(accent: AccentColor): AccentTones = when (accent) {
        AccentColor.BLUE -> AccentTones(Color(0xFF3DCFEF), Color(0xFF00313D), Color(0xFF0B3440), Color(0xFFBDF0FB))
        AccentColor.INDIGO -> AccentTones(Color(0xFF9DA7FF), Color(0xFF151B5C), Color(0xFF262D63), Color(0xFFDDE1FF))
        AccentColor.PURPLE -> AccentTones(Color(0xFFC99BFF), Color(0xFF30104F), Color(0xFF3A2458), Color(0xFFF0E2FF))
        AccentColor.GREEN -> AccentTones(Color(0xFF5FD68A), Color(0xFF00391A), Color(0xFF163B25), Color(0xFFC8F5D6))
        AccentColor.PINK -> AccentTones(Color(0xFFFF8AB5), Color(0xFF4A0722), Color(0xFF4A1A2E), Color(0xFFFFD9E6))
        AccentColor.GRAPHITE -> AccentTones(Color(0xFFE5E5EA), Color(0xFF1C1C1E), Color(0xFF3A3A3C), Color(0xFFF2F2F7))
    }

    /** Kółko w wyborze palety. */
    fun swatch(accent: AccentColor): Color = light(accent).primary

    /**
     * Schemat kolorów z akcentem. Zaznaczone elementy Material (przyciski segmentowe, chipy)
     * używają secondaryContainer - dostają odcień akcentu zamiast szarości.
     */
    fun apply(base: ColorScheme, tones: AccentTones): ColorScheme = base.copy(
        primary = tones.primary,
        onPrimary = tones.onPrimary,
        primaryContainer = tones.container,
        onPrimaryContainer = tones.onContainer,
        secondaryContainer = tones.container,
        onSecondaryContainer = tones.onContainer,
        surfaceTint = tones.primary
    )
}
