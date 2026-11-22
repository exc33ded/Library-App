package com.exc33ded.library.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.exc33ded.library.settings.ThemeMode
import com.exc33ded.library.ui.theme.LocalAppSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenModels: () -> Unit, onOpenBackup: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val s = LocalAppSettings.current
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Settings") },
            navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        )
    }) { pad ->
        Column(
            Modifier.padding(pad).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Appearance", style = MaterialTheme.typography.titleMedium)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                ThemeMode.entries.forEachIndexed { i, m ->
                    SegmentedButton(
                        selected = s.themeMode == m,
                        onClick = { vm.update { it.copy(themeMode = m) } },
                        shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size),
                    ) { Text(m.name.lowercase().replaceFirstChar { it.uppercase() }) }
                }
            }
            ThemePicker(s, vm::update)
            SwitchRow("Match app icon to theme", s.iconFollowsTheme) { v -> vm.update { it.copy(iconFollowsTheme = v) } }
            Text(
                "The home-screen icon changes colour with your theme. Some launchers take a few seconds to update, or briefly drop a pinned icon.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SwitchRow("AMOLED black (dark only)", s.amoled) { v -> vm.update { it.copy(amoled = v) } }

            Text("Library", style = MaterialTheme.typography.titleMedium)
            DisplayOptions(s, vm::update)

            Text("Your data", style = MaterialTheme.typography.titleMedium)
            ListItem(
                headlineContent = { Text("Backup & restore") },
                supportingContent = { Text("Export or import your library") },
                modifier = Modifier.clickable(onClick = onOpenBackup),
            )

            Text("On-device AI", style = MaterialTheme.typography.titleMedium)
            ListItem(
                headlineContent = { Text("Models & storage") },
                supportingContent = { Text("Download AI models, choose where they are stored") },
                modifier = Modifier.clickable(onClick = onOpenModels).padding(bottom = 24.dp),
            )
        }
    }
}
