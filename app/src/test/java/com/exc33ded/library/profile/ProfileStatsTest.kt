package com.exc33ded.library.profile

import com.exc33ded.library.data.db.BookEntity
import com.exc33ded.library.data.db.ReadingStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class ProfileStatsTest {
    private fun millis(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    private val now = millis(2026, 6, 15)

    private fun book(id: String, status: ReadingStatus, pages: Int? = null, page: Int = 0, rating: Int? = null, finished: Long? = null) =
        BookEntity(id = id, title = id, status = status, pageCount = pages, currentPage = page, rating = rating, finishedAt = finished)

    @Test fun emptyLibrary() {
        val s = computeStats(emptyList(), now, ZoneOffset.UTC)
        assertEquals(ProfileStats(0, 0, 0, 0, 0, null, 0), s)
    }

    @Test fun countsPagesAndRating() {
        val books = listOf(
            book("a", ReadingStatus.READ, pages = 300, rating = 5, finished = millis(2026, 2, 1)),
            book("b", ReadingStatus.READ, pages = 200, rating = 3, finished = millis(2026, 3, 1)),
            book("c", ReadingStatus.READING, pages = 400, page = 120),
            book("d", ReadingStatus.WANT, pages = 999),
            book("e", ReadingStatus.DNF, pages = 500, page = 50),
        )
        val s = computeStats(books, now, ZoneOffset.UTC)
        assertEquals(2, s.read); assertEquals(1, s.reading); assertEquals(1, s.want); assertEquals(1, s.dnf)
        assertEquals(300 + 200 + 120, s.pagesRead) // READ = page count, READING = current page, others 0
        assertEquals(4.0, s.averageRating!!, 1e-9)
        assertEquals(2, s.readThisYear)
    }

    @Test fun readThisYearUsesTheCalendarYearBoundary() {
        val books = listOf(
            book("new", ReadingStatus.READ, finished = millis(2026, 1, 1)),                 // first instant of 2026: counts
            book("old", ReadingStatus.READ, finished = millis(2026, 1, 1) - 1),             // last millisecond of 2025: does not
            book("unknown", ReadingStatus.READ, finished = null),                           // no date: does not
            book("notRead", ReadingStatus.READING, finished = millis(2026, 3, 3)),          // wrong status: does not
        )
        assertEquals(1, computeStats(books, now, ZoneOffset.UTC).readThisYear)
    }

    @Test fun noRatingsMeansNullAverage() =
        assertNull(computeStats(listOf(book("a", ReadingStatus.READ)), now, ZoneOffset.UTC).averageRating)
}
