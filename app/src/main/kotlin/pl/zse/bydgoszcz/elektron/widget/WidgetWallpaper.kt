package pl.zse.bydgoszcz.elektron.widget

import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.Context
import android.os.Build
import pl.zse.bydgoszcz.elektron.domain.repository.ThemeMode

/**
 * Przy (prawie) przezroczystym tle tekst widżetu leży na tapecie, a nie na tle widżetu: czarny
 * tekst trybu jasnego na ciemnej tapecie był nieczytelny, a jasne wiersze wyglądały jak naklejki.
 * Wtedy kolory treści (i resztki tła) idą za jasnością tapety - ciemny tekst na jasnej tapecie,
 * jasny na ciemnej. Tylko w motywie "Auto": wybrany w aplikacji motyw jasny albo ciemny zawsze
 * obowiązuje (inaczej zmiana motywu nie zmieniała widżetów). Bez informacji o tapecie (Android 8.0,
 * część tapet animowanych) - motyw jak dotąd.
 */
object WidgetWallpaper {
    /** Poniżej tego krycia tła (%) o kolorach treści decyduje tapeta. */
    const val OPACITY = 60

    fun effectiveMode(mode: ThemeMode, opacity: Int, wallpaperSupportsDarkText: Boolean?): ThemeMode =
        if (mode != ThemeMode.SYSTEM || opacity >= OPACITY || wallpaperSupportsDarkText == null) mode
        else if (wallpaperSupportsDarkText) ThemeMode.LIGHT else ThemeMode.DARK

    /** Czy na tapecie ekranu głównego lepiej widać ciemny tekst; null - brak informacji. */
    fun supportsDarkText(context: Context): Boolean? = runCatching {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) return@runCatching null
        supportsDarkText(WallpaperManager.getInstance(context).getWallpaperColors(WallpaperManager.FLAG_SYSTEM))
    }.getOrNull()

    /** To samo dla kolorów podanych przez system (np. w słuchaczu zmian tapety). */
    fun supportsDarkText(colors: WallpaperColors?): Boolean? = runCatching {
        if (colors == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) return@runCatching null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) (colors.colorHints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT) != 0
        else colors.primaryColor.luminance() > 0.5f
    }.getOrNull()
}
