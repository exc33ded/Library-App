package com.exc33ded.library.data

import com.exc33ded.library.data.db.BookEntity
import com.exc33ded.library.data.db.ReadingStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BookRulesTest {
    private val book = BookEntity(id = "b", title = "Yellowface", authors = "Rebecca F. Kuang", pageCount = 320)

    @Test fun startingReadingSetsStartedAtOnce() {
        val r = book.withStatus(ReadingStatus.READING, now = 100)
        assertEquals(100L, r.startedAt)
        assertEquals(100L, r.withStatus(ReadingStatus.READING, now = 200).startedAt)
    }

    @Test fun finishingSetsFinishedAtAndLastPage() {
        val r = book.withStatus(ReadingStatus.READ, now = 500)
        assertEquals(500L, r.finishedAt)
        assertEquals(320, r.currentPage)
        assertEquals(500L, r.startedAt) // never started explicitly -> same moment
    }

    @Test fun movingBackToWantClearsFinishedAt() {
        val r = book.withStatus(ReadingStatus.READ, 500).withStatus(ReadingStatus.WANT, 600)
        assertNull(r.finishedAt)
    }

    @Test fun progressIsClampedToPageCount() {
        assertEquals(320, book.withProgress(999).currentPage)
        assertEquals(0, book.withProgress(-4).currentPage)
    }

    @Test fun progressOnUnknownPageCountOnlyClampsNegative() =
        assertEquals(50, book.copy(pageCount = null).withProgress(50).currentPage)

    @Test fun reachingLastPageMarksRead() =
        assertEquals(ReadingStatus.READ, book.copy(status = ReadingStatus.READING).withProgress(320, now = 9).status)
}
