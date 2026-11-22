package com.exc33ded.library.ui.imports

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.exc33ded.library.data.db.ImportState
import com.exc33ded.library.metadata.BookCandidate
import com.exc33ded.library.ui.common.BookCover

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    onBack: () -> Unit,
    onEdit: (importId: String, BookCandidate) -> Unit,
    onSearch: (importId: String, query: String?) -> Unit,
    vm: ImportViewModel = hiltViewModel(),
) {
    val rows by vm.rows.collectAsStateWithLifecycle()
    val waiting = rows.count { it.item.state == ImportState.PENDING || it.item.state == ImportState.PROCESSING }
    val failed = rows.count { it.item.state == ImportState.FAILED }
    val reviewable = rows.count { it.item.state == ImportState.REVIEW || it.item.state == ImportState.FAILED }
    var confirmAll by remember { mutableStateOf(false) }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(if (waiting > 0) "Reading covers… $waiting left" else "Review imports") },
            navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        )
    }) { pad ->
        if (rows.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(pad), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Nothing to review")
            }
            return@Scaffold
        }
        LazyColumn(
            Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (reviewable > 0) item(key = "bulk-actions") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (failed > 0) AssistChip(onClick = { vm.dismissFailed() }, label = { Text("Dismiss failed ($failed)") })
                    AssistChip(onClick = { confirmAll = true }, label = { Text("Dismiss all ($reviewable)") })
                }
            }
            items(rows, key = { it.item.id }) { row ->
                ElevatedCard(Modifier.fillMaxWidth().animateItem()) {
                    Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        BookCover("", row.item.photoPath, Modifier.width(64.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            when (row.item.state) {
                                ImportState.PENDING, ImportState.PROCESSING -> Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                    Text("  Reading cover…", style = MaterialTheme.typography.bodyMedium)
                                }
                                ImportState.REVIEW -> row.candidate?.let { c ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        if (c.coverUrl != null) BookCover(c.title, c.coverUrl, Modifier.width(44.dp))
                                        Column {
                                            Text(c.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                            Text(c.authors, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button({ vm.add(row) }) { Text("Add") }
                                        OutlinedButton({ onEdit(row.item.id, c) }) { Text("Edit") }
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        TextButton({ onSearch(row.item.id, row.item.query) }) { Text("Wrong book? Search") }
                                        TextButton({ vm.skip(row.item.id) }) { Text("Dismiss") }
                                    }
                                }
                                ImportState.FAILED -> {
                                    Text(row.item.error ?: "Failed", style = MaterialTheme.typography.bodyMedium)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton({ onSearch(row.item.id, row.item.query) }) { Text("Search") }
                                        TextButton({ vm.retry(row.item.id) }) { Text("Retry") }
                                        TextButton({ vm.skip(row.item.id) }) { Text("Skip") }
                                    }
                                }
                                ImportState.DONE -> Unit
                            }
                        }
                    }
                }
            }
        }
    }
    if (confirmAll) AlertDialog(
        onDismissRequest = { confirmAll = false },
        title = { Text("Dismiss all $reviewable?") },
        text = { Text("Books you haven't added will be removed from this list. Your photos stay in your gallery.") },
        confirmButton = { TextButton({ vm.dismissAll(); confirmAll = false }) { Text("Dismiss all") } },
        dismissButton = { TextButton({ confirmAll = false }) { Text("Cancel") } },
    )
}
