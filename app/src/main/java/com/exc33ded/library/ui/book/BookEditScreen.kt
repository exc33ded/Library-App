package com.exc33ded.library.ui.book

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.exc33ded.library.metadata.BookCandidate
import com.exc33ded.library.ui.common.BookCover
import com.exc33ded.library.ui.common.StatusSelector

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BookEditScreen(
    bookId: String?,
    prefill: BookCandidate?,
    onDone: () -> Unit,
    vm: BookEditViewModel = hiltViewModel<BookEditViewModel, BookEditViewModel.Factory>(
        creationCallback = { it.create(bookId, prefill) },
    ),
) {
    val s by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(s.savedId) { if (s.savedId != null) onDone() }
    var newCategory by rememberSaveable { mutableStateOf("") }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(if (bookId == null) "Add book" else "Edit book") },
            navigationIcon = { IconButton(onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            actions = { TextButton(vm::save, enabled = !s.saving) { Text(if (s.saving) "Saving…" else "Save") } },
        )
    }) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (s.coverPreview != null) BookCover(s.form.title, s.coverPreview, Modifier.width(120.dp))
            val f = s.form
            OutlinedTextField(f.title, { v -> vm.edit { it.copy(title = v) } }, Modifier.fillMaxWidth(),
                label = { Text("Title *") }, isError = s.error == FormError.TitleRequired, singleLine = true)
            OutlinedTextField(f.authors, { v -> vm.edit { it.copy(authors = v) } }, Modifier.fillMaxWidth(),
                label = { Text("Author(s)") }, singleLine = true)
            OutlinedTextField(f.isbn, { v -> vm.edit { it.copy(isbn = v) } }, Modifier.fillMaxWidth(),
                label = { Text("ISBN") }, isError = s.error == FormError.BadIsbn, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedTextField(f.pageCount, { v -> vm.edit { it.copy(pageCount = v) } }, Modifier.fillMaxWidth(),
                label = { Text("Pages") }, isError = s.error == FormError.BadPageCount, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            Text("Status", style = MaterialTheme.typography.labelLarge)
            StatusSelector(f.status, { st -> if (st != null) vm.edit { it.copy(status = st) } }, Modifier.fillMaxWidth())
            Text("Categories", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                s.categories.forEach { c ->
                    FilterChip(c.id in s.selectedCategories, { vm.toggleCategory(c.id) }, { Text(c.name) })
                }
            }
            if (s.suggestions.isNotEmpty()) {
                Text("Suggested", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    s.suggestions.forEach { name ->
                        AssistChip(onClick = { vm.addCategory(name) }, label = { Text("+ $name") })
                    }
                }
            }
            OutlinedTextField(newCategory, { newCategory = it }, Modifier.fillMaxWidth(),
                label = { Text("New category") }, singleLine = true,
                trailingIcon = { TextButton({ vm.addCategory(newCategory); newCategory = "" }) { Text("Add") } })
            OutlinedTextField(f.description, { v -> vm.edit { it.copy(description = v) } }, Modifier.fillMaxWidth(),
                label = { Text("Description") }, minLines = 3)
            OutlinedTextField(f.notes, { v -> vm.edit { it.copy(notes = v) } }, Modifier.fillMaxWidth(),
                label = { Text("My notes") }, minLines = 2)
        }
    }
}
