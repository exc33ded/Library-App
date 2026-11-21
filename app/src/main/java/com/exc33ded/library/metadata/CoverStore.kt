package com.exc33ded.library.metadata

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/** Covers live in filesDir/covers/{bookId}.jpg; BookEntity.coverPath stores the absolute path. */
@Singleton
class CoverStore @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val client: HttpClient,
) {
    /** Returns the saved path, or null if the download failed (book is saved without a cover). */
    suspend fun download(url: String, bookId: String): String? = try {
        val bytes = client.get(url).bodyAsBytes()
        withContext(Dispatchers.IO) {
            val file = File(ctx.filesDir, "covers/$bookId.jpg").apply { parentFile?.mkdirs() }
            file.writeBytes(bytes)
            file.absolutePath
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }

    /** Use the user's own photo as the cover (no online cover found). */
    suspend fun copyLocal(path: String, bookId: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            File(path).copyTo(File(ctx.filesDir, "covers/$bookId.jpg").apply { parentFile?.mkdirs() }, overwrite = true).absolutePath
        }.getOrNull()
    }
}
