package com.exc33ded.library.imports

import com.exc33ded.library.data.db.ImportItemEntity
import com.exc33ded.library.data.db.ImportState
import com.exc33ded.library.metadata.BookCandidate
import com.exc33ded.library.metadata.BookSource
import com.exc33ded.library.metadata.MetadataLookup
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

private class FakePrep : PhotoPrep {
    override suspend fun prepare(source: String, id: String) = "/photos/$id.jpg"
}
private class FakeReader(val lines: List<OcrLine>) : TextReader {
    override suspend fun read(photoPath: String) = lines
}
private class FakeSource(val results: Map<String, List<BookCandidate>> = emptyMap(), val fail: Boolean = false) : BookSource {
    val queries = mutableListOf<String>()
    override suspend fun byIsbn(isbn: String): BookCandidate? = null
    override suspend fun search(query: String, limit: Int): List<BookCandidate> {
        queries += query
        if (fail) throw IOException("offline")
        return results[query].orEmpty()
    }
}

class ImportProcessorTest {
    private val item = ImportItemEntity(id = "i1", source = "content://x", state = ImportState.PROCESSING)
    private val yellowLines = listOf(OcrLine("YELLOWFACE", 120), OcrLine("REBECCA F. KUANG", 40))
    private val yf = BookCandidate("Yellowface", "R. F. Kuang")

    private fun processor(lines: List<OcrLine>, ol: FakeSource, gb: FakeSource = FakeSource()) =
        ImportProcessor(FakePrep(), FakeReader(lines), MetadataLookup(ol, gb))

    @Test fun matchGoesToReviewWithCandidateJson() = runTest {
        val r = processor(yellowLines, FakeSource(mapOf("yellowface rebecca f kuang" to listOf(yf)))).process(item)
        assertEquals(ImportState.REVIEW, r.state)
        assertEquals("/photos/i1.jpg", r.photoPath)
        assertEquals("Yellowface", Json.decodeFromString<BookCandidate>(r.resultJson!!).title)
    }

    @Test fun retriesWithTopTwoLinesWhenFullQueryFindsNothing() = runTest {
        val lines = yellowLines + OcrLine("Some Publisher", 10)
        val ol = FakeSource(mapOf("yellowface rebecca f kuang" to listOf(yf)))
        val r = processor(lines, ol).process(item)
        assertEquals(ImportState.REVIEW, r.state)
        assertEquals(listOf("yellowface rebecca f kuang some publisher", "yellowface rebecca f kuang"), ol.queries)
    }

    @Test fun noTextFails() = runTest {
        val r = processor(emptyList(), FakeSource()).process(item)
        assertEquals(ImportState.FAILED, r.state)
        assertEquals("No readable text on the cover", r.error)
    }

    @Test fun noMatchFailsButKeepsQueryForManualSearch() = runTest {
        val r = processor(yellowLines, FakeSource()).process(item)
        assertEquals(ImportState.FAILED, r.state)
        assertEquals("yellowface rebecca f kuang", r.query)
        assertTrue(r.error!!.startsWith("No match"))
    }

    @Test fun ocrCrashFailsTheItemInsteadOfThrowing() = runTest {
        val crashing = object : TextReader { override suspend fun read(photoPath: String): List<OcrLine> = error("ml kit") }
        val r = ImportProcessor(FakePrep(), crashing, MetadataLookup(FakeSource(), FakeSource())).process(item)
        assertEquals(ImportState.FAILED, r.state)
        assertEquals("Couldn't read the cover text", r.error)
    }

    @Test fun offlineFailsWithRetryableMessage() = runTest {
        val r = processor(yellowLines, FakeSource(fail = true), FakeSource(fail = true)).process(item)
        assertEquals(ImportState.FAILED, r.state)
        assertEquals("Couldn't reach the book services", r.error)
    }
}
