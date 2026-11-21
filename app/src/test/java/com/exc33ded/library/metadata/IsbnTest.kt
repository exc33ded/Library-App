package com.exc33ded.library.metadata

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IsbnTest {
    @Test fun validIsbn13IsKept() = assertEquals("9780063250833", Isbn.normalize("978-0-06-325083-3"))
    @Test fun badChecksumRejected() = assertNull(Isbn.normalize("9780063250834"))
    @Test fun nonBookEanRejected() = assertNull(Isbn.normalize("4006381333931")) // valid EAN, not 978/979
    @Test fun isbn10ConvertedTo13() = assertEquals("9780063250833", Isbn.normalize("0063250837"))
    @Test fun isbn10WithXCheckDigit() = assertEquals("9780804429573", Isbn.normalize("080442957X"))
    @Test fun garbageRejected() {
        assertNull(Isbn.normalize(""))
        assertNull(Isbn.normalize("12345"))
    }
}
