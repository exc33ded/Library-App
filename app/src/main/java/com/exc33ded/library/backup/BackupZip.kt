package com.exc33ded.library.backup

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object BackupZip {
    const val JSON = "backup.json"
    private const val COVERS = "covers/"

    /** [covers] maps a zip entry name ("covers/<id>.<ext>") to the file to store; missing files are skipped. */
    fun write(out: OutputStream, backup: BackupFile, covers: Map<String, File>) {
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(JSON))
            zip.write(backupJson.encodeToString(BackupFile.serializer(), backup).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            covers.forEach { (name, file) ->
                if (file.isFile) {
                    zip.putNextEntry(ZipEntry(name))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
    }

    /**
     * Parses backup.json and extracts cover images into [coversDir] (overwriting same-named files).
     * Only entries named exactly "covers/<plain file name>" are extracted; anything else (paths, "..") is ignored.
     */
    fun read(input: InputStream, coversDir: File): BackupFile {
        var json: String? = null
        ZipInputStream(input).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name
                when {
                    entry.isDirectory -> Unit
                    name == JSON -> json = zip.readBytes().toString(Charsets.UTF_8)
                    name.startsWith(COVERS) -> {
                        val leaf = name.removePrefix(COVERS)
                        if (leaf.isNotEmpty() && '/' !in leaf && '\\' !in leaf && leaf != ".." && leaf != ".") {
                            coversDir.mkdirs()
                            File(coversDir, leaf).outputStream().use { zip.copyTo(it) }
                        }
                    }
                }
            }
        }
        val text = json ?: throw IOException("This is not a library backup (backup.json is missing)")
        val backup = backupJson.decodeFromString(BackupFile.serializer(), text)
        if (backup.version > BACKUP_VERSION) throw IOException("This backup was made by a newer version of the app")
        return backup
    }
}
