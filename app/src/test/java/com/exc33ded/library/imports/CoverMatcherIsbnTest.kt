package com.exc33ded.library.imports

import com.exc33ded.library.metadata.BookCandidate
import com.exc33ded.library.metadata.BookSource
import com.exc33ded.library.metadata.MetadataLookup
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class IsbnSource(private val book: BookCandidate?) : BookSource {
    val queries = mutableListOf<String>()
    override suspend fun byIsbn(isbn: String): BookCandidate? = book
    override suspend fun search(query: String, limit: Int): List<BookCandidate> {
        queries += query
        return emptyList()
    }
}

class CoverMatcherIsbnTest {
    private val lines = listOf(OcrLine("ISBN 9780063250833", 30), OcrLine("YELLOWFACE", 120))
    private val yf = BookCandidate("Yellowface", "R. F. Kuang")

    @Test fun isbnLineIsLookedUpDirectlyWithoutTitleSearch() = runTest {
        val ol = IsbnSource(yf)
        val m = CoverMatcher(MetadataLookup(ol, IsbnSource(null))).match(lines)
        assertEquals("Yellowface", m!!.candidate.title)
        assertEquals("isbn:9780063250833", m.query)
        assertEquals(1.0, m.score, 0.0)
        assertTrue(ol.queries.isEmpty())
    }

    @Test fun fallsBackToTitleSearchWhenIsbnLookupFails() = runTest {
        val ol = IsbnSource(null)
        val m = CoverMatcher(MetadataLookup(ol, IsbnSource(null))).match(lines)
        assertNull(m)
        assertTrue(ol.queries.isNotEmpty())
    }
}
