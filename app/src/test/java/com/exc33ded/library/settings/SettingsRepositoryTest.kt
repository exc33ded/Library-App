package com.exc33ded.library.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SettingsRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun repo() = SettingsRepository(
        PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + SupervisorJob())) {
            File(tmp.root, "settings.preferences_pb")
        },
    )

    @Test fun emptyStoreGivesDefaults() = runBlocking {
        assertEquals(AppSettings(), repo().settings.first())
    }

    @Test fun updatePersistsAllFields() = runBlocking {
        val r = repo()
        r.update {
            it.copy(
                themeMode = ThemeMode.DARK, dynamicColor = false, amoled = true,
                displayMode = LibraryDisplayMode.LIST, columns = 5,
                showProgressBadge = false, showStatusBadge = false,
            )
        }
        val s = r.settings.first()
        assertEquals(ThemeMode.DARK, s.themeMode)
        assertEquals(false, s.dynamicColor)
        assertEquals(true, s.amoled)
        assertEquals(LibraryDisplayMode.LIST, s.displayMode)
        assertEquals(5, s.columns)
        assertEquals(false, s.showProgressBadge)
        assertEquals(false, s.showStatusBadge)
    }

    @Test fun columnsAreClampedOnWrite() = runBlocking {
        val r = repo()
        r.update { it.copy(columns = 99) }
        assertEquals(6, r.settings.first().columns)
    }

    @Test fun lastExportAtPersists() = runBlocking {
        val r = repo()
        r.update { it.copy(lastExportAt = 123L) }
        assertEquals(123L, r.settings.first().lastExportAt)
    }

    @Test fun modelsLocationAndWifiOnlyPersist() = runBlocking {
        val r = repo()
        r.update { it.copy(modelsLocation = ModelsLocation.SD_CARD, wifiOnlyDownloads = false) }
        val s = r.settings.first()
        assertEquals(ModelsLocation.SD_CARD, s.modelsLocation)
        assertEquals(false, s.wifiOnlyDownloads)
    }

    @Test fun palettePersists() = runBlocking {
        val r = repo()
        r.update { it.copy(palette = ThemePalette.OCEAN) }
        assertEquals(ThemePalette.OCEAN, r.settings.first().palette)
    }

    @Test fun iconFollowsThemePersists() = runBlocking {
        val r = repo()
        r.update { it.copy(iconFollowsTheme = false) }
        assertEquals(false, r.settings.first().iconFollowsTheme)
    }
}
