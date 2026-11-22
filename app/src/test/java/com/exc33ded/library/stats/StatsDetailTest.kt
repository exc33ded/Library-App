package com.exc33ded.library.stats

import com.exc33ded.library.data.db.BookCategoryEntity
import com.exc33ded.library.data.db.BookEntity
import com.exc33ded.library.data.db.CategoryEntity
import com.exc33ded.library.data.db.ReadingStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class StatsDetailTest {
    private fun at(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    private fun read(id: String, y: Int, m: Int, pages: Int? = null, rating: Int? = null, deleted: Boolean = false) =
        BookEntity(id = id, title = id, status = ReadingStatus.READ, pageCount = pages, rating = rating, finishedAt = at(y, m, 15), deleted = deleted)

    private val fiction = CategoryEntity("c-fic", "Fiction")
    private val history = CategoryEntity("c-his", "History")
    private fun link(book: String, cat: String, deleted: Boolean = false) = BookCategoryEntity("$book|$cat", book, cat, deleted = deleted)

    @Test fun emptyLibraryGivesTwelveZeroMonths() {
        val s = computeStatsDetail(emptyList(), emptyList(), emptyList(), 2026, ZoneOffset.UTC)
        assertEquals(12, s.months.size)
        assertEquals(List(12) { 0 }, s.months.map { it.books })
        assertEquals(0, s.booksRead); assertEquals(0, s.pagesRead)
        assertNull(s.averageRating)
        assertEquals(List(5) { 0 }, s.ratingCounts)
        assertEquals(emptyList<CategoryCount>(), s.topCategories)
    }

    @Test fun countsOnlyReadBooksFinishedInTheYearPerMonth() {
        val books = listOf(
            read("jan1", 2026, 1, pages = 100), read("jan2", 2026, 1, pages = 50), read("mar", 2026, 3, pages = 200),
            read("lastYear", 2025, 12, pages = 999),
            BookEntity(id = "reading", title = "r", status = ReadingStatus.READING, finishedAt = at(2026, 2, 1)),
            read("gone", 2026, 5, pages = 10, deleted = true),
        )
        val s = computeStatsDetail(books, emptyList(), emptyList(), 2026, ZoneOffset.UTC)
        assertEquals(listOf(2, 0, 1), s.months.take(3).map { it.books })
        assertEquals(listOf(150, 0, 200), s.months.take(3).map { it.pages })
        assertEquals(3, s.booksRead)
        assertEquals(350, s.pagesRead)
    }

    @Test fun ratingsAverageAndDistribution() {
        val books = listOf(read("a", 2026, 1, rating = 5), read("b", 2026, 2, rating = 5), read("c", 2026, 3, rating = 3), read("d", 2026, 4))
        val s = computeStatsDetail(books, emptyList(), emptyList(), 2026, ZoneOffset.UTC)
        assertEquals(listOf(0, 0, 1, 0, 2), s.ratingCounts)        // index 0 = one star ... index 4 = five stars
        assertEquals(13.0 / 3.0, s.averageRating!!, 1e-9)
    }

    @Test fun topCategoriesCountReadBooksOfTheYearIgnoringDeletedLinksAndSortByCountThenName() {
        val books = listOf(read("a", 2026, 1), read("b", 2026, 2), read("c", 2026, 3), read("old", 2025, 1))
        val links = listOf(
            link("a", "c-fic"), link("b", "c-fic"), link("c", "c-his"), link("old", "c-his"),
            link("a", "c-his", deleted = true),
        )
        val s = computeStatsDetail(books, listOf(fiction, history), links, 2026, ZoneOffset.UTC)
        assertEquals(listOf(CategoryCount("Fiction", 2), CategoryCount("History", 1)), s.topCategories)
    }

    @Test fun topCategoriesAreCappedAtFive() {
        val cats = (1..7).map { CategoryEntity("c$it", "Cat$it") }
        val books = (1..7).map { read("b$it", 2026, 1) }
        val links = (1..7).map { link("b$it", "c$it") }
        assertEquals(5, computeStatsDetail(books, cats, links, 2026, ZoneOffset.UTC).topCategories.size)
    }

    @Test fun yearsListsYearsWithFinishedBooksPlusTheCurrentOneNewestFirst() {
        val books = listOf(read("a", 2024, 1), read("b", 2026, 1), read("c", 2024, 6), read("gone", 2023, 1, deleted = true))
        assertEquals(listOf(2026, 2024), availableYears(books, currentYear = 2026, zone = ZoneOffset.UTC))
        assertEquals(listOf(2027, 2026, 2024), availableYears(books, currentYear = 2027, zone = ZoneOffset.UTC))
    }
}
