package com.exc33ded.library.backup

import com.exc33ded.library.data.db.BookCategoryEntity
import com.exc33ded.library.data.db.BookEntity
import com.exc33ded.library.data.db.CategoryEntity
import com.exc33ded.library.data.db.ReadingStatus
import com.exc33ded.library.data.db.SummarySource
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupDtoTest {
    private val book = BookEntity(
        id = "b1", title = "Yellowface", authors = "R. F. Kuang", isbn = "9780063250833", description = "d",
        aiSummary = "s", aiSummarySource = SummarySource.USER, coverPath = "/data/user/0/p/files/covers/b1.jpg",
        coverRemotePath = "https://x/y.jpg", pageCount = 352, status = ReadingStatus.READING, currentPage = 120,
        rating = 4, startedAt = 10L, finishedAt = null, olWorkKey = "/works/OL1W", notes = "n", updatedAt = 99L, deleted = false,
    )

    @Test fun bookRoundTripsThroughDtoAndJson() {
        val dto = book.toDto(coverFile = "covers/b1.jpg")
        val json = backupJson.encodeToString(dto)
        val back = backupJson.decodeFromString<BookDto>(json).toEntity(coverPath = "/new/path/b1.jpg")
        assertEquals(book.copy(coverPath = "/new/path/b1.jpg", dirty = true), back)
    }

    @Test fun missingCoverGivesNullPath() {
        val dto = book.copy(coverPath = null).toDto(coverFile = null)
        assertNull(dto.coverFile)
        assertNull(dto.toEntity(coverPath = null).coverPath)
    }

    @Test fun categoryAndLinkRoundTrip() {
        val c = CategoryEntity("c1", "Fiction", color = 0xFFAA5500, updatedAt = 5L, deleted = true)
        assertEquals(c.copy(dirty = true), c.toDto().toEntity())
        val l = BookCategoryEntity("l1", "b1", "c1", updatedAt = 6L)
        assertEquals(l.copy(dirty = true), l.toDto().toEntity())
    }

    @Test fun unknownJsonKeysAreIgnoredAndDefaultsAreEncoded() {
        val file = BackupFile(exportedAt = 1L, books = emptyList(), categories = emptyList(), bookCategories = emptyList())
        assertTrue(backupJson.encodeToString(file).contains("\"version\":1"))
        val parsed = backupJson.decodeFromString<BackupFile>("""{"version":1,"exportedAt":2,"books":[],"categories":[],"bookCategories":[],"future":"x"}""")
        assertEquals(2L, parsed.exportedAt)
        assertNull(parsed.profile)
    }
}
