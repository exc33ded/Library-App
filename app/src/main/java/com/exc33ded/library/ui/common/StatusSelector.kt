package com.exc33ded.library.ui.common

import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.exc33ded.library.data.db.ReadingStatus

fun ReadingStatus.label() = when (this) {
    ReadingStatus.WANT -> "Want"
    ReadingStatus.READING -> "Reading"
    ReadingStatus.READ -> "Read"
    ReadingStatus.DNF -> "DNF"
}

/**
 * ponytail: SegmentedButton is stable and known-good; swap for M3 Expressive ButtonGroup in Phase 7.
 * `allowNone` adds an "All" segment (library filter); null = All.
 */
@Composable
fun StatusSelector(
    selected: ReadingStatus?,
    onSelect: (ReadingStatus?) -> Unit,
    modifier: Modifier = Modifier,
    allowNone: Boolean = false,
) {
    val options: List<ReadingStatus?> = (if (allowNone) listOf(null) else emptyList()) + ReadingStatus.entries
    SingleChoiceSegmentedButtonRow(modifier) {
        options.forEachIndexed { i, s ->
            SegmentedButton(
                selected = s == selected,
                onClick = { onSelect(s) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
            ) { Text(s?.label() ?: "All") }
        }
    }
}
