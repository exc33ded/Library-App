package com.exc33ded.library.ui.library

import com.exc33ded.library.data.db.BookCategoryEntity
import com.exc33ded.library.data.db.BookEntity
import com.exc33ded.library.data.db.ReadingStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryFilterTest {
    private val dune = BookEntity("1", "Dune", "Frank Herbert", status = ReadingStatus.READ)
    private val yf = BookEntity("2", "Yellowface", "Rebecca F. Kuang", status = ReadingStatus.READING)
    private val bond = BookEntity("3", "Great Stories for Children", "Ruskin Bond")
    private val links = listOf(BookCategoryEntity("l1", "1", "scifi"))
    private val all = listOf(dune, yf, bond)

    @Test fun noFilterReturnsAll() = assertEquals(all, filterBooks(all, links, LibraryFilter()))

    @Test fun byStatus() =
        assertEquals(listOf(yf), filterBooks(all, links, LibraryFilter(status = ReadingStatus.READING)))

    @Test fun byCategory() =
        assertEquals(listOf(dune), filterBooks(all, links, LibraryFilter(categoryId = "scifi")))

    @Test fun queryMatchesTitleOrAuthorCaseInsensitive() {
        assertEquals(listOf(bond), filterBooks(all, links, LibraryFilter(query = "ruskin")))
        assertEquals(listOf(yf), filterBooks(all, links, LibraryFilter(query = "YELLOW")))
    }

    @Test fun filtersCombine() =
        assertEquals(emptyList<BookEntity>(),
            filterBooks(all, links, LibraryFilter(status = ReadingStatus.READING, categoryId = "scifi")))
}
