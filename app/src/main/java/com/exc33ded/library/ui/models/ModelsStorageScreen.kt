package com.exc33ded.library.ui.models

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.exc33ded.library.models.ModelKind
import com.exc33ded.library.models.ModelStatus
import com.exc33ded.library.models.ModelUi
import com.exc33ded.library.models.formatBytes
import com.exc33ded.library.settings.ModelsLocation
import com.exc33ded.library.ui.settings.SwitchRow
import com.exc33ded.library.ui.theme.LocalAppSettings

private fun ModelsLocation.label() = when (this) {
    ModelsLocation.PHONE -> "Phone storage"
    ModelsLocation.APP_FOLDER -> "App folder (visible in file manager)"
    ModelsLocation.SD_CARD -> "SD card"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelsStorageScreen(onBack: () -> Unit, vm: ModelsStorageViewModel = hiltViewModel()) {
    val settings = LocalAppSettings.current
    val models by vm.models.collectAsStateWithLifecycle()
    val usage by vm.usage.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); vm.clearMessage() }
    }

    var pendingId by remember { mutableStateOf<String?>(null) }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        pendingId?.let(vm::download)
        pendingId = null
    }
    fun startDownload(id: String) {
        if (Build.VERSION.SDK_INT >= 33) {
            pendingId = id
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else vm.download(id)
    }

    var confirmDelete by remember { mutableStateOf<ModelUi?>(null) }
    confirmDelete?.let { m ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete ${m.spec.name}?") },
            text = { Text("You can download it again later.") },
            confirmButton = { TextButton({ vm.delete(m.spec.id); confirmDelete = null }) { Text("Delete") } },
            dismissButton = { TextButton({ confirmDelete = null }) { Text("Cancel") } },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Models & storage") },
                navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        Column(
            Modifier.padding(pad).padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Models folder", style = MaterialTheme.typography.titleMedium)
            Column {
                vm.availableLocations.forEach { loc ->
                    Row(
                        Modifier.fillMaxWidth().selectable(
                            selected = settings.modelsLocation == loc,
                            role = Role.RadioButton,
                            onClick = { if (settings.modelsLocation != loc) vm.setLocation(loc) },
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = settings.modelsLocation == loc, onClick = null)
                        Text(loc.label(), Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp))
                    }
                }
            }
            usage?.let { u ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    u.modelsPath?.let { SelectionContainer { Text(it, style = MaterialTheme.typography.bodySmall) } }
                    Text("Free: ${formatBytes(u.freeBytes)}")
                    Text("Models use ${formatBytes(u.modelsBytes)}")
                }
            }

            SwitchRow("Download on Wi-Fi only", settings.wifiOnlyDownloads) { vm.setWifiOnly(it) }

            Text("AI models", style = MaterialTheme.typography.titleMedium)
            Text("Free, no account needed. On-device AI features that use these models arrive in a later update.")
            models.forEach { m ->
                ModelCard(
                    m, settings.wifiOnlyDownloads,
                    onDownload = { startDownload(m.spec.id) },
                    onCancel = { vm.cancel(m.spec.id) },
                    onDelete = { confirmDelete = m },
                )
            }

            Text("Stored on this phone", style = MaterialTheme.typography.titleMedium)
            usage?.let { u ->
                SizeRow("Database", u.databaseBytes)
                SizeRow("Covers", u.coversBytes)
                SizeRow("Import photos", u.photosBytes)
            }
            Text(
                "These stay in the app's private storage on purpose: a database on removable or shared storage can be lost or corrupted when the card is removed. A backup/export feature is planned.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 24.dp),
            )
        }
    }
}

@Composable
private fun SizeRow(label: String, bytes: Long) =
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Text(formatBytes(bytes))
    }

@Composable
private fun ModelCard(m: ModelUi, wifiOnly: Boolean, onDownload: () -> Unit, onCancel: () -> Unit, onDelete: () -> Unit) {
    val spec = m.spec
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(spec.name, style = MaterialTheme.typography.titleSmall)
                if (spec.recommended) Text("Recommended", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            Text(spec.description, style = MaterialTheme.typography.bodyMedium)
            Text(
                "${formatBytes(spec.sizeBytes)} · ${if (spec.kind == ModelKind.VISION) "Vision" else "Text"}",
                style = MaterialTheme.typography.bodySmall,
            )
            when (val st = m.status) {
                ModelStatus.NotDownloaded -> Button(onDownload) { Text("Download") }
                is ModelStatus.Paused -> {
                    Text("Paused at ${formatBytes(st.bytes)}")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onDownload) { Text("Resume") }
                        TextButton(onDelete) { Text("Delete") }
                    }
                }
                is ModelStatus.Queued -> {
                    Text("Waiting for ${if (wifiOnly) "Wi-Fi" else "network"}…")
                    TextButton(onCancel) { Text("Cancel") }
                }
                is ModelStatus.Downloading -> {
                    LinearProgressIndicator(progress = { st.bytes.toFloat() / spec.sizeBytes }, modifier = Modifier.fillMaxWidth())
                    Text("${formatBytes(st.bytes)} of ${formatBytes(spec.sizeBytes)}")
                    TextButton(onCancel) { Text("Cancel") }
                }
                is ModelStatus.Ready -> {
                    Text("Downloaded")
                    TextButton(onDelete) { Text("Delete") }
                }
                is ModelStatus.Failed -> {
                    Text(st.message, color = MaterialTheme.colorScheme.error)
                    Button(onDownload) { Text("Retry") }
                }
            }
        }
    }
}
