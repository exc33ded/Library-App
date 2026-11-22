package com.exc33ded.library.ui.nav

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.navigation3.runtime.metadata
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import com.exc33ded.library.metadata.Isbn
import com.exc33ded.library.ui.backup.BackupScreen
import com.exc33ded.library.ui.book.BookDetailScreen
import com.exc33ded.library.ui.book.BookEditScreen
import com.exc33ded.library.ui.categories.CategoriesScreen
import com.exc33ded.library.ui.feed.FeedScreen
import com.exc33ded.library.ui.feed.ManageFeedsScreen
import com.exc33ded.library.ui.find.FindBookScreen
import com.exc33ded.library.ui.imports.ImportScreen
import com.exc33ded.library.ui.library.LibraryScreen
import com.exc33ded.library.ui.models.ModelsStorageScreen
import com.exc33ded.library.ui.profile.ProfileScreen
import com.exc33ded.library.ui.settings.SettingsScreen
import com.exc33ded.library.ui.stats.StatsScreen
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
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
    // Lightweight shared-axis transitions: short tweens (no long spring tails) and small offsets keep low-end phones smooth.
    val moveSpec = tween<IntOffset>(durationMillis = 220, easing = FastOutSlowInEasing)
    val fadeInSpec = tween<Float>(durationMillis = 180, delayMillis = 40, easing = LinearOutSlowInEasing)
    val fadeOutSpec = tween<Float>(durationMillis = 90, easing = FastOutLinearInEasing)
    val top = backStack.lastOrNull()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    // One shared scan flow for the "+" menu and the library search bar.
    fun scanIsbn() = scope.launch {
        snackbar.currentSnackbarData?.dismiss()
        val opening = launch { snackbar.showSnackbar("Opening scanner…", duration = SnackbarDuration.Short) }
        val raw = try {
            val scanner = GmsBarcodeScanning.getClient(
                context,
                GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_EAN_13).enableAutoZoom().build(),
            )
            // Ask Google Play services to fetch the scanner module up front so the first scan does not fail while it downloads.
            ModuleInstall.getClient(context).installModules(ModuleInstallRequest.newBuilder().addApi(scanner).build()).await()
            scanner.startScan().await().rawValue
        } catch (e: CancellationException) {
            null // user closed the scanner; the scope itself is not cancelled
        } catch (e: Exception) {
            Log.w("ScanIsbn", e)
            opening.cancel()
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar("Couldn't open the scanner", actionLabel = "Enter ISBN", duration = SnackbarDuration.Long)
            if (result == SnackbarResult.ActionPerformed) push(FindBookKey())
            null
        }
        opening.cancel()
        snackbar.currentSnackbarData?.dismiss()
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
                        // push: new screen eases in from the right a quarter of the width while fading in
                        (slideInHorizontally(moveSpec) { it / 4 } + fadeIn(fadeInSpec)) togetherWith
                            (slideOutHorizontally(moveSpec) { -it / 8 } + fadeOut(fadeOutSpec))
                    } else {
                        // tab switch: a small nudge toward the direction of travel plus a fade
                        (slideInHorizontally(moveSpec) { d * it / 10 } + fadeIn(fadeInSpec)) togetherWith
                            (slideOutHorizontally(moveSpec) { -d * it / 10 } + fadeOut(fadeOutSpec))
                    }
                },
                popTransitionSpec = {
                    (slideInHorizontally(moveSpec) { -it / 8 } + fadeIn(fadeInSpec)) togetherWith
                        (slideOutHorizontally(moveSpec) { it / 4 } + fadeOut(fadeOutSpec))
                },
                entryProvider = entryProvider {
                    entry<LibraryKey> {
                        LibraryScreen(
                            onOpenBook = { id -> push(BookDetailKey(id)) },
                            onManageCategories = { push(CategoriesKey) },
                            onOpenImports = { push(ImportKey) },
                            onScanIsbn = { scanIsbn() },
                        )
                    }
                    entry<FeedKey> { FeedScreen(onManage = { push(ManageFeedsKey) }) }
                    entry<ManageFeedsKey> { ManageFeedsScreen(onBack = { backStack.removeLastOrNull() }) }
                    entry<StatsKey> { StatsScreen() }
                    entry<ProfileKey> { ProfileScreen(onOpenSettings = { push(SettingsKey) }, onOpenBackup = { push(BackupKey) }) }
                    entry<SettingsKey> { SettingsScreen(onBack = { backStack.removeLastOrNull() }, onOpenModels = { push(ModelsStorageKey) }, onOpenBackup = { push(BackupKey) }) }
                    entry<BackupKey> { BackupScreen(onBack = { backStack.removeLastOrNull() }) }
                    entry<ModelsStorageKey> { ModelsStorageScreen(onBack = { backStack.removeLastOrNull() }) }
                    entry<BookDetailKey> { key ->
                        BookDetailScreen(
                            bookId = key.id,
                            onBack = { backStack.removeLastOrNull() },
                            onEdit = { push(BookEditKey(key.id)) },
                        )
                    }
                    entry<BookEditKey>(
                        metadata = metadata {
                            // Sheet-like: slides up on push, slides down on pop.
                            put(NavDisplay.TransitionKey) {
                                (slideInVertically(tween(260, easing = FastOutSlowInEasing)) { it / 6 } + fadeIn(tween(200))) togetherWith
                                    fadeOut(tween(200))
                            }
                            put(NavDisplay.PopTransitionKey) {
                                fadeIn(tween(200)) togetherWith
                                    (slideOutVertically(tween(240, easing = FastOutLinearInEasing)) { it / 6 } + fadeOut(tween(160)))
                            }
                        },
                    ) { key ->
                        BookEditScreen(
                            bookId = key.id,
                            prefill = key.prefill,
                            importId = key.importId,
                            onDone = { backStack.removeLastOrNull() },
                            onSaved = { title ->
                                backStack.removeLastOrNull()
                                scope.launch { snackbar.showSnackbar("Saved “$title”") }
                            },
                        )
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
