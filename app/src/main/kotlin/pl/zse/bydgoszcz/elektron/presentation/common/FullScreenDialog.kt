package pl.zse.bydgoszcz.elektron.presentation.common

import android.os.Build
import android.view.WindowManager
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat

/**
 * Okno pełnoekranowego Dialogu (decorFitsSystemWindows = false) od krawędzi do krawędzi.
 * Bez tego na telefonach z wycięciem w ekranie okno zaczyna się pod paskiem stanu, a ma wysokość
 * całego ekranu - dół (mapa) wychodzi poza ekran. Wcięcia paska nawigacji okno dialogu nie dostaje -
 * dolny odstęp trzeba wziąć z okna aplikacji. Ikony pasków w kolorze motywu aplikacji.
 * Wołać wewnątrz treści Dialogu.
 */
@Composable
fun EdgeToEdgeDialogWindow() {
    val view = LocalView.current
    val lightBars = MaterialTheme.colorScheme.background.luminance() > 0.5f
    SideEffect {
        val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
        // Parametry okna tylko przy zmianie - przypisanie attributes przelicza okno (np. przy odświeżaniu mapy co 10 s).
        val attrs = window.attributes
        val cutoutOk = Build.VERSION.SDK_INT < Build.VERSION_CODES.P ||
            attrs.layoutInDisplayCutoutMode == WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        val inScreen = attrs.flags and WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN != 0
        if (!cutoutOk && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = attrs.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        // Okno liczone od górnej krawędzi ekranu, a nie od dołu paska stanu.
        if (!inScreen) window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)
        WindowCompat.getInsetsController(window, view).apply {
            if (isAppearanceLightStatusBars != lightBars) isAppearanceLightStatusBars = lightBars
            if (isAppearanceLightNavigationBars != lightBars) isAppearanceLightNavigationBars = lightBars
        }
    }
}
