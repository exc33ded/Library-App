package com.exc33ded.library.ui.find

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import com.exc33ded.library.ui.common.SearchField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.exc33ded.library.metadata.BookCandidate
import com.exc33ded.library.ui.common.BookCover

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FindBookScreen(
    isbn: String?,
    query: String?,
    onBack: () -> Unit,
    onPicked: (BookCandidate) -> Unit,
    vm: FindBookViewModel = hiltViewModel<FindBookViewModel, FindBookViewModel.Factory>(
        creationCallback = { it.create(isbn, query) },
    ),
) {
    val s by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(s.picked) { s.picked?.let { vm.consumePicked(); onPicked(it) } }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(if (isbn != null) "ISBN $isbn" else "Find a book") },
            navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        )
    }) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SearchField(
                value = s.query, onValueChange = vm::setQuery, placeholder = "Title, author, or both",
                modifier = Modifier.fillMaxWidth(), onSearch = { vm.search() },
            )
            if (s.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            s.message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            s.notFoundIsbn?.let { code ->
                OutlinedButton({ onPicked(BookCandidate(title = "", authors = "", isbn = code)) }) { Text("Enter it manually") }
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(s.results, key = { it.olWorkKey ?: (it.title + it.authors) }) { c ->
                    Row(
                        Modifier.fillMaxWidth().animateItem().clickable(enabled = !s.loading) { vm.pick(c) },
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        BookCover(c.title, c.coverUrl, Modifier.width(56.dp))
                        Column {
                            Text(c.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(c.authors, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}
