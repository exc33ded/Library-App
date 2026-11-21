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
import org.junit.Test

class GoogleBooksSourceTest {
    private val json = """
        {"items":[{"volumeInfo":{"title":"Yellowface","authors":["R. F. Kuang"],"description":"A satire.",
        "pageCount":336,"categories":["Fiction / Satire"],
        "imageLinks":{"thumbnail":"http://books.google.com/books/content?id=x&printsec=frontcover&img=1&zoom=1&edge=curl"},
        "industryIdentifiers":[{"type":"ISBN_10","identifier":"0063250837"},{"type":"ISBN_13","identifier":"9780063250833"}]}}]}
    """.trimIndent()

    private val urls = mutableListOf<io.ktor.http.Url>()
    private fun source(key: String) = GoogleBooksSource(
        buildHttpClient(MockEngine { req ->
            urls += req.url
            respond(json, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }),
        apiKey = key,
    )

    @Test fun mapsVolume() = runTest {
        val c = source("").byIsbn("9780063250833")!!
        assertEquals("Yellowface", c.title)
        assertEquals("A satire.", c.description)
        assertEquals("9780063250833", c.isbn)
        assertEquals(listOf("Fiction / Satire"), c.subjects)
        assertEquals("https://books.google.com/books/content?id=x&printsec=frontcover&img=1&zoom=1", c.coverUrl)
        assertEquals("isbn:9780063250833", urls.single().parameters["q"])
    }

    @Test fun keyOnlySentWhenPresent() = runTest {
        source("").search("dune")
        assertNull(urls.last().parameters["key"])
        source("abc").search("dune")
        assertEquals("abc", urls.last().parameters["key"])
    }
}
