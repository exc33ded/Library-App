package com.exc33ded.library.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.exc33ded.library.settings.AppSettings
import com.exc33ded.library.settings.ThemePalette
import com.exc33ded.library.settings.isDark
import com.exc33ded.library.ui.theme.paletteScheme

private fun label(p: ThemePalette) = when (p) {
    ThemePalette.INK_AMBER -> "Ink & amber"
    ThemePalette.DEEP_TEAL -> "Deep teal"
    ThemePalette.FOREST -> "Forest"
    ThemePalette.OCEAN -> "Ocean"
    ThemePalette.BERRY -> "Berry"
    ThemePalette.GRAPHITE -> "Graphite"
}

/** Row of colour-theme swatches: Wallpaper (Material You) first, then the fixed palettes. */
@Composable
fun ThemePicker(settings: AppSettings, onChange: ((AppSettings) -> AppSettings) -> Unit) {
    val ctx = LocalContext.current
    val dark = isDark(settings.themeMode, isSystemInDarkTheme())
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Color theme", style = MaterialTheme.typography.titleSmall)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
            item {
                Swatch(
                    label = "Wallpaper",
                    scheme = if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx),
                    selected = settings.dynamicColor,
                ) { onChange { it.copy(dynamicColor = true) } }
            }
            items(ThemePalette.entries) { p ->
                Swatch(
                    label = label(p),
                    scheme = paletteScheme(p, dark),
                    selected = !settings.dynamicColor && settings.palette == p,
                ) { onChange { it.copy(dynamicColor = false, palette = p) } }
            }
        }
    }
}

@Composable
private fun Swatch(label: String, scheme: ColorScheme, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.width(76.dp).semantics { this.selected = selected }.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val shape = RoundedCornerShape(20.dp)
        Box(
            Modifier.size(64.dp).clip(shape).background(scheme.surface)
                .border(if (selected) 3.dp else 1.dp, if (selected) scheme.primary else scheme.outlineVariant, shape),
        ) {
            Row(Modifier.fillMaxSize().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (c in listOf(scheme.primary, scheme.primaryContainer, scheme.tertiary)) {
                    Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(6.dp)).background(c))
                }
            }
            if (selected) {
                Box(
                    Modifier.align(Alignment.BottomEnd).padding(4.dp).size(20.dp).clip(CircleShape).background(scheme.primary),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Check, null, Modifier.size(14.dp), tint = scheme.onPrimary) }
            }
        }
        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
    }
}
