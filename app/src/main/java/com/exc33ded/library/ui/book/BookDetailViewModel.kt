package com.exc33ded.library.ui.book

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.exc33ded.library.data.BookRepository
import com.exc33ded.library.data.db.ReadingStatus
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = BookDetailViewModel.Factory::class)
class BookDetailViewModel @AssistedInject constructor(
    private val repo: BookRepository,
    @Assisted private val bookId: String,
) : ViewModel() {
    @AssistedFactory interface Factory { fun create(bookId: String): BookDetailViewModel }

    val book = repo.observeBook(bookId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setStatus(s: ReadingStatus) = viewModelScope.launch { repo.setStatus(bookId, s) }
    fun setProgress(page: Int) = viewModelScope.launch { repo.setProgress(bookId, page) }
    fun delete(onDeleted: () -> Unit) = viewModelScope.launch { repo.deleteBook(bookId); onDeleted() }
}
