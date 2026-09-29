package pl.zse.bydgoszcz.elektron.widget

import android.content.Context
import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.glance.unit.ColorProvider
import pl.zse.bydgoszcz.elektron.domain.repository.ThemeMode
import androidx.glance.color.ColorProvider as DayNightColorProvider

/**
 * Kolory widżetu wyliczone z ustawień aplikacji:
 *  - motyw jasny/ciemny/systemowy jak w aplikacji (dawniej widżety zawsze szły za systemem),
 *  - kolory z tapety (Android 12+), jeśli włączone w Ustawieniach; inaczej paleta z logo.
 */
data class WidgetPalette(
    val background: ColorProvider,
    val textPrimary: ColorProvider,
    val textSecondary: ColorProvider,
    val textFaded: ColorProvider,
    val accent: ColorProvider,
    val accentContainer: ColorProvider,
    val onAccentContainer: ColorProvider,
    val substitution: ColorProvider,
    val substitutionContainer: ColorProvider,
    /** Tło wiersza bez podświetlenia — jawnie przezroczyste (patrz DayPlanWidget.LessonRow). */
    val transparent: ColorProvider = ColorProvider(Color.Transparent)
)

/** Paleta dostarczana do treści widżetu (provideContent -> CompositionLocalProvider). */
val LocalWidgetPalette = staticCompositionLocalOf { WidgetPalettes.create(null, ThemeMode.SYSTEM, dynamic = false) }

/** Dostęp do bieżącej palety z kodu widżetów: WidgetColors.accent itd. */
object WidgetColors {
    val background: ColorProvider @Composable get() = LocalWidgetPalette.current.background
    val textPrimary: ColorProvider @Composable get() = LocalWidgetPalette.current.textPrimary
    val textSecondary: ColorProvider @Composable get() = LocalWidgetPalette.current.textSecondary
    val textFaded: ColorProvider @Composable get() = LocalWidgetPalette.current.textFaded
    val accent: ColorProvider @Composable get() = LocalWidgetPalette.current.accent
    val accentContainer: ColorProvider @Composable get() = LocalWidgetPalette.current.accentContainer
    val onAccentContainer: ColorProvider @Composable get() = LocalWidgetPalette.current.onAccentContainer
    val substitution: ColorProvider @Composable get() = LocalWidgetPalette.current.substitution
    val substitutionContainer: ColorProvider @Composable get() = LocalWidgetPalette.current.substitutionContainer
    val transparent: ColorProvider @Composable get() = LocalWidgetPalette.current.transparent
}

object WidgetPalettes {

    /** Para kolorów (jasny, ciemny) jednej roli. */
    private class Pair2(val day: Color, val night: Color)

    /** [opacity] - krycie tła widżetu w procentach (Ustawienia -> Widżety). */
    fun create(context: Context?, mode: ThemeMode, dynamic: Boolean, opacity: Int = 100): WidgetPalette {
        val bgAlpha = opacity.coerceIn(20, 100) / 100f
        val useDynamic = dynamic && context != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        val light: ColorScheme? = if (useDynamic) dynamicLightColorScheme(context) else null
        val dark: ColorScheme? = if (useDynamic) dynamicDarkColorScheme(context) else null

        fun role(brandDay: Long, brandNight: Long, alpha: Float = 1f, fromScheme: (ColorScheme) -> Color): ColorProvider {
            val base = if (light != null && dark != null) Pair2(fromScheme(light), fromScheme(dark))
            else Pair2(Color(brandDay), Color(brandNight))
            val pair = if (alpha >= 1f) base else Pair2(base.day.copy(alpha = alpha), base.night.copy(alpha = alpha))
            return when (mode) {
                ThemeMode.LIGHT -> ColorProvider(pair.day)
                ThemeMode.DARK -> ColorProvider(pair.night)
                ThemeMode.SYSTEM -> DayNightColorProvider(day = pair.day, night = pair.night)
            }
        }

        return WidgetPalette(
            background = role(0xFFFFFFFF, 0xFF1C1C1E, bgAlpha) { it.surface },
            textPrimary = role(0xFF000000, 0xFFFFFFFF) { it.onSurface },
            textSecondary = role(0xFF6C6C70, 0xFF8E8E93) { it.onSurfaceVariant },
            textFaded = role(0xFF8E8E93, 0xFF6E6E73) { it.outline },
            accent = role(0xFF0091B0, 0xFF3DCFEF) { it.primary },
            accentContainer = role(0xFFDDF5FB, 0xFF0B3440) { it.primaryContainer },
            onAccentContainer = role(0xFF003845, 0xFFBDF0FB) { it.onPrimaryContainer },
            substitution = role(0xFFA85A00, 0xFFFF9F0A) { it.tertiary },
            substitutionContainer = role(0xFFFFF0DB, 0xFF3A2A12) { it.tertiaryContainer }
        )
    }
}
