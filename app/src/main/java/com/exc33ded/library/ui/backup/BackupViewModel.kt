package com.exc33ded.library.ui.backup

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.exc33ded.library.backup.BackupManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

data class BackupUiState(val busy: Boolean = false, val message: String? = null)

@HiltViewModel
class BackupViewModel @Inject constructor(private val manager: BackupManager) : ViewModel() {
    private val _state = MutableStateFlow(BackupUiState())
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    fun export(uri: Uri) = run("export") {
        val n = manager.export(uri)
        "Exported $n ${if (n == 1) "book" else "books"}"
    }

    fun import(uri: Uri) = run("import") {
        val s = manager.import(uri)
        "Imported: ${s.added} new, ${s.updated} updated, ${s.unchanged} unchanged"
    }

    private fun run(what: String, block: suspend () -> String) {
        if (_state.value.busy) return
        _state.value = BackupUiState(busy = true)
        viewModelScope.launch {
            val msg = try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                "Couldn't $what: ${e.message}"
            }
            _state.value = BackupUiState(message = msg)
        }
    }
}
