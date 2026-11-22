package com.exc33ded.library.storage

import android.content.Context
import com.exc33ded.library.models.dirSize
import com.exc33ded.library.settings.ModelsLocation
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class StorageUsage(
    val databaseBytes: Long,
    val coversBytes: Long,
    val photosBytes: Long,
    val modelsBytes: Long,
    val modelsPath: String?,
    val freeBytes: Long,
)

@Singleton
class StorageInfo @Inject constructor(@ApplicationContext private val ctx: Context, private val roots: StorageRoots) {
    suspend fun usage(location: ModelsLocation): StorageUsage = withContext(Dispatchers.IO) {
        val db = ctx.getDatabasePath("library.db")
        val dbBytes = listOf("", "-wal", "-shm").sumOf { File(db.path + it).let { f -> if (f.isFile) f.length() else 0L } }
        val models = roots.modelsDir(location)
        StorageUsage(
            databaseBytes = dbBytes,
            coversBytes = dirSize(File(ctx.filesDir, "covers")),
            photosBytes = dirSize(File(ctx.filesDir, "imports")) + dirSize(File(ctx.filesDir, "camera")),
            modelsBytes = models?.let(::dirSize) ?: 0L,
            modelsPath = models?.absolutePath,
            freeBytes = models?.let(roots::freeBytes) ?: 0L,
        )
    }
}
