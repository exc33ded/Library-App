package com.exc33ded.library.imports

import com.exc33ded.library.metadata.BookCandidate
import com.exc33ded.library.metadata.MetadataLookup

data class CoverMatch(val candidate: BookCandidate, val query: String, val score: Double)

/**
 * Tries each query variant, ranks the results against the OCR text and returns the first confident one.
 * If none is confident, returns the best-scoring candidate seen across all variants (the review screen
 * lets the user correct it). Lookup errors propagate to the caller.
 */
class CoverMatcher(private val lookup: MetadataLookup) {
    suspend fun match(lines: List<OcrLine>): CoverMatch? {
        var best: CoverMatch? = null
        for (q in buildQueries(lines)) {
            val top = lookup.search(q).map { CoverMatch(it, q, scoreCandidate(lines, it)) }.maxByOrNull { it.score } ?: continue
            if (top.score >= MATCH_THRESHOLD) return top
            if (best == null || top.score > best.score) best = top
        }
        return best
    }
}
