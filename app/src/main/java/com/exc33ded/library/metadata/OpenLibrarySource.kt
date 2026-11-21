package com.exc33ded.library.metadata

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

class OpenLibrarySource(private val client: HttpClient) : BookSource {

    override suspend fun byIsbn(isbn: String): BookCandidate? =
        query { parameter("isbn", isbn); parameter("limit", 1) }.firstOrNull()?.copy(isbn = isbn)

    override suspend fun search(query: String, limit: Int): List<BookCandidate> =
        query { parameter("q", query); parameter("limit", limit) }

    override suspend fun enrich(c: BookCandidate): BookCandidate {
        if (c.description != null || c.olWorkKey == null) return c
        val work: OlWork = client.get("$BASE${c.olWorkKey}.json").body()
        val text = when (val d = work.description) {
            is JsonPrimitive -> d.content
            is JsonObject -> d["value"]?.jsonPrimitive?.content
            else -> null
        }
        return c.copy(description = text?.trim()?.ifEmpty { null })
    }

    private suspend fun query(params: io.ktor.client.request.HttpRequestBuilder.() -> Unit): List<BookCandidate> {
        val res: OlSearch = client.get("$BASE/search.json") {
            parameter("fields", "key,title,author_name,cover_i,number_of_pages_median,isbn,subject")
            params()
        }.body()
        return res.docs.map { it.toCandidate() }
    }

    private fun OlDoc.toCandidate() = BookCandidate(
        title = title,
        authors = authorName.joinToString(", "),
        isbn = isbn.firstNotNullOfOrNull { Isbn.normalize(it) },
        pageCount = pages,
        coverUrl = coverId?.let { "https://covers.openlibrary.org/b/id/$it-L.jpg" },
        subjects = subject.take(15),
        olWorkKey = key,
    )

    @Serializable private data class OlSearch(val docs: List<OlDoc> = emptyList())

    @Serializable private data class OlDoc(
        val key: String,
        val title: String,
        @SerialName("author_name") val authorName: List<String> = emptyList(),
        @SerialName("cover_i") val coverId: Long? = null,
        @SerialName("number_of_pages_median") val pages: Int? = null,
        val isbn: List<String> = emptyList(),
        val subject: List<String> = emptyList(),
    )

    @Serializable private data class OlWork(val description: JsonElement? = null)

    private companion object { const val BASE = "https://openlibrary.org" }
}
