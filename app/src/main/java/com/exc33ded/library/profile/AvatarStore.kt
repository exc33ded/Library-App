package com.exc33ded.library.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Largest centred square inside a w x h image, as (left, top, size). */
fun centerSquare(w: Int, h: Int): Triple<Int, Int, Int> {
    val s = minOf(w, h)
    return Triple((w - s) / 2, (h - s) / 2, s)
}

@Singleton
class AvatarStore @Inject constructor(@ApplicationContext private val ctx: Context) {
    private val dir: File get() = File(ctx.filesDir, "profile").apply { mkdirs() }

    /**
     * Decodes [uri] (EXIF-rotated), centre-crops to a square, scales to at most 512 px and saves it under a
     * new file name (so Coil never serves a stale image), deleting any older avatar. Returns the new path.
     */
    suspend fun save(uri: Uri): String = withContext(Dispatchers.IO) {
        val decoded = ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, uri)) { d, _, _ ->
            d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        val (x, y, s) = centerSquare(decoded.width, decoded.height)
        val square = Bitmap.createBitmap(decoded, x, y, s, s)
        val size = minOf(s, 512)
        val scaled = Bitmap.createScaledBitmap(square, size, size, true)
        val out = File(dir, "avatar-${System.currentTimeMillis()}.jpg")
        out.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        dir.listFiles()?.filter { it != out }?.forEach { it.delete() }
        listOf(decoded, square, scaled).distinct().forEach { it.recycle() }
        out.absolutePath
    }

    fun remove() {
        dir.listFiles()?.forEach { it.delete() }
    }
}
