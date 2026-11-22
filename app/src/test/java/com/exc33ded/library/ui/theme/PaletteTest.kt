package com.exc33ded.library.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.exc33ded.library.settings.ThemePalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class PaletteTest {
    private fun contrast(a: Color, b: Color): Float {
        val l1 = a.luminance(); val l2 = b.luminance()
        return (max(l1, l2) + 0.05f) / (min(l1, l2) + 0.05f)
    }

    @Test fun toneEndpointsAreBlackAndWhite() {
        assertEquals(Color.Black, toneColor(200f, 0.5f, 0))
        assertEquals(Color.White, toneColor(200f, 0.5f, 100))
    }

    @Test fun toneLuminanceFollowsCieLightness() {
        // tone 50 -> Y = ((50+16)/116)^3 = 0.1842
        assertTrue(abs(toneColor(210f, 0.6f, 50).luminance() - 0.1842f) < 0.01f)
        assertTrue(toneColor(130f, 0.5f, 90).luminance() > toneColor(130f, 0.5f, 40).luminance())
    }

    @Test fun everyPaletteMeetsContrastInLightAndDark() {
        for (p in ThemePalette.entries) for (dark in listOf(false, true)) {
            val s = paletteScheme(p, dark)
            val pairs = listOf(
                "onPrimary/primary" to contrast(s.onPrimary, s.primary),
                "onPrimaryContainer/primaryContainer" to contrast(s.onPrimaryContainer, s.primaryContainer),
                "onSecondaryContainer/secondaryContainer" to contrast(s.onSecondaryContainer, s.secondaryContainer),
                "onTertiaryContainer/tertiaryContainer" to contrast(s.onTertiaryContainer, s.tertiaryContainer),
                "onSurface/surface" to contrast(s.onSurface, s.surface),
                "onBackground/background" to contrast(s.onBackground, s.background),
                "onSurfaceVariant/surfaceVariant" to contrast(s.onSurfaceVariant, s.surfaceVariant),
            )
            for ((name, c) in pairs) assertTrue("$p dark=$dark $name = $c", c >= 4.5f)
        }
    }

    @Test fun lightAndDarkDiffer() {
        for (p in ThemePalette.entries) assertTrue(paletteScheme(p, false).surface != paletteScheme(p, true).surface)
    }
}
