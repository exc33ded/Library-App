package com.exc33ded.library.imports

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExtractIsbnTest {
    @Test fun findsIsbn13WithHyphens() =
        assertEquals("9780063250833", extractIsbn(listOf(OcrLine("ISBN 978-0-06-325083-3", 30))))

    @Test fun convertsIsbn10ToIsbn13() =
        assertEquals("9780063250833", extractIsbn(listOf(OcrLine("ISBN 0-06-325083-7", 30))))

    @Test fun findsBareIsbn13() =
        assertEquals("9780063250833", extractIsbn(listOf(OcrLine("9780063250833", 30))))

    @Test fun rejectsBadChecksum() =
        assertNull(extractIsbn(listOf(OcrLine("ISBN 978-0-06-325083-4", 30))))

    @Test fun ignoresLinesWithoutIsbn() =
        assertNull(extractIsbn(listOf(OcrLine("YELLOWFACE", 100), OcrLine("REBECCA F. KUANG", 40))))

    @Test fun findsIsbnAcrossLines() =
        assertEquals("9780063250833", extractIsbn(listOf(OcrLine("Published by", 20), OcrLine("ISBN: 9780063250833", 20))))
}
