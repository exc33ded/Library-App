package com.exc33ded.library.ui.feed

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.exc33ded.library.feed.FeedRepository
import com.exc33ded.library.feed.FeedSource
import com.exc33ded.library.feed.isValidFeedUrl
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ManageFeedsViewModel @Inject constructor(private val repo: FeedRepository) : ViewModel() {
    val sources: StateFlow<List<FeedSource>> = repo.sources.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun toggle(url: String, enabled: Boolean) { viewModelScope.launch { repo.toggle(url, enabled) } }
    fun remove(url: String) { viewModelScope.launch { repo.removeSource(url) } }
    fun add(url: String) { viewModelScope.launch { repo.addSource(url) } }
    fun restoreDefaults() { viewModelScope.launch { repo.resetToDefaults() } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageFeedsScreen(onBack: () -> Unit, vm: ManageFeedsViewModel = hiltViewModel()) {
    val sources by vm.sources.collectAsStateWithLifecycle()
    var url by rememberSaveable { mutableStateOf("") }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Manage feeds") },
            navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        )
    }) { pad ->
        Column(Modifier.padding(pad)) {
            LazyColumn(Modifier.weight(1f)) {
                items(sources, key = { it.url }) { s ->
                    ListItem(
                        headlineContent = { Text(s.title) },
                        supportingContent = { Text(s.url, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Switch(checked = s.enabled, onCheckedChange = { vm.toggle(s.url, it) })
                                IconButton(onClick = { vm.remove(s.url) }) { Icon(Icons.Filled.Delete, "Remove ${s.title}") }
                            }
                        },
                    )
                }
            }
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = url, onValueChange = { url = it },
                    label = { Text("Add a feed (RSS or Atom URL)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = vm::restoreDefaults) { Text("Restore default feeds") }
                    Button(onClick = { vm.add(url); url = "" }, enabled = isValidFeedUrl(url)) { Text("Add") }
                }
            }
        }
    }
}
