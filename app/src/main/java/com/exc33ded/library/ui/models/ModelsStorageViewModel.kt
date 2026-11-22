package com.exc33ded.library.ui.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.exc33ded.library.models.ModelRepository
import com.exc33ded.library.models.ModelUi
import com.exc33ded.library.settings.ModelsLocation
import com.exc33ded.library.settings.SettingsRepository
import com.exc33ded.library.storage.StorageInfo
import com.exc33ded.library.storage.StorageRoots
import com.exc33ded.library.storage.StorageUsage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ModelsStorageViewModel @Inject constructor(
    private val repo: ModelRepository,
    private val settingsRepo: SettingsRepository,
    private val info: StorageInfo,
    roots: StorageRoots,
) : ViewModel() {
    val availableLocations: List<ModelsLocation> = roots.available()
    val message = MutableStateFlow<String?>(null)

    val models: StateFlow<List<ModelUi>> =
        repo.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Recomputed whenever the location changes or the model list changes (a download finished / was deleted). */
    val usage: StateFlow<StorageUsage?> = combine(settingsRepo.settings, models) { s, _ -> s.modelsLocation }
        .flatMapLatest { loc -> flow { emit(info.usage(loc)) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun download(id: String) { viewModelScope.launch { repo.download(id) } }
    fun cancel(id: String) = repo.cancel(id)
    fun delete(id: String) { viewModelScope.launch { repo.delete(id) } }
    fun setWifiOnly(v: Boolean) { viewModelScope.launch { settingsRepo.update { it.copy(wifiOnlyDownloads = v) } } }
    fun setLocation(loc: ModelsLocation) { viewModelScope.launch { message.value = repo.moveModels(loc) } }
    fun clearMessage() { message.value = null }
}
