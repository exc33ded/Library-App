package com.exc33ded.library.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/** Fixed "Ink & amber" scheme, used when dynamic colors are off. */
val InkLight = lightColorScheme(
    primary = Color(0xFFA8691A), onPrimary = Color(0xFFFFFFFF),
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
