package com.exc33ded.library.ui.nav

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.exc33ded.library.metadata.Isbn
import com.exc33ded.library.ui.book.BookDetailScreen
import com.exc33ded.library.ui.book.BookEditScreen
import com.exc33ded.library.ui.categories.CategoriesScreen
import com.exc33ded.library.ui.common.PlaceholderScreen
import com.exc33ded.library.ui.find.FindBookScreen
import com.exc33ded.library.ui.library.LibraryScreen
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.cancellation.CancellationException

@Composable
fun AppNav() {
    val backStack = rememberNavBackStack(LibraryKey)
    val top = backStack.lastOrNull()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    fun scanIsbn() = scope.launch {
        val raw = try {
            GmsBarcodeScanning.getClient(
                context,
                GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_EAN_13).enableAutoZoom().build(),
            ).startScan().await().rawValue
        } catch (e: CancellationException) {
            null // user closed the scanner; the scope itself is not cancelled
        } catch (e: Exception) {
            snackbar.showSnackbar("Scanner unavailable — try Search instead"); null
        }
        val isbn = raw?.let(Isbn::normalize)
        when {
            raw == null -> Unit
            isbn == null -> snackbar.showSnackbar("That barcode isn't a book ISBN")
            else -> backStack.add(FindBookKey(isbn))
        }
    }
    Scaffold(
        // Inner screens own their top insets (TopAppBar / statusBarsPadding); avoids a double status-bar gap.
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            AnimatedVisibility(visible = top in topLevelKeys) {
                BottomBar(
                    current = top,
                    onSelect = { backStack.switchTo(it) },
                    onScan = { scanIsbn() },
                    onSearch = { backStack.add(FindBookKey()) },
                    onManual = { backStack.add(BookEditKey()) },
                )
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
                entry<BookEditKey> { key ->
                    BookEditScreen(bookId = key.id, prefill = key.prefill, onDone = { backStack.removeLastOrNull() })
                }
                entry<FindBookKey> { key ->
                    FindBookScreen(
                        isbn = key.isbn,
                        onBack = { backStack.removeLastOrNull() },
                        onPicked = { c ->
                            backStack.removeLastOrNull() // replace Find with Edit so Back returns to the library
                            backStack.add(BookEditKey(prefill = c))
                        },
                    )
                }
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
private fun BottomBar(
    current: NavKey?,
    onSelect: (NavKey) -> Unit,
    onScan: () -> Unit,
    onSearch: () -> Unit,
    onManual: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    NavigationBar {
        Tab(current, LibraryKey, Icons.Filled.AutoStories, "Library", onSelect)
        Tab(current, FeedKey, Icons.Filled.RssFeed, "Feed", onSelect)
        // ponytail: plain FAB + DropdownMenu; Phase 7 swaps in the Expressive FAB menu and cookie shape.
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            FloatingActionButton(onClick = { menu = true }) { Icon(Icons.Filled.Add, contentDescription = "Add book") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Scan ISBN") }, leadingIcon = { Icon(Icons.Filled.QrCodeScanner, null) },
                    onClick = { menu = false; onScan() })
                DropdownMenuItem(text = { Text("Search") }, leadingIcon = { Icon(Icons.Filled.Search, null) },
                    onClick = { menu = false; onSearch() })
                DropdownMenuItem(text = { Text("Enter manually") }, leadingIcon = { Icon(Icons.Filled.Edit, null) },
                    onClick = { menu = false; onManual() })
            }
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
