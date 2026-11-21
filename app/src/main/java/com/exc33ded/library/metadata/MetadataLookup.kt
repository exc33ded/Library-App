package com.exc33ded.library.metadata

import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * Open Library first, Google Books as fallback / description filler.
 * One source failing is tolerated; if every source fails the last error is rethrown
 * so the UI can say "offline" instead of "not found".
 */
@Singleton
class MetadataLookup(private val ol: BookSource, private val gb: BookSource) {
    @Inject constructor(ol: OpenLibrarySource, gb: GoogleBooksSource) : this(ol as BookSource, gb as BookSource)

    suspend fun lookupIsbn(isbn: String): BookCandidate? {
        val errors = mutableListOf<Exception>()
        val fromOl = attempt(errors) { ol.byIsbn(isbn) }
        val fromGb = if (fromOl == null) attempt(errors) { gb.byIsbn(isbn) } else null
        val base = fromOl ?: fromGb ?: run { if (errors.size == 2) throw errors.last(); return null }
        return details(base)
    }

    suspend fun search(query: String): List<BookCandidate> {
        val errors = mutableListOf<Exception>()
        val results = (attempt(errors) { ol.search(query) }.orEmpty() + attempt(errors) { gb.search(query) }.orEmpty())
            .distinctBy { it.title.lowercase().trim() to it.authors.lowercase().substringBefore(",").trim() }
        if (results.isEmpty() && errors.size == 2) throw errors.last()
        return results
    }

    /** Description: OL work → Google Books by ISBN. Never throws. */
    suspend fun details(c: BookCandidate): BookCandidate {
        val errors = mutableListOf<Exception>()
        var r = attempt(errors) { ol.enrich(c) } ?: c
        if (r.description == null && r.isbn != null) {
            attempt(errors) { gb.byIsbn(r.isbn!!) }?.let { g ->
                r = r.copy(
                    description = g.description,
                    pageCount = r.pageCount ?: g.pageCount,
                    coverUrl = r.coverUrl ?: g.coverUrl,
                    subjects = r.subjects.ifEmpty { g.subjects },
                )
            }
        }
        return r
    }

    private suspend fun <T> attempt(errors: MutableList<Exception>, block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        errors += e; null
    }
}
