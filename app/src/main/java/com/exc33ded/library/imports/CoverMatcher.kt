package com.exc33ded.library.imports

import com.exc33ded.library.metadata.BookCandidate
import com.exc33ded.library.metadata.MetadataLookup

data class CoverMatch(val candidate: BookCandidate, val query: String, val score: Double)

/**
 * Tries each query variant, ranks the results against the OCR text and returns the first confident one.
 * If none is confident, falls back to the first result of the first query that returned anything
 * (the review screen lets the user correct it). Lookup errors propagate to the caller.
 */
class CoverMatcher(private val lookup: MetadataLookup) {
    suspend fun match(lines: List<OcrLine>): CoverMatch? {
        var fallback: CoverMatch? = null
        for (q in buildQueries(lines)) {
            val results = lookup.search(q)
            val best = results.map { CoverMatch(it, q, scoreCandidate(lines, it)) }.maxByOrNull { it.score } ?: continue
            if (best.score >= MATCH_THRESHOLD) return best
            if (fallback == null) fallback = CoverMatch(results.first(), q, scoreCandidate(lines, results.first()))
        }
        return fallback
    }
}
