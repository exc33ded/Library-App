package com.exc33ded.library.ui.find

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.exc33ded.library.metadata.BookCandidate
import com.exc33ded.library.metadata.MetadataLookup
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

data class FindUiState(
    val query: String = "",
    val loading: Boolean = false,
    val results: List<BookCandidate> = emptyList(),
    val message: String? = null,
    /** Set when a single result is ready to open in the edit screen. */
    val picked: BookCandidate? = null,
    val notFoundIsbn: String? = null,
)

@HiltViewModel(assistedFactory = FindBookViewModel.Factory::class)
class FindBookViewModel @AssistedInject constructor(
    private val lookup: MetadataLookup,
    @Assisted("isbn") private val isbn: String?,
    @Assisted("query") private val initialQuery: String?,
) : ViewModel() {
    @AssistedFactory interface Factory { fun create(@Assisted("isbn") isbn: String?, @Assisted("query") query: String?): FindBookViewModel }

    private val _state = MutableStateFlow(FindUiState())
    val state: StateFlow<FindUiState> = _state
    private var job: Job? = null

    init {
        when {
            isbn != null -> lookupIsbn(isbn)
            !initialQuery.isNullOrBlank() -> { _state.update { it.copy(query = initialQuery) }; search() }
        }
    }

    fun setQuery(q: String) = _state.update { it.copy(query = q) }

    fun search() {
        val q = _state.value.query.trim()
        if (q.length < 2) return
        launchLoading {
            val r = lookup.search(q)
            _state.update { it.copy(results = r, message = if (r.isEmpty()) "No books found" else null) }
        }
    }

    fun pick(c: BookCandidate) = launchLoading {
        val d = lookup.details(c)
        _state.update { it.copy(picked = d) }
    }

    fun consumePicked() = _state.update { it.copy(picked = null) }

    private fun lookupIsbn(isbn: String) = launchLoading {
        val r = lookup.lookupIsbn(isbn)
        _state.update { if (r != null) it.copy(picked = r) else it.copy(notFoundIsbn = isbn, message = "No match for ISBN $isbn") }
    }

    private fun launchLoading(block: suspend () -> Unit) {
        job?.cancel()
        _state.update { it.copy(loading = true, message = null) }
        job = viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(message = "Couldn't reach the book services — check your connection") }
            } finally {
                _state.update { it.copy(loading = false) }
            }
        }
    }
}
