package pl.zse.bydgoszcz.elektron.presentation.common.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Paleta eLektron — akcent z logo (atom #00B6DD) + neutralne tła w stylu iOS
 * (systemGroupedBackground, secondaryLabel, separator).
 *
 * #00B6DD ma na białym za słaby kontrast dla tekstu (~2.3:1), więc w trybie jasnym
 * akcent jest przyciemniony (#0091B0), a w ciemnym rozjaśniony (#3DCFEF).
 */
object ElektronColors {
    val Brand = Color(0xFF00B6DD)

    // iOS: pomarańcz (systemOrange) — wyróżnienie zastępstw.
    val SubstitutionLight = Color(0xFFFF9500)
    val SubstitutionDark = Color(0xFFFF9F0A)
}

val LightColors = lightColorScheme(
    primary = Color(0xFF0091B0),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDF5FB),
    onPrimaryContainer = Color(0xFF003845),
    secondary = Color(0xFF5B6770),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE6ECEF),
    onSecondaryContainer = Color(0xFF1C2328),
    tertiary = Color(0xFFC96F00),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFF0DB),
    onTertiaryContainer = Color(0xFF4F2C00),
    error = Color(0xFFFF3B30),
    onError = Color.White,
    errorContainer = Color(0xFFFFE5E3),
    onErrorContainer = Color(0xFF5C0A05),
    background = Color(0xFFF2F2F7),
    onBackground = Color(0xFF000000),
    surface = Color(0xFFF2F2F7),
    onSurface = Color(0xFF000000),
    surfaceVariant = Color(0xFFFFFFFF),
    onSurfaceVariant = Color(0xFF6C6C70),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFF9F9F9),
    surfaceContainerHigh = Color(0xFFFFFFFF),
    surfaceContainerHighest = Color(0xFFFFFFFF),
    outline = Color(0xFFC6C6C8),
    outlineVariant = Color(0xFFE5E5EA)
)

val DarkColors = darkColorScheme(
    primary = Color(0xFF3DCFEF),
    onPrimary = Color(0xFF00313D),
    primaryContainer = Color(0xFF0B3440),
    onPrimaryContainer = Color(0xFFBDF0FB),
    secondary = Color(0xFFB5C1C8),
    onSecondary = Color(0xFF1C2328),
    secondaryContainer = Color(0xFF2C2C2E),
    onSecondaryContainer = Color(0xFFE6ECEF),
    tertiary = Color(0xFFFF9F0A),
    onTertiary = Color(0xFF3A2000),
    tertiaryContainer = Color(0xFF3A2A12),
    onTertiaryContainer = Color(0xFFFFDDB0),
    error = Color(0xFFFF453A),
    onError = Color.White,
    errorContainer = Color(0xFF3B1412),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF000000),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF1C1C1E),
    onSurfaceVariant = Color(0xFF8E8E93),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF1C1C1E),
    surfaceContainer = Color(0xFF121212),
    surfaceContainerHigh = Color(0xFF1C1C1E),
    surfaceContainerHighest = Color(0xFF1C1C1E),
    outline = Color(0xFF48484A),
    outlineVariant = Color(0xFF38383A)
)
