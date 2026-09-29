package pl.zse.bydgoszcz.elektron.domain.model

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Odcienie akcentu z dowolnego koloru (własny kolor z próbnika). Kolory ARGB jako Long.
 * Gwarancje (testy w AccentMathTest), dla każdego wybranego koloru:
 *  - jasny motyw: primary na białym >= 4.5:1 (czytelny tekst i ikony),
 *  - ciemny motyw: primary na #1C1C1E >= 6:1,
 *  - tekst na tle wyróżnienia (container) >= 7:1 w obu motywach.
 */
object AccentMath {

    data class Tones(val primary: Long, val onPrimary: Long, val container: Long, val onContainer: Long)

    data class Hsl(val h: Float, val s: Float, val l: Float)

    private const val WHITE = 0xFFFFFFFF
    private const val DARK_SURFACE = 0xFF1C1C1E

    fun light(seed: Long): Tones {
        val (h, s, l) = toHsl(seed)
        var pl = min(l, 0.5f)
        var primary = fromHsl(h, s, pl)
        while (contrast(primary, WHITE) < 4.6 && pl > 0.05f) {
            pl -= 0.01f; primary = fromHsl(h, s, pl)
        }
        val container = fromHsl(h, min(s, 0.75f), 0.92f)
        val onContainer = fromHsl(h, min(s, 0.8f), 0.16f)
        return Tones(primary, WHITE, container, onContainer)
    }

    fun dark(seed: Long): Tones {
        val (h, s, l) = toHsl(seed)
        var pl = max(l, 0.6f)
        var primary = fromHsl(h, s, pl)
        while (contrast(primary, DARK_SURFACE) < 6.2 && pl < 0.97f) {
            pl += 0.01f; primary = fromHsl(h, s, pl)
        }
        val onPrimary = fromHsl(h, min(s, 0.8f), 0.12f)
        val container = fromHsl(h, min(s, 0.5f), 0.2f)
        val onContainer = fromHsl(h, min(s, 0.7f), 0.9f)
        return Tones(primary, onPrimary, container, onContainer)
    }

    // --- kolor ---

    fun toHsl(argb: Long): Hsl {
        val r = ((argb shr 16) and 0xFF) / 255f
        val g = ((argb shr 8) and 0xFF) / 255f
        val b = (argb and 0xFF) / 255f
        val mx = max(r, max(g, b)); val mn = min(r, min(g, b))
        val l = (mx + mn) / 2f
        val d = mx - mn
        if (d < 1e-6f) return Hsl(0f, 0f, l)
        val s = d / (1f - abs(2f * l - 1f))
        val h = when (mx) {
            r -> 60f * (((g - b) / d) % 6f)
            g -> 60f * ((b - r) / d + 2f)
            else -> 60f * ((r - g) / d + 4f)
        }
        return Hsl((h + 360f) % 360f, s.coerceIn(0f, 1f), l)
    }

    fun fromHsl(h: Float, s: Float, l: Float): Long {
        val c = (1f - abs(2f * l - 1f)) * s
        val hp = ((h % 360f) + 360f) % 360f / 60f
        val x = c * (1f - abs(hp % 2f - 1f))
        val (r1, g1, b1) = when {
            hp < 1f -> Triple(c, x, 0f)
            hp < 2f -> Triple(x, c, 0f)
            hp < 3f -> Triple(0f, c, x)
            hp < 4f -> Triple(0f, x, c)
            hp < 5f -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        val m = l - c / 2f
        fun ch(v: Float) = ((v + m).coerceIn(0f, 1f) * 255f + 0.5f).toLong()
        return (0xFFL shl 24) or (ch(r1) shl 16) or (ch(g1) shl 8) or ch(b1)
    }

    /** Współczynnik kontrastu WCAG 2.x. */
    fun contrast(a: Long, b: Long): Double {
        val la = luminance(a); val lb = luminance(b)
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    private fun luminance(argb: Long): Double {
        fun lin(c: Long): Double {
            val v = c / 255.0
            return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * lin((argb shr 16) and 0xFF) + 0.7152 * lin((argb shr 8) and 0xFF) + 0.0722 * lin(argb and 0xFF)
    }
}
