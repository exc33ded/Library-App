package com.exc33ded.library.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.exc33ded.library.settings.ThemePalette

/** Fixed "Ink & amber" scheme, used when dynamic colors are off. */
val InkLight = lightColorScheme(
    primary = Color(0xFFA46618), onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF0D9B0), onPrimaryContainer = Color(0xFF3A2200),
    secondary = Color(0xFF6F5B3E), onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE8E0D0), onSecondaryContainer = Color(0xFF251A06),
    tertiary = Color(0xFF55633A), onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF6F1E8), onBackground = Color(0xFF1C1A16),
    surface = Color(0xFFF6F1E8), onSurface = Color(0xFF1C1A16),
    surfaceVariant = Color(0xFFE8E0D0), onSurfaceVariant = Color(0xFF4B4639),
    surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF1EBDF),
    surfaceContainer = Color(0xFFECE5D6), surfaceContainerHigh = Color(0xFFE6DFCF),
    surfaceContainerHighest = Color(0xFFE0D9C9),
    outline = Color(0xFF7D7667), outlineVariant = Color(0xFFCFC7B8),
)

val InkDark = darkColorScheme(
    primary = Color(0xFFE0A85A), onPrimary = Color(0xFF2A1A00),
    primaryContainer = Color(0xFF4A3000), onPrimaryContainer = Color(0xFFFFDDAE),
    secondary = Color(0xFFD9C3A0), onSecondary = Color(0xFF3B2F15),
    secondaryContainer = Color(0xFF2A2926), onSecondaryContainer = Color(0xFFF1E0BB),
    tertiary = Color(0xFFBBCB98), onTertiary = Color(0xFF273512),
    background = Color(0xFF0F0F10), onBackground = Color(0xFFECE8E1),
    surface = Color(0xFF0F0F10), onSurface = Color(0xFFECE8E1),
    surfaceVariant = Color(0xFF2A2926), onSurfaceVariant = Color(0xFFCFC7B8),
    surfaceContainerLowest = Color(0xFF0A0A0B), surfaceContainerLow = Color(0xFF171715),
    surfaceContainer = Color(0xFF1D1C1A), surfaceContainerHigh = Color(0xFF262522),
    surfaceContainerHighest = Color(0xFF302F2B),
    outline = Color(0xFF989080), outlineVariant = Color(0xFF4B4639),
)

/** A colour with the given hue/saturation whose perceptual tone (CIE L*, 0 black .. 100 white) is [tone], like a Material tonal palette. */
fun toneColor(hue: Float, sat: Float, tone: Int): Color {
    if (tone <= 0) return Color.Black
    if (tone >= 100) return Color.White
    val target = if (tone > 8) Math.pow((tone + 16) / 116.0, 3.0).toFloat() else (tone / 903.3).toFloat()
    var lo = 0f
    var hi = 1f
    repeat(24) {
        val mid = (lo + hi) / 2
        if (Color.hsl(hue, sat, mid).luminance() < target) lo = mid else hi = mid
    }
    return Color.hsl(hue, sat, (lo + hi) / 2)
}

fun paletteScheme(p: ThemePalette, dark: Boolean): ColorScheme = when (p) {
    ThemePalette.INK_AMBER -> if (dark) InkDark else InkLight
    ThemePalette.DEEP_TEAL -> generatedScheme(174f, 0.65f, dark)
    ThemePalette.FOREST -> generatedScheme(135f, 0.45f, dark)
    ThemePalette.OCEAN -> generatedScheme(214f, 0.75f, dark)
    ThemePalette.BERRY -> generatedScheme(332f, 0.55f, dark)
    ThemePalette.GRAPHITE -> generatedScheme(220f, 0.10f, dark)
}

private fun generatedScheme(hue: Float, sat: Float, dark: Boolean): ColorScheme {
    fun primary(t: Int) = toneColor(hue, sat, t)
    fun secondary(t: Int) = toneColor(hue, sat * 0.35f, t)
    fun tertiary(t: Int) = toneColor((hue + 60f) % 360f, sat * 0.6f, t)
    fun neutral(t: Int) = toneColor(hue, 0.06f, t)
    fun variant(t: Int) = toneColor(hue, 0.10f, t)
    return if (!dark) lightColorScheme(
        primary = primary(40), onPrimary = Color.White, primaryContainer = primary(90), onPrimaryContainer = primary(10),
        secondary = secondary(40), onSecondary = Color.White, secondaryContainer = secondary(90), onSecondaryContainer = secondary(10),
        tertiary = tertiary(40), onTertiary = Color.White, tertiaryContainer = tertiary(90), onTertiaryContainer = tertiary(10),
        background = neutral(98), onBackground = neutral(10), surface = neutral(98), onSurface = neutral(10),
        surfaceVariant = variant(90), onSurfaceVariant = variant(30),
        surfaceContainerLowest = Color.White, surfaceContainerLow = neutral(96), surfaceContainer = neutral(94),
        surfaceContainerHigh = neutral(92), surfaceContainerHighest = neutral(90),
        outline = variant(50), outlineVariant = variant(80),
    ) else darkColorScheme(
        primary = primary(80), onPrimary = primary(20), primaryContainer = primary(30), onPrimaryContainer = primary(90),
        secondary = secondary(80), onSecondary = secondary(20), secondaryContainer = secondary(30), onSecondaryContainer = secondary(90),
        tertiary = tertiary(80), onTertiary = tertiary(20), tertiaryContainer = tertiary(30), onTertiaryContainer = tertiary(90),
        background = neutral(6), onBackground = neutral(90), surface = neutral(6), onSurface = neutral(90),
        surfaceVariant = variant(30), onSurfaceVariant = variant(80),
        surfaceContainerLowest = neutral(4), surfaceContainerLow = neutral(10), surfaceContainer = neutral(12),
        surfaceContainerHigh = neutral(17), surfaceContainerHighest = neutral(22),
        outline = variant(60), outlineVariant = variant(30),
    )
}
