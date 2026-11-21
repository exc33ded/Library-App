package com.exc33ded.library.ui.categories

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(onBack: () -> Unit, vm: CategoriesViewModel = hiltViewModel()) {
    val cats by vm.categories.collectAsStateWithLifecycle()
    var newName by rememberSaveable { mutableStateOf("") }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Categories") },
            navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        )
    }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp)) {
            item {
                OutlinedTextField(newName, { newName = it }, Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("New category") },
                    trailingIcon = { TextButton({ vm.add(newName); newName = "" }) { Text("Add") } })
            }
            items(cats, key = { it.id }) { c ->
                var name by remember(c.name) { mutableStateOf(c.name) }
                OutlinedTextField(
                    name, { name = it }, Modifier.fillMaxWidth().animateItem().padding(top = 8.dp), singleLine = true,
                    trailingIcon = {
                        if (name != c.name) TextButton({ vm.rename(c.id, name) }) { Text("Save") }
                        else IconButton({ vm.delete(c.id) }) { Icon(Icons.Filled.Delete, "Delete ${c.name}") }
                    },
                )
            }
        }
    }
}
