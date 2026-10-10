package pl.zse.bydgoszcz.elektron.widget

import org.junit.Assert.assertEquals
import org.junit.Test
import pl.zse.bydgoszcz.elektron.domain.repository.ThemeMode

class WidgetWallpaperModeTest {
    @Test fun opaqueBackgroundKeepsTheAppTheme() {
        assertEquals(ThemeMode.SYSTEM, WidgetWallpaper.effectiveMode(ThemeMode.SYSTEM, 100, wallpaperSupportsDarkText = false))
        assertEquals(ThemeMode.SYSTEM, WidgetWallpaper.effectiveMode(ThemeMode.SYSTEM, WidgetWallpaper.OPACITY, false))
    }

    @Test fun transparentBackgroundInAutoThemeFollowsWallpaperBrightness() {
        assertEquals(ThemeMode.DARK, WidgetWallpaper.effectiveMode(ThemeMode.SYSTEM, 0, wallpaperSupportsDarkText = false))
        assertEquals(ThemeMode.LIGHT, WidgetWallpaper.effectiveMode(ThemeMode.SYSTEM, 30, wallpaperSupportsDarkText = true))
    }

    @Test fun themeChosenInTheAppAlwaysWins() {
        // Zmiana motywu w aplikacji musi zmieniać także przezroczyste widżety.
        assertEquals(ThemeMode.LIGHT, WidgetWallpaper.effectiveMode(ThemeMode.LIGHT, 0, wallpaperSupportsDarkText = false))
        assertEquals(ThemeMode.DARK, WidgetWallpaper.effectiveMode(ThemeMode.DARK, 0, wallpaperSupportsDarkText = true))
    }

    @Test fun withoutWallpaperInfoTheThemeStays() {
        assertEquals(ThemeMode.SYSTEM, WidgetWallpaper.effectiveMode(ThemeMode.SYSTEM, 0, wallpaperSupportsDarkText = null))
    }
}
