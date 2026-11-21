package com.exc33ded.library.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Text "cover" used until real covers arrive in Phase 2. Colour is stable per title. */
@Composable
fun CoverPlaceholder(title: String, modifier: Modifier = Modifier) {
    val palette = listOf(
        MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer,
        MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer,
        MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer,
    )
    val (bg, fg) = palette[Math.floorMod(title.hashCode(), palette.size)]
    Box(
        modifier.aspectRatio(2f / 3f).clip(RoundedCornerShape(12.dp)).background(bg).padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            title, color = fg, style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center, maxLines = 4, overflow = TextOverflow.Ellipsis,
        )
    }
}
