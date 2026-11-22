package com.exc33ded.library.storage

import com.exc33ded.library.settings.ModelsLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class StorageRootsTest {
    private val files = File("/data/files")
    private val shared = File("/storage/emulated/0/Android/data/p/files")
    private val sd = File("/storage/ABCD-1234/Android/data/p/files")

    @Test fun phoneIsAlwaysAvailable() =
        assertEquals(File(files, "models"), resolveModelsDir(ModelsLocation.PHONE, files, emptyList()))

    @Test fun appFolderUsesFirstExternalDir() =
        assertEquals(File(shared, "models"), resolveModelsDir(ModelsLocation.APP_FOLDER, files, listOf(shared, sd)))

    @Test fun sdCardUsesSecondExternalDirOnlyIfPresent() {
        assertEquals(File(sd, "models"), resolveModelsDir(ModelsLocation.SD_CARD, files, listOf(shared, sd)))
        assertNull(resolveModelsDir(ModelsLocation.SD_CARD, files, listOf(shared)))
        assertNull(resolveModelsDir(ModelsLocation.SD_CARD, files, listOf(shared, null)))
    }

    @Test fun availableLocations() {
        assertEquals(listOf(ModelsLocation.PHONE), availableLocations(files, emptyList()))
        assertEquals(listOf(ModelsLocation.PHONE, ModelsLocation.APP_FOLDER), availableLocations(files, listOf(shared)))
        assertEquals(ModelsLocation.entries, availableLocations(files, listOf(shared, sd)))
    }
}
