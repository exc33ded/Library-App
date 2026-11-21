package com.exc33ded.library.imports

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import kotlin.math.max

interface PhotoPrep {
    /** Decode (EXIF-rotated), downscale, save to filesDir/imports/{id}.jpg; returns that path. */
    suspend fun prepare(source: String, id: String): String
}

interface TextReader {
    suspend fun read(photoPath: String): List<OcrLine>
}

class AndroidPhotoPrep @Inject constructor(@ApplicationContext private val ctx: Context) : PhotoPrep {
    override suspend fun prepare(source: String, id: String): String = withContext(Dispatchers.IO) {
        val src = if (source.startsWith("content://")) ImageDecoder.createSource(ctx.contentResolver, Uri.parse(source))
        else ImageDecoder.createSource(File(source))
        // ImageDecoder applies EXIF orientation itself.
        val bmp = ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
            val longest = max(info.size.width, info.size.height)
            if (longest > MAX_EDGE) {
                val s = MAX_EDGE.toFloat() / longest
                decoder.setTargetSize((info.size.width * s).toInt(), (info.size.height * s).toInt())
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        val out = File(ctx.filesDir, "imports/$id.jpg").apply { parentFile?.mkdirs() }
        out.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        bmp.recycle()
        out.absolutePath
    }

    private companion object { const val MAX_EDGE = 1600 }
}

/** ML Kit Latin OCR. Tries 0, 90 and 270 degrees (photos are often sideways) and keeps the read with most letters. */
class MlKitTextReader @Inject constructor() : TextReader {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    override suspend fun read(photoPath: String): List<OcrLine> {
        val bmp = withContext(Dispatchers.IO) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(File(photoPath))) { d, _, _ ->
                d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        }
        try {
            return listOf(0, 90, 270)
                .map { rot -> recognize(bmp, rot) }
                .maxBy { letterCount(it) }
        } finally {
            bmp.recycle()
        }
    }

    private suspend fun recognize(bmp: Bitmap, rotation: Int): List<OcrLine> {
        val text = recognizer.process(InputImage.fromBitmap(bmp, rotation)).await()
        return text.textBlocks.flatMap { it.lines }.map { line ->
            val box = line.boundingBox
            // Height in the rotated frame = the shorter side of the line's box.
            OcrLine(line.text, box?.let { minOf(it.width(), it.height()) } ?: 0)
        }
    }
}
