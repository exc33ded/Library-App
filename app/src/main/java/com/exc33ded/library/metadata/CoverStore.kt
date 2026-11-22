package com.exc33ded.library.metadata

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
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

/** Target size that fits the longest edge into max, keeping aspect ratio and never upscaling. */
fun coverTargetSize(w: Int, h: Int, max: Int = 1200): Pair<Int, Int> {
    val longest = maxOf(w, h)
    if (longest <= max) return w to h
    val scale = max.toDouble() / longest
    return maxOf(1, Math.round(w * scale).toInt()) to maxOf(1, Math.round(h * scale).toInt())
}

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

    fun pendingFile(bookId: String): File = File(ctx.filesDir, "covers/$bookId.pending.jpg")

    fun clearPending(bookId: String) {
        pendingFile(bookId).delete()
    }

    /** Promote the pending cover to covers/{bookId}.jpg and delete the pending file. */
    suspend fun commitPending(bookId: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val target = File(ctx.filesDir, "covers/$bookId.jpg").apply { parentFile?.mkdirs() }
            pendingFile(bookId).copyTo(target, overwrite = true)
            clearPending(bookId)
            target.absolutePath
        }.getOrNull()
    }

    suspend fun delete(bookId: String) = withContext(Dispatchers.IO) {
        File(ctx.filesDir, "covers/$bookId.jpg").delete()
        Unit
    }

    /** Decode a picked or captured image (EXIF-rotated), downsize to 1200 px, store as the pending cover. */
    suspend fun saveFromUri(uri: Uri, bookId: String): String? = withContext(Dispatchers.IO) {
        try {
            val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, uri)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val (w, h) = coverTargetSize(info.size.width, info.size.height)
                decoder.setTargetSize(w, h)
            }
            val file = pendingFile(bookId).apply { parentFile?.mkdirs() }
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            file.absolutePath
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }
}
