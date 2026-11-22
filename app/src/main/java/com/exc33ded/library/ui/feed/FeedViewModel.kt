package com.exc33ded.library.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.exc33ded.library.feed.FeedItem
import com.exc33ded.library.feed.FeedRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FeedRow(val item: FeedItem, val unread: Boolean)

@HiltViewModel
class FeedViewModel @Inject constructor(private val repo: FeedRepository) : ViewModel() {
    val items: StateFlow<List<FeedRow>> = combine(repo.items, repo.readLinks) { items, read ->
        items.map { FeedRow(it, it.link !in read) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    init {
        viewModelScope.launch { if (repo.items.first().isEmpty()) refresh() }
    }

    fun refresh() {
        if (_refreshing.value) return
        viewModelScope.launch {
            _refreshing.value = true
            try {
                val failed = repo.refresh()
                if (failed > 0) _message.value = if (failed == 1) "1 feed couldn't be loaded" else "$failed feeds couldn't be loaded"
            } finally {
                _refreshing.value = false
            }
        }
    }

    fun open(link: String) { viewModelScope.launch { repo.markRead(link) } }

    fun messageShown() { _message.value = null }
}
