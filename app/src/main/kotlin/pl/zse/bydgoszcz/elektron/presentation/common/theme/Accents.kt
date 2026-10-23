package pl.zse.bydgoszcz.elektron.presentation.common.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import pl.zse.bydgoszcz.elektron.domain.model.AccentColor
import pl.zse.bydgoszcz.elektron.domain.model.AccentMath
import pl.zse.bydgoszcz.elektron.domain.model.AccentSetting

/** Odcienie akcentu dla jednego trybu (jasny/ciemny). */
data class AccentTones(
    val primary: Color,
    val onPrimary: Color,
    val container: Color,
    val onContainer: Color
)

/**
 * Palety akcentu (gotowe odcienie) i własny kolor (odcienie z AccentMath). Kolor "primary"
 * w trybie jasnym ma kontrast z białym >= 4.5:1 (tekst, ikony), w ciemnym - z czarnym. "container" to delikatne tło wyróżnień (karta najbliższej
 * lekcji, zaznaczony przycisk, podświetlona lekcja w widżecie).
 */
object Accents {

    fun light(accent: AccentSetting): AccentTones = when (accent.color) {
        AccentColor.GREEN -> AccentTones(Color(0xFF237A45), Color.White, Color(0xFFD9F2E1), Color(0xFF0A3A1E))
        AccentColor.BLUE -> AccentTones(Color(0xFF007C98), Color.White, Color(0xFFD3F1F8), Color(0xFF00343F))
        AccentColor.PURPLE -> AccentTones(Color(0xFF8036C0), Color.White, Color(0xFFF1E4FD), Color(0xFF30104F))
        AccentColor.PINK -> AccentTones(Color(0xFFC0185A), Color.White, Color(0xFFFCE1EB), Color(0xFF4A0722))
        AccentColor.RED -> AccentTones(Color(0xFFD22D2D), Color.White, Color(0xFFF8DDDD), Color(0xFF430E0E))
        AccentColor.CUSTOM -> AccentMath.light(accent.custom).toTones()
    }

    fun dark(accent: AccentSetting): AccentTones = when (accent.color) {
        AccentColor.GREEN -> AccentTones(Color(0xFF5FD68A), Color(0xFF00391A), Color(0xFF163B25), Color(0xFFC8F5D6))
        AccentColor.BLUE -> AccentTones(Color(0xFF3DCFEF), Color(0xFF00313D), Color(0xFF0B3440), Color(0xFFBDF0FB))
        AccentColor.PURPLE -> AccentTones(Color(0xFFC99BFF), Color(0xFF30104F), Color(0xFF3A2458), Color(0xFFF0E2FF))
        AccentColor.PINK -> AccentTones(Color(0xFFFF8AB5), Color(0xFF4A0722), Color(0xFF4A1A2E), Color(0xFFFFD9E6))
        AccentColor.RED -> AccentTones(Color(0xFFE48181), Color(0xFF330B0B), Color(0xFF4D1A1A), Color(0xFFF6D5D5))
        AccentColor.CUSTOM -> AccentMath.dark(accent.custom).toTones()
    }

    /** Kółko palety w Ustawieniach (dla własnego - wybrany kolor). */
    fun swatch(color: AccentColor, custom: Long): Color =
        if (color == AccentColor.CUSTOM) Color(custom) else light(AccentSetting(color)).primary

    private fun AccentMath.Tones.toTones() =
        AccentTones(Color(primary), Color(onPrimary), Color(container), Color(onContainer))

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

/** Główny wygląd (BaseLooks) na schemacie Material: tła, karty, linie, drugorzędny tekst. */
object Looks {
    fun apply(base: ColorScheme, tones: pl.zse.bydgoszcz.elektron.domain.model.BaseLooks.Tones): ColorScheme {
        val card = Color(tones.card)
        return base.copy(
            background = Color(tones.background),
            surface = Color(tones.background),
            onBackground = Color(tones.onSurface),
            onSurface = Color(tones.onSurface),
            surfaceVariant = Color(tones.surfaceVariant),
            onSurfaceVariant = Color(tones.onSurfaceVariant),
            surfaceContainerLowest = if (base.background.luminance() < 0.5f) Color(tones.background) else card,
            surfaceContainerLow = card,
            surfaceContainer = Color(tones.cardMuted),
            surfaceContainerHigh = card,
            surfaceContainerHighest = card,
            outline = Color(tones.outline),
            outlineVariant = Color(tones.outlineVariant),
            // Zwykłe (niezaznaczone) przyciski tonalne - lekko w kolorze wyglądu.
            secondary = Color(tones.onSurfaceVariant),
            inverseSurface = Color(tones.onSurface),
            inverseOnSurface = Color(tones.background)
        )
    }

    fun scheme(dark: Boolean, accent: AccentSetting, look: pl.zse.bydgoszcz.elektron.domain.model.LookSetting): ColorScheme {
        val base = if (dark) DarkColors else LightColors
        // Klasyczny = dotychczasowa paleta bez zmian (także secondary i kolory odwrócone).
        val tinted = if (look.look == pl.zse.bydgoszcz.elektron.domain.model.BaseLook.CLASSIC) base
            else apply(base, if (dark) pl.zse.bydgoszcz.elektron.domain.model.BaseLooks.dark(look) else pl.zse.bydgoszcz.elektron.domain.model.BaseLooks.light(look))
        return Accents.apply(tinted, if (dark) Accents.dark(accent) else Accents.light(accent))
    }
}

