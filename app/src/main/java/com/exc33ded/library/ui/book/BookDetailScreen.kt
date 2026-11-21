package com.exc33ded.library.ui.book

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.exc33ded.library.data.db.ReadingStatus
import com.exc33ded.library.ui.common.BookCover
import com.exc33ded.library.ui.common.StatusSelector

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailScreen(
    bookId: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    vm: BookDetailViewModel = hiltViewModel<BookDetailViewModel, BookDetailViewModel.Factory>(
        creationCallback = { it.create(bookId) },
    ),
) {
    val book by vm.book.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    Scaffold(topBar = {
        TopAppBar(
            title = {},
            navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            actions = {
                IconButton(onEdit) { Icon(Icons.Filled.Edit, "Edit") }
                IconButton({ confirmDelete = true }) { Icon(Icons.Filled.Delete, "Delete") }
            },
        )
    }) { pad ->
        val b = book ?: return@Scaffold
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BookCover(b.title, b.coverPath, Modifier.width(160.dp))
            Text(b.title, style = MaterialTheme.typography.headlineSmall)
            if (b.authors.isNotBlank()) Text(b.authors, style = MaterialTheme.typography.titleMedium)
            StatusSelector(b.status, { if (it != null) vm.setStatus(it) }, Modifier.fillMaxWidth())
            val pages = b.pageCount
            if (b.status == ReadingStatus.READING && pages != null) {
                var page by remember(b.currentPage) { mutableFloatStateOf(b.currentPage.toFloat()) }
                Text("Page ${page.toInt()} of $pages", style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = page, onValueChange = { page = it },
                    onValueChangeFinished = { vm.setProgress(page.toInt()) },
                    valueRange = 0f..pages.toFloat(),
                )
            }
            b.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            b.notes?.let { Text("Notes: $it", style = MaterialTheme.typography.bodyMedium) }
        }
    }

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete this book?") },
        confirmButton = { TextButton({ confirmDelete = false; vm.delete(onBack) }) { Text("Delete") } },
        dismissButton = { TextButton({ confirmDelete = false }) { Text("Cancel") } },
    )
}
