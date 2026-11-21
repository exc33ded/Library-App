package com.exc33ded.library.metadata

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.serialization.Serializable

class GoogleBooksSource(private val client: HttpClient, private val apiKey: String) : BookSource {

    override suspend fun byIsbn(isbn: String): BookCandidate? = fetch("isbn:$isbn", 1).firstOrNull()

    override suspend fun search(query: String, limit: Int): List<BookCandidate> = fetch(query, limit)

    private suspend fun fetch(q: String, limit: Int): List<BookCandidate> {
        val res: GbResponse = client.get("https://www.googleapis.com/books/v1/volumes") {
            parameter("q", q)
            parameter("maxResults", limit.coerceIn(1, 40))
            if (apiKey.isNotBlank()) parameter("key", apiKey)
        }.body()
        return res.items.map { it.volumeInfo.toCandidate() }
    }

    private fun GbVolume.toCandidate() = BookCandidate(
        title = title,
        authors = authors.joinToString(", "),
        isbn = industryIdentifiers.firstOrNull { it.type == "ISBN_13" }?.identifier?.let(Isbn::normalize)
            ?: industryIdentifiers.firstOrNull { it.type == "ISBN_10" }?.identifier?.let(Isbn::normalize),
        description = description,
        pageCount = pageCount,
        coverUrl = imageLinks?.thumbnail?.replace("http://", "https://")?.replace("&edge=curl", ""),
        subjects = categories,
    )

    @Serializable private data class GbResponse(val items: List<GbItem> = emptyList())
    @Serializable private data class GbItem(val volumeInfo: GbVolume)
    @Serializable private data class GbVolume(
        val title: String = "",
        val authors: List<String> = emptyList(),
        val description: String? = null,
        val pageCount: Int? = null,
        val categories: List<String> = emptyList(),
        val imageLinks: GbImages? = null,
        val industryIdentifiers: List<GbId> = emptyList(),
    )
    @Serializable private data class GbImages(val thumbnail: String? = null)
    @Serializable private data class GbId(val type: String, val identifier: String)
}
