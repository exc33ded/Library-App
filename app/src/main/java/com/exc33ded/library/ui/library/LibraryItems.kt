package com.exc33ded.library.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.exc33ded.library.data.db.BookEntity
import com.exc33ded.library.data.db.ReadingStatus
import com.exc33ded.library.settings.AppSettings
import com.exc33ded.library.settings.progressPercent
import com.exc33ded.library.ui.common.BookCover

@Composable
private fun BadgePill(text: String, modifier: Modifier) = Text(
    text,
    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
    color = MaterialTheme.colorScheme.onPrimary,
    modifier = modifier
        .padding(6.dp)
        .clip(RoundedCornerShape(6.dp))
        .background(MaterialTheme.colorScheme.primary)
        .padding(horizontal = 6.dp, vertical = 2.dp),
)

@Composable
private fun ProgressLine(b: BookEntity, s: AppSettings, modifier: Modifier) {
    val pct = if (s.showProgressBadge && b.status == ReadingStatus.READING) progressPercent(b.currentPage, b.pageCount) else null
    if (pct != null) Box(modifier.fillMaxWidth(pct / 100f).height(3.dp).background(MaterialTheme.colorScheme.primary))
}

/** Mihon-style: title over a bottom gradient, corner badge, thin progress line. */
@Composable
fun OverlayTile(b: BookEntity, s: AppSettings, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(12.dp))) {
        BookCover(b.title, b.coverPath)
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.85f)),
            ),
        )
        Text(
            b.title,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
        )
        badgeText(b, s)?.let { BadgePill(it, Modifier.align(Alignment.TopStart)) }
        ProgressLine(b, s, Modifier.align(Alignment.BottomStart))
    }
}

/** Clean cover with title + author underneath. */
@Composable
fun CaptionTile(b: BookEntity, s: AppSettings, modifier: Modifier = Modifier) = Column(modifier) {
    Box(Modifier.clip(RoundedCornerShape(12.dp))) {
        BookCover(b.title, b.coverPath)
        badgeText(b, s)?.let { BadgePill(it, Modifier.align(Alignment.TopStart)) }
        ProgressLine(b, s, Modifier.align(Alignment.BottomStart))
    }
    Text(b.title, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
    if (b.authors.isNotBlank())
        Text(b.authors, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
fun ListRow(b: BookEntity, s: AppSettings, modifier: Modifier = Modifier) = Row(
    modifier.fillMaxWidth().padding(vertical = 4.dp),
    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
) {
    BookCover(b.title, b.coverPath, Modifier.width(56.dp))
    Column(Modifier.align(Alignment.CenterVertically)) {
        Text(b.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (b.authors.isNotBlank())
            Text(b.authors, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        badgeText(b, s)?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
    }
}
