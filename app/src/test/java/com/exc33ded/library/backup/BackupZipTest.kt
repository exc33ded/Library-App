package com.exc33ded.library.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BackupZipTest {
    @get:Rule val tmp = TemporaryFolder()

    private val backup = BackupFile(
        exportedAt = 7L,
        books = listOf(BookDto(id = "b1", title = "T", status = "WANT", updatedAt = 1L, coverFile = "covers/b1.jpg")),
        categories = emptyList(), bookCategories = emptyList(),
        profile = ProfileDto(name = "Sam", bio = "hi", yearlyGoal = 12),
    )

    private fun zipBytes(write: (ZipOutputStream) -> Unit): ByteArray =
        ByteArrayOutputStream().also { ZipOutputStream(it).use(write) }.toByteArray()

    @Test fun writeThenReadRoundTripsJsonAndCovers() {
        val cover = File(tmp.root, "src.jpg").apply { writeBytes(byteArrayOf(1, 2, 3, 4)) }
        val out = ByteArrayOutputStream()
        BackupZip.write(out, backup, mapOf("covers/b1.jpg" to cover))
        val dest = File(tmp.root, "covers").apply { mkdirs() }
        val read = BackupZip.read(ByteArrayInputStream(out.toByteArray()), dest)
        assertEquals(backup, read)
        assertEquals(listOf<Byte>(1, 2, 3, 4), File(dest, "b1.jpg").readBytes().toList())
    }

    @Test fun missingCoverFilesAreSkippedOnWrite() {
        val out = ByteArrayOutputStream()
        BackupZip.write(out, backup, mapOf("covers/b1.jpg" to File(tmp.root, "does-not-exist.jpg")))
        val dest = File(tmp.root, "covers").apply { mkdirs() }
        BackupZip.read(ByteArrayInputStream(out.toByteArray()), dest)
        assertFalse(File(dest, "b1.jpg").exists())
    }

    @Test fun zipSlipEntriesAreIgnored() {
        val bytes = zipBytes { z ->
            z.putNextEntry(ZipEntry(BackupZip.JSON)); z.write(backupJson.encodeToString(BackupFile.serializer(), backup).toByteArray()); z.closeEntry()
            z.putNextEntry(ZipEntry("covers/../evil.txt")); z.write(9); z.closeEntry()
            z.putNextEntry(ZipEntry("covers/sub/dir.jpg")); z.write(9); z.closeEntry()
            z.putNextEntry(ZipEntry("other/thing.bin")); z.write(9); z.closeEntry()
        }
        val dest = File(tmp.root, "covers").apply { mkdirs() }
        BackupZip.read(ByteArrayInputStream(bytes), dest)
        assertTrue(dest.listFiles().orEmpty().isEmpty())
        assertFalse(File(tmp.root, "evil.txt").exists())
    }

    @Test fun missingJsonIsAnError() {
        val bytes = zipBytes { z -> z.putNextEntry(ZipEntry("covers/a.jpg")); z.write(1); z.closeEntry() }
        assertThrows(IOException::class.java) { BackupZip.read(ByteArrayInputStream(bytes), tmp.root) }
    }

    @Test fun newerFormatVersionIsRejected() {
        val future = backupJson.encodeToString(BackupFile.serializer(), backup.copy(version = 99))
        val bytes = zipBytes { z -> z.putNextEntry(ZipEntry(BackupZip.JSON)); z.write(future.toByteArray()); z.closeEntry() }
        assertThrows(IOException::class.java) { BackupZip.read(ByteArrayInputStream(bytes), tmp.root) }
    }
}
