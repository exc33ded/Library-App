package com.exc33ded.library.metadata

interface BookSource {
    suspend fun byIsbn(isbn: String): BookCandidate?
    suspend fun search(query: String, limit: Int = 10): List<BookCandidate>
    /** Fill in details the search response lacks (e.g. description). */
    suspend fun enrich(c: BookCandidate): BookCandidate = c
}
