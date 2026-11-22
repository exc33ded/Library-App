package com.exc33ded.library.ui.common

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.exc33ded.library.data.db.ReadingStatus

fun ReadingStatus.label() = when (this) {
    ReadingStatus.WANT -> "Want"
    ReadingStatus.READING -> "Reading"
    ReadingStatus.READ -> "Read"
    ReadingStatus.DNF -> "DNF"
}

/**
 * Pill chips instead of a segmented button: labels never wrap mid-word on narrow screens or large fonts, and the row
 * scrolls sideways if it ever runs out of room. `allowNone` adds an "All" chip (library filter); null = All.
 */
@Composable
fun StatusSelector(
    selected: ReadingStatus?,
    onSelect: (ReadingStatus?) -> Unit,
    modifier: Modifier = Modifier,
    allowNone: Boolean = false,
) {
    val options: List<ReadingStatus?> = (if (allowNone) listOf(null) else emptyList()) + ReadingStatus.entries
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { s ->
            val isSelected = s == selected
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(s) },
                shape = CircleShape,
                label = { Text(s?.label() ?: "All", maxLines = 1, softWrap = false) },
                leadingIcon = if (isSelected) ({ Icon(Icons.Filled.Check, null) }) else null,
            )
        }
    }
}
