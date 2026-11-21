package com.exc33ded.library.ui.nav

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.exc33ded.library.ui.book.BookDetailScreen
import com.exc33ded.library.ui.book.BookEditScreen
import com.exc33ded.library.ui.categories.CategoriesScreen
import com.exc33ded.library.ui.common.PlaceholderScreen
import com.exc33ded.library.ui.library.LibraryScreen

@Composable
fun AppNav() {
    val backStack = rememberNavBackStack(LibraryKey)
    val top = backStack.lastOrNull()
    Scaffold(
        bottomBar = {
            AnimatedVisibility(visible = top in topLevelKeys) {
                BottomBar(current = top, onSelect = { backStack.switchTo(it) }, onAdd = { backStack.add(BookEditKey()) })
            }
        },
    ) { padding ->
        NavDisplay(
            backStack = backStack,
            onBack = { backStack.removeLastOrNull() },
            modifier = Modifier.padding(padding),
            entryDecorators = listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
            entryProvider = entryProvider {
                entry<LibraryKey> {
                    LibraryScreen(
                        onOpenBook = { id -> backStack.add(BookDetailKey(id)) },
                        onManageCategories = { backStack.add(CategoriesKey) },
                    )
                }
                entry<FeedKey> { PlaceholderScreen("Feed — Phase 6") }
                entry<StatsKey> { PlaceholderScreen("Stats — Phase 5") }
                entry<ProfileKey> { PlaceholderScreen("Profile — Phase 5") }
                entry<BookDetailKey> { key ->
                    BookDetailScreen(
                        bookId = key.id,
                        onBack = { backStack.removeLastOrNull() },
                        onEdit = { backStack.add(BookEditKey(key.id)) },
                    )
                }
                entry<BookEditKey> { key -> BookEditScreen(bookId = key.id, onDone = { backStack.removeLastOrNull() }) }
                entry<CategoriesKey> { CategoriesScreen(onBack = { backStack.removeLastOrNull() }) }
            },
        )
    }
}

/** Top-level tabs reset the stack to a single destination. */
private fun NavBackStack<NavKey>.switchTo(key: NavKey) {
    if (lastOrNull() == key) return
    clear(); add(key)
}

@Composable
private fun BottomBar(current: NavKey?, onSelect: (NavKey) -> Unit, onAdd: () -> Unit) {
    NavigationBar {
        Tab(current, LibraryKey, Icons.Filled.AutoStories, "Library", onSelect)
        Tab(current, FeedKey, Icons.Filled.RssFeed, "Feed", onSelect)
        // ponytail: plain FAB opening manual entry; Phase 2 makes it the scan button + FAB menu,
        // Phase 7 gives it the MaterialShapes cookie shape and spring press.
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            FloatingActionButton(onClick = onAdd) { Icon(Icons.Filled.Add, contentDescription = "Add book") }
        }
        Tab(current, StatsKey, Icons.Filled.Insights, "Stats", onSelect)
        Tab(current, ProfileKey, Icons.Filled.Person, "Profile", onSelect)
    }
}

@Composable
private fun RowScope.Tab(current: NavKey?, key: NavKey, icon: ImageVector, label: String, onSelect: (NavKey) -> Unit) =
    NavigationBarItem(
        selected = current == key,
        onClick = { onSelect(key) },
        icon = { Icon(icon, contentDescription = null) },
        label = { Text(label) },
    )
