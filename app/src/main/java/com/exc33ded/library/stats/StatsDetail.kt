package com.exc33ded.library.stats

import com.exc33ded.library.data.db.BookCategoryEntity
import com.exc33ded.library.data.db.BookEntity
import com.exc33ded.library.data.db.CategoryEntity
import com.exc33ded.library.data.db.ReadingStatus
import java.time.Instant
import java.time.ZoneId

data class MonthCount(val month: Int, val books: Int, val pages: Int)
data class CategoryCount(val name: String, val count: Int)

data class StatsDetail(
    val year: Int,
    val months: List<MonthCount>,          // always 12 entries, month 1..12
    val topCategories: List<CategoryCount>, // at most 5
    val ratingCounts: List<Int>,           // 5 entries: index 0 = one star ... index 4 = five stars
    val booksRead: Int,
    val pagesRead: Int,
    val averageRating: Double?,
)

private fun finishedIn(b: BookEntity, year: Int, zone: ZoneId): Int? =
    if (b.deleted || b.status != ReadingStatus.READ) null
    else b.finishedAt?.let { Instant.ofEpochMilli(it).atZone(zone) }?.takeIf { it.year == year }?.monthValue

fun computeStatsDetail(
    books: List<BookEntity>,
    categories: List<CategoryEntity>,
    links: List<BookCategoryEntity>,
    year: Int,
    zone: ZoneId = ZoneId.systemDefault(),
): StatsDetail {
    val readThisYear = books.mapNotNull { b -> finishedIn(b, year, zone)?.let { m -> b to m } }
    val months = (1..12).map { m ->
        val inMonth = readThisYear.filter { it.second == m }.map { it.first }
        MonthCount(m, inMonth.size, inMonth.sumOf { it.pageCount ?: 0 })
    }
    val ratings = readThisYear.mapNotNull { it.first.rating }
    val ids = readThisYear.map { it.first.id }.toSet()
    val names = categories.filter { !it.deleted }.associate { it.id to it.name }
    val top = links.filter { !it.deleted && it.bookId in ids }
        .groupingBy { it.categoryId }.eachCount()
        .mapNotNull { (cat, n) -> names[cat]?.let { CategoryCount(it, n) } }
        .sortedWith(compareByDescending<CategoryCount> { it.count }.thenBy { it.name })
        .take(5)
    return StatsDetail(
        year = year,
        months = months,
        topCategories = top,
        ratingCounts = (1..5).map { star -> ratings.count { it == star } },
        booksRead = readThisYear.size,
        pagesRead = months.sumOf { it.pages },
        averageRating = ratings.takeIf { it.isNotEmpty() }?.average(),
    )
}

/** Years that have at least one finished book, plus [currentYear], newest first. */
fun availableYears(books: List<BookEntity>, currentYear: Int, zone: ZoneId = ZoneId.systemDefault()): List<Int> =
    (books.filter { !it.deleted && it.status == ReadingStatus.READ }
        .mapNotNull { b -> b.finishedAt?.let { Instant.ofEpochMilli(it).atZone(zone).year } } + currentYear)
        .distinct().sortedDescending()
