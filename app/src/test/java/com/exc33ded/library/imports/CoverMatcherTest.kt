package com.exc33ded.library.imports

import com.exc33ded.library.metadata.BookCandidate
import com.exc33ded.library.metadata.BookSource
import com.exc33ded.library.metadata.MetadataLookup
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

private class MapSource(val results: Map<String, List<BookCandidate>>) : BookSource {
    val queries = mutableListOf<String>()
    override suspend fun byIsbn(isbn: String): BookCandidate? = null
    override suspend fun search(query: String, limit: Int): List<BookCandidate> { queries += query; return results[query].orEmpty() }
}

class CoverMatcherTest {
    private val lines = listOf(OcrLine("YELLOWFACE", 120), OcrLine("REBECCA F. KUANG", 40), OcrLine("Some Publisher", 10))
    private val yf = BookCandidate("Yellowface", "R. F. Kuang")
    private val peste = BookCandidate("La Peste", "Albert Camus")

    private fun matcher(ol: MapSource) = CoverMatcher(MetadataLookup(ol, MapSource(emptyMap())))

    @Test fun picksBestScoringCandidateNotTheFirst() = runTest {
        val m = matcher(MapSource(mapOf("yellowface rebecca f kuang some publisher" to listOf(peste, yf)))).match(lines)
        assertEquals("Yellowface", m!!.candidate.title)
    }

    @Test fun keepsTryingVariantsUntilOneIsConfident() = runTest {
        val ol = MapSource(mapOf(
            "yellowface rebecca f kuang some publisher" to listOf(peste),   // wrong, low score
            "yellowface" to listOf(yf),
        ))
        val m = matcher(ol).match(lines)
        assertEquals("Yellowface", m!!.candidate.title)
        assertEquals("yellowface", m.query)
    }

    @Test fun fallsBackToFirstResultOfFirstNonEmptyQueryWhenNothingIsConfident() = runTest {
        val m = matcher(MapSource(mapOf("yellowface rebecca f kuang some publisher" to listOf(peste)))).match(lines)
        assertEquals("La Peste", m!!.candidate.title)
    }

    @Test fun nothingFoundIsNull() = runTest {
        assertNull(matcher(MapSource(emptyMap())).match(lines))
    }

    @Test fun noQueriesMeansNoSearchAtAll() = runTest {
        val ol = MapSource(emptyMap())
        assertNull(matcher(ol).match(listOf(OcrLine("@ 7", 50))))
        assertEquals(emptyList<String>(), ol.queries)
        assertNotNull(OcrDump("a.jpg", emptyList()))
    }
}
