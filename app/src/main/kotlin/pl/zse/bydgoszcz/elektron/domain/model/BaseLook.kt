package pl.zse.bydgoszcz.elektron.domain.model

/**
 * Główny wygląd aplikacji (Ustawienia -> Wygląd): tła, karty, linie i drugorzędny tekst - osobno
 * od koloru akcentu. Kolejność = kolejność kółek; na końcu własny.
 */
enum class BaseLook(val key: String) {
    /** Dotychczasowy: szare tło i białe karty, w ciemnym czerń (jak iOS). */
    CLASSIC("classic"),
    /** Ciemnoszary zamiast czerni, w jasnym chłodniejsza szarość. */
    GRAPHITE("graphite"),
    /** Ciepły papier / ciepły brąz. */
    WARM("warm"),
    /** Morski granat. */
    OCEAN("ocean"),
    /** Leśna zieleń. */
    FOREST("forest"),
    /** Własny: odcień i intensywność z próbnika. */
    CUSTOM("custom");

    companion object {
        fun fromKey(key: String?): BaseLook = entries.firstOrNull { it.key == key } ?: CLASSIC
    }
}

/** Wybór wyglądu i własne ustawienia (pamiętane także przy innym wyglądzie). */
data class LookSetting(
    val look: BaseLook = BaseLook.CLASSIC,
    /** Odcień 0..360. */
    val hue: Float = DEFAULT_HUE,
    /** Intensywność zabarwienia 0..1 (0 - neutralna szarość, w ciemnym czerń). */
    val tint: Float = DEFAULT_TINT
) {
    companion object {
        const val DEFAULT_HUE = 250f
        const val DEFAULT_TINT = 0.5f
    }
}

/**
 * Kolory wyglądu (ARGB). Gwarancje (BaseLookTest), dla każdego odcienia i intensywności:
 * tekst na tle i kartach >= 12:1, drugorzędny tekst na kartach >= 4.5:1, karty odróżnialne od tła.
 */
object BaseLooks {
    data class Tones(
        val background: Long,
        /** Karty, arkusze, okna (surfaceContainerHigh i pokrewne). */
        val card: Long,
        /** Spokojniejsze tło wewnątrz kart (surfaceContainer). */
        val cardMuted: Long,
        /** O odcień inny niż karty - patrz LightColors.surfaceVariant. */
        val surfaceVariant: Long,
        val onSurface: Long,
        val onSurfaceVariant: Long,
        val outline: Long,
        val outlineVariant: Long
    )

    private val CLASSIC_LIGHT = Tones(0xFFF2F2F7, 0xFFFFFFFF, 0xFFF9F9F9, 0xFFFEFEFE, 0xFF000000, 0xFF6C6C70, 0xFFC6C6C8, 0xFFE5E5EA)
    private val CLASSIC_DARK = Tones(0xFF000000, 0xFF1C1C1E, 0xFF121212, 0xFF1D1D1F, 0xFFFFFFFF, 0xFF8E8E93, 0xFF48484A, 0xFF38383A)

    /** Odcień i intensywność gotowych wyglądów. */
    fun preset(look: BaseLook): Pair<Float, Float>? = when (look) {
        BaseLook.GRAPHITE -> 220f to 0.12f
        BaseLook.WARM -> 32f to 0.6f
        BaseLook.OCEAN -> 212f to 0.7f
        BaseLook.FOREST -> 150f to 0.5f
        else -> null
    }

    private fun params(setting: LookSetting): Pair<Float, Float>? = when (setting.look) {
        BaseLook.CLASSIC -> null
        BaseLook.CUSTOM -> setting.hue.coerceIn(0f, 360f) to setting.tint.coerceIn(0f, 1f)
        else -> preset(setting.look)
    }

    fun light(setting: LookSetting): Tones {
        val (h, t) = params(setting) ?: return CLASSIC_LIGHT
        fun c(s: Float, l: Float) = AccentMath.fromHsl(h, s.coerceIn(0f, 1f), l.coerceIn(0f, 1f))
        return Tones(
            background = c(0.08f + 0.42f * t, 0.955f - 0.025f * t),
            card = c(0.5f * t, 0.995f - 0.008f * t),
            cardMuted = c(0.4f * t, 0.975f - 0.012f * t),
            surfaceVariant = c(0.5f * t, 0.99f - 0.008f * t),
            onSurface = c(0.25f * t, 0.06f),
            onSurfaceVariant = c(0.06f + 0.14f * t, 0.4f - 0.04f * t),
            outline = c(0.06f + 0.16f * t, 0.79f - 0.02f * t),
            outlineVariant = c(0.08f + 0.22f * t, 0.9f - 0.015f * t)
        )
    }

    fun dark(setting: LookSetting): Tones {
        val (h, t) = params(setting) ?: return CLASSIC_DARK
        // Grafit: ciemnoszary zamiast czerni (wygląd o niskiej intensywności, ale jaśniejsze tło).
        val lift = if (setting.look == BaseLook.GRAPHITE) 0.07f else 0f
        fun c(s: Float, l: Float) = AccentMath.fromHsl(h, s.coerceIn(0f, 1f), l.coerceIn(0f, 1f))
        return Tones(
            background = c(0.4f * t, 0.015f + 0.06f * t + lift),
            card = c(0.3f * t, 0.11f + 0.04f * t + lift),
            cardMuted = c(0.3f * t, 0.07f + 0.04f * t + lift),
            surfaceVariant = c(0.3f * t, 0.115f + 0.04f * t + lift),
            onSurface = c(0.2f * t, 0.97f),
            onSurfaceVariant = c(0.06f + 0.1f * t, 0.6f + 0.03f * t + 0.8f * lift),
            outline = c(0.06f + 0.12f * t, 0.29f + 0.03f * t + lift),
            outlineVariant = c(0.06f + 0.12f * t, 0.21f + 0.03f * t + lift)
        )
    }

    /**
     * Kółko wyglądu w Ustawieniach: (jasna połowa, ciemna połowa) - wyraźniejsze niż prawdziwe tła,
     * żeby gotowe wyglądy dało się odróżnić na pierwszy rzut oka.
     */
    fun swatch(setting: LookSetting): Pair<Long, Long> {
        val (h, t) = params(setting) ?: return 0xFFE5E5EA to 0xFF000000
        val lift = if (setting.look == BaseLook.GRAPHITE) 0.1f else 0f
        return AccentMath.fromHsl(h, (0.1f + 0.5f * t).coerceAtMost(1f), 0.86f - 0.04f * t) to
            AccentMath.fromHsl(h, (0.5f * t).coerceAtMost(1f), 0.08f + 0.12f * t + lift)
    }
}
