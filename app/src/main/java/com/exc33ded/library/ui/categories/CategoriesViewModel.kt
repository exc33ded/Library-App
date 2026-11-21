package com.exc33ded.library.ui.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.exc33ded.library.data.BookRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CategoriesViewModel @Inject constructor(private val repo: BookRepository) : ViewModel() {
    val categories = repo.observeCategories().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun add(name: String) { if (name.isNotBlank()) viewModelScope.launch { repo.saveCategory(name) } }
    fun rename(id: String, name: String) { if (name.isNotBlank()) viewModelScope.launch { repo.saveCategory(name, id) } }
    fun delete(id: String) = viewModelScope.launch { repo.deleteCategory(id) }
}
