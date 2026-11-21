package com.exc33ded.library.metadata

import com.exc33ded.library.di.buildHttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenLibrarySourceTest {
    private val searchJson = """
        {"numFound":1,"docs":[{"key":"/works/OL29050559W","title":"Yellowface","author_name":["R. F. Kuang"],
        "cover_i":13195421,"number_of_pages_median":352,"isbn":["0063250837","9780063250833"],
        "subject":["form:novel","genre:satire"]}]}
    """.trimIndent()

    private fun source(vararg bodies: Pair<String, String>): Pair<OpenLibrarySource, MutableList<String>> {
        val urls = mutableListOf<String>()
        val engine = MockEngine { req ->
            urls += req.url.toString()
            val body = bodies.firstOrNull { req.url.encodedPath.startsWith(it.first) }?.second ?: """{"docs":[]}"""
            respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }
        return OpenLibrarySource(buildHttpClient(engine)) to urls
    }

    @Test fun byIsbnMapsFields() = runTest {
        val (src, urls) = source("/search.json" to searchJson)
        val c = src.byIsbn("9780063250833")!!
        assertEquals("Yellowface", c.title)
        assertEquals("R. F. Kuang", c.authors)
        assertEquals("9780063250833", c.isbn)
        assertEquals(352, c.pageCount)
        assertEquals("https://covers.openlibrary.org/b/id/13195421-L.jpg", c.coverUrl)
        assertEquals("/works/OL29050559W", c.olWorkKey)
        assertTrue(urls.single().contains("isbn=9780063250833"))
    }

    @Test fun searchReturnsEmptyWhenNoDocs() = runTest {
        val (src, _) = source()
        assertTrue(src.search("nothing").isEmpty())
    }

    @Test fun byIsbnNullWhenNotFound() = runTest {
        assertNull(source().first.byIsbn("9780000000002"))
    }

    @Test fun enrichReadsStringOrObjectDescription() = runTest {
        val base = BookCandidate("Yellowface", "R. F. Kuang", olWorkKey = "/works/OL1W")
        val (s1, _) = source("/works/" to """{"description":"Plain text."}""")
        assertEquals("Plain text.", s1.enrich(base).description)
        val (s2, _) = source("/works/" to """{"description":{"type":"/type/text","value":"Object text."}}""")
        assertEquals("Object text.", s2.enrich(base).description)
    }
}
