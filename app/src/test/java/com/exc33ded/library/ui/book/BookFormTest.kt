package com.exc33ded.library.ui.book

import com.exc33ded.library.data.db.BookEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BookFormTest {
    @Test fun titleIsRequired() =
        assertEquals(FormError.TitleRequired, BookForm(title = "  ").validate())

    @Test fun pageCountMustBePositiveNumber() {
        assertEquals(FormError.BadPageCount, BookForm(title = "A", pageCount = "abc").validate())
        assertEquals(FormError.BadPageCount, BookForm(title = "A", pageCount = "0").validate())
        assertNull(BookForm(title = "A", pageCount = "").validate())
    }

    @Test fun isbnAcceptsDigitsAndHyphensOnly() {
        assertEquals(FormError.BadIsbn, BookForm(title = "A", isbn = "97A").validate())
        assertNull(BookForm(title = "A", isbn = "978-0-14-311774-2").validate())
    }

    @Test fun roundTripsThroughEntityAndTrims() {
        val form = BookForm(title = " Dune ", authors = "Frank Herbert", pageCount = "600", isbn = "978-0441013593")
        val e = form.applyTo(BookEntity(id = "x", title = ""))
        assertEquals("Dune", e.title)
        assertEquals(600, e.pageCount)
        assertEquals("9780441013593", e.isbn)
        assertEquals(form.copy(title = "Dune", isbn = "9780441013593"), BookForm.from(e))
    }
}
