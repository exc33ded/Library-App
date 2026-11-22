package com.exc33ded.library.ui.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.exc33ded.library.ui.theme.LocalAppSettings
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

private fun lastExportedText(at: Long): String {
    if (at <= 0L) return "never"
    val days = ChronoUnit.DAYS.between(Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalDate(), LocalDate.now())
    return when {
        days <= 0 -> "today"
        days == 1L -> "1 day ago"
        else -> "$days days ago"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupScreen(onBack: () -> Unit, vm: BackupViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val lastExportAt = LocalAppSettings.current.lastExportAt
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) vm.export(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.import(uri)
    }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Backup & restore") },
            navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        )
    }) { pad ->
        Column(
            Modifier.padding(pad).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Backups contain your books, categories, notes, covers and profile text. Import merges: newer changes win, " +
                    "so importing the same backup twice is safe, and it works for moving to a new phone.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text("Last exported: ${lastExportedText(lastExportAt)}", style = MaterialTheme.typography.bodyMedium)
            Button(
                onClick = { exportLauncher.launch("library-backup-${LocalDate.now()}.zip") },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Export backup") }
            OutlinedButton(
                onClick = { importLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")) },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Import backup") }
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}
