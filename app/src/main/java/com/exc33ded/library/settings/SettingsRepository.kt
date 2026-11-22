package com.exc33ded.library.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(private val store: DataStore<Preferences>) {
    val settings: Flow<AppSettings> = store.data.map(::read)

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        store.edit { p ->
            val n = transform(read(p))
            p[THEME] = n.themeMode.name
            p[DYNAMIC] = n.dynamicColor
            p[AMOLED] = n.amoled
            p[MODE] = n.displayMode.name
            p[COLUMNS] = clampColumns(n.columns)
            p[PROGRESS] = n.showProgressBadge
            p[STATUS] = n.showStatusBadge
            p[MODELS_LOC] = n.modelsLocation.name
            p[WIFI_ONLY] = n.wifiOnlyDownloads
        }
    }

    private fun read(p: Preferences): AppSettings {
        val d = AppSettings()
        return AppSettings(
            themeMode = p[THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: d.themeMode,
            dynamicColor = p[DYNAMIC] ?: d.dynamicColor,
            amoled = p[AMOLED] ?: d.amoled,
            displayMode = p[MODE]?.let { runCatching { LibraryDisplayMode.valueOf(it) }.getOrNull() } ?: d.displayMode,
            columns = clampColumns(p[COLUMNS] ?: d.columns),
            showProgressBadge = p[PROGRESS] ?: d.showProgressBadge,
            showStatusBadge = p[STATUS] ?: d.showStatusBadge,
            modelsLocation = p[MODELS_LOC]?.let { runCatching { ModelsLocation.valueOf(it) }.getOrNull() } ?: d.modelsLocation,
            wifiOnlyDownloads = p[WIFI_ONLY] ?: d.wifiOnlyDownloads,
        )
    }

    private companion object {
        val THEME = stringPreferencesKey("theme_mode")
        val DYNAMIC = booleanPreferencesKey("dynamic_color")
        val AMOLED = booleanPreferencesKey("amoled")
        val MODE = stringPreferencesKey("display_mode")
        val COLUMNS = intPreferencesKey("columns")
        val PROGRESS = booleanPreferencesKey("show_progress_badge")
        val STATUS = booleanPreferencesKey("show_status_badge")
        val MODELS_LOC = stringPreferencesKey("models_location")
        val WIFI_ONLY = booleanPreferencesKey("wifi_only_downloads")
    }
}
