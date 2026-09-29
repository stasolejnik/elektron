package pl.zse.bydgoszcz.elektron.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccentMathTest {

    /** Kolory z całego koła barw, różne nasycenia i jasności - też skrajne (żółty, biały, czarny). */
    private val seeds: List<Long> = buildList {
        for (h in 0 until 360 step 15) for (s in listOf(0f, 0.3f, 0.7f, 1f)) for (l in listOf(0.2f, 0.5f, 0.8f)) {
            add(AccentMath.fromHsl(h.toFloat(), s, l))
        }
        addAll(listOf(0xFFFFFF00, 0xFFFFFFFF, 0xFF000000, 0xFF00FF00, 0xFF00FFFF))
    }

    @Test
    fun lightToneIsReadableOnWhite() {
        seeds.forEach { seed ->
            val t = AccentMath.light(seed)
            assertTrue("primary ${seed.toString(16)}", AccentMath.contrast(t.primary, 0xFFFFFFFF) >= 4.5)
            assertTrue("container ${seed.toString(16)}", AccentMath.contrast(t.onContainer, t.container) >= 7.0)
        }
    }

    @Test
    fun darkToneIsReadableOnDark() {
        seeds.forEach { seed ->
            val t = AccentMath.dark(seed)
            assertTrue("primary ${seed.toString(16)}", AccentMath.contrast(t.primary, 0xFF1C1C1E) >= 6.0)
            assertTrue("onPrimary ${seed.toString(16)}", AccentMath.contrast(t.onPrimary, t.primary) >= 4.5)
            assertTrue("container ${seed.toString(16)}", AccentMath.contrast(t.onContainer, t.container) >= 7.0)
        }
    }

    @Test
    fun hslRoundTrip() {
        listOf(0xFF007C98, 0xFFE53935, 0xFF7CB342, 0xFF3F51B5).forEach { c ->
            val (h, s, l) = AccentMath.toHsl(c)
            val back = AccentMath.fromHsl(h, s, l)
            // Zaokrąglenia: najwyżej 1 na kanał.
            listOf(16, 8, 0).forEach { sh ->
                assertTrue(kotlin.math.abs(((c shr sh) and 0xFF) - ((back shr sh) and 0xFF)) <= 1)
            }
        }
    }

    @Test
    fun oldPaletteKeysFallBackToBlue() {
        assertEquals(AccentColor.BLUE, AccentColor.fromKey("graphite"))
        assertEquals(AccentColor.CUSTOM, AccentColor.fromKey("custom"))
    }
}
