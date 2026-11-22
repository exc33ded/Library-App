package com.exc33ded.library.ui.book

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.exc33ded.library.metadata.BookCandidate
import com.exc33ded.library.ui.common.BookCover
import com.exc33ded.library.ui.common.StatusSelector
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BookEditScreen(
    bookId: String?,
    prefill: BookCandidate?,
    importId: String?,
    onDone: () -> Unit,
    onSaved: (title: String) -> Unit,
    vm: BookEditViewModel = hiltViewModel<BookEditViewModel, BookEditViewModel.Factory>(
        creationCallback = { it.create(bookId, prefill, importId) },
    ),
) {
    val s by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(s.savedId) {
        if (s.savedId != null) { delay(600); onSaved(s.form.title) }
    }
    var newCategory by rememberSaveable { mutableStateOf("") }
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(vm::setCoverFromUri) }
    var cameraFile by remember { mutableStateOf<File?>(null) }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = cameraFile
        if (ok && f != null) vm.setCoverFromUri(Uri.fromFile(f))
    }
    fun launchCamera() {
        val f = File(context.filesDir, "camera/${System.currentTimeMillis()}.jpg").apply { parentFile?.mkdirs() }
        cameraFile = f
        takePhoto.launch(FileProvider.getUriForFile(context, "${context.packageName}.files", f))
    }
    val hasCover = s.coverPreview != null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (bookId == null) "Add book" else "Edit book") },
                navigationIcon = { IconButton(onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
        floatingActionButton = { SaveButton(s.saving, s.saved, vm::save) },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                if (hasCover) BookCover(s.form.title, s.coverPreview, Modifier.width(120.dp).clickable { sheetOpen = true })
                else CoverAddPlaceholder(Modifier.width(120.dp)) { sheetOpen = true }
                FilledTonalButton({ sheetOpen = true }) { Text(if (hasCover) "Edit cover" else "Add cover") }
            }
            val f = s.form
            OutlinedTextField(f.title, { v -> vm.edit { it.copy(title = v) } }, Modifier.fillMaxWidth(),
                label = { Text("Title *") }, isError = s.error == FormError.TitleRequired, singleLine = true)
            OutlinedTextField(f.authors, { v -> vm.edit { it.copy(authors = v) } }, Modifier.fillMaxWidth(),
                label = { Text("Author(s)") }, singleLine = true)
            OutlinedTextField(f.isbn, { v -> vm.edit { it.copy(isbn = v) } }, Modifier.fillMaxWidth(),
                label = { Text("ISBN") }, isError = s.error == FormError.BadIsbn, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedTextField(f.pageCount, { v -> vm.edit { it.copy(pageCount = v) } }, Modifier.fillMaxWidth(),
                label = { Text("Pages") }, isError = s.error == FormError.BadPageCount, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            Text("Status", style = MaterialTheme.typography.labelLarge)
            StatusSelector(f.status, { st -> if (st != null) vm.edit { it.copy(status = st) } }, Modifier.fillMaxWidth())
            Text("Categories", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                s.categories.forEach { c ->
                    FilterChip(c.id in s.selectedCategories, { vm.toggleCategory(c.id) }, { Text(c.name) })
                }
            }
            if (s.suggestions.isNotEmpty()) {
                Text("Suggested", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    s.suggestions.forEach { name ->
                        AssistChip(onClick = { vm.addCategory(name) }, label = { Text("+ $name") })
                    }
                }
            }
            OutlinedTextField(newCategory, { newCategory = it }, Modifier.fillMaxWidth(),
                label = { Text("New category") }, singleLine = true,
                trailingIcon = { TextButton({ vm.addCategory(newCategory); newCategory = "" }) { Text("Add") } })
            OutlinedTextField(f.description, { v -> vm.edit { it.copy(description = v) } }, Modifier.fillMaxWidth(),
                label = { Text("Description") }, minLines = 3)
            OutlinedTextField(f.notes, { v -> vm.edit { it.copy(notes = v) } }, Modifier.fillMaxWidth(),
                label = { Text("My notes") }, minLines = 2)
            Spacer(Modifier.height(88.dp)) // keeps the last field clear of the save button
        }
    }
    if (sheetOpen) {
        val sheetState = rememberModalBottomSheetState()
        val scope = rememberCoroutineScope()
        fun act(run: () -> Unit) { scope.launch { sheetState.hide() }.invokeOnCompletion { sheetOpen = false; run() } }
        ModalBottomSheet(onDismissRequest = { sheetOpen = false }, sheetState = sheetState) {
            Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
                SheetRow(Icons.Filled.PhotoLibrary, "Choose from gallery") {
                    act { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                }
                SheetRow(Icons.Filled.PhotoCamera, "Take a photo") { act { launchCamera() } }
                if (hasCover) SheetRow(Icons.Filled.Delete, "Remove cover") { act { vm.removeCover() } }
                if (prefill?.coverUrl != null && s.coverPreview != prefill.coverUrl) {
                    SheetRow(Icons.Filled.CloudDownload, "Use the online cover") { act { vm.useOnlineCover() } }
                }
            }
        }
    }
}

@Composable
private fun SheetRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun CoverAddPlaceholder(modifier: Modifier, onClick: () -> Unit) {
    val line = MaterialTheme.colorScheme.outline
    Box(
        modifier.aspectRatio(2f / 3f).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).drawBehind {
            val w = 2.dp.toPx()
            drawRoundRect(
                line, topLeft = Offset(w / 2, w / 2), size = Size(size.width - w, size.height - w),
                cornerRadius = CornerRadius(12.dp.toPx()),
                style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f))),
            )
        },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(Icons.Filled.AddAPhoto, null, tint = line)
            Text("Add cover", style = MaterialTheme.typography.labelMedium, color = line)
        }
    }
}

private enum class SaveUi { Idle, Saving, Saved }

@Composable
private fun SaveButton(saving: Boolean, saved: Boolean, onSave: () -> Unit) {
    val ui = if (saved) SaveUi.Saved else if (saving) SaveUi.Saving else SaveUi.Idle
    val container by animateColorAsState(
        if (ui == SaveUi.Saved) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer,
        tween(250), label = "saveColor",
    )
    ExtendedFloatingActionButton(onClick = { if (ui == SaveUi.Idle) onSave() }, containerColor = container) {
        AnimatedContent(
            ui,
            transitionSpec = { (fadeIn(tween(150)) + scaleIn(tween(150), 0.8f)) togetherWith (fadeOut(tween(100)) + scaleOut(tween(100), 0.8f)) },
            label = "saveContent",
        ) { st ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                when (st) {
                    SaveUi.Idle -> { Icon(Icons.Filled.Save, null); Text("Save") }
                    SaveUi.Saving -> { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp); Text("Saving…") }
                    SaveUi.Saved -> { Icon(Icons.Filled.Check, null); Text("Saved") }
                }
            }
        }
    }
}
