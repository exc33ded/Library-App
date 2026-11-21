package com.exc33ded.library.ui.nav

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.exc33ded.library.imports.ImportRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppNavViewModel @Inject constructor(private val imports: ImportRepository) : ViewModel() {
    fun import(uris: List<Uri>) = viewModelScope.launch { imports.enqueueUris(uris) }
    fun importFile(path: String) = viewModelScope.launch { imports.enqueue(listOf(path)) }
}
