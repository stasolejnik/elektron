package pl.zse.bydgoszcz.elektron.widget

import androidx.compose.ui.graphics.Color
import androidx.glance.color.ColorProvider

/** Kolory widżetów — te same co w aplikacji (jasny/ciemny, iOS-owe neutralne tła + akcent z logo). */
object WidgetColors {
    val background = ColorProvider(day = Color(0xFFFFFFFF), night = Color(0xFF1C1C1E))
    val textPrimary = ColorProvider(day = Color(0xFF000000), night = Color(0xFFFFFFFF))
    val textSecondary = ColorProvider(day = Color(0xFF6C6C70), night = Color(0xFF8E8E93))
    val textFaded = ColorProvider(day = Color(0xFFAEAEB2), night = Color(0xFF48484A))
    val accent = ColorProvider(day = Color(0xFF0091B0), night = Color(0xFF3DCFEF))
    val accentContainer = ColorProvider(day = Color(0xFFDDF5FB), night = Color(0xFF0B3440))
    val onAccentContainer = ColorProvider(day = Color(0xFF003845), night = Color(0xFFBDF0FB))
    val substitution = ColorProvider(day = Color(0xFFC96F00), night = Color(0xFFFF9F0A))
    val substitutionContainer = ColorProvider(day = Color(0xFFFFF0DB), night = Color(0xFF3A2A12))
}
