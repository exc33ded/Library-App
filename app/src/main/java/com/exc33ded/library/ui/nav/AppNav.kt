package com.exc33ded.library.ui.nav

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
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
import com.exc33ded.library.ui.imports.ImportScreen
import com.exc33ded.library.ui.library.LibraryScreen
import com.exc33ded.library.ui.models.ModelsStorageScreen
import com.exc33ded.library.ui.profile.ProfileScreen
import com.exc33ded.library.ui.settings.SettingsScreen
import com.exc33ded.library.ui.stats.StatsScreen
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import kotlin.coroutines.cancellation.CancellationException

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppNav() {
    val backStack = rememberNavBackStack(LibraryKey)
    // -1 / +1 while a tab switch is animating (direction of travel), 0 for ordinary pushes.
    var tabDirection by remember { mutableIntStateOf(0) }
    var addMenuOpen by remember { mutableStateOf(false) }
    fun push(key: NavKey) { tabDirection = 0; backStack.add(key) }
    val spatial = MaterialTheme.motionScheme.defaultSpatialSpec<IntOffset>()
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
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
            else -> push(FindBookKey(isbn))
        }
    }
    val navVm: AppNavViewModel = hiltViewModel()
    val pickPhotos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(100)) { uris ->
        if (uris.isNotEmpty()) { navVm.import(uris); push(ImportKey) }
    }
    var cameraFile by remember { mutableStateOf<File?>(null) }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = cameraFile
        if (ok && f != null) { navVm.importFile(f.absolutePath); push(ImportKey) }
    }
    fun launchCamera() {
        val f = File(context.filesDir, "camera/${System.currentTimeMillis()}.jpg").apply { parentFile?.mkdirs() }
        cameraFile = f
        takePhoto.launch(FileProvider.getUriForFile(context, "${context.packageName}.files", f))
    }
    val addActions = listOf(
        AddAction(Icons.Filled.PhotoCamera, "Photo of cover") { addMenuOpen = false; launchCamera() },
        AddAction(Icons.Filled.PhotoLibrary, "Import from gallery") { addMenuOpen = false; pickPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        AddAction(Icons.Filled.QrCodeScanner, "Scan ISBN") { addMenuOpen = false; scanIsbn() },
        AddAction(Icons.Filled.Search, "Search online") { addMenuOpen = false; push(FindBookKey()) },
        AddAction(Icons.Filled.Edit, "Enter manually") { addMenuOpen = false; push(BookEditKey()) },
    )
    Scaffold(
        // Inner screens own their top insets (TopAppBar / statusBarsPadding); avoids a double status-bar gap.
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            AnimatedVisibility(visible = top in topLevelKeys) {
                BottomBar(
                    current = top,
                    onSelect = { key ->
                        addMenuOpen = false
                        tabDirection = Integer.signum(topLevelKeys.indexOf(key) - topLevelKeys.indexOf(top))
                        backStack.switchTo(key)
                    },
                    menuOpen = addMenuOpen,
                    onMenuToggle = { addMenuOpen = !addMenuOpen },
                )
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            NavDisplay(
                backStack = backStack,
                onBack = { backStack.removeLastOrNull() },
                entryDecorators = listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
                transitionSpec = {
                    val d = tabDirection
                    if (d == 0) {
                        // ordinary push: standard horizontal slide
                        slideInHorizontally { it } togetherWith slideOutHorizontally { -it }
                    } else {
                        // tab switch: a nudge toward the direction of travel plus a fade; timing from the Expressive motion scheme
                        (slideInHorizontally(spatial) { d * it / 3 } + fadeIn(effects)) togetherWith
                            (slideOutHorizontally(spatial) { -d * it / 3 } + fadeOut(effects))
                    }
                },
                popTransitionSpec = { slideInHorizontally { -it } togetherWith slideOutHorizontally { it } },
                entryProvider = entryProvider {
                    entry<LibraryKey> {
                        LibraryScreen(
                            onOpenBook = { id -> push(BookDetailKey(id)) },
                            onManageCategories = { push(CategoriesKey) },
                            onOpenImports = { push(ImportKey) },
                            onScanIsbn = { scanIsbn() },
                        )
                    }
                    entry<FeedKey> { PlaceholderScreen("Feed — Phase 6") }
                    entry<StatsKey> { StatsScreen() }
                    entry<ProfileKey> { ProfileScreen(onOpenSettings = { push(SettingsKey) }) }
                    entry<SettingsKey> { SettingsScreen(onBack = { backStack.removeLastOrNull() }, onOpenModels = { push(ModelsStorageKey) }) }
                    entry<ModelsStorageKey> { ModelsStorageScreen(onBack = { backStack.removeLastOrNull() }) }
                    entry<BookDetailKey> { key ->
                        BookDetailScreen(
                            bookId = key.id,
                            onBack = { backStack.removeLastOrNull() },
                            onEdit = { push(BookEditKey(key.id)) },
                        )
                    }
                    entry<BookEditKey> { key ->
                        BookEditScreen(bookId = key.id, prefill = key.prefill, importId = key.importId, onDone = { backStack.removeLastOrNull() })
                    }
                    entry<FindBookKey> { key ->
                        FindBookScreen(
                            isbn = key.isbn,
                            query = key.query,
                            onBack = { backStack.removeLastOrNull() },
                            onPicked = { c ->
                                backStack.removeLastOrNull() // replace Find with Edit so Back returns to the library
                                push(BookEditKey(prefill = c, importId = key.importId))
                            },
                        )
                    }
                    entry<CategoriesKey> { CategoriesScreen(onBack = { backStack.removeLastOrNull() }) }
                    entry<ImportKey> {
                        ImportScreen(
                            onBack = { backStack.removeLastOrNull() },
                            onEdit = { id, c -> push(BookEditKey(prefill = c, importId = id)) },
                            onSearch = { id, q -> push(FindBookKey(query = q, importId = id)) },
                        )
                    }
                },
            )
            AddMenuOverlay(visible = addMenuOpen && top in topLevelKeys, actions = addActions, onDismiss = { addMenuOpen = false })
        }
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
    menuOpen: Boolean,
    onMenuToggle: () -> Unit,
) {
    NavigationBar {
        Tab(current, LibraryKey, Icons.Filled.AutoStories, "Library", onSelect)
        Tab(current, FeedKey, Icons.Filled.RssFeed, "Feed", onSelect)
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { AddToggleButton(menuOpen, onMenuToggle) }
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
