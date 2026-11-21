package com.exc33ded.library.ui.common

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import java.io.File

/** Local file path or remote URL; falls back to the text placeholder while loading / on error. */
@Composable
fun BookCover(title: String, cover: String?, modifier: Modifier = Modifier) {
    if (cover == null) { CoverPlaceholder(title, modifier); return }
    SubcomposeAsyncImage(
        model = if (cover.startsWith("http")) cover else File(cover),
        contentDescription = title,
        contentScale = ContentScale.Crop,
        loading = { CoverPlaceholder(title) },
        error = { CoverPlaceholder(title) },
        modifier = modifier.aspectRatio(2f / 3f).clip(RoundedCornerShape(12.dp)),
    )
}
