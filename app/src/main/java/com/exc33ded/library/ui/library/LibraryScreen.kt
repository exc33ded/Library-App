package com.exc33ded.library.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.exc33ded.library.settings.LibraryDisplayMode
import com.exc33ded.library.ui.common.PlaceholderScreen
import com.exc33ded.library.ui.common.StatusSelector
import com.exc33ded.library.ui.settings.DisplayOptions
import com.exc33ded.library.ui.settings.SettingsViewModel
import com.exc33ded.library.ui.theme.LocalAppSettings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onOpenBook: (String) -> Unit,
    onManageCategories: () -> Unit,
    onOpenImports: () -> Unit,
    vm: LibraryViewModel = hiltViewModel(),
    settingsVm: SettingsViewModel = hiltViewModel(),
) {
    val s by vm.state.collectAsStateWithLifecycle()
    val pending by vm.openImports.collectAsStateWithLifecycle()
    val cfg = LocalAppSettings.current
    var showDisplay by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = s.filter.query, onValueChange = vm::setQuery, singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, null) }, placeholder = { Text("Search title or author") },
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { showDisplay = true }) { Icon(Icons.Filled.GridView, contentDescription = "Display options") }
        }
        StatusSelector(s.filter.status, vm::setStatus, Modifier.fillMaxWidth(), allowNone = true)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (pending > 0) item { AssistChip(onClick = onOpenImports, label = { Text("Review imports ($pending)") }) }
            item { FilterChip(s.filter.categoryId == null, { vm.setCategory(null) }, { Text("All") }) }
            items(s.categories, key = { it.id }) { c ->
                FilterChip(s.filter.categoryId == c.id, { vm.setCategory(c.id) }, { Text(c.name) })
            }
            item { FilterChip(false, onManageCategories, { Text("Edit…") }) }
        }
        when {
            s.totalBooks == 0 -> PlaceholderScreen("No books yet — tap + to add one")
            s.books.isEmpty() -> PlaceholderScreen("No books match")
            else -> if (cfg.displayMode == LibraryDisplayMode.LIST) {
                LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                    items(s.books, key = { it.id }) { b ->
                        ListRow(b, cfg, Modifier.animateItem().clickable { onOpenBook(b.id) })
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(cfg.columns),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(if (cfg.columns >= 4) 8.dp else 12.dp),
                    verticalArrangement = Arrangement.spacedBy(if (cfg.columns >= 4) 8.dp else 16.dp),
                ) {
                    items(s.books, key = { it.id }) { b ->
                        val m = Modifier.animateItem().clickable { onOpenBook(b.id) }
                        if (cfg.displayMode == LibraryDisplayMode.OVERLAY_GRID) OverlayTile(b, cfg, m) else CaptionTile(b, cfg, m)
                    }
                }
            }
        }
    }
    if (showDisplay) ModalBottomSheet(onDismissRequest = { showDisplay = false }) {
        DisplayOptions(cfg, settingsVm::update, Modifier.padding(start = 16.dp, end = 16.dp, bottom = 32.dp))
    }
}
