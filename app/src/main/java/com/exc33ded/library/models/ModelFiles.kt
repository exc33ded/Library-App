package com.exc33ded.library.models

import java.io.File
import java.security.MessageDigest

fun finalFile(dir: File, spec: ModelSpec): File = File(dir, spec.fileName)
fun partFile(dir: File, spec: ModelSpec): File = File(dir, spec.fileName + ".part")

/** A model counts as installed only when the file exists with exactly the expected size. */
fun isInstalled(dir: File, spec: ModelSpec): Boolean = finalFile(dir, spec).let { it.isFile && it.length() == spec.sizeBytes }

fun sha256Hex(file: File): String {
    val md = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buf = ByteArray(1 shl 16)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            md.update(buf, 0, n)
        }
    }
    return md.digest().joinToString("") { "%02x".format(it) }
}

/** Total size of all files under [dir] (0 if it does not exist). */
fun dirSize(dir: File): Long = if (!dir.exists()) 0L else dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
