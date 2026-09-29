package pl.zse.bydgoszcz.elektron.presentation.common.theme

import pl.zse.bydgoszcz.elektron.domain.model.AccentColor
import androidx.compose.runtime.remember
import android.os.Build
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/**
 * Motyw eLektrona — spójna identyfikacja z logo zamiast kolorów z tapety,
 * tła i proporcje wzorowane na iOS (grupowane tło, białe karty, większe zaokrąglenia).
 */
val ElektronShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun ElektronTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    /** Kolory z tapety (Material You, Android 12+). Domyślnie wyłączone — paleta z logo. */
    dynamicColor: Boolean = false,
    /** Kolor akcentu (Ustawienia -> Wygląd); nie dotyczy kolorów z tapety. */
    accent: AccentColor = AccentColor.BLUE,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> remember(accent) { Accents.apply(DarkColors, Accents.dark(accent)) }
        else -> remember(accent) { Accents.apply(LightColors, Accents.light(accent)) }
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = ElektronTypography,
        shapes = ElektronShapes,
        content = content
    )
}
