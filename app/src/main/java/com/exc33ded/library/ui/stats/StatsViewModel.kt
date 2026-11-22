package com.exc33ded.library.ui.stats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.exc33ded.library.data.BookRepository
import com.exc33ded.library.stats.StatsDetail
import com.exc33ded.library.stats.availableYears
import com.exc33ded.library.stats.computeStatsDetail
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

data class StatsUiState(val years: List<Int>, val detail: StatsDetail)

@HiltViewModel
class StatsViewModel @Inject constructor(repo: BookRepository) : ViewModel() {
    private val selected = MutableStateFlow<Int?>(null)

    val state: StateFlow<StatsUiState> = combine(
        repo.observeBooks(), repo.observeCategories(), repo.observeLinks(), selected,
    ) { books, cats, links, sel ->
        val current = LocalDate.now().year
        val years = availableYears(books, current)
        val year = (sel ?: current).takeIf { it in years } ?: current
        StatsUiState(years, computeStatsDetail(books, cats, links, year))
    }.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000),
        LocalDate.now().year.let { y -> StatsUiState(listOf(y), computeStatsDetail(emptyList(), emptyList(), emptyList(), y)) },
    )

    fun selectYear(y: Int) { selected.value = y }
}
