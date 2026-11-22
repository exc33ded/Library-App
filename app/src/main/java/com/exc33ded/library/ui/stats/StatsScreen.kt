package com.exc33ded.library.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun StatsScreen(vm: StatsViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val d = state.detail
    val years = state.years // newest first
    val idx = years.indexOf(d.year)
    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Stats", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
            IconButton(onClick = { vm.selectYear(years[idx + 1]) }, enabled = idx in 0 until years.lastIndex) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous year")
            }
            Text("${d.year}", style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = { vm.selectYear(years[idx - 1]) }, enabled = idx > 0) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next year")
            }
        }
        if (d.booksRead == 0) {
            Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
                Text(
                    "No finished books in ${d.year} yet — mark a book as Read to see your stats.",
                    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceAround) {
                    Tile("${d.booksRead}", "Books")
                    Tile("${d.pagesRead}", "Pages")
                    Tile(d.averageRating?.let { "%.1f".format(it) } ?: "—", "Avg rating")
                }
            }
            ChartCard("Books per month") { MonthlyBarChart(d.months) }
            ChartCard("Top categories") {
                if (d.topCategories.isEmpty()) {
                    Text("No categories yet", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else HorizontalBars(d.topCategories.map { it.name to it.count })
            }
            ChartCard("Ratings") {
                HorizontalBars((5 downTo 1).map { star -> "$star ★" to d.ratingCounts[star - 1] })
            }
        }
    }
}

@Composable
private fun Tile(value: String, label: String) = Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(value, style = MaterialTheme.typography.headlineSmall)
    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun ChartCard(title: String, content: @Composable () -> Unit) = Card(Modifier.fillMaxWidth()) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}
