package com.exc33ded.library.ui.book

import com.exc33ded.library.data.db.BookEntity
import com.exc33ded.library.data.db.ReadingStatus

enum class FormError { TitleRequired, BadPageCount, BadIsbn }

data class BookForm(
    val title: String = "",
    val authors: String = "",
    val isbn: String = "",
    val pageCount: String = "",
    val description: String = "",
    val notes: String = "",
    val status: ReadingStatus = ReadingStatus.WANT,
) {
    fun validate(): FormError? = when {
        title.isBlank() -> FormError.TitleRequired
        pageCount.isNotBlank() && (pageCount.trim().toIntOrNull() ?: 0) <= 0 -> FormError.BadPageCount
        isbn.isNotBlank() && !isbn.trim().matches(Regex("[0-9Xx-]+")) -> FormError.BadIsbn
        else -> null
    }

    fun applyTo(e: BookEntity): BookEntity = e.copy(
        title = title.trim(),
        authors = authors.trim(),
        isbn = isbn.filter { it.isDigit() || it == 'X' || it == 'x' }.uppercase().ifEmpty { null },
        pageCount = pageCount.trim().toIntOrNull(),
        description = description.trim().ifEmpty { null },
        notes = notes.trim().ifEmpty { null },
        status = status,
    )

    companion object {
        fun from(e: BookEntity) = BookForm(
            title = e.title,
            authors = e.authors,
            isbn = e.isbn.orEmpty(),
            pageCount = e.pageCount?.toString().orEmpty(),
            description = e.description.orEmpty(),
            notes = e.notes.orEmpty(),
            status = e.status,
        )
    }
}
