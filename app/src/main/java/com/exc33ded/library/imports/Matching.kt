package com.exc33ded.library.imports

import com.exc33ded.library.metadata.BookCandidate
import com.exc33ded.library.metadata.Isbn
import kotlin.math.abs

/** A candidate scoring at least this (0..1) against the OCR text is accepted without trying more queries. */
const val MATCH_THRESHOLD = 0.75 // author + big-word overlap alone tops out at 0.5, so the title has to match

private val STOP_WORDS = setOf("the", "a", "an", "of", "and", "in", "to", "on", "for", "la", "le", "el", "der", "die", "das")

private fun tokens(s: String): List<String> =
    s.lowercase().replace(Regex("[^\\p{L}\\p{N} ]"), " ").split(Regex("\\s+")).filter { it.length >= 2 }

fun editDistance(a: String, b: String): Int {
    var prev = IntArray(b.length + 1) { it }
    for (i in 1..a.length) {
        val cur = IntArray(b.length + 1).also { it[0] = i }
        for (j in 1..b.length) {
            cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
        }
        prev = cur
    }
    return prev[b.length]
}

/** Exact hit, or within a small edit distance (OCR misreads like "velloweace" for "yellowface"); short words must match exactly. */
fun fuzzyHas(word: String, ocr: Set<String>): Boolean {
    if (word in ocr) return true
    val max = when { word.length >= 8 -> 2; word.length >= 5 -> 1; else -> 0 }
    return max > 0 && ocr.any { abs(it.length - word.length) <= max && editDistance(it, word) <= max }
}

/**
 * 0.5 * share of title words found in the OCR text + 0.2 * share of author surnames found
 * + 0.3 * share of the cover's big words (the words buildQuery keeps) that the candidate explains,
 * so a short title like "Poems" no longer beats "100 Selected Poems".
 */
fun scoreCandidate(lines: List<OcrLine>, c: BookCandidate): Double {
    val ocr = lines.flatMap { tokens(it.text) }.toSet()
    val allTitle = tokens(c.title)
    val title = allTitle.filter { it !in STOP_WORDS }.ifEmpty { allTitle }
    val surnames = c.authors.split(',').flatMap { tokens(it.trim().substringAfterLast(' ')) }
    fun share(words: List<String>) = if (words.isEmpty()) 0.0 else words.count { fuzzyHas(it, ocr) }.toDouble() / words.size
    val core = tokens(buildQuery(lines)).toSet()
    val candidateWords = (allTitle + tokens(c.authors)).toSet()
    val explained = if (core.isEmpty()) 0.0 else core.count { fuzzyHas(it, candidateWords) }.toDouble() / core.size
    return 0.5 * share(title) + 0.2 * share(surnames) + 0.3 * explained
}

/** Search queries to try in order: everything, the two biggest lines, then each of the three biggest lines alone. */
fun buildQueries(lines: List<OcrLine>): List<String> {
    val full = buildQuery(lines)
    if (full.isBlank()) return emptyList()
    val byHeight = lines.sortedByDescending { it.height }
    return (listOf(full, buildQuery(byHeight.take(2))) + byHeight.take(3).map { buildQuery(listOf(it)) })
        .filter { it.isNotBlank() }
        .distinct()
}

private val ISBN_CANDIDATE = Regex("(?:97[89][-\\s]?)?(?:\\d[-\\s]?){9}[\\dXx]")

/** First valid ISBN-13 / ISBN-10 in the OCR text (back covers and copyright pages print one), returned as ISBN-13, or null. */
fun extractIsbn(lines: List<OcrLine>): String? {
    val text = lines.joinToString(" ") { it.text }
    return ISBN_CANDIDATE.findAll(text).firstNotNullOfOrNull { Isbn.normalize(it.value) }
}
