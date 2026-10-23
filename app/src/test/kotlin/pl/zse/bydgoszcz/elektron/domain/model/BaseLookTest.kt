package pl.zse.bydgoszcz.elektron.domain.model

import org.junit.Assert.*
import org.junit.Test

class BaseLookTest {
    private fun all(): List<LookSetting> = BaseLook.entries.filter { it != BaseLook.CUSTOM }.map { LookSetting(it) } +
        (0..12).flatMap { h -> listOf(0f, 0.25f, 0.5f, 0.75f, 1f).map { t -> LookSetting(BaseLook.CUSTOM, h * 30f, t) } }

    @Test fun textStaysReadableForEveryHueAndIntensity() {
        for (setting in all()) for ((mode, tones) in listOf("jasny" to BaseLooks.light(setting), "ciemny" to BaseLooks.dark(setting))) {
            val name = "$setting $mode"
            assertTrue(name, AccentMath.contrast(tones.onSurface, tones.background) >= 12.0)
            assertTrue(name, AccentMath.contrast(tones.onSurface, tones.card) >= 12.0)
            assertTrue("$name: drugorzędny tekst", AccentMath.contrast(tones.onSurfaceVariant, tones.card) >= 4.5)
            assertTrue("$name: drugorzędny tekst na tle", AccentMath.contrast(tones.onSurfaceVariant, tones.background) >= 4.5)
            assertNotEquals("$name: karta i tło", tones.card, tones.background)
            assertNotEquals("$name: patrz LightColors.surfaceVariant", tones.card, tones.surfaceVariant)
        }
    }

    @Test fun classicKeepsTheOriginalPaletteAndKeysRoundTrip() {
        val light = BaseLooks.light(LookSetting())
        assertEquals(0xFFF2F2F7, light.background)
        assertEquals(0xFF000000, BaseLooks.dark(LookSetting()).background)
        BaseLook.entries.forEach { assertEquals(it, BaseLook.fromKey(it.key)) }
        assertEquals(BaseLook.CLASSIC, BaseLook.fromKey("nieznany"))
        // Grafit w ciemnym nie jest czarny.
        assertNotEquals(0xFF000000, BaseLooks.dark(LookSetting(BaseLook.GRAPHITE)).background)
    }
}
