package com.exc33ded.library.storage

import android.content.Context
import com.exc33ded.library.settings.ModelsLocation
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [externalDirs] = Context.getExternalFilesDirs(null): index 0 is the app folder on shared storage,
 * index 1 (if present) is the SD card. Returns null when that location is not available.
 */
fun resolveModelsDir(location: ModelsLocation, filesDir: File, externalDirs: List<File?>): File? = when (location) {
    ModelsLocation.PHONE -> File(filesDir, "models")
    ModelsLocation.APP_FOLDER -> externalDirs.getOrNull(0)?.let { File(it, "models") }
    ModelsLocation.SD_CARD -> externalDirs.getOrNull(1)?.let { File(it, "models") }
}

fun availableLocations(filesDir: File, externalDirs: List<File?>): List<ModelsLocation> =
    ModelsLocation.entries.filter { resolveModelsDir(it, filesDir, externalDirs) != null }

@Singleton
class StorageRoots @Inject constructor(@ApplicationContext private val ctx: Context) {
    private fun externals(): List<File?> = ctx.getExternalFilesDirs(null).toList()
    fun modelsDir(location: ModelsLocation): File? = resolveModelsDir(location, ctx.filesDir, externals())
    fun available(): List<ModelsLocation> = availableLocations(ctx.filesDir, externals())
    fun freeBytes(dir: File): Long { dir.mkdirs(); return dir.usableSpace }
}
