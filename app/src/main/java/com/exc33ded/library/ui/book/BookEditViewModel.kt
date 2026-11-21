package com.exc33ded.library.ui.book

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.exc33ded.library.data.BookRepository
import com.exc33ded.library.data.Ids
import com.exc33ded.library.data.db.BookEntity
import com.exc33ded.library.data.db.CategoryEntity
import com.exc33ded.library.imports.ImportRepository
import com.exc33ded.library.metadata.BookCandidate
import com.exc33ded.library.metadata.CoverStore
import com.exc33ded.library.metadata.suggestCategories
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
    /** Category names suggested from subjects that the user hasn't added yet. */
    val suggestions: List<String> = emptyList(),
    val coverPreview: String? = null,
    val saving: Boolean = false,
    val error: FormError? = null,
    val savedId: String? = null,
)

@HiltViewModel(assistedFactory = BookEditViewModel.Factory::class)
class BookEditViewModel @AssistedInject constructor(
    private val repo: BookRepository,
    private val covers: CoverStore,
    private val imports: ImportRepository,
    @Assisted("bookId") private val bookId: String?,
    @Assisted private val prefill: BookCandidate?,
    @Assisted("importId") private val importId: String?,
) : ViewModel() {
    @AssistedFactory interface Factory { fun create(@Assisted("bookId") bookId: String?, prefill: BookCandidate?, @Assisted("importId") importId: String?): BookEditViewModel }

    private var original: BookEntity = BookEntity(id = bookId ?: Ids.new(), title = "")
    private var preselectDone = bookId != null // only auto-select suggested categories once, for new books
    private val _state = MutableStateFlow(EditUiState())
    val state: StateFlow<EditUiState> = _state

    init {
        viewModelScope.launch {
            bookId?.let { id ->
                repo.getBook(id)?.let { original = it }
                _state.update {
                    it.copy(form = BookForm.from(original), selectedCategories = repo.categoryIdsFor(id), coverPreview = original.coverPath)
                }
            }
            prefill?.let { p ->
                original = original.copy(olWorkKey = p.olWorkKey)
                _state.update {
                    it.copy(
                        form = BookForm(
                            title = p.title, authors = p.authors, isbn = p.isbn.orEmpty(),
                            pageCount = p.pageCount?.toString().orEmpty(), description = p.description.orEmpty(),
                        ),
                        coverPreview = p.coverUrl,
                    )
                }
            }
            if (prefill?.coverUrl == null && importId != null) {
                imports.get(importId)?.photoPath?.let { p -> _state.update { it.copy(coverPreview = p) } }
            }
        }
        viewModelScope.launch {
            repo.observeCategories().collect { cats ->
                _state.update { s ->
                    val names = cats.map { it.name }
                    val suggested = prefill?.let { suggestCategories(it.subjects, names) }.orEmpty()
                    val preselect = cats.filter { c -> suggested.any { it.equals(c.name, ignoreCase = true) } }.map { it.id }
                    val selected = if (!preselectDone) s.selectedCategories + preselect else s.selectedCategories
                    preselectDone = true
                    s.copy(
                        categories = cats,
                        selectedCategories = selected,
                        suggestions = suggested.filter { n -> cats.none { it.name.equals(n, ignoreCase = true) } },
                    )
                }
            }
        }
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
        if (s.saving) return
        s.form.validate()?.let { e -> _state.update { it.copy(error = e) }; return }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            val remote = s.coverPreview?.takeIf { it.startsWith("http") }
            val photo = importId?.let { imports.get(it)?.photoPath }
            val coverPath = remote?.let { covers.download(it, original.id) }
                ?: photo?.let { covers.copyLocal(it, original.id) }
                ?: original.coverPath
            val id = repo.saveBook(s.form.applyTo(original).copy(coverPath = coverPath), s.selectedCategories)
            importId?.let { imports.markDone(it) }
            _state.update { it.copy(savedId = id, saving = false) }
        }
    }
}
