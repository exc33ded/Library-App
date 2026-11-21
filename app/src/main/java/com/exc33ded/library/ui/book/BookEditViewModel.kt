package com.exc33ded.library.ui.book

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.exc33ded.library.data.BookRepository
import com.exc33ded.library.data.Ids
import com.exc33ded.library.data.db.BookEntity
import com.exc33ded.library.data.db.CategoryEntity
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditUiState(
    val form: BookForm = BookForm(),
    val categories: List<CategoryEntity> = emptyList(),
    val selectedCategories: Set<String> = emptySet(),
    val error: FormError? = null,
    val savedId: String? = null,
)

@HiltViewModel(assistedFactory = BookEditViewModel.Factory::class)
class BookEditViewModel @AssistedInject constructor(
    private val repo: BookRepository,
    @Assisted private val bookId: String?,
) : ViewModel() {
    @AssistedFactory interface Factory { fun create(bookId: String?): BookEditViewModel }

    private var original: BookEntity = BookEntity(id = bookId ?: Ids.new(), title = "")
    private val _state = MutableStateFlow(EditUiState())
    val state: StateFlow<EditUiState> = _state

    init {
        viewModelScope.launch {
            bookId?.let { id ->
                repo.getBook(id)?.let { original = it }
                _state.update { it.copy(form = BookForm.from(original), selectedCategories = repo.categoryIdsFor(id)) }
            }
        }
        viewModelScope.launch { repo.observeCategories().collect { c -> _state.update { it.copy(categories = c) } } }
    }

    fun edit(change: (BookForm) -> BookForm) = _state.update { it.copy(form = change(it.form), error = null) }

    fun toggleCategory(id: String) = _state.update {
        it.copy(selectedCategories = if (id in it.selectedCategories) it.selectedCategories - id else it.selectedCategories + id)
    }

    fun addCategory(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { toggleCategory(repo.saveCategory(name)) }
    }

    fun save() {
        val s = _state.value
        s.form.validate()?.let { e -> _state.update { it.copy(error = e) }; return }
        viewModelScope.launch {
            val id = repo.saveBook(s.form.applyTo(original), s.selectedCategories)
            _state.update { it.copy(savedId = id) }
        }
    }
}
