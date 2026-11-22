package com.exc33ded.library.ui.imports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.exc33ded.library.data.BookRepository
import com.exc33ded.library.data.Ids
import com.exc33ded.library.data.db.BookEntity
import com.exc33ded.library.data.db.ImportItemEntity
import com.exc33ded.library.imports.ImportRepository
import com.exc33ded.library.metadata.BookCandidate
import com.exc33ded.library.metadata.CoverStore
import com.exc33ded.library.metadata.suggestCategories
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject

data class ImportRow(val item: ImportItemEntity, val candidate: BookCandidate?)

@HiltViewModel
class ImportViewModel @Inject constructor(
    private val imports: ImportRepository,
    private val books: BookRepository,
    private val covers: CoverStore,
) : ViewModel() {
    val rows = imports.observeOpen()
        .map { list -> list.map { ImportRow(it, it.resultJson?.let { j -> runCatching { Json.decodeFromString<BookCandidate>(j) }.getOrNull() }) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _adding = MutableStateFlow<Set<String>>(emptySet())
    val adding: StateFlow<Set<String>> = _adding
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    fun retry(id: String) = viewModelScope.launch { imports.retry(id) }
    fun skip(id: String) = viewModelScope.launch { imports.markDone(id) }
    fun dismissFailed() = viewModelScope.launch { imports.dismissFailed() }
    fun dismissAll() = viewModelScope.launch { imports.dismissReviewable() }

    /** One-tap add: online cover, else the user's photo; existing categories that match subjects. */
    fun add(row: ImportRow) = viewModelScope.launch {
        val c = row.candidate ?: return@launch
        if (row.item.id in _adding.value) return@launch
        _adding.update { it + row.item.id }
        val id = Ids.new()
        val cover = c.coverUrl?.let { covers.download(it, id) } ?: row.item.photoPath?.let { covers.copyLocal(it, id) }
        val cats = books.observeCategories().first()
        val names = suggestCategories(c.subjects, cats.map { it.name })
        val catIds = cats.filter { cat -> names.any { it.equals(cat.name, ignoreCase = true) } }.map { it.id }.toSet()
        books.saveBook(
            BookEntity(
                id = id, title = c.title, authors = c.authors, isbn = c.isbn, description = c.description,
                pageCount = c.pageCount, olWorkKey = c.olWorkKey, coverPath = cover,
            ),
            catIds,
        )
        imports.markDone(row.item.id)
        _adding.update { it - row.item.id }
        _messages.tryEmit("Added “${c.title}”")
    }
}
