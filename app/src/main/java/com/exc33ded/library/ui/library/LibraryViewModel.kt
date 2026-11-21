package com.exc33ded.library.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.exc33ded.library.data.BookRepository
import com.exc33ded.library.data.db.BookEntity
import com.exc33ded.library.data.db.CategoryEntity
import com.exc33ded.library.data.db.ReadingStatus
import com.exc33ded.library.imports.ImportRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class LibraryUiState(
    val books: List<BookEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val filter: LibraryFilter = LibraryFilter(),
    val totalBooks: Int = 0,
)

@HiltViewModel
class LibraryViewModel @Inject constructor(repo: BookRepository, imports: ImportRepository) : ViewModel() {
    private val filter = MutableStateFlow(LibraryFilter())

    val state: StateFlow<LibraryUiState> = combine(
        repo.observeBooks(), repo.observeCategories(), repo.observeLinks(), filter,
    ) { books, cats, links, f ->
        LibraryUiState(filterBooks(books, links, f), cats, f, books.size)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    val openImports: StateFlow<Int> = imports.observeOpen().map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun setStatus(s: ReadingStatus?) = filter.update { it.copy(status = s) }
    fun setCategory(id: String?) = filter.update { it.copy(categoryId = id) }
    fun setQuery(q: String) = filter.update { it.copy(query = q) }
}
