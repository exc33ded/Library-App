package com.exc33ded.library.ui.library

import com.exc33ded.library.data.db.BookCategoryEntity
import com.exc33ded.library.data.db.BookEntity
import com.exc33ded.library.data.db.ReadingStatus

data class LibraryFilter(
    val status: ReadingStatus? = null,
    val categoryId: String? = null,
    val query: String = "",
)

fun filterBooks(books: List<BookEntity>, links: List<BookCategoryEntity>, f: LibraryFilter): List<BookEntity> {
    val inCategory = f.categoryId?.let { c -> links.filter { it.categoryId == c }.map { it.bookId }.toSet() }
    val q = f.query.trim()
    return books.filter { b ->
        (f.status == null || b.status == f.status) &&
            (inCategory == null || b.id in inCategory) &&
            (q.isEmpty() || b.title.contains(q, ignoreCase = true) || b.authors.contains(q, ignoreCase = true) ||
                (b.notes?.contains(q, ignoreCase = true) == true))
    }
}
