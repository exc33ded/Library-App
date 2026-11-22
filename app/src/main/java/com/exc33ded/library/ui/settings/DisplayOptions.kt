package com.exc33ded.library.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.exc33ded.library.settings.AppSettings
import com.exc33ded.library.settings.LibraryDisplayMode
import com.exc33ded.library.settings.MAX_COLUMNS
import com.exc33ded.library.settings.MIN_COLUMNS
import kotlin.math.roundToInt

private val modeLabels = listOf(
    LibraryDisplayMode.OVERLAY_GRID to "Overlay",
    LibraryDisplayMode.CAPTION_GRID to "Caption",
    LibraryDisplayMode.LIST to "List",
)

/** Display mode, column count and badge toggles. Used by the library sheet and the Settings screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DisplayOptions(settings: AppSettings, onChange: ((AppSettings) -> AppSettings) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Display", style = MaterialTheme.typography.titleSmall)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            modeLabels.forEachIndexed { i, (mode, label) ->
                SegmentedButton(
                    selected = settings.displayMode == mode,
                    onClick = { onChange { it.copy(displayMode = mode) } },
                    shape = SegmentedButtonDefaults.itemShape(i, modeLabels.size),
                ) { Text(label) }
            }
        }
        if (settings.displayMode != LibraryDisplayMode.LIST) {
            Text("Columns: ${settings.columns}", style = MaterialTheme.typography.titleSmall)
            Slider(
                value = settings.columns.toFloat(),
                onValueChange = { v ->
                    val n = v.roundToInt() // fires every drag frame: only write when the step changes
                    if (n != settings.columns) onChange { it.copy(columns = n) }
                },
                valueRange = MIN_COLUMNS.toFloat()..MAX_COLUMNS.toFloat(),
                steps = MAX_COLUMNS - MIN_COLUMNS - 1,
            )
        }
        SwitchRow("Show progress", settings.showProgressBadge) { v -> onChange { it.copy(showProgressBadge = v) } }
        SwitchRow("Show status", settings.showStatusBadge) { v -> onChange { it.copy(showStatusBadge = v) } }
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) =
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked, onChecked)
    }
