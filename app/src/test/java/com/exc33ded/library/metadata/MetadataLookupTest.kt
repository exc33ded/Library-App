package com.exc33ded.library.metadata

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException

private class FakeSource(
    val isbnResult: BookCandidate? = null,
    val searchResult: List<BookCandidate> = emptyList(),
    val fail: Boolean = false,
    val enrichWith: String? = null,
) : BookSource {
    override suspend fun byIsbn(isbn: String) = if (fail) throw IOException("down") else isbnResult
    override suspend fun search(query: String, limit: Int) = if (fail) throw IOException("down") else searchResult
    override suspend fun enrich(c: BookCandidate) = if (enrichWith != null) c.copy(description = enrichWith) else c
}

class MetadataLookupTest {
    private val ol = BookCandidate("Yellowface", "R. F. Kuang", isbn = "9780063250833", olWorkKey = "/works/1")
    private val gb = BookCandidate("Yellowface", "R. F. Kuang", isbn = "9780063250833", description = "From Google.")

    @Test fun isbnPrefersOpenLibraryAndFillsDescriptionFromWork() = runTest {
        val r = MetadataLookup(FakeSource(ol, enrichWith = "From work."), FakeSource(gb)).lookupIsbn("9780063250833")
        assertEquals("From work.", r?.description)
    }

    @Test fun isbnFallsBackToGoogleDescriptionWhenWorkHasNone() = runTest {
        val r = MetadataLookup(FakeSource(ol), FakeSource(gb)).lookupIsbn("9780063250833")
        assertEquals("From Google.", r?.description)
    }

    @Test fun isbnUsesGoogleWhenOpenLibraryIsDown() = runTest {
        val r = MetadataLookup(FakeSource(fail = true), FakeSource(gb)).lookupIsbn("9780063250833")
        assertEquals("From Google.", r?.description)
    }

    @Test(expected = IOException::class)
    fun isbnThrowsWhenEverySourceFails() = runTest {
        MetadataLookup(FakeSource(fail = true), FakeSource(fail = true)).lookupIsbn("9780063250833")
    }

    @Test fun isbnNullWhenNobodyKnowsIt() = runTest {
        assertNull(MetadataLookup(FakeSource(), FakeSource()).lookupIsbn("9780063250833"))
    }

    @Test fun searchMergesAndDedupes() = runTest {
        val other = BookCandidate("Babel", "R. F. Kuang")
        val r = MetadataLookup(FakeSource(searchResult = listOf(ol)), FakeSource(searchResult = listOf(gb, other)))
            .search("kuang")
        assertEquals(listOf("Yellowface", "Babel"), r.map { it.title })
    }
}
