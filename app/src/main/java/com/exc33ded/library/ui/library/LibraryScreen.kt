package com.exc33ded.library.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.exc33ded.library.data.db.BookEntity
import com.exc33ded.library.ui.common.BookCover
import com.exc33ded.library.ui.common.PlaceholderScreen
import com.exc33ded.library.ui.common.StatusSelector

@Composable
fun LibraryScreen(
    onOpenBook: (String) -> Unit,
    onManageCategories: () -> Unit,
    vm: LibraryViewModel = hiltViewModel(),
) {
    val s by vm.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = s.filter.query, onValueChange = vm::setQuery, singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, null) }, placeholder = { Text("Search title or author") },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
        StatusSelector(s.filter.status, vm::setStatus, Modifier.fillMaxWidth(), allowNone = true)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { FilterChip(s.filter.categoryId == null, { vm.setCategory(null) }, { Text("All") }) }
            items(s.categories, key = { it.id }) { c ->
                FilterChip(s.filter.categoryId == c.id, { vm.setCategory(c.id) }, { Text(c.name) })
            }
            item { FilterChip(false, onManageCategories, { Text("Edit…") }) }
        }
        when {
            s.totalBooks == 0 -> PlaceholderScreen("No books yet — tap + to add one")
            s.books.isEmpty() -> PlaceholderScreen("No books match")
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(110.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(s.books, key = { it.id }) { b ->
                    BookTile(b, Modifier.animateItem().clickable { onOpenBook(b.id) })
                }
            }
        }
    }
}

@Composable
private fun BookTile(b: BookEntity, modifier: Modifier) = Column(modifier) {
    BookCover(b.title, b.coverPath)
    Text(b.title, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
    if (b.authors.isNotBlank())
        Text(b.authors, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
}
